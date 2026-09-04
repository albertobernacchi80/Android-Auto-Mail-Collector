package com.androidauto.mailcollector;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Elenco degli account IMAP configurati, salvato cifrato (contiene le password) come un unico
 * array JSON in una singola chiave di EncryptedSharedPreferences. Stesso schema difensivo di
 * AppSettings nel progetto MX-5 Driver Metrics: se la cifratura non è disponibile si passa a
 * preferenze normali, per non bloccare l'app.
 */
final class ImapAccountStore {

    private static final String TAG = "ImapAccountStore";
    private static final String PREFS_NAME = "mailnotify_imap_accounts";
    private static final String KEY_ACCOUNTS = "accounts_json";

    private final SharedPreferences prefs;

    ImapAccountStore(Context context) {
        prefs = openPrefs(context.getApplicationContext());
    }

    private static SharedPreferences openPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context, PREFS_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "SharedPreferences cifrate non disponibili, uso preferenze normali: " + e.getMessage());
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    List<ImapAccount> list() {
        List<ImapAccount> out = new ArrayList<>();
        String raw = prefs.getString(KEY_ACCOUNTS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(ImapAccount.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Elenco account IMAP corrotto, riparto vuoto: " + e.getMessage());
        }
        return out;
    }

    private void saveAll(List<ImapAccount> accounts) {
        JSONArray arr = new JSONArray();
        try {
            for (ImapAccount a : accounts) arr.put(a.toJson());
        } catch (JSONException e) {
            Log.e(TAG, "Errore serializzando gli account IMAP: " + e.getMessage());
            return;
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply();
    }

    /** Aggiunge un account nuovo (id generato qui) oppure aggiorna un account esistente con lo
     *  stesso id, mantenendone così invariata la cronologia UID in MailStore. */
    void addOrUpdate(ImapAccount account) {
        List<ImapAccount> accounts = list();
        boolean updated = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id.equals(account.id)) {
                accounts.set(i, account);
                updated = true;
                break;
            }
        }
        if (!updated) accounts.add(account);
        saveAll(accounts);
    }

    void remove(String accountId) {
        List<ImapAccount> accounts = list();
        accounts.removeIf(a -> a.id.equals(accountId));
        saveAll(accounts);
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }
}
