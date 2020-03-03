package com.cs4347.cadence

import kotlin.math.pow

object CadenceTrackerUtils {
    private val MIN_TO_NS = 60 * 10.0.pow(9).toLong()

    fun convertMinToNs(mins: Long): Long {
        return mins * MIN_TO_NS
    }
}