package com.androidauto.mailcollector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Riceve il tocco sulla notifica, sia dal telefono sia dallo schermo dell'auto (stesso
 * PendingIntent per entrambi, vedi MailNotifier), e segna il messaggio come letto localmente.
 * Non fa nient'altro: niente risposta, niente altre azioni, come richiesto.
 */
public final class ReadMailReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long messageId = intent.getLongExtra(MailNotifier.EXTRA_MESSAGE_ID, -1);
        if (messageId < 0) return;

        MailStore.get(context).markReadLocally(messageId);
    }
}
