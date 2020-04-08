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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bluetoothscanning.BluetoothConfig
import com.bluetoothscanning.Config
import com.cs4347.cadence.audio.CadenceAudioPlayerService
import com.gauravk.audiovisualizer.model.AnimSpeed
import kotlinx.android.synthetic.main.cadence_activity_main.*
import java.util.concurrent.Semaphore


class MainActivity : AppCompatActivity() {
    private var isStarted = false
    private val broadcastReceivers: MutableList<BroadcastReceiver> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.cadence_activity_main)
        visualiserView.setAnimationSpeed(AnimSpeed.FAST)
        isStarted = true
        Intent(this, CadenceAudioPlayerService::class.java).also { intent ->
            startService(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        checkAndRequestAudioRecord()
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
        registerReceivers()
        sendBroadcast(Intent(ACTION_GET_STEPS_PER_MINUTE))
        sendBroadcast(Intent(ACTION_REQUEST_AUDIO_STATE))
    }

    override fun onDestroy() {
        broadcastReceivers.forEach(this::unregisterReceiver)
        super.onDestroy()
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

    private fun checkAndRequestAudioRecord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("This app needs record audio access.")
                builder.setMessage("Please grant access so that the visualizer works.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener(DialogInterface.OnDismissListener {
                    requestPermissions(
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        PERMISSION_REQUEST_RECORD_AUDIO
                    )
                })
                builder.show()
            } else {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Functionality limited")
                builder.setMessage("Since Audio Record access has not been granted, this app will not be able to recognize your steps.")
                builder.setPositiveButton(android.R.string.ok, null)
                builder.setOnDismissListener(DialogInterface.OnDismissListener { })
                builder.show()
            }
        }
    }

    fun close(view: View) {
        Intent(this, CadenceAudioPlayerService::class.java).also { intent ->
            stopService(intent)
        }
        Intent(this, CadenceTrackerEsenseService::class.java).also { intent ->
            stopService(intent)
        }
        Intent(this, CadenceTrackerService::class.java).also { intent ->
            stopService(intent)
        }
        Intent(this, CadenceTrackerAccelerometerService::class.java).also { intent ->
            stopService(intent)
        }
        finish()
    }

    private fun registerReceivers() {
        val stepReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) {
                    throw IllegalArgumentException("Intent cannot be null.")
                }
                val stepsPerMinute = intent.getDoubleExtra("STEPS_PER_MINUTE", -1.0)
                val totalStepsTaken = intent.getIntExtra("TOTAL_STEPS_TAKEN", -1)

                if (totalStepsTaken < 0 || stepsPerMinute < 0) {
                    return
                }

                waitingForStepsLabelTextView.visibility = View.GONE
                stepsPerMinuteLabelTextView.visibility = View.VISIBLE
                stepsPerMinuteView.visibility = View.VISIBLE
                totalStepsTakenView.visibility = View.VISIBLE
                totalStepsTakenLabelTextView.visibility = View.VISIBLE

                totalStepsTakenView.text = totalStepsTaken.toString()
                stepsPerMinuteView.text = stepsPerMinute.toInt().toString()
            }
        }
        broadcastReceivers.add(stepReceiver)
        registerReceiver(stepReceiver, IntentFilter(ACTION_UPDATE_STEPS_PER_MINUTE).also {
            it.addAction(ACTION_SEND_STEPS_PER_MINUTE)
        })

        val audioStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) {
                    throw IllegalArgumentException("Intent cannot be null.")
                }
                val trackBpm = intent.getIntExtra("CURRENT_TRACK_BPM", -1)
                val trackName = intent.getStringExtra("CURRENT_TRACK_NAME")
                val isLoading = intent.getBooleanExtra("IS_LOADING", false)
                val isPaused = intent.getBooleanExtra("IS_PAUSED", false)
                val audioSessionId = intent.getIntExtra("AUDIO_SESSION_ID", -1)

                if (audioSessionId < 0) {
                    return
                }

                try {
                    visualiserView.setAudioSessionId(audioSessionId)
                } catch (re: RuntimeException) {
                    // No permissions
                }

                if (isLoading) {
                    songBpmTextView.text = ""
                    songTitleTextView.text = "Loading next track..."
                    playPauseButton.isEnabled = false
                    return
                }

                if (trackBpm < 0 || trackName == null) {
                    songTitleTextView.text = "Waiting for pace..."
                    songTitleTextView.text = "Please start walking or running"
                    playPauseButton.isEnabled = false
                    return
                }


                songTitleTextView.text = trackName
                songBpmTextView.text = "$trackBpm Beats Per Minute"

                playPauseButton.isEnabled = true
                playPauseButton.icon =
                    getDrawable(if (isPaused) R.drawable.ic_play_arrow_black_18dp else R.drawable.ic_pause_24px)
                playPauseButton.setOnClickListener {
                    sendBroadcast(Intent(if (isPaused) ACTION_PLAY_AUDIO else ACTION_PAUSE_AUDIO))
                }
            }
        }

        broadcastReceivers.add(audioStateReceiver)
        registerReceiver(audioStateReceiver, IntentFilter(ACTION_AUDIO_STATE_UPDATED))
    }

    companion object {
        private val PERMISSION_REQUEST_FINE_LOCATION = 1
        private val PERMISSION_REQUEST_BACKGROUND_LOCATION = 2
        private val PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 3
        private val PERMISSION_REQUEST_RECORD_AUDIO = 4
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