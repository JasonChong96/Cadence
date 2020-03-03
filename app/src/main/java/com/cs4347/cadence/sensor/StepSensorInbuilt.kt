package com.cs4347.cadence.sensor

import android.app.Service
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class StepSensorInbuilt(context: Context): SensorEventListener,
    StepSensor {
    val sensorManager = context.getSystemService(Service.SENSOR_SERVICE) as SensorManager
    val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    val listeners: MutableList<StepListener> = ArrayList()

    init {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun registerListener(stepListener: StepListener) {
        listeners.add(stepListener)
    }

    override fun unregisterListener(stepListener: StepListener) {
        listeners.remove(stepListener)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        print("Accuracy Changed")
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        listeners.forEach {
            it.sensorStopped()
        }
        listeners.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            listeners.forEach {
                it.step(event.timestamp)
            }
        }
    }
}