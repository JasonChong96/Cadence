package com.cs4347.cadence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import com.cs4347.cadence.sensor.StepListener
import com.cs4347.cadence.sensor.StepSensor
import com.cs4347.cadence.sensor.StepSensorAccelerometer
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


    override fun onBind(intent: Intent): IBinder {
        return Binder()
    }

    override fun onCreate() {
        stepSensor = getStepSensorInstance()
        channelId = getNewChannelId()

        val notification = getNotificationBuilder()?.build()

        startForeground(1, notification)
        stepSensor?.registerListener(this)
        super.onCreate()
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
        super.onDestroy()
    }

    open fun getStepSensorInstance(): StepSensor {
        return StepSensorInbuilt(this)
    }

    override fun step(timeNs: Long) {
        val delta = timeNs - lastStepTime
        lastStepDeltas[lastStepIndex] = if (lastStepTime == 0L) 0 else delta
        this.lastStepTime = timeNs
        lastStepIndex = (lastStepIndex + 1) % lastStepDeltas.size

        if (lastStepDeltas.contains(-1) || channelId == null) {
            return
        }
        sendBroadcast(Intent("com.cadence.stepsChanged").also {
            it.putExtra("STEPS_PER_MINUTE", getStepsPerMinute())
        })
        updateNotification()
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
                .setContentText(getStepsPerMinute().toString())
                .setContentTitle("Test")
                .setSmallIcon(R.drawable.ic_launcher_background)
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

    private fun updateNotification() {
        val notification: Notification = getNotificationBuilder()
            ?.setContentText("Total steps: ${numSteps}, Steps per min: ${getStepsPerMinute()}")
            ?.build()
            ?: return
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
}
