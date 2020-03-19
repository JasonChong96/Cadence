package com.cs4347.cadence

import com.cs4347.cadence.sensor.StepSensor
import com.cs4347.cadence.sensor.StepSensorAccelerometer

class CadenceTrackerAccelerometerService : CadenceTrackerService() {
    override fun getStepSensorInstance(): StepSensor {
        return StepSensorAccelerometer(this)
    }
}