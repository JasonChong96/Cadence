package com.cs4347.cadence.voice;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.Random;

public class SpeechHandler implements SpeechAdapter {

    private Context mContext;
    private TextToSpeech textToSpeech;

    // Some reserved text to update the BPM
    private String[] reservedSentence = {
            "Your current speed is %d beat per minute",
            "Your are running at %d beat per minute",
            "It's %d beat per minute now!"
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
    }
    @Override
    public void speak(String content) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null,null);
        } else {
            textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null);
        }
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
