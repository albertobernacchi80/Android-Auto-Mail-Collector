package com.androidauto.mailcollector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.model.ListTemplate;
import androidx.lifecycle.DefaultLifecycleObserver;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Elenco unico delle mail ricevute (qualunque sia la loro provenienza), più recenti in cima,
 * senza sottocartelle per account, come richiesto. Selezionare una riga con la rotella avvia la
 * lettura vocale di quel messaggio (MessageReadingScreen), non apre nessuna risposta.
 */
final class InboxListScreen extends Screen implements DefaultLifecycleObserver {

    private static final int MAX_ROWS = 30;

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            invalidate();
        }
    };

    InboxListScreen(@NonNull CarContext carContext) {
        super(carContext);
        getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        IntentFilter filter = new IntentFilter(MailNotifier.ACTION_NEW_MAIL);
        int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                ? Context.RECEIVER_NOT_EXPORTED : 0;
        if (flags != 0) {
            getCarContext().registerReceiver(refreshReceiver, filter, flags);
        } else {
            getCarContext().registerReceiver(refreshReceiver, filter);
        }
    }

    @Override
    public void onStop(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        try {
            getCarContext().unregisterReceiver(refreshReceiver);
        } catch (IllegalArgumentException ignored) {
            // non registrato, nulla da fare
        }
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        List<MailMessage> messages = MailStore.get(getCarContext()).listRecent(MAX_ROWS);

        ItemList.Builder listBuilder = new ItemList.Builder();
        if (messages.isEmpty()) {
            listBuilder.setNoItemsMessage("Nessuna mail ricevuta finora");
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.ITALY);
            for (MailMessage m : messages) {
                String title = (m.sender == null || m.sender.trim().isEmpty()) ? "(sconosciuto)" : m.sender.trim();
                String subject = (m.subject == null || m.subject.trim().isEmpty()) ? "(nessun oggetto)" : m.subject.trim();
                Row.Builder rowBuilder = new Row.Builder()
                        .setTitle((m.readLocally ? "" : "● ") + title)
                        .addText(subject)
                        .addText(fmt.format(m.receivedAtMs) + " · " + m.sourceLabel)
                        .setOnClickListener(() ->
                                getScreenManager().push(new MessageReadingScreen(getCarContext(), m.id)));
                listBuilder.addItem(rowBuilder.build());
            }
        }

        Header header = new Header.Builder()
                .setTitle("Posta in arrivo")
                .build();

        return new ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(listBuilder.build())
                .build();
    }
}
