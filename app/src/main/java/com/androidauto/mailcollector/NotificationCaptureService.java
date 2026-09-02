package com.androidauto.mailcollector;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Modalità "Notifiche app Outlook": non legge nessuna casella via IMAP, si limita a osservare
 * le notifiche che l'app Outlook mostra già (che raccoglie tutti gli account configurati al
 * suo interno, Gmail/Libero/Aruba/Outlook.com inclusi) e le rilancia come notifica di questa
 * app con CarAppExtender, così compaiono anche su Android Auto in un'unica schermata, senza
 * sottocartelle. Non modifica né tocca in alcun modo la casella sul server: legge solo il
 * contenuto già presente nella notifica di sistema, quindi lo stato "non letta" sul server (e
 * quindi anche quando si apre il client sul PC) resta invariato per costruzione.
 * Richiede che l'utente conceda manualmente l'accesso alle notifiche in Impostazioni Android
 * (Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, vedi SettingsActivity).
 */
public final class NotificationCaptureService extends NotificationListenerService {

    private static final String OUTLOOK_PACKAGE = "com.microsoft.office.outlook";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        AppMode mode = new AppMode(this);
        if (!mode.isOutlookEnabled()) return;
        if (sbn == null || !OUTLOOK_PACKAGE.equals(sbn.getPackageName())) return;

        // Linea di partenza: la prima volta che il listener cattura qualcosa, fissa "adesso"
        // come riferimento e ignora tutto ciò che è precedente. Senza questo, ogni volta che
        // Android riconnette il listener (riavvio del telefono, del processo, o dell'accesso
        // alle notifiche) le notifiche Outlook già presenti nella tendina di sistema
        // verrebbero ricatturate come se fossero nuove, facendo riapparire all'infinito le
        // stesse mail vecchie. Stesso principio dell'UID di partenza in ImapPollService.
        long cutoff = mode.getOutlookCutoffMillis();
        if (cutoff == 0L) {
            cutoff = System.currentTimeMillis();
            mode.setOutlookCutoffMillis(cutoff);
        }
        if (sbn.getPostTime() < cutoff) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (textCs == null) textCs = extras.getCharSequence(Notification.EXTRA_TEXT);

        String title = titleCs != null ? titleCs.toString() : "";
        String text = textCs != null ? textCs.toString() : "";
        if (title.isEmpty() && text.isEmpty()) return;

        // Outlook nel titolo mette di solito "Mittente" e nel testo "Oggetto" (o "Oggetto -
        // anteprima"): non c'è un modo affidabile di separare oggetto e corpo da una notifica
        // di sistema, quindi il titolo va a "sender" e il resto del testo a "subject/body".
        //
        // La chiave di deduplicazione è basata sul contenuto (mittente + testo + giorno),
        // non su sbn.getKey(): quest'ultimo può cambiare quando Outlook ripubblica la stessa
        // notifica (es. dopo un aggiornamento della app o un refresh della sincronizzazione),
        // facendola sembrare una mail nuova anche se non lo è. Il giorno nella chiave permette
        // comunque a un mittente/oggetto identico di essere notificato di nuovo in un giorno
        // diverso (es. una newsletter ricorrente).
        long dayBucket = sbn.getPostTime() / DAY_MS;
        String dedupKey = "outlook:" + title + "|" + text + "|" + dayBucket;
        long receivedAt = sbn.getPostTime();

        long insertedId = MailStore.get(this).insertIfNew(
                dedupKey, "Outlook", title, text, text, receivedAt);
        if (insertedId <= 0) return; // già notificata in precedenza (es. notifica aggiornata)

        MailMessage message = MailStore.get(this).findById(insertedId);
        if (message != null) MailNotifier.notify(this, message);
    }
}
