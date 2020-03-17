package com.cs4347.cadence

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.cs4347.cadence.musicPlayer.MediaPlayerHolder
import com.cs4347.cadence.musicPlayer.PlaybackInfoListener
import java.util.concurrent.Semaphore


class MainActivity : AppCompatActivity() {
    var isStarted = false
    var toggleSemaphore = Semaphore(1)

    private lateinit var mPlayerAdapter : MediaPlayerHolder
    lateinit var mTextDebug: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Intent(this, CadenceTrackerService::class.java).also { intent ->
            startService(intent)
        }
        val button = findViewById<Button>(R.id.button2)
        isStarted = true
        button.text = "Stop"
        initializeUI()
        initializePlaybackController()
    }

    override fun onStart() {
        super.onStart()
        mPlayerAdapter.loadMedia(120)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations && mPlayerAdapter.isPlaying) {

        } else {
            mPlayerAdapter.release()
        }
    }

    fun toggleCounting(view: View) {
        toggleSemaphore.acquire()
        if (isStarted) {
            stopCounting()
        } else {
            startCounting()
        }
        toggleSemaphore.release()
    }

    private fun startCounting() {
        Intent(this, CadenceTrackerService::class.java).also { intent ->
            startService(intent)
        }
        val button = findViewById<Button>(R.id.button2)
        isStarted = true
        button.text = "Stop"
    }

    private fun stopCounting() {
        Intent(this, CadenceTrackerService::class.java).also { intent ->
            stopService(intent)
        }
        val button = findViewById<Button>(R.id.button2)
        isStarted = false
        button.text = "Start"
    }

    private fun initializeUI(){
        mTextDebug = findViewById<TextView>(R.id.textView)

        val mPlayButton = findViewById<Button>(R.id.play_button)
        mPlayButton.text = "Play"

        val mPauseButton = findViewById<Button>(R.id.pause_button)
        mPauseButton.text = "Pause"

        val mResetButton = findViewById<Button>(R.id.reset_button)
        mResetButton.text = "Stop"

        mPlayButton.setOnClickListener  { mPlayerAdapter.play() }
        mPauseButton.setOnClickListener { mPlayerAdapter.pause() }
        mResetButton.setOnClickListener { mPlayerAdapter.reset() }

    }

    private fun initializePlaybackController() {
        val mMediaPlayerHolder = MediaPlayerHolder(this)
        mMediaPlayerHolder.setPlaybackInfoListener(PlaybackListener())
        mPlayerAdapter = mMediaPlayerHolder
    }

    inner class PlaybackListener : PlaybackInfoListener() {
        override fun onStateChanged(@State state: Int) {
            val stateToString = convertStateToString(state)
            onLogUpdated(String.format("onStateChanged(%s)", stateToString))
        }

        override fun onPlaybackCompleted() {
            mPlayerAdapter.reset()
            mPlayerAdapter.loadMedia(109)
            mPlayerAdapter.play()
            onLogUpdated("Playback Completed")
        }

        override fun onLogUpdated(message: String?) {
            if (mTextDebug != null) {
                mTextDebug.append(message)
                mTextDebug.append("\n")
                // Moves the scrollContainer focus to the end.
//                mScrollContainer.post(
//                    Runnable { mScrollContainer.fullScroll(ScrollView.FOCUS_DOWN) })
            }
        }
    }
}
