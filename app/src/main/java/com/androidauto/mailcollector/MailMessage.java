package com.androidauto.mailcollector;

/**
 * Un messaggio di posta così come mostrato nell'elenco su Android Auto, indipendentemente
 * dal fatto che provenga dalla cattura delle notifiche di Outlook o da un account IMAP
 * configurato direttamente. "sourceLabel" identifica la provenienza (es. "Outlook" oppure
 * il nickname dell'account IMAP), utile quando più caselle sono attive insieme.
 */
final class MailMessage {
    final long id;
    final String sourceLabel;
    final String sender;
    final String subject;
    final String body;
    final long receivedAtMs;
    boolean readLocally;

    MailMessage(long id, String sourceLabel, String sender, String subject, String body,
                long receivedAtMs, boolean readLocally) {
        this.id = id;
        this.sourceLabel = sourceLabel;
        this.sender = sender;
        this.subject = subject;
        this.body = body;
        this.receivedAtMs = receivedAtMs;
        this.readLocally = readLocally;
    }
}
