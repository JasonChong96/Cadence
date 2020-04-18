package com.cs4347.cadence.voice;

public interface SpeechAdapter {
    public void speak(String content);
    public void speak(int bpm);
    public int recognize();

}
