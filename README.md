# Android Auto Mail Collector

App per Android Auto che avvisa in tempo reale quando arriva una nuova mail e ne legge a voce mittente, oggetto e testo al tocco della notifica. Nessuna risposta possibile: solo notifica ed elenco.

Applicazione indipendente sviluppata da Alberto Bernacchi.

## Le due modalità

L'app **non modifica mai lo stato delle mail sul server**: qualunque sia la combinazione di modalità scelta, aprendo il client email dal PC le mail risulteranno sempre non lette, anche se già notificate e lette in auto.

Le due modalità sono **interruttori indipendenti**, non un'alternativa esclusiva: puoi attivarle insieme, per esempio Notifiche Outlook per la maggior parte degli account e IMAP diretto solo per uno specifico (es. Libero).

**Attenzione ai duplicati**: se per lo stesso account attivi entrambe le modalità (es. Libero letto sia via IMAP diretto sia ancora notificato da Outlook), riceverai la notifica — e la lettura vocale — due volte, perché i due canali sono indipendenti e non sanno riconoscere che è la stessa mail. Se aggiungi un account in IMAP diretto, vai nell'app Outlook → Impostazioni → Notifiche → quell'account, e disattiva le notifiche lì, lasciando che sia solo l'IMAP diretto a occuparsene.

### Modalità A — Notifiche app Outlook (predefinita)

Richiede che l'app Outlook sia già installata sul telefono e configurata al suo interno, indipendentemente da questa app, con tutti gli account che vuoi monitorare (Gmail, Libero, Aruba, Outlook.com): questa app non configura né gestisce Outlook, si limita a osservare le notifiche che Outlook genera già per gli account che tu vi hai aggiunto.

Non serve configurare nessun account in questa app: si appoggia alle notifiche che l'app Outlook già mostra sul telefono per tutti i tuoi account, le raccoglie in un'unica schermata senza sottocartelle e le rilancia su Android Auto.

**Attivazione**: in Impostazioni → tocca "Attiva accesso alle notifiche" → nella schermata di Android che si apre, abilita "Android Auto Mail Collector". È un permesso speciale che va concesso manualmente, non c'è un pop-up automatico.

Limite da sapere: legge solo mittente e testo così come compaiono nella notifica di Outlook (di solito un'anteprima, non sempre il testo integrale della mail).

### Modalità B — IMAP diretto

L'app si collega direttamente alle caselle Gmail, Libero e Aruba (configurabili in Impostazioni, quante ne vuoi) e controlla la posta in arrivo ogni 2 minuti, leggendo sempre il testo integrale.

**Outlook.com non è incluso in questa modalità**: Microsoft ha disattivato l'accesso IMAP con nome utente e password per gli account Outlook.com/Hotmail/Live (dal 2024/2025, confermato ancora in vigore ad agosto 2026) — serve l'autenticazione OAuth2, che richiede la registrazione di un'app su Azure Portal. Se in futuro vuoi anche quello, fammelo sapere e lo aggiungo come passo successivo. Per ora, se scegli questa modalità, per Outlook.com resta valida la modalità A.

**Come configurare un account:**

| Campo | Gmail | Libero | Aruba |
|---|---|---|---|
| Server | imap.gmail.com | imapmail.libero.it | imaps.aruba.it |
| Porta | 993 | 993 | 993 |
| Sicurezza | SSL | SSL | SSL |
| Password | **Password per le app** (non quella normale) | password normale | password normale |

Per Gmail: attiva la Verifica in due passaggi sul tuo account Google, poi genera una "Password per le app" da myaccount.google.com/apppasswords e usa quella, non la password di accesso normale — con quella non funziona più da nessuna app esterna.

Nel dialog "Nuovo account" scegli il provider dalla tendina: host, porta e sicurezza si compilano da soli. Usa "Verifica connessione" prima di salvare per essere sicuro che i parametri siano giusti.

## Lettura vocale

In Impostazioni puoi scegliere se la lettura vocale includa anche il testo del messaggio, oppure solo mittente e oggetto. La lettura parte solo quando tocchi la notifica (sul telefono o sullo schermo dell'auto) o selezioni la mail dall'elenco in Android Auto con la rotella: non è mai automatica all'arrivo della mail.

## Requisiti

- Telefono Android con Android Auto.
- Un'auto compatibile con Android Auto, oppure Android Auto Desktop Head Unit (DHU) per il test.
- Non è disponibile su Google Play: installazione manuale (vedi sotto).

## Installazione

### Scaricare l'APK già pronto

Se è già disponibile una build, scarica `Android-Auto-Email.apk` dalla sezione **Releases** di questo repository, oppure dagli **Artifacts** dell'ultima esecuzione del workflow **Build APK** in **Actions**, e installalo manualmente sul telefono — vedi il manuale incluso nel repository per i dettagli. (Servono KingInstaller (https://github.com/fcaronte/KingInstaller/releases) con Android Auto in modalità sviluppatore (https://www.smartworld.it/guide/come-abilitare-opzioni-sviluppatore-android-auto.html).)

### Compilare l'APK da soli (via GitHub Actions, senza Android Studio)

1. Crea un repository GitHub con il contenuto di questa cartella.
2. Vai su **Actions → Build APK → Run workflow**.
3. A esecuzione completata, apri quell'esecuzione e scarica l'artifact **AndroidAutoMailCollector-debug**: contiene `Android-Auto-Email.apk`.
4. Installala manualmente sul telefono con la stessa procedura già usata per gli altri tuoi progetti (KingInstaller + Android Auto in modalità sviluppatore).

Dopo l'installazione, apri l'app dalla home del telefono per scegliere la modalità e configurare gli account, poi collega il telefono ad Android Auto.

## Nota tecnica per la prima compilazione

Non ho potuto compilare questo progetto in locale (l'ambiente in cui lavoro non ha accesso ai repository Android/Google necessari): è stato scritto seguendo fedelmente i pattern già collaudati nel progetto MX-5 Driver Metrics, ma è possibile che alla prima esecuzione del workflow **Build APK** emerga qualche piccolo errore di compilazione, in particolare nell'uso della classe `CarAppExtender` (che rende le notifiche visibili anche su Android Auto) o del `ListTemplate` in `InboxListScreen.java` e `MessageReadingScreen.java`. Se capita, incollami l'errore del log della Action e lo correggo subito.

## Licenza

Applicazione per uso personale, non destinata alla distribuzione o vendita a terzi. © 2026 Alberto Bernacchi. Tutti i diritti riservati.
