package com.cs4347.cadence.audio

import kotlin.math.round

data class LoadedTimeShiftedSong(val slow: LoadedSong, val original: LoadedSong, val fast: LoadedSong) {
    fun getAverageDifference(): Int {
        return (original.bpm - slow.bpm + fast.bpm - original.bpm) / 2
    }
}

data class LoadedSong(val bpm: Int, val samples: ByteArray)