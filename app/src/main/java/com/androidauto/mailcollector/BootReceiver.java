package com.androidauto.mailcollector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * La modalità "Notifiche Outlook" non richiede nulla qui: il sistema ricollega da solo i
 * NotificationListenerService abilitati dopo il riavvio. Solo ImapPollService, essendo un
 * servizio in primo piano avviato esplicitamente, va fatto ripartire a mano.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (new AppMode(context).isImapEnabled()) {
            context.startForegroundService(new Intent(context, ImapPollService.class));
        }
    }
}
