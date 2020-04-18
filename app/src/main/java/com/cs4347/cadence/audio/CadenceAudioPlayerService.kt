package com.cs4347.cadence.audio

import android.annotation.SuppressLint
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
import android.util.Log
import androidx.annotation.RequiresApi
import com.cs4347.cadence.*
import com.cs4347.cadence.musicPlayer.SongSelector
import com.cs4347.cadence.util.PrioLock
import com.cs4347.cadence.voice.SpeechAdapter
import com.cs4347.cadence.voice.SpeechHandler
import com.cs4347.cadence.voice.VoiceCommandAdapter
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.properties.Delegates

// CadenceAudioPlayerService handles Audio Playback
class CadenceAudioPlayerService : Service() {
    // Notification for Foreground Service
    private var builder: Notification.Builder? = null

    // Channel ID for notification
    private var channelId: String? = null

    // AudioTrack API used for playback
    private var audioTrack: AudioTrack? = null

    // songLibrary determines which song set to play
    private val songLibrary = SongSelector()

    // The current loaded song set
    private var currentSongSet by Delegates.observable<LoadedTimeShiftedSongSet?>(null) { _, _, _ ->
        broadcastState()
    }

    // lastAudioTrackIndex is the index of the last byte written to audioTrack relative to the first
    // byte written after the audioTrack was last flushed
    private var lastAudioTrackIndex = 0

    // curWritingIndex is the start index in the current song of the next buffer to be written to the
    // audioTrack
    private var curWritingIndex = 0

    // The currently playing song
    private var currentSong by Delegates.observable<LoadedSong?>(null) { _, _, _ ->
        broadcastState()
    }

    // Mutex used to prevent race conditions between onPeriodicCallback and bpmChanged
    private var bufferMutex = PrioLock()

    // Timestamp of the last time bpmChanged was called.
    private var lastBpmChangeTime: Long = 0

    // Timestamp of the last time the last song set was loaded
    private var lastSongChangeTime: Long = 0

    // Boolean representing whether the service is currently decoding a new song set
    private var isLoading: Boolean by Delegates.observable(false) { _, _, _ ->
        broadcastState()
    }

    // Handlers for messages received from Broadcasts API
    private var broadcastReceivers: MutableList<BroadcastReceiver> = ArrayList()

    // For text to speech feedback
    private lateinit var mSpeechHandler: SpeechAdapter

    // For speech commands
    private lateinit var voiceAdapter: VoiceCommandAdapter

    override fun onBind(intent: Intent): IBinder {
        return Binder()
    }

    override fun onCreate() {
        channelId = getNewChannelId()
        mSpeechHandler = SpeechHandler(this)
        voiceAdapter = VoiceCommandAdapter(this)
        initializeAudioTrack()
        registerBroadcastReceivers()
        broadcastState()
        val notificationBuilder = getNotificationBuilder()
        if (notificationBuilder != null) {
            startForeground(2, notificationBuilder.build())
        }

        super.onCreate()
    }

    override fun onDestroy() {
        broadcastReceivers.forEach(this::unregisterReceiver)
        audioTrack?.pause()
        audioTrack?.flush()
        voiceAdapter.onStop()
        super.onDestroy()
    }

    // Initialize AudioTrack instance with appropriate parameters.
    private fun initializeAudioTrack() {
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
        audioTrack.setPlaybackPositionUpdateListener(object :
            AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                return
            }

            override fun onPeriodicNotification(track: AudioTrack?) {
                if (track == null) {
                    return
                }
                CompletableFuture.runAsync {
                    onPeriodicPlayback(track)
                }
            }
        })
        audioTrack.positionNotificationPeriod =
            round(SAMPLE_RATE * BUFFER_SIZE_SECONDS * 0.9).toInt()
        this.audioTrack = audioTrack
    }

    // Registers receivers for Broadcasts API
    private fun registerBroadcastReceivers() {
        val stepsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) {
                    throw IllegalArgumentException("Intent cannot be null.")
                }
                val newBpm = intent.getDoubleExtra("STEPS_PER_MINUTE", 1f.toDouble()).roundToInt()
                Log.d(TAG, "Received BPM: $newBpm")
                this@CadenceAudioPlayerService.bpmChanged(newBpm)
            }
        }
        broadcastReceivers.add(stepsReceiver)
        registerReceiver(stepsReceiver, IntentFilter(ACTION_UPDATE_STEPS_PER_MINUTE))

        val stateRequestReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                this@CadenceAudioPlayerService.broadcastState()
            }
        }
        registerReceiver(stateRequestReceiver, IntentFilter(ACTION_REQUEST_AUDIO_STATE))
        broadcastReceivers.add(stateRequestReceiver)

        val playRequestReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                tryResumeFromPause()
            }
        }
        broadcastReceivers.add(playRequestReceiver)
        registerReceiver(playRequestReceiver, IntentFilter(ACTION_PLAY_AUDIO))

        val pauseRequestReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                tryPause()
            }
        }
        broadcastReceivers.add(pauseRequestReceiver)
        registerReceiver(pauseRequestReceiver, IntentFilter(ACTION_PAUSE_AUDIO))
    }

    // Attempt to pause current playback
    private fun tryPause() {
        if (isLoading) {
            return
        }
        CompletableFuture.runAsync {
            bufferMutex.lockPriority()
            try {
                this@CadenceAudioPlayerService.audioTrack?.pause()
                broadcastState()
            } finally {
                bufferMutex.unlock()
            }
        }
    }

    // Attempt to resume playback from paused state
    private fun tryResumeFromPause() {
        if (isLoading) {
            return
        }
        CompletableFuture.runAsync {
            bufferMutex.lockPriority()
            if (this@CadenceAudioPlayerService.audioTrack?.playState != AudioTrack.PLAYSTATE_PAUSED) {
                return@runAsync
            }
            try {
                this@CadenceAudioPlayerService.audioTrack?.flush()
                this@CadenceAudioPlayerService.audioTrack?.stop()
                this@CadenceAudioPlayerService.lastAudioTrackIndex = 0
                initializeAudioTrack()
                this@CadenceAudioPlayerService.audioTrack?.play()
                broadcastState()

                writeNextBuffers(2)
            } finally {
                bufferMutex.unlock()
            }
        }
    }

    // Load next song set into memory and assigns it to this.currentSongSet
    private fun loadAndAssignNextSong(bpm: Int): LoadedTimeShiftedSongSet {
        mSpeechHandler.speak("Loading next song set.")
        this.isLoading = true
        this.currentSongSet = null
        val nextSongInfo = songLibrary.getNextSong(bpm)
        val resourceIds = arrayOf(nextSongInfo.slowId, nextSongInfo.originalId, nextSongInfo.fastId)
        val resourceBpm =
            arrayOf(nextSongInfo.slowBpm, nextSongInfo.originalBpm, nextSongInfo.fastBpm)

        val loadedSongs = resourceIds
            .mapIndexed { i: Int, id: Int ->
                loadSongById(id, resourceBpm[i])
            }

        val result = LoadedTimeShiftedSongSet(loadedSongs[0], loadedSongs[1], loadedSongs[2])
        this.currentSongSet = result

        this.isLoading = false
        this.lastSongChangeTime = System.currentTimeMillis()
        return result
    }

    // Load song by resource ID into PCM 16-bit format
    private fun loadSongById(id: Int, bpm: Int): LoadedSong {
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
                    Log.d(TAG, "Sample Rate: ${format.getInteger(MediaFormat.KEY_SAMPLE_RATE)}")
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
        return LoadedSong(getSongName(id), bpm, list.toByteArray())
    }

    private fun getNotificationBuilder(): Notification.Builder? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null
        }

        val result = builder
        if (result != null) {
            return result
        }

        val newBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentText("You may close this through the Cadence Application")
                .setContentTitle("Cadence Audio Player is running.")
                .setSmallIcon(R.drawable.ic_audiotrack_black_24dp)
        } else {
            return null
        }
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

    // Get the most appropriate song out of the input song set that best matches the input bpm.
    private fun getAppropriateLoadedTrack(songs: LoadedTimeShiftedSongSet, bpm: Int): LoadedSong {
        val loadedSongs = arrayOf(songs.slow, songs.original, songs.fast)

        return loadedSongs.minBy {
            min(abs(it.bpm - bpm), abs(it.bpm - 2 * bpm))
        } ?: throw IllegalArgumentException("No loaded songs.")
    }

    // Called when the users' steps per minute is changed.
    @Synchronized
    private fun bpmChanged(bpm: Int) {
        if (isLoading || System.currentTimeMillis() - lastBpmChangeTime < MIN_BPM_CHECK_INTERVAL) {
            return
        }
        lastBpmChangeTime = System.currentTimeMillis()
        CompletableFuture.runAsync {
            bufferMutex.lockPriority()
            try {
                // Load a song set if uninitialized
                var loadedSongs = this.currentSongSet ?: loadAndAssignNextSong(bpm)

                val currentlyPlaying = this.currentSong
                var closestLoadedTrack = getAppropriateLoadedTrack(loadedSongs, bpm)

                if (currentlyPlaying == null) {
                    // If there is no song currently loaded, then start playing the msot appropriate
                    // track.
                    this.currentSong = closestLoadedTrack
                    audioTrack?.play()
                    writeNextBuffers()
                    return@runAsync
                }

                val shouldChangeSongSet =
                    min(
                        abs(2 * bpm - closestLoadedTrack.bpm),
                        abs(bpm - closestLoadedTrack.bpm)
                    ) > loadedSongs.getAverageDifference() * SONG_CHANGE_TRESHOLD_FACTOR &&
                            System.currentTimeMillis() - lastSongChangeTime > MIN_INTERVAL_BETWEEN_SONG_CHANGE &&
                            songLibrary.getBestFitBpm(bpm) != loadedSongs.original.bpm
                Log.d(
                    TAG,
                    "New BPM: $bpm, shouldChangeSongSet: $shouldChangeSongSet, closestBpm: ${closestLoadedTrack.bpm}"
                )

                if (shouldChangeSongSet) {
                    // Song set is changed if there is a more appropriate song set
                    loadedSongs = loadAndAssignNextSong(bpm)
                    closestLoadedTrack = getAppropriateLoadedTrack(loadedSongs, bpm)
                    this.currentSong = closestLoadedTrack
                    reset()
                    writeNextBuffers()
                    mSpeechHandler.speak(closestLoadedTrack.bpm)
                    return@runAsync
                }

                if (closestLoadedTrack.bpm == currentlyPlaying.bpm) {
                    // No need to change songs.
                    return@runAsync
                }

                // Change current song within the song set
                this.currentSong = closestLoadedTrack
                curWritingIndex =
                    (round((currentlyPlaying.bpm.toDouble() / closestLoadedTrack.bpm.toDouble()) * curWritingIndex).toInt()) / 4 * 4
                writeNextBuffers()
                mSpeechHandler.speak(closestLoadedTrack.bpm)
            } finally {
                bufferMutex.unlock()
            }
        }
    }

    // Flush audio track buffers and reset index counters.
    private fun reset() {
        audioTrack?.flush()
        this.curWritingIndex = 0
        this.lastAudioTrackIndex = 0
        audioTrack?.play()
    }

    // Write the next buffers from the currently playing song into the AudioTrack API
    private fun writeNextBuffers(numBuffers: Int = 1) {
        val currentlyPlaying = this.currentSong ?: return
        for (i in 1..numBuffers) {
            val bytesRemaining = currentlyPlaying.samples.size - curWritingIndex
            if (bytesRemaining <= 0) {
                return
            }

            val sizeWritten = audioTrack?.write(
                currentlyPlaying.samples,
                curWritingIndex,
                min(bytesRemaining, BUFFER_SIZE_BYTES)
            )
            Log.d(TAG, "Written $sizeWritten bytes at ${currentlyPlaying.bpm} BPM")
            curWritingIndex += BUFFER_SIZE_BYTES
            lastAudioTrackIndex += BUFFER_SIZE_BYTES
        }
    }

    // Periodically called when the AudioTrack API is playing back audio
    private fun onPeriodicPlayback(track: AudioTrack) {
        bufferMutex.lock()
        try {
            val currentlyPlaying = this@CadenceAudioPlayerService.currentSong
                ?: throw IllegalStateException("currentlyPlaying is null on callback.")
            val bytesRemaining =
                currentlyPlaying.samples.size - this@CadenceAudioPlayerService.curWritingIndex
            Log.d(
                TAG,
                "${currentlyPlaying.samples.size} ${this@CadenceAudioPlayerService.curWritingIndex} ${bytesRemaining}"
            )
            if (bytesRemaining <= 0) {
                val newSongSet = loadAndAssignNextSong(currentlyPlaying.bpm)
                this@CadenceAudioPlayerService.currentSong =
                    getAppropriateLoadedTrack(newSongSet, currentlyPlaying.bpm)
                reset()
            }
            val samplesLeft = lastAudioTrackIndex - track.playbackHeadPosition * 4
            if (samplesLeft > BUFFER_SIZE_BYTES) {
                return
            }
            writeNextBuffers()
        } finally {
            bufferMutex.unlock()
        }
    }

    // Broadcasts the current state of this service. This is mainly used for the GUI to display
    // the state to users.
    private fun broadcastState() {
        sendBroadcast(Intent(ACTION_AUDIO_STATE_UPDATED).also {
            it.putExtra("CURRENT_TRACK_NAME", currentSong?.name)
            it.putExtra("CURRENT_TRACK_BPM", currentSong?.bpm)
            it.putExtra("AUDIO_SESSION_ID", audioTrack?.audioSessionId)
            it.putExtra("IS_PAUSED", audioTrack?.playState == AudioTrack.PLAYSTATE_PAUSED)
            it.putExtra("IS_LOADING", isLoading)
        })
    }

    @SuppressLint("DefaultLocale")
    private fun getSongName(id: Int): String {
        return resources.getResourceEntryName(id)
            .split(".")[0]
            .split("_")
            .dropLast(1).joinToString(separator = " ") {
                it.capitalize()
            }
    }

    companion object {
        private const val TAG = "CadenceAudioPlayerSvc"
    }
}
