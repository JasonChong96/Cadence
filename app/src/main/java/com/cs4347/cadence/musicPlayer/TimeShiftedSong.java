package com.cs4347.cadence.musicPlayer;

public class TimeShiftedSong {

    private int originalId;
    private int originalBpm;
    private int fastId;
    private int fastBpm;
    private int slowId;
    private int slowBpm;

    public TimeShiftedSong(int slowId, int slowBpm, int originalId, int originalBpm, int fastId, int fastBpm) {
        this.slowId = slowId;
        this.slowBpm = slowBpm;
        this.originalId = originalId;
        this.originalBpm = originalBpm;
        this.fastId = fastId;
        this.fastBpm = fastBpm;
    }

    public int getOriginalId() {
        return originalId;
    }

    public int getOriginalBpm() {
        return originalBpm;
    }

    public int getFastId() {
        return fastId;
    }

    public int getFastBpm() {
        return fastBpm;
    }

    public int getSlowId() {
        return slowId;
    }

    public int getSlowBpm() {
        return slowBpm;
    }
}
