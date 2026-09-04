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

/**
 * Mostrata quando si seleziona una mail dall'elenco: mittente, oggetto e testo, in sola
 * visualizzazione (nessuna lettura vocale). Segna il messaggio come letto localmente.
 *
 * Usa un ListTemplate (righe informative) invece di un MessageTemplate: nel progetto MX-5
 * Driver Metrics era emerso su hardware reale (Mazda MX-5) che le icone di un ActionStrip non
 * sono sempre raggiungibili con la rotellina su certe combinazioni di template/head unit,
 * mentre le righe di un ListTemplate (stesso meccanismo di CreditsScreen/GaugesMenuScreen in
 * quel progetto) lo sono sempre. La freccia indietro resta comunque disponibile tramite
 * Header.setStartHeaderAction, gestita nativamente dall'host.
 */
final class MessageReadingScreen extends Screen {

    private final long messageId;

    MessageReadingScreen(@NonNull CarContext carContext, long messageId) {
        super(carContext);
        this.messageId = messageId;
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        MailMessage message = MailStore.get(getCarContext()).findById(messageId);
        String subject = message != null && message.subject != null && !message.subject.trim().isEmpty()
                ? message.subject.trim() : "(nessun oggetto)";
        String body = message != null && message.body != null ? message.body.trim() : "";
        String sender = message != null && message.sender != null ? message.sender.trim() : "";

        if (message != null) {
            MailStore.get(getCarContext()).markReadLocally(messageId);
        }

        ItemList.Builder list = new ItemList.Builder();
        list.addItem(new Row.Builder().setTitle("Da").addText(sender.isEmpty() ? "(sconosciuto)" : sender).build());
        list.addItem(new Row.Builder().setTitle("Oggetto").addText(subject).build());
        if (!body.isEmpty()) {
            list.addItem(new Row.Builder().setTitle("Testo").addText(body).build());
        }

        Header header = new Header.Builder()
                .setTitle("Mail")
                .setStartHeaderAction(Action.BACK)
                .build();

        return new ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build();
    }
}
