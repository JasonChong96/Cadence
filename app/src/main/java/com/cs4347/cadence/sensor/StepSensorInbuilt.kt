package com.cs4347.cadence.sensor

import android.app.Service
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

// Main implementation for StepSensor that uses the built-in step detector on smartphones.
class StepSensorInbuilt(context: Context) : SensorEventListener,
    StepSensor() {
    private val sensorManager = context.getSystemService(Service.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    init {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        print("Accuracy Changed")
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        super.stop()
    }

    override fun onSensorChanged(event: SensorEvent) {
        println("STEPPED")
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            listeners.forEach {
                it.step(event.timestamp)
            }
        }
    }
}