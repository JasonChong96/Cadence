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
import com.cs4347.cadence.sensor.StepSensorInbuilt


open class CadenceTrackerService : Service(),
    StepListener {
    var channelId: String? = null
    var builder: Notification.Builder? = null
    var lastStepTime = 0L
    var lastStepIndex = 0
    var lastStepDeltas = LongArray(DELTA_TIME_BUFFER_SIZE) { -1 }
    var stepSensor: StepSensor? = null


    override fun onBind(intent: Intent): IBinder {
        return Binder()
    }

    override fun onCreate() {
        stepSensor = getStepSensorInstance()
        channelId = getNewChannelId()

        val notification = getNotificationBuilder()
            .build()

        startForeground(1, notification)
        stepSensor?.registerListener(this)
        super.onCreate()
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
        return CadenceTrackerUtils.convertMinToNs(1) / lastStepDeltas.average()
    }

    private fun getNotificationManager(): NotificationManager {
        return getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun getNotificationBuilder(): Notification.Builder {
        val result = builder
        if (result != null) {
            return result
        }

        val newBuilder = Notification.Builder(this, channelId)
            .setContentText(getStepsPerMinute().toString())
            .setContentTitle("Test")
            .setSmallIcon(R.drawable.ic_launcher_background)
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
            .setContentText(getStepsPerMinute().toString())
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
}
