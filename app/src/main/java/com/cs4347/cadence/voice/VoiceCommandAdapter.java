package com.cs4347.cadence.voice;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import java.io.File;
import java.io.IOException;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

import static com.cs4347.cadence.CadenceIntentConstants.ACTION_PAUSE_AUDIO;
import static com.cs4347.cadence.CadenceIntentConstants.ACTION_PLAY_AUDIO;

public class VoiceCommandAdapter implements RecognitionListener {

    private Context mContext;

    private static final String KEYPHRASE = "Cadence";
    private static final String KWS_SEARCH = "Wake up";
    private static final String MENU_SEARCH = "menu";
    private static final String KWS_NEXT = "Next";
    private static final String KWS_PLAY = "Play";
    private static final String KWS_PAUSE = "Pause";
    private static final String KWS_STOP = "Stop";

    private SpeechRecognizer recognizer;

    public VoiceCommandAdapter(Context context) {
        mContext = context.getApplicationContext();
        runRecognizerSetup();
    }

    @SuppressLint("StaticFieldLeak")
    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(mContext);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    System.out.println(result.getMessage());
                } else {
                    switchSearch(KWS_SEARCH);
                }
            }
        }.execute();
    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onEndOfSpeech() {
        if (!recognizer.getSearchName().equals(KWS_SEARCH))
            switchSearch(KWS_SEARCH);
    }

    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis == null)
            return;

        String text = hypothesis.getHypstr();
        if (text.equals(KEYPHRASE)){
            switchSearch(MENU_SEARCH);
        } else if (text.equals(KWS_STOP)) {
            System.out.println("Stopping the audio");
        } else if (text.equals(KWS_PAUSE)) {
            mContext.sendBroadcast(new Intent(ACTION_PAUSE_AUDIO));
        } else if (text.equals(KWS_PLAY)) {
            mContext.sendBroadcast(new Intent(ACTION_PLAY_AUDIO));
        } else if (text.equals(KWS_NEXT)) {
            System.out.println("Go to the next audio");
        } else {
            System.out.println(hypothesis.getHypstr());
        }
    }

    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            // Logging
            System.out.println(hypothesis.getHypstr());
        }
    }

    @Override
    public void onError(Exception error) {
        System.out.println(error.getMessage());
    }

    @Override
    public void onTimeout() {
        switchSearch(KWS_SEARCH);
    }

    public void onStop() {
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                // Disable this line if you don't want recognizer to save raw
                // audio files to app's storage
                //.setRawLogDir(assetsDir)
                .getRecognizer();

        recognizer.addListener(this);


        // Create keyword-activation search.
        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

        // Create your custom grammar-based search
        File menuGrammar = new File(assetsDir, "mymenu.gram");
        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);
    }

    private void switchSearch(String searchName) {
        recognizer.stop();

        if (searchName.equals(KWS_SEARCH))
            recognizer.startListening(searchName);
        else
            recognizer.startListening(searchName, 10000);
    }
}
