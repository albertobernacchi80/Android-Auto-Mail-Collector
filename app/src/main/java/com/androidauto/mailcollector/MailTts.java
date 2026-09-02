package com.androidauto.mailcollector;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * Sintesi vocale del messaggio quando l'utente tocca la notifica: legge mittente, oggetto e,
 * se abilitato nelle impostazioni, il testo. Un'unica istanza per processo, inizializzata al
 * bisogno e rilasciata alla fine della lettura per non tenere il motore TTS sempre acceso.
 */
final class MailTts {

    private static final String TAG = "MailTts";

    interface Callback {
        void onDone();
    }

    static void speak(Context context, MailMessage message, boolean includeBody, Callback callback) {
        String text = buildSpeechText(message, includeBody);
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(appContext, status -> {
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Motore TTS non disponibile (status=" + status + ")");
                if (callback != null) mainHandler.post(callback::onDone);
                return;
            }
            holder[0].setLanguage(Locale.ITALIAN);
            holder[0].setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }

                // onDone/onError arrivano su un thread interno del motore TTS, non sul thread
                // main: chi riceve il callback (es. MessageReadingScreen.getScreenManager())
                // richiede invece il thread main, quindi il rientro va marshalled qui.
                @Override public void onDone(String utteranceId) {
                    holder[0].shutdown();
                    if (callback != null) mainHandler.post(callback::onDone);
                }

                @Override public void onError(String utteranceId) {
                    holder[0].shutdown();
                    if (callback != null) mainHandler.post(callback::onDone);
                }
            });
            holder[0].speak(text, TextToSpeech.QUEUE_FLUSH, null, "mailnotify_read");
        });
    }

    private static String buildSpeechText(MailMessage message, boolean includeBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nuova mail da ").append(nonEmpty(message.sender, "mittente sconosciuto")).append(". ");
        sb.append("Oggetto: ").append(nonEmpty(message.subject, "nessun oggetto")).append(". ");
        if (includeBody && message.body != null && !message.body.trim().isEmpty()) {
            sb.append("Testo: ").append(message.body.trim());
        }
        return sb.toString();
    }

    private static String nonEmpty(String s, String fallback) {
        return (s == null || s.trim().isEmpty()) ? fallback : s.trim();
    }

    private MailTts() {
    }
}
