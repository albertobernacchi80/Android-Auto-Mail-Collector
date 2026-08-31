package com.androidauto.mailcollector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Avvia la lettura vocale quando l'utente tocca la notifica, sia dal telefono sia dallo schermo
 * dell'auto (stesso PendingIntent per entrambi, vedi MailNotifier). Non fa nient'altro: niente
 * risposta, niente altre azioni, come richiesto. goAsync() tiene vivo il receiver il tempo
 * necessario alla sintesi vocale, che altrimenti verrebbe interrotta a metà.
 */
public final class ReadMailReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long messageId = intent.getLongExtra(MailNotifier.EXTRA_MESSAGE_ID, -1);
        if (messageId < 0) return;

        MailMessage message = MailStore.get(context).findById(messageId);
        if (message == null) return;

        MailStore.get(context).markReadLocally(messageId);

        boolean includeBody = new AppMode(context).isReadBodyEnabled();
        PendingResult pendingResult = goAsync();
        MailTts.speak(context, message, includeBody, pendingResult::finish);
    }
}
