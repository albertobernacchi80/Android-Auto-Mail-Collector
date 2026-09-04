package com.androidauto.mailcollector;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferenze generali non sensibili: non contengono credenziali, quindi restano su
 * SharedPreferences normali (quelle cifrate sono riservate a ImapAccountStore).
 *
 * Le due modalità di rilevamento non sono più esclusive: possono essere attive insieme, per
 * esempio Notifiche Outlook per la maggior parte degli account e IMAP diretto solo per uno
 * specifico (es. Libero). In quel caso è responsabilità dell'utente disattivare le notifiche
 * di quell'account dentro l'app Outlook, per non ricevere/sentire la stessa mail due volte da
 * due canali indipendenti che non si "vedono" a vicenda.
 */
final class AppMode {

    private static final String PREFS_NAME = "mailnotify_mode";
    private static final String KEY_OUTLOOK_ENABLED = "outlook_enabled";
    private static final String KEY_IMAP_ENABLED = "imap_enabled";
    private static final String KEY_OUTLOOK_CUTOFF = "outlook_cutoff_ms";

    private final SharedPreferences prefs;

    AppMode(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Modalità A: cattura le notifiche dell'app Outlook. Attiva di default alla prima
     *  installazione, così un'app appena installata fa già qualcosa senza configurazione. */
    boolean isOutlookEnabled() {
        return prefs.getBoolean(KEY_OUTLOOK_ENABLED, true);
    }

    void setOutlookEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_OUTLOOK_ENABLED, enabled).apply();
    }

    /** Modalità B: controllo periodico degli account IMAP configurati. */
    boolean isImapEnabled() {
        return prefs.getBoolean(KEY_IMAP_ENABLED, false);
    }

    void setImapEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_IMAP_ENABLED, enabled).apply();
    }

    /** Istante da cui in poi le notifiche Outlook vengono catturate: 0 significa "non ancora
     *  stabilito". Serve a ignorare le notifiche già presenti nella tendina di sistema prima
     *  che questa modalità venisse attivata (altrimenti, ad ogni riconnessione del listener,
     *  quelle notifiche "vecchie" verrebbero ricatturate come se fossero nuove), esattamente
     *  come ImapPollService usa l'ultimo UID come base di partenza. */
    long getOutlookCutoffMillis() {
        return prefs.getLong(KEY_OUTLOOK_CUTOFF, 0L);
    }

    void setOutlookCutoffMillis(long millis) {
        prefs.edit().putLong(KEY_OUTLOOK_CUTOFF, millis).apply();
    }
}
