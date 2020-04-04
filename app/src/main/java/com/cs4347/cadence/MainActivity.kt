package com.cs4347.cadence

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bluetoothscanning.BluetoothConfig
import com.bluetoothscanning.Config
import com.cs4347.cadence.audio.CadenceAudioPlayerService
import com.cs4347.cadence.musicPlayer.MediaPlayerHolder
import com.cs4347.cadence.musicPlayer.PlaybackInfoListener
import java.util.concurrent.Semaphore
import kotlin.math.roundToInt


class MainActivity : AppCompatActivity() {
    private var isStarted = false
    private var toggleSemaphore = Semaphore(1)

    private lateinit var mPlayerAdapter: MediaPlayerHolder
    lateinit var mTextDebug: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.cadence_activity_main)
        val button = findViewById<Button>(R.id.button2)
        isStarted = true
        button.text = "Stop"
        initializeUI()
        initializePlaybackController()
        Intent(this, CadenceAudioPlayerService::class.java).also { intent ->
            startService(intent)
        }
//        registerReceiver(object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                mPlayerAdapter.updateBpm(
//                    intent!!.getDoubleExtra("STEPS_PER_MINUTE", 1f.toDouble()).roundToInt()
//                )
//            }
//        }, IntentFilter(ACTION_UPDATE_STEPS_PER_MINUTE))

    }

    override fun onStart() {
        super.onStart()
        if (IS_USING_ESENSE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkAndRequestBluetooth()
            }
            val deviceName = intent.getStringExtra("DEVICE_NAME")
            if (deviceName != null) {
                Intent(this, CadenceTrackerEsenseService::class.java).also { intent ->
                    intent.putExtra("DEVICE_NAME", deviceName)
                    this.startService(intent)
                }
            } else {
                ActivityCodeBuilder(this, 1)
                    .setBackgroundColor(Color.parseColor("#1E90FF"))
                    .setPulseColor(Color.parseColor("#ffffff"))
                    .start()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkAndRequestActivityRecognition()
            }
            Intent(this, CadenceTrackerService::class.java).also { intent ->
                startService(intent)
            }
        }
        mPlayerAdapter.loadMedia(120)
    }

    private fun checkAndRequestBluetooth() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("This app needs background location access")
                    builder.setMessage("Please grant location access so this app can detect beacons in the background.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener(DialogInterface.OnDismissListener {
                        requestPermissions(
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            PERMISSION_REQUEST_BACKGROUND_LOCATION
                        )
                    })
                    builder.show()
                } else {
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("Functionality limited")
                    builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener(DialogInterface.OnDismissListener { })
                    builder.show()
                }
            }
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ),
                    PERMISSION_REQUEST_FINE_LOCATION
                )
            } else {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Functionality limited")
                builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.  Please go to Settings -> Applications -> Permissions and grant location access to this app.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener(DialogInterface.OnDismissListener { })
                builder.show()
            }
        }
    }

    private fun checkAndRequestActivityRecognition() {
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION)) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("This app needs activity recognition access")
                builder.setMessage("Please grant activity recognition so this app can detect your pace.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener(DialogInterface.OnDismissListener {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                        PERMISSION_REQUEST_ACTIVITY_RECOGNITION
                    )
                })
                builder.show()
            } else {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Functionality limited")
                builder.setMessage("Since Activity Recognition access has not been granted, this app will not be able to recognize your steps.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener(DialogInterface.OnDismissListener { })
                builder.show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations && mPlayerAdapter.isPlaying) {
            return
        }

        mPlayerAdapter.release()
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

    private fun initializeUI() {
        mTextDebug = findViewById<TextView>(R.id.textView)

        val mPlayButton = findViewById<Button>(R.id.play_button)
        mPlayButton.text = "130 BPM"

        val mClearButton = findViewById<Button>(R.id.clear_button)
        mClearButton.text = "Clear"

        val mResetButton = findViewById<Button>(R.id.reset_button)
        mResetButton.text = "138 BPM"

        mPlayButton.setOnClickListener {
//            mPlayerAdapter.play()
            sendBroadcast(Intent(ACTION_UPDATE_STEPS_PER_MINUTE).also {
                it.putExtra("STEPS_PER_MINUTE", 130.0)
            })
        }
        mClearButton.setOnClickListener {
            mTextDebug.text = ""
        }
        mResetButton.setOnClickListener {
//            mPlayerAdapter.reset()
            sendBroadcast(Intent(ACTION_UPDATE_STEPS_PER_MINUTE).also {
                it.putExtra("STEPS_PER_MINUTE", 138.0)
            })
        }

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
            onLogUpdated("Playback Completed")
        }

        override fun onLogUpdated(message: String?) {
            if (mTextDebug != null) {
                mTextDebug.append(message)
                mTextDebug.append("\n")
            }
        }
    }

    companion object {
        private val PERMISSION_REQUEST_FINE_LOCATION = 1
        private val PERMISSION_REQUEST_BACKGROUND_LOCATION = 2
        private val PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 2
    }
}


class ActivityCodeBuilder(private val activity: Activity, private val code: Int) :
    BluetoothConfig.Builder(activity) {
    override fun start() {
        val intent = Intent(activity, CadenceBluetoothDetection::class.java)
        intent.putExtra(Config.EXTRA_CONFIG, config)
        intent.putExtra(Config.EXTRA_Listener, listener)
        activity.startActivityForResult(intent, code)
    }
}