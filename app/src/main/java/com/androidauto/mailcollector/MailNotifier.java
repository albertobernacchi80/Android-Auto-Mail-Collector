package com.androidauto.mailcollector;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.car.app.notification.CarAppExtender;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Pubblica la notifica di sistema per una nuova mail, con CarAppExtender in modo che compaia
 * anche sullo schermo dell'auto quando Android Auto è connesso, non solo sul telefono. Il tocco
 * (sul telefono o in auto) avvia ReadMailReceiver, che segna il messaggio come letto localmente.
 */
final class MailNotifier {

    private static final String CHANNEL_ID = "mail_alerts";
    static final String EXTRA_MESSAGE_ID = "message_id";
    static final String ACTION_NEW_MAIL = "com.androidauto.mailcollector.NEW_MAIL";

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Nuove mail", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Avviso di nuova mail ricevuta, anche su Android Auto");
            nm.createNotificationChannel(channel);
        }
    }

    static void notify(Context context, MailMessage message) {
        ensureChannel(context);

        Intent readIntent = new Intent(context, ReadMailReceiver.class);
        readIntent.putExtra(EXTRA_MESSAGE_ID, message.id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent readPendingIntent = PendingIntent.getBroadcast(
                context, (int) message.id, readIntent, flags);

        String contentText = message.subject == null || message.subject.trim().isEmpty()
                ? "(nessun oggetto)" : message.subject.trim();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mail)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentTitle(message.sender)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        contentText + (message.body != null ? "\n" + message.body : "")))
                .setSubText(message.sourceLabel)
                .setAutoCancel(true)
                .setContentIntent(readPendingIntent)
                .extend(new CarAppExtender.Builder()
                        .setContentTitle(message.sender)
                        .setContentText(contentText)
                        .setContentIntent(readPendingIntent)
                        .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                        .build());

        NotificationManagerCompat.from(context).notify((int) message.id, builder.build());

        // Avvisa la schermata elenco (se aperta su Android Auto) di aggiornarsi.
        Intent refreshIntent = new Intent(ACTION_NEW_MAIL).setPackage(context.getPackageName());
        context.sendBroadcast(refreshIntent);
    }

    private MailNotifier() {
    }
}
