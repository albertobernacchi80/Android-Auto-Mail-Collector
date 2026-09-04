package com.androidauto.mailcollector;

import android.app.Application;
import android.content.Intent;

/**
 * Punto unico di ingresso del processo, sia da Android Auto sia dal telefono (Impostazioni):
 * se la modalità attiva è "IMAP diretto", assicura che il servizio di polling sia in esecuzione
 * (idempotente: se è già attivo, onStartCommand non fa ripartire il loop da capo).
 */
public final class MailApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MailNotifier.ensureChannel(this);
        if (new AppMode(this).isImapEnabled()) {
            startForegroundService(new Intent(this, ImapPollService.class));
        }
    }
}
