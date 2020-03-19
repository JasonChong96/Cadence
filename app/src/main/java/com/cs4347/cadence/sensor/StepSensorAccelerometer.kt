package com.cs4347.cadence.sensor

import android.app.Service
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.esense.esenselib.ESenseConfig
import io.esense.esenselib.ESenseConnectionListener
import io.esense.esenselib.ESenseManager

class StepSensorAccelerometer(context: Context) : SensorEventListener, StepSensor() {
    private var accelRingCounter = 0
    private val accelRingX =
        FloatArray(ACCEL_RING_SIZE)
    private val accelRingY =
        FloatArray(ACCEL_RING_SIZE)
    private val accelRingZ =
        FloatArray(ACCEL_RING_SIZE)
    private var velRingCounter = 0
    private val velRing = FloatArray(VEL_RING_SIZE)
    private var lastStepTimeNs: Long = 0
    private var oldVelocityEstimate = 0f
    private val sensorManager = context.getSystemService(Service.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    init {
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    override fun stop() {
        sensorManager.unregisterListener(this)
        super.stop()
    }

    fun updateAccel(
        timeNs: Long,
        x: Float,
        y: Float,
        z: Float
    ) {
        val currentAccel = FloatArray(3)
        currentAccel[0] = x
        currentAccel[1] = y
        currentAccel[2] = z

        // First step is to update our guess of where the global z vector is.
        accelRingCounter++
        accelRingX[accelRingCounter % ACCEL_RING_SIZE] =
            currentAccel[0]
        accelRingY[accelRingCounter % ACCEL_RING_SIZE] =
            currentAccel[1]
        accelRingZ[accelRingCounter % ACCEL_RING_SIZE] =
            currentAccel[2]
        val worldZ = FloatArray(3)
        worldZ[0] = SensorFilterUtils.sum(accelRingX) / Math.min(
            accelRingCounter,
            ACCEL_RING_SIZE
        )
        worldZ[1] = SensorFilterUtils.sum(accelRingY) / Math.min(
            accelRingCounter,
            ACCEL_RING_SIZE
        )
        worldZ[2] = SensorFilterUtils.sum(accelRingZ) / Math.min(
            accelRingCounter,
            ACCEL_RING_SIZE
        )
        val normalization_factor = SensorFilterUtils.norm(worldZ)
        worldZ[0] = worldZ[0] / normalization_factor
        worldZ[1] = worldZ[1] / normalization_factor
        worldZ[2] = worldZ[2] / normalization_factor
        val currentZ = SensorFilterUtils.dot(worldZ, currentAccel) - normalization_factor
        velRingCounter++
        velRing[velRingCounter % VEL_RING_SIZE] = currentZ
        val velocityEstimate = SensorFilterUtils.sum(velRing)
        if (velocityEstimate > STEP_THRESHOLD && oldVelocityEstimate <= STEP_THRESHOLD && timeNs - lastStepTimeNs > STEP_DELAY_NS
        ) {
            println("STEPPED")
            listeners.forEach {
                it.step(timeNs)
            }
            lastStepTimeNs = timeNs
        }
        oldVelocityEstimate = velocityEstimate

    }

    companion object {
        private const val ACCEL_RING_SIZE = 50
        private const val VEL_RING_SIZE = 10

        // change this threshold according to your sensitivity preferences
        private const val STEP_THRESHOLD = 50f
        private const val STEP_DELAY_NS = 250000000
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        println("ACCURACY CHANGED")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) {
            return
        }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            updateAccel(
                event.timestamp, event.values[0], event.values[1], event.values[2]);
        }
    }
}