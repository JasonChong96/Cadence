package com.cs4347.cadence.sensor

interface StepListener {
    fun step(timeNs: Long)
    fun sensorStopped()
}