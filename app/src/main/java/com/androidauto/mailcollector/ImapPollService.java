package com.androidauto.mailcollector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.internet.InternetAddress;

/**
 * Modalità "IMAP diretto": ogni POLL_INTERVAL_MS interroga in sequenza tutti gli account
 * configurati, uno alla volta, aprendo ogni volta una connessione IMAP nuova in sola lettura
 * (Folder.READ_ONLY): su una cartella aperta in sola lettura JavaMail recupera sempre il
 * contenuto con BODY.PEEK invece di BODY[], quindi non imposta mai il flag \Seen sul server —
 * per questo aprire il client email sul PC continua a mostrare le mail come non lette anche
 * dopo che sono state notificate e lette qui. Gira come servizio in primo piano per non essere
 * terminato dal sistema mentre il telefono è in tasca o lo schermo è spento durante la guida.
 */
public final class ImapPollService extends Service {

    private static final String TAG = "ImapPollService";
    private static final String STATUS_CHANNEL_ID = "mail_poll_status";
    private static final int STATUS_NOTIFICATION_ID = 1;
    private static final long POLL_INTERVAL_MS = 2 * 60 * 1000; // 2 minuti

    private HandlerThread workerThread;
    private Handler workerHandler;
    private volatile boolean running;

    private final Runnable pollLoop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            pollAllAccounts();
            if (running) workerHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        workerThread = new HandlerThread("ImapPollWorker");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(STATUS_NOTIFICATION_ID, buildStatusNotification());
        if (!running) {
            running = true;
            workerHandler.post(pollLoop);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        workerHandler.removeCallbacksAndMessages(null);
        workerThread.quitSafely();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildStatusNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    STATUS_CHANNEL_ID, "Stato controllo posta", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mail)
                .setContentTitle("Android Auto Mail Collector")
                .setContentText("Controllo posta IMAP attivo")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void pollAllAccounts() {
        List<ImapAccount> accounts = new ImapAccountStore(this).list();
        for (ImapAccount account : accounts) {
            try {
                pollOnce(account);
            } catch (Exception e) {
                Log.w(TAG, "Controllo fallito per l'account " + account.nickname + ": " + e.getMessage());
            }
        }
    }

    private void pollOnce(ImapAccount account) throws Exception {
        Properties props = new Properties();
        String protocol = "imap";
        props.put("mail.store.protocol", protocol);
        switch (account.security) {
            case "SSL":
                props.put("mail." + protocol + ".ssl.enable", "true");
                break;
            case "STARTTLS":
                props.put("mail." + protocol + ".starttls.enable", "true");
                props.put("mail." + protocol + ".starttls.required", "true");
                break;
            case "NESSUNA":
            default:
                break;
        }
        props.put("mail." + protocol + ".host", account.host);
        props.put("mail." + protocol + ".port", String.valueOf(account.port));

        Session session = Session.getInstance(props);
        Store store = session.getStore(protocol);
        try {
            store.connect(account.host, account.port, account.email, account.password);
            Folder folder = store.getFolder("INBOX");
            // Sola lettura: JavaMail userà BODY.PEEK per il contenuto, non tocca mai \Seen.
            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder uidFolder = (UIDFolder) folder;
                long lastUid = MailStore.get(this).getLastUid(account.id);

                if (lastUid < 0) {
                    // Prima esecuzione per questo account: prendiamo come base l'ultimo UID
                    // presente, senza notificare tutto lo storico della casella.
                    long uidNext = uidFolder.getUIDNext();
                    MailStore.get(this).setLastUid(account.id, uidNext - 1);
                    return;
                }

                Message[] newMessages = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
                long highestUid = lastUid;
                for (Message message : newMessages) {
                    long uid = uidFolder.getUID(message);
                    if (uid <= lastUid) continue; // può capitare un placeholder di bordo
                    if (uid > highestUid) highestUid = uid;

                    String sender = extractSender(message);
                    String subject = message.getSubject();
                    String body = extractPlainText(message);
                    long receivedAt = message.getReceivedDate() != null
                            ? message.getReceivedDate().getTime() : System.currentTimeMillis();

                    String dedupKey = "imap:" + account.id + ":" + uid;
                    long insertedId = MailStore.get(this).insertIfNew(
                            dedupKey, account.nickname, sender, subject, body, receivedAt);
                    if (insertedId > 0) {
                        MailMessage stored = MailStore.get(this).findById(insertedId);
                        if (stored != null) MailNotifier.notify(this, stored);
                    }
                }
                if (highestUid > lastUid) MailStore.get(this).setLastUid(account.id, highestUid);
            } finally {
                folder.close(false);
            }
        } finally {
            store.close();
        }
    }

    private static String extractSender(Message message) throws Exception {
        javax.mail.Address[] from = message.getFrom();
        if (from == null || from.length == 0) return "";
        if (from[0] instanceof InternetAddress) {
            InternetAddress addr = (InternetAddress) from[0];
            String personal = addr.getPersonal();
            return personal != null && !personal.isEmpty() ? personal : addr.getAddress();
        }
        return from[0].toString();
    }

    /** Estrae solo il testo semplice del messaggio (prima parte text/plain trovata), ignorando
     *  allegati e parti HTML: sufficiente per la lettura vocale, senza tag da "leggere" a voce. */
    private static String extractPlainText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content != null ? content.toString() : "";
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = extractPlainText(multipart.getBodyPart(i));
                if (text != null && !text.trim().isEmpty()) return text;
            }
            return "";
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            String html = content != null ? content.toString() : "";
            return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
        return "";
    }
}
