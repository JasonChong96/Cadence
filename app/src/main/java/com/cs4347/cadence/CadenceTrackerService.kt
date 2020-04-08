package com.cs4347.cadence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.cs4347.cadence.sensor.StepListener
import com.cs4347.cadence.sensor.StepSensor
import com.cs4347.cadence.sensor.StepSensorInbuilt


open class CadenceTrackerService : Service(),
    StepListener {
    private var channelId: String? = null
    private var builder: Notification.Builder? = null
    private var lastStepTime = 0L
    private var lastStepIndex = 0
    private var lastStepDeltas = LongArray(DELTA_TIME_BUFFER_SIZE) { -1 }
    private var stepSensor: StepSensor? = null
    protected var deviceName: String = ESENSE_DEVICE_NAME
    private var numSteps = 0
    private var broadcastReceiver: BroadcastReceiver? = null


    override fun onBind(intent: Intent): IBinder {
        return Binder()
    }

    override fun onCreate() {
        stepSensor = getStepSensorInstance()
        channelId = getNewChannelId()

        val notification = getNotificationBuilder()?.build()
        if (notification != null) {
            startForeground(1, notification)
        }
        registerBroadcastReceivers()
        stepSensor?.registerListener(this)
        super.onCreate()
    }

    private fun registerBroadcastReceivers() {
        val requestReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                sendBroadcast(Intent(ACTION_SEND_STEPS_PER_MINUTE).also {
                    it.putExtra("STEPS_PER_MINUTE", getStepsPerMinute())
                    it.putExtra("TOTAL_STEPS_TAKEN", getNumSteps())
                })
            }
        }
        broadcastReceiver = requestReceiver
        registerReceiver(requestReceiver, IntentFilter(ACTION_GET_STEPS_PER_MINUTE))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra("DEVICE_NAME")
        if (deviceName != null) {
            this.deviceName = deviceName
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        stepSensor?.stop()
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)
        }
        super.onDestroy()
    }

    open fun getStepSensorInstance(): StepSensor {
        return StepSensorInbuilt(this)
    }

    override fun step(timeNs: Long) {
        this.numSteps++
        val delta = timeNs - lastStepTime
        lastStepDeltas[lastStepIndex] = if (lastStepTime == 0L) 0 else delta
        this.lastStepTime = timeNs
        lastStepIndex = (lastStepIndex + 1) % lastStepDeltas.size

        if (lastStepDeltas.contains(-1) || channelId == null) {
            return
        }
        sendBroadcast(Intent(ACTION_UPDATE_STEPS_PER_MINUTE).also {
            it.putExtra("STEPS_PER_MINUTE", getStepsPerMinute())
            it.putExtra("TOTAL_STEPS_TAKEN", getNumSteps())
        })
    }

    override fun sensorStopped() {
        stepSensor = null
    }

    private fun getStepsPerMinute(): Double {
        val median =
            (lastStepDeltas.sortedArray()[(lastStepDeltas.size - 1) / 2].toDouble()
                    + lastStepDeltas.sortedArray()[lastStepDeltas.size / 2].toDouble()) / 2
        val mean = lastStepDeltas.average()
        return CadenceTrackerUtils.convertMinToNs(1) / mean
    }

    private fun getNotificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun getNotificationBuilder(): Notification.Builder? {
        val result = builder
        if (result != null) {
            return result
        }

        val newBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentText("You may close this through the Cadence Application")
                .setContentTitle("Cadence Step Tracker is running.")
                .setSmallIcon(R.drawable.ic_directions_run_black_24dp)
        } else {
            return null
        }
        builder = newBuilder

        return newBuilder
    }

    private fun getNewChannelId(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel("my_service", "My Background Service")
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            ""
        }
    }

    private fun getNumSteps(): Int {
        return numSteps
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
}
