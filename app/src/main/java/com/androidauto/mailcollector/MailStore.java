package com.androidauto.mailcollector;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Unico database locale dell'app: l'elenco dei messaggi mostrati su Android Auto (qualunque
 * sia la loro provenienza, Outlook o IMAP diretto) e, per la modalità IMAP diretto, l'ultimo
 * UID già notificato per ciascun account, per non ripresentare due volte la stessa mail.
 * Non contiene mai credenziali: quelle sono in ImapAccountStore, su preferenze cifrate.
 */
final class MailStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "mailnotify.db";
    private static final int DB_VERSION = 1;

    private static final String T_MESSAGES = "messages";
    private static final String T_IMAP_STATE = "imap_state";

    private static MailStore instance;

    static synchronized MailStore get(Context context) {
        if (instance == null) instance = new MailStore(context.getApplicationContext());
        return instance;
    }

    private MailStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_MESSAGES + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "dedup_key TEXT UNIQUE, " +
                "source_label TEXT, " +
                "sender TEXT, " +
                "subject TEXT, " +
                "body TEXT, " +
                "received_at INTEGER, " +
                "read_locally INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE " + T_IMAP_STATE + " (" +
                "account_id TEXT PRIMARY KEY, " +
                "last_uid INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + T_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + T_IMAP_STATE);
        onCreate(db);
    }

    /** Inserisce un messaggio; se dedupKey è già presente (stessa mail già notificata) non fa
     *  nulla e restituisce -1, per evitare doppie notifiche/letture vocali. */
    long insertIfNew(String dedupKey, String sourceLabel, String sender, String subject,
                      String body, long receivedAtMs) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("dedup_key", dedupKey);
        cv.put("source_label", sourceLabel);
        cv.put("sender", sender);
        cv.put("subject", subject);
        cv.put("body", body);
        cv.put("received_at", receivedAtMs);
        cv.put("read_locally", 0);
        return db.insertWithOnConflict(T_MESSAGES, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    List<MailMessage> listRecent(int limit) {
        List<MailMessage> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT id, source_label, sender, subject, body, received_at, read_locally " +
                        "FROM " + T_MESSAGES + " ORDER BY received_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                out.add(new MailMessage(
                        c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getLong(5), c.getInt(6) != 0));
            }
        }
        return out;
    }

    MailMessage findById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT id, source_label, sender, subject, body, received_at, read_locally " +
                        "FROM " + T_MESSAGES + " WHERE id = ?", new String[]{String.valueOf(id)})) {
            if (c.moveToFirst()) {
                return new MailMessage(
                        c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getLong(5), c.getInt(6) != 0);
            }
        }
        return null;
    }

    void markReadLocally(long id) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("read_locally", 1);
        db.update(T_MESSAGES, cv, "id = ?", new String[]{String.valueOf(id)});
    }

    long getLastUid(String accountId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT last_uid FROM " + T_IMAP_STATE + " WHERE account_id = ?",
                new String[]{accountId})) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return -1;
    }

    void setLastUid(String accountId, long uid) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("account_id", accountId);
        cv.put("last_uid", uid);
        db.insertWithOnConflict(T_IMAP_STATE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
