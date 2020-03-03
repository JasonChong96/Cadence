package com.cs4347.cadence.sensor

interface StepSensor {
    fun registerListener(listener: StepListener)
    fun unregisterListener(stepListener: StepListener)

    fun stop()
}