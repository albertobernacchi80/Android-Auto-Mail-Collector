package com.androidauto.mailcollector;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.lifecycle.DefaultLifecycleObserver;

/**
 * Mostrata quando si seleziona una mail dall'elenco: avvia subito la lettura vocale di oggetto
 * e testo (rispettando l'opzione "leggi anche il testo") e torna automaticamente all'elenco
 * quando la lettura finisce.
 *
 * Usa un ListTemplate (righe informative + una riga "Interrompi" selezionabile) invece di un
 * MessageTemplate con azioni in ActionStrip: nel progetto MX-5 Driver Metrics era emerso su
 * hardware reale (Mazda MX-5) che le icone di un ActionStrip non sono sempre raggiungibili con
 * la rotellina su certe combinazioni di template/head unit, mentre le righe di un ListTemplate
 * (stesso meccanismo di CreditsScreen/GaugesMenuScreen in quel progetto) lo sono sempre. La
 * freccia indietro resta comunque disponibile tramite Header.setStartHeaderAction, gestita
 * nativamente dall'host.
 */
final class MessageReadingScreen extends Screen implements DefaultLifecycleObserver {

    private final long messageId;
    private boolean started;

    MessageReadingScreen(@NonNull CarContext carContext, long messageId) {
        super(carContext);
        this.messageId = messageId;
        getLifecycle().addObserver(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        MailMessage message = MailStore.get(getCarContext()).findById(messageId);
        String subject = message != null && message.subject != null && !message.subject.trim().isEmpty()
                ? message.subject.trim() : "(nessun oggetto)";
        String body = message != null && message.body != null ? message.body.trim() : "";
        String sender = message != null && message.sender != null ? message.sender.trim() : "";

        if (!started && message != null) {
            started = true;
            MailStore.get(getCarContext()).markReadLocally(messageId);
            boolean includeBody = new AppMode(getCarContext()).isReadBodyEnabled();
            MailTts.speak(getCarContext(), message, includeBody, () -> getScreenManager().pop());
        }

        ItemList.Builder list = new ItemList.Builder();
        list.addItem(new Row.Builder().setTitle("Da").addText(sender.isEmpty() ? "(sconosciuto)" : sender).build());
        list.addItem(new Row.Builder().setTitle("Oggetto").addText(subject).build());
        if (!body.isEmpty()) {
            list.addItem(new Row.Builder().setTitle("Testo").addText(body).build());
        }
        list.addItem(new Row.Builder()
                .setTitle("Interrompi")
                .setOnClickListener(() -> getScreenManager().pop())
                .build());

        Header header = new Header.Builder()
                .setTitle("Lettura in corso")
                .setStartHeaderAction(Action.BACK)
                .build();

        return new ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build();
    }
}
