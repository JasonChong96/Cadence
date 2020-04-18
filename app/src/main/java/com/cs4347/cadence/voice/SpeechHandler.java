package com.cs4347.cadence.voice;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.Random;

public class SpeechHandler implements SpeechAdapter {

    private Context mContext;
    private TextToSpeech textToSpeech;
    private Bundle params;

    // Some reserved text to update the BPM
    private String[] reservedSentence = {
            "Song changed to %d beats per minute",
    };
    public SpeechHandler(Context context) {
        mContext = context.getApplicationContext();
        textToSpeech = new TextToSpeech(mContext, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(Locale.UK);
                }
            }
        });
        params = new Bundle();
        params.putCharSequence(TextToSpeech.Engine.KEY_PARAM_VOLUME, "200");
    }
    @Override
    public void speak(String content) {
        textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, params,null);
    }

    @Override
    public void speak(int bpm) {
        String content = String.format(reservedSentence[new Random().nextInt(reservedSentence.length)], bpm);
        speak(content);
    }

    @Override
    public int recognize() {
        return 0;
    }
}
