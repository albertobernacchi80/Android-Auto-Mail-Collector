package com.androidauto.mailcollector;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Un account IMAP configurato dall'utente in modalità "IMAP diretto". "id" è generato una sola
 * volta alla creazione e resta invariato: è la chiave con cui ImapPollService ricorda l'ultimo
 * UID già notificato per quell'account (vedi MailStore.getLastUid/setLastUid), anche se
 * l'utente rinomina il nickname in seguito.
 */
final class ImapAccount {

    enum Provider {
        GMAIL("Gmail", "imap.gmail.com", 993, "SSL"),
        LIBERO("Libero", "imapmail.libero.it", 993, "SSL"),
        ARUBA("Aruba", "imaps.aruba.it", 993, "SSL"),
        MANUALE("Altro (manuale)", "", 993, "SSL");

        final String label;
        final String defaultHost;
        final int defaultPort;
        final String defaultSecurity;

        Provider(String label, String defaultHost, int defaultPort, String defaultSecurity) {
            this.label = label;
            this.defaultHost = defaultHost;
            this.defaultPort = defaultPort;
            this.defaultSecurity = defaultSecurity;
        }
    }

    final String id;
    String nickname;
    Provider provider;
    String email;
    String password;
    String host;
    int port;
    String security; // SSL | STARTTLS | NESSUNA

    ImapAccount(String id, String nickname, Provider provider, String email, String password,
                String host, int port, String security) {
        this.id = id;
        this.nickname = nickname;
        this.provider = provider;
        this.email = email;
        this.password = password;
        this.host = host;
        this.port = port;
        this.security = security;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("nickname", nickname);
        o.put("provider", provider.name());
        o.put("email", email);
        o.put("password", password);
        o.put("host", host);
        o.put("port", port);
        o.put("security", security);
        return o;
    }

    static ImapAccount fromJson(JSONObject o) throws JSONException {
        return new ImapAccount(
                o.getString("id"),
                o.getString("nickname"),
                Provider.valueOf(o.getString("provider")),
                o.getString("email"),
                o.getString("password"),
                o.getString("host"),
                o.getInt("port"),
                o.getString("security"));
    }
}
