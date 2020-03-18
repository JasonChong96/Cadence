package com.cs4347.cadence

import com.cs4347.cadence.sensor.StepSensor
import com.cs4347.cadence.sensor.StepSensorEsense

class CadenceTrackerEsenseService : CadenceTrackerService() {
    override fun getStepSensorInstance(): StepSensor {
        return StepSensorEsense(this, deviceName)
    }
}