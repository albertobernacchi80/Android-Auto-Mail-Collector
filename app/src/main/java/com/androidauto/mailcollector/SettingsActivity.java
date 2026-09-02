package com.androidauto.mailcollector;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;

/**
 * Unica Activity sul telefono: sceglie la modalità di rilevamento (Notifiche Outlook / IMAP
 * diretto) e, per la seconda, gestisce l'elenco degli account Gmail/Libero/Aruba. Non gira mai
 * in auto: è l'attività "normale" lanciata dall'icona sulla home, come EmailSettingsActivity
 * nel progetto MX-5 Driver Metrics.
 */
public final class SettingsActivity extends AppCompatActivity {

    private CheckBox checkModeOutlook;
    private CheckBox checkModeImap;
    private LinearLayout blockOutlook;
    private LinearLayout blockImap;
    private LinearLayout listAccountsContainer;
    private TextView txtNotificationAccessStatus;
    private CheckBox checkReadBody;

    private AppMode appMode;
    private ImapAccountStore accountStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        appMode = new AppMode(this);
        accountStore = new ImapAccountStore(this);

        checkModeOutlook = findViewById(R.id.check_mode_outlook);
        checkModeImap = findViewById(R.id.check_mode_imap);
        blockOutlook = findViewById(R.id.block_outlook);
        blockImap = findViewById(R.id.block_imap);
        listAccountsContainer = findViewById(R.id.list_accounts_container);
        txtNotificationAccessStatus = findViewById(R.id.txt_notification_access_status);
        checkReadBody = findViewById(R.id.check_read_body);

        // I due interruttori sono indipendenti (non un RadioGroup): entrambi i blocchi possono
        // restare visibili insieme, per permettere di usare le due modalità in parallelo su
        // account diversi (es. IMAP diretto solo per Libero, Notifiche Outlook per il resto).
        checkModeOutlook.setOnCheckedChangeListener((btn, checked) -> updateBlocksVisibility());
        checkModeImap.setOnCheckedChangeListener((btn, checked) -> updateBlocksVisibility());

        findViewById(R.id.btn_notification_access).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        findViewById(R.id.btn_battery_optimization).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                // Alcuni produttori (es. MIUI, ColorOS) non implementano questo intent di
                // sistema: in quel caso l'esclusione va cercata a mano nelle impostazioni
                // batteria/autoavvio del telefono per questa app.
                Toast.makeText(this,
                        "Il tuo telefono non supporta questa scorciatoia: cerca \"Risparmio energetico\" o \"Autoavvio\" nelle impostazioni Android per questa app",
                        Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.btn_add_account).setOnClickListener(v -> showAccountDialog(null));

        findViewById(R.id.btn_save).setOnClickListener(v -> saveAndApply());

        checkReadBody.setChecked(appMode.isReadBodyEnabled());
        checkModeOutlook.setChecked(appMode.isOutlookEnabled());
        checkModeImap.setChecked(appMode.isImapEnabled());
        updateBlocksVisibility();
        refreshAccountList();
        requestNotificationPermissionIfNeeded();
    }

    /** Su Android 13+ (API 33) il permesso di mostrare notifiche va concesso a runtime: senza
     *  questo, MailNotifier.notify() non fallisce, semplicemente non mostra nulla, in modo
     *  silenzioso e senza errori visibili — un modo facile per credere che l'app "non funzioni"
     *  quando in realtà sta solo lavorando in silenzio senza poter avvisare. */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationAccessStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistSettings();
    }

    private void updateBlocksVisibility() {
        blockOutlook.setVisibility(checkModeOutlook.isChecked() ? View.VISIBLE : View.GONE);
        blockImap.setVisibility(checkModeImap.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void updateNotificationAccessStatus() {
        boolean enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(getPackageName());
        txtNotificationAccessStatus.setText(enabled
                ? "Accesso alle notifiche attivo"
                : "Accesso alle notifiche NON attivo: tocca il pulsante sopra e abilita \"Android Auto Mail Collector\"");
    }

    private void refreshAccountList() {
        listAccountsContainer.removeAllViews();
        List<ImapAccount> accounts = accountStore.list();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ImapAccount account : accounts) {
            View row = inflater.inflate(R.layout.item_imap_account, listAccountsContainer, false);
            ((TextView) row.findViewById(R.id.txt_account_nickname))
                    .setText(account.nickname + " (" + account.provider.label + ")");
            ((TextView) row.findViewById(R.id.txt_account_email)).setText(account.email);
            row.findViewById(R.id.btn_account_edit).setOnClickListener(v -> showAccountDialog(account));
            row.findViewById(R.id.btn_account_delete).setOnClickListener(v -> {
                accountStore.remove(account.id);
                refreshAccountList();
            });
            listAccountsContainer.addView(row);
        }
    }

    /** editingAccount è null quando si aggiunge un account nuovo. */
    private void showAccountDialog(ImapAccount editingAccount) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_imap_account, null);

        EditText editNickname = view.findViewById(R.id.edit_nickname);
        Spinner spinnerProvider = view.findViewById(R.id.spinner_provider);
        EditText editEmail = view.findViewById(R.id.edit_email);
        EditText editPassword = view.findViewById(R.id.edit_password);
        EditText editHost = view.findViewById(R.id.edit_host);
        EditText editPort = view.findViewById(R.id.edit_port);
        Spinner spinnerSecurity = view.findViewById(R.id.spinner_security);
        TextView txtTestResult = view.findViewById(R.id.txt_test_result);
        Button btnTest = view.findViewById(R.id.btn_test_connection);

        ImapAccount.Provider[] providers = ImapAccount.Provider.values();
        String[] providerLabels = new String[providers.length];
        for (int i = 0; i < providers.length; i++) providerLabels[i] = providers[i].label;
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, providerLabels);
        spinnerProvider.setAdapter(providerAdapter);

        String[] securityOptions = {"SSL", "STARTTLS", "NESSUNA"};
        ArrayAdapter<String> securityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, securityOptions);
        spinnerSecurity.setAdapter(securityAdapter);

        spinnerProvider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                ImapAccount.Provider selected = providers[position];
                boolean manual = selected == ImapAccount.Provider.MANUALE;
                editHost.setEnabled(manual);
                editPort.setEnabled(manual);
                spinnerSecurity.setEnabled(manual);
                if (!manual) {
                    editHost.setText(selected.defaultHost);
                    editPort.setText(String.valueOf(selected.defaultPort));
                    spinnerSecurity.setSelection(indexOf(securityOptions, selected.defaultSecurity));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        if (editingAccount != null) {
            editNickname.setText(editingAccount.nickname);
            spinnerProvider.setSelection(indexOfProvider(providers, editingAccount.provider));
            editEmail.setText(editingAccount.email);
            editPassword.setText(editingAccount.password);
            editHost.setText(editingAccount.host);
            editPort.setText(String.valueOf(editingAccount.port));
            spinnerSecurity.setSelection(indexOf(securityOptions, editingAccount.security));
        } else {
            spinnerProvider.setSelection(0); // Gmail di default
        }

        btnTest.setOnClickListener(v -> {
            txtTestResult.setText("Verifica in corso...");
            String host = editHost.getText().toString().trim();
            int port = parsePortOrDefault(editPort.getText().toString().trim(), 993);
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString();
            String security = securityOptions[spinnerSecurity.getSelectedItemPosition()];
            testConnection(host, port, email, password, security, txtTestResult);
        });

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.SettingsTheme)
                .setTitle(editingAccount == null ? "Nuovo account" : "Modifica account")
                .setView(view)
                .setPositiveButton("Salva", null) // sovrascritto sotto per validare prima di chiudere
                .setNegativeButton("Annulla", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nickname = editNickname.getText().toString().trim();
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString();
            String host = editHost.getText().toString().trim();
            String portText = editPort.getText().toString().trim();
            ImapAccount.Provider provider = providers[spinnerProvider.getSelectedItemPosition()];
            String security = securityOptions[spinnerSecurity.getSelectedItemPosition()];

            if (nickname.isEmpty() || email.isEmpty() || password.isEmpty() || host.isEmpty() || portText.isEmpty()) {
                Toast.makeText(this, "Compila tutti i campi", Toast.LENGTH_SHORT).show();
                return;
            }
            int port = parsePortOrDefault(portText, -1);
            if (port < 0) {
                Toast.makeText(this, "Porta non valida", Toast.LENGTH_SHORT).show();
                return;
            }

            String id = editingAccount != null ? editingAccount.id : ImapAccountStore.newId();
            accountStore.addOrUpdate(new ImapAccount(id, nickname, provider, email, password, host, port, security));
            refreshAccountList();
            dialog.dismiss();
        }));

        dialog.show();
    }

    /** Prova solo l'accesso alla casella (connessione + apertura INBOX in sola lettura), senza
     *  leggere né notificare nulla: serve solo a validare i parametri inseriti. */
    private void testConnection(String host, int port, String email, String password,
                                 String security, TextView resultView) {
        new Thread(() -> {
            String result;
            try {
                Properties props = new Properties();
                props.put("mail.store.protocol", "imap");
                switch (security) {
                    case "SSL":
                        props.put("mail.imap.ssl.enable", "true");
                        break;
                    case "STARTTLS":
                        props.put("mail.imap.starttls.enable", "true");
                        props.put("mail.imap.starttls.required", "true");
                        break;
                    default:
                        break;
                }
                props.put("mail.imap.host", host);
                props.put("mail.imap.port", String.valueOf(port));

                Session session = Session.getInstance(props);
                Store store = session.getStore("imap");
                store.connect(host, port, email, password);
                Folder folder = store.getFolder("INBOX");
                folder.open(Folder.READ_ONLY);
                int count = folder.getMessageCount();
                folder.close(false);
                store.close();
                result = "Connessione riuscita (" + count + " messaggi in Posta in arrivo)";
            } catch (Exception e) {
                result = "Connessione fallita: " + e.getMessage();
            }
            String finalResult = result;
            new Handler(Looper.getMainLooper()).post(() -> resultView.setText(finalResult));
        }).start();
    }

    private void saveAndApply() {
        persistSettings();
        Toast.makeText(this, "Impostazioni salvate", Toast.LENGTH_SHORT).show();
    }

    /** Scrive le preferenze e avvia/ferma ImapPollService di conseguenza, senza mostrare alcun
     *  messaggio: usata da onPause per salvare sempre, qualunque sia il modo in cui si esce
     *  dalla schermata (freccia indietro, tasto di sistema, cambio app, spegnimento schermo),
     *  senza disturbare con un Toast ogni volta che l'Activity va semplicemente in pausa. */
    private void persistSettings() {
        boolean outlookEnabled = checkModeOutlook.isChecked();
        boolean imapEnabled = checkModeImap.isChecked();
        appMode.setReadBodyEnabled(checkReadBody.isChecked());

        boolean wasImapEnabled = appMode.isImapEnabled();
        appMode.setOutlookEnabled(outlookEnabled);
        appMode.setImapEnabled(imapEnabled);

        if (imapEnabled) {
            startForegroundService(new Intent(this, ImapPollService.class));
        } else if (wasImapEnabled) {
            stopService(new Intent(this, ImapPollService.class));
        }
        // La modalità Notifiche Outlook non richiede avviare/fermare nulla qui: è
        // NotificationCaptureService.onNotificationPosted stesso a controllare
        // AppMode.isOutlookEnabled() ad ogni notifica, vedi quella classe.
    }

    private static int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) if (array[i].equals(value)) return i;
        return 0;
    }

    private static int indexOfProvider(ImapAccount.Provider[] array, ImapAccount.Provider value) {
        for (int i = 0; i < array.length; i++) if (array[i] == value) return i;
        return 0;
    }

    private static int parsePortOrDefault(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
