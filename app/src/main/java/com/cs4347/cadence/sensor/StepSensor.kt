package com.cs4347.cadence.sensor

abstract class StepSensor {
    protected val listeners: MutableList<StepListener> = ArrayList()

    fun registerListener(stepListener: StepListener) {
        listeners.add(stepListener)
    }

    fun unregisterListener(stepListener: StepListener) {
        listeners.remove(stepListener)
    }

    open fun stop() {
        listeners.forEach {
            it.sensorStopped()
        }
        listeners.clear()
    }
}