package com.cs4347.cadence.sensor

import android.content.Context
import io.esense.esenselib.ESenseConfig
import io.esense.esenselib.ESenseConnectionListener
import io.esense.esenselib.ESenseManager

class StepSensorEsense(context: Context, deviceName: String) : StepSensor() {
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
    private var manager: ESenseManager? = null
    private var esenseConfig: ESenseConfig? = null

    init {
        manager = ESenseManager(deviceName, context, object : ESenseConnectionListener {
            override fun onDeviceNotFound(manager: ESenseManager?) {
                println("DEVICE NOT FOUND")
            }

            override fun onConnected(manager: ESenseManager?) {
                if (manager == null) {
                    return
                }

//                manager.registerEventListener(object : ESenseEventListener {
//                    override fun onBatteryRead(voltage: Double) {
//                        return
//                    }
//
//                    override fun onDeviceNameRead(deviceName: String?) {
//                        return
//                    }
//
//                    override fun onAdvertisementAndConnectionIntervalRead(
//                        minAdvertisementInterval: Int,
//                        maxAdvertisementInterval: Int,
//                        minConnectionInterval: Int,
//                        maxConnectionInterval: Int
//                    ) {
//                        return
//                    }
//
//                    override fun onButtonEventChanged(pressed: Boolean) {
//                        return
//                    }
//
//                    override fun onSensorConfigRead(config: ESenseConfig?) {
//                        println(config)
//                        this@StepSensorEsense.esenseConfig = config
//                    }
//
//                    override fun onAccelerometerOffsetRead(
//                        offsetX: Int,
//                        offsetY: Int,
//                        offsetZ: Int
//                    ) {
//                        return
//                    }
//                })
//                while (!manager.sensorConfig) {
//                    println("Failed to get config")
//                    sleep(3000L)
//                }
//                sleep(3000L)
                val status = manager.registerSensorListener({
                    this@StepSensorEsense.esenseConfig = ESenseConfig()
                    if (this@StepSensorEsense.esenseConfig == null) {
                        println("CONFIG IS NULL")
                    } else {
                        val accel = it.convertAccToG(esenseConfig)
                            .map { it.toFloat() * 9.81f } // G to m/s^-2
                        updateAccel(it.timestamp * 1000000, accel[0], accel[1], accel[2])
                    }
                }, 10)
                manager.unregisterEventListener()


                println("CONNECTED $status")
            }

            override fun onDisconnected(manager: ESenseManager?) {
                println("DISCONNECTED")
            }

            override fun onDeviceFound(manager: ESenseManager?) {
                println("DEVICE FOUND")
            }
        })
        manager?.connect(10000)

    }

    override fun stop() {
        // TODO: NPE within library function here
        manager?.unregisterEventListener()
        manager?.unregisterSensorListener()
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
            listeners.forEach {
                it.step(timeNs)
            }
            lastStepTimeNs = timeNs
        }
        oldVelocityEstimate = velocityEstimate
        println("$oldVelocityEstimate $velocityEstimate")

    }

    companion object {
        private const val ACCEL_RING_SIZE = 50
        private const val VEL_RING_SIZE = 10

        // change this threshold according to your sensitivity preferences
        private const val STEP_THRESHOLD = 20f
        private const val STEP_DELAY_NS = 250000000
    }
}