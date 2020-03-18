package com.cs4347.cadence.musicPlayer;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;

import com.cs4347.cadence.MainActivity;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MediaPlayerHolder implements PlayerAdapter {

    private static final int PLAYBACK_POSITION_REFRESH_INTERVAL_MS = 1000;
    private static final int SONG_CHANGE_INTERVAL = 5000;

    private final Context mContext;
    private MediaPlayer mMediaPlayer;
    private int mResourceId;
    private PlaybackInfoListener mPlaybackInfoListener;
    private ScheduledExecutorService mExecutor;
    private Runnable mSeekbarPositionUpdateTask;
    private SongSelector songLibrary;
    private long lastLoadTime;

    public MediaPlayerHolder(Context context) {
        mContext = context.getApplicationContext();
    }


    /**
     * Once the {@link MediaPlayer} is released, it can't be used again, and another one has to be
     * created. In the onStop() method of the {@link MainActivity} the {@link MediaPlayer} is
     * released. Then in the onStart() of the {@link MainActivity} a new {@link MediaPlayer}
     * object has to be created. That's why this method is private, and called by load(int) and
     * not the constructor.
     */
    private void initializeMediaPlayer() {
        if (mMediaPlayer == null) {
            songLibrary = new SongSelector();
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    stopUpdatingCallbackWithPosition(true);
//                   logToUI("MediaPlayer playback completed");
                    if (mPlaybackInfoListener != null) {
                        mPlaybackInfoListener.onStateChanged(PlaybackInfoListener.State.COMPLETED);
                        mPlaybackInfoListener.onPlaybackCompleted();
                    }
                }
            });
        }
    }

    public void setPlaybackInfoListener(PlaybackInfoListener listener) {
        mPlaybackInfoListener = listener;
    }

    @Override
    public void loadMedia(int bpm) {
        initializeMediaPlayer();

        mResourceId = songLibrary.getNextSong(bpm);

        AssetFileDescriptor assetFileDescriptor = mContext.getResources().openRawResourceFd(mResourceId);

        try {
            mMediaPlayer.setDataSource(assetFileDescriptor);
        } catch (Exception e) {
            logToUI("Failed to load file");
        }

        try {
            mMediaPlayer.prepare();
        } catch (Exception e) {
            logToUI("Failed to prepare file");
        }
        initializeProgressCallback();
    }

    @Override
    public void release() {
        if (mMediaPlayer != null) {
            logToUI("release() and mMediaPlayer = null");
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public boolean isPlaying() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.isPlaying();
        }
        return false;
    }

    @Override
    public void play() {
        if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
            logToUI(String.format("playbackStart() %s",
                    mContext.getResources().getResourceEntryName(mResourceId)));
            mMediaPlayer.start();
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onStateChanged(PlaybackInfoListener.State.PLAYING);
            }
            startUpdatingCallbackWithPosition();
        }
    }

    @Override
    public void reset() {
        if (mMediaPlayer != null) {
            logToUI("playbackReset()");
            mMediaPlayer.reset();
//            loadMedia(120);
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onStateChanged(PlaybackInfoListener.State.RESET);
            }
            stopUpdatingCallbackWithPosition(true);
        }
    }

    @Override
    public void pause() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onStateChanged(PlaybackInfoListener.State.PAUSED);
            }
            logToUI("playbackPause()");
        }
    }

    @Override
    public void seekTo(int position) {
        if (mMediaPlayer != null) {
//            logToUI(String.format("seekTo() %d ms", position));
            mMediaPlayer.seekTo(position);
        }
    }

    /**
     * Syncs the mMediaPlayer position with mPlaybackProgressCallback via recurring task.
     */
    private void startUpdatingCallbackWithPosition() {
        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        if (mSeekbarPositionUpdateTask == null) {
            mSeekbarPositionUpdateTask = new Runnable() {
                @Override
                public void run() {
                    updateProgressCallbackTask();
                }
            };
        }

        mExecutor.scheduleAtFixedRate(
                mSeekbarPositionUpdateTask,
                0,
                PLAYBACK_POSITION_REFRESH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void stopUpdatingCallbackWithPosition(boolean resetUIPlaybackPosition) {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
            mSeekbarPositionUpdateTask = null;
            if (resetUIPlaybackPosition && mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onPositionChanged((0));
            }
        }
    }

    private void updateProgressCallbackTask() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            int currentPosition = mMediaPlayer.getCurrentPosition();
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener.onPositionChanged(currentPosition);
            }
        }
    }

    @Override
    public void initializeProgressCallback() {
        final int duration = mMediaPlayer.getDuration();
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener.onDurationChanged(duration);
            mPlaybackInfoListener.onPositionChanged(0);
        }
    }

    private void logToUI(String message) {
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener.onLogUpdated(message);
        }
    }

    synchronized public void updateBpm(int newBpm) {
        logToUI(String.format(Locale.ENGLISH, "BGM Updated %d. Should change bpm: %b. Time from last: %d",
                newBpm, songLibrary.isShouldChangeBpm(newBpm), System.currentTimeMillis() - lastLoadTime));
        if (System.currentTimeMillis() - lastLoadTime < SONG_CHANGE_INTERVAL || !songLibrary.isShouldChangeBpm(newBpm)) {
            return;
        }
        boolean isAutoStart = mMediaPlayer.isPlaying();
        lastLoadTime = System.currentTimeMillis();
        reset();
        loadMedia(newBpm);
        if (mMediaPlayer != null && isAutoStart) {
            mMediaPlayer.seekTo(
                    mMediaPlayer.getDuration() / 2);
            play();
        }
    }
}
