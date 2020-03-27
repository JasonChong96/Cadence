package com.cs4347.cadence.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetFileDescriptor
import android.graphics.Color
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.os.trace
import com.cs4347.cadence.R
import com.cs4347.cadence.musicPlayer.SongSelector
import com.cs4347.cadence.util.PrioLock
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt


class CadenceAudioPlayerService : Service() {
    private var builder: Notification.Builder? = null
    private var channelId: String? = null
    private var audioTrack: AudioTrack? = null
    private val songLibrary = SongSelector()
    private var currentSong: LoadedTimeShiftedSong? = null
    private var lastAudioTrackIndex = 0
    private var curWritingIndex = 0
    private var currentlyPlaying: LoadedSong? = null
    private var bufferMutex = PrioLock()
    private var lastBpmChangeTime: Long = 0

    override fun onBind(intent: Intent): IBinder {
        return Binder()
    }

    override fun onCreate() {
        channelId = getNewChannelId()
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(SAMPLE_RATE)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) {
                    throw IllegalArgumentException("Intent cannot be null.")
                }
                this@CadenceAudioPlayerService
                    .bpmChanged(intent.getDoubleExtra("STEPS_PER_MINUTE",
                        1f.toDouble()).roundToInt())
            }
        }, IntentFilter("com.cadence.stepsChanged"))
        audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                return
            }

            override fun onPeriodicNotification(track: AudioTrack?) {
                if (track == null) {
                    return
                }
                CompletableFuture.runAsync {
                        bufferMutex.lock()
                        try {
                            val currentlyPlaying = this@CadenceAudioPlayerService.currentlyPlaying
                                ?: throw IllegalStateException("currentlyPlaying is null on callback.")
                            val bytesRemaining =
                                currentlyPlaying.samples.size - this@CadenceAudioPlayerService.curWritingIndex
                            Log.d(TAG, "${currentlyPlaying.samples.size} ${this@CadenceAudioPlayerService.curWritingIndex} ${bytesRemaining}")
                            if (bytesRemaining <= 0) {
                                val newSongSet = loadAndAssignNextSong(currentlyPlaying.bpm)
                                this@CadenceAudioPlayerService.currentlyPlaying = getAppropriateLoadedTrack(newSongSet, currentlyPlaying.bpm)
                                reset()
                            }
                            val samplesLeft = lastAudioTrackIndex - track.playbackHeadPosition * 4
                            if (samplesLeft > BUFFER_SIZE_SECONDS * SAMPLES_PER_SECOND) {
                                return@runAsync
                            }
                            writeNextBuffer()
                        } finally {
                            bufferMutex.unlock()
                        }
                }
            }
        })
        audioTrack.positionNotificationPeriod = round(SAMPLE_RATE * 1.9).toInt()
        this.audioTrack = audioTrack

//        loadAndAssignNextSong(120)

        super.onCreate()
    }

    private fun loadAndAssignNextSong(bpm: Int): LoadedTimeShiftedSong {
        updateNotification("Loading Song")
        this.currentlyPlaying = null
        this.currentSong = null
        val nextSongInfo = songLibrary.getNextSong(bpm)
        val resourceIds = arrayOf(nextSongInfo.slowId, nextSongInfo.originalId, nextSongInfo.fastId)
        val resourceBpm =
            arrayOf(nextSongInfo.slowBpm, nextSongInfo.originalBpm, nextSongInfo.fastBpm)

        val loadedSongs = resourceIds
            .map(this::loadSongById)
            .mapIndexed { i: Int, bytes: ByteArray ->
                LoadedSong(resourceBpm[i], bytes)
            }

        val result = LoadedTimeShiftedSong(loadedSongs[0], loadedSongs[1], loadedSongs[2])
        this.currentSong = result

        updateNotification("Songs Loaded")
        return result
    }

    private fun loadSongById(id: Int): ByteArray {
        val assetFileDescriptor: AssetFileDescriptor =
            resources.openRawResourceFd(id)

        val mediaExtractor = MediaExtractor()
        mediaExtractor.setDataSource(assetFileDescriptor)

        val format = mediaExtractor.getTrackFormat(0)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalStateException("KEY_MINE is null")
        mediaExtractor.selectTrack(0)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */)
        codec.start()
        var endOfFile = false
        val info = MediaCodec.BufferInfo()
        val list = ArrayList<Byte>()
        var outputEndOfFile = false
        while (!endOfFile) {
            val inputBufferIndex: Int = codec.dequeueInputBuffer(-1)
            if (inputBufferIndex >= 0) {
                val size: Int = codec.getInputBuffer(inputBufferIndex)?.let {
                    mediaExtractor.readSampleData(
                        it, 0
                    )
                }!!
                if (size < 0) {
                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    endOfFile = true
                } else {
                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        size,
                        mediaExtractor.getSampleTime(),
                        0
                    )
                    mediaExtractor.advance()
                }
                val res = codec.dequeueOutputBuffer(info, -1)
                if (res >= 0) {
                    val buf: ByteBuffer = codec.getOutputBuffer(res)
                        ?: throw IllegalArgumentException("Output buffer index invalid")
                    val chunk = ByteArray(info.size)
                    buf.get(chunk) // Read the buffer all at once
                    buf.clear() // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN
                    chunk.forEach {
                        list.add((it).toByte())
                    }
                    codec.releaseOutputBuffer(res, false /* render */)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputEndOfFile = true
                    }
                } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "Format changed on file ${resources.getResourceEntryName(id)}")
                    Log.d(TAG, "Channel count: ${format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}")
                    Log.d(TAG, "Sample Rate ${format.getInteger(MediaFormat.KEY_SAMPLE_RATE)}")
                }
            } else {
                Log.d(TAG, "Negative input buffer index $inputBufferIndex")
            }
        }

        while (!outputEndOfFile) {
            val res = codec.dequeueOutputBuffer(info, -1)
            if (res >= 0) {
                val buf: ByteBuffer = codec.getOutputBuffer(res)
                    ?: throw IllegalArgumentException("Output buffer index invalid")
                val chunk = ByteArray(info.size)
                buf.get(chunk) // Read the buffer all at once
                buf.clear() // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN
                chunk.forEach {
                    list.add(it)
                }
                codec.releaseOutputBuffer(res, false /* render */)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputEndOfFile = true
                }
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "Format changed ${codec.outputFormat}")
            }
        }
        codec.stop()
        assetFileDescriptor.close()
        return list.toByteArray()
    }

    private fun getNotificationBuilder(): Notification.Builder {
        val result = builder
        if (result != null) {
            return result
        }

        val newBuilder = Notification.Builder(this, channelId)
            .setContentText("Started")
            .setContentTitle("Cadence Audio Player")
            .setSmallIcon(R.drawable.ic_launcher_background)
        builder = newBuilder

        return newBuilder
    }

    private fun getNewChannelId(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel("cadence_audio_player_service", TAG)
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    private fun updateNotification(msg: String) {
        val notification: Notification = getNotificationBuilder()
            .setContentText(msg)
            .build()
        val mNotificationManager = getNotificationManager()
        mNotificationManager.notify(1, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(channelId: String, channelName: String): String {
        val chan = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    private fun getNotificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun getAppropriateLoadedTrack(songs: LoadedTimeShiftedSong, bpm: Int): LoadedSong {
        val loadedSongs = arrayOf(songs.slow, songs.original, songs.fast)

        return loadedSongs.minBy {
            abs(it.bpm - bpm)
        } ?: throw IllegalArgumentException("No loaded songs.")
    }

    @Synchronized
    private fun bpmChanged(bpm: Int) {
        if (System.currentTimeMillis() - lastBpmChangeTime < MIN_BPM_CHECK_INTERVAL) {
            return
        }
        lastBpmChangeTime = System.currentTimeMillis()
        CompletableFuture.runAsync {
            bufferMutex.lockPriority()
            try {
                var loadedSongs = this.currentSong ?: loadAndAssignNextSong(bpm)

                val currentlyPlaying = this.currentlyPlaying
                var closestLoadedTrack = getAppropriateLoadedTrack(loadedSongs, bpm)

                if (currentlyPlaying == null) {
//                    reset()
                    this.currentlyPlaying = closestLoadedTrack
                    audioTrack?.play()
                    writeNextBuffer()
                    return@runAsync
                }

                val shouldChangeSongSet =
                    abs(currentlyPlaying.bpm - closestLoadedTrack.bpm) > loadedSongs.getAverageDifference()

                if (shouldChangeSongSet) {
                    loadedSongs = loadAndAssignNextSong(bpm)
                    closestLoadedTrack = getAppropriateLoadedTrack(loadedSongs, bpm)
                    this.currentlyPlaying = closestLoadedTrack
                    reset()
                    writeNextBuffer()
                    audioTrack?.play()
                    return@runAsync
                }

                if (closestLoadedTrack.bpm == currentlyPlaying.bpm) {
                    return@runAsync
                }
                this.currentlyPlaying = closestLoadedTrack
                curWritingIndex =
                    round((currentlyPlaying.bpm.toDouble() / closestLoadedTrack.bpm.toDouble()) * curWritingIndex).toInt()
                writeNextBuffer()
            } finally {
                bufferMutex.unlock()
            }
        }
    }

    private fun reset() {
        audioTrack?.flush()
        this.curWritingIndex = 0
        this.lastAudioTrackIndex = 0
        audioTrack?.play()
    }

    private fun writeNextBuffer() {
        val currentlyPlaying = this.currentlyPlaying ?: return
        val bytesRemaining = currentlyPlaying.samples.size - curWritingIndex
        if (bytesRemaining <= 0) {
            return
        }

        val sizeWritten = audioTrack?.write(
            currentlyPlaying.samples,
            curWritingIndex,
            min(bytesRemaining, BUFFER_SIZE_SECONDS * SAMPLES_PER_SECOND)
        )
        Log.d(TAG, "Written $sizeWritten bytes at ${currentlyPlaying.bpm} BPM")
        curWritingIndex += BUFFER_SIZE_SECONDS * SAMPLES_PER_SECOND
        lastAudioTrackIndex += BUFFER_SIZE_SECONDS * SAMPLES_PER_SECOND
    }

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val SAMPLES_PER_SECOND = SAMPLE_RATE * 2 * 2
        private const val BUFFER_SIZE_SECONDS = 2
        private const val MIN_BPM_CHECK_INTERVAL = 1000
        private const val TAG = "CadenceAudioPlayerService"
    }
}
