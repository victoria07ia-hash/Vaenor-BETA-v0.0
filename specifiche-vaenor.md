# Vaenor — Specifiche del progetto

App di messaggistica per Android, pensata per la massima privacy: nessun dato personale richiesto, cifratura end-to-end reale, nessuna traccia permanente dei contenuti.

---

## 1. Identità e accesso

- Nessuna email, numero di telefono o altro dato personale richiesto: basta un nickname
- Coppia di chiavi ECDH (P-256) generata sul dispositivo al primo avvio: è l'identità dell'utente
- PIN obbligatorio a ogni apertura, salvato solo come hash SHA-256
- Dopo 3 PIN sbagliati: cancellazione automatica e irreversibile di tutti i dati
- Nessun recupero PIN/account possibile, per design

## 2. Crittografia

- Scambio chiavi: ECDH, curva P-256
- Derivazione chiave: HKDF-SHA256 (RFC 5869), non un hash diretto
- Cifratura messaggi: AES-GCM 256 bit, IV casuale a ogni messaggio
- Codice di sicurezza per-chat (safety number), calcolato dalle chiavi pubbliche
- Gestione errori: un pacchetto non decifrabile viene scartato, mai causa crash

## 3. Messaggistica

- Testo cifrato end-to-end via Tor
- Foto viste una volta: cancellate dalla memoria subito dopo la visualizzazione
- Eliminazione per tutti: reale, comunicata al contatto con un identificativo condiviso
- Nessun salvataggio persistente: i messaggi restano solo finché l'app è aperta

## 4. Rete e trasporto

- Nessun server centrale: comunicazione diretta P2P
- Instradamento tramite Tor, sfruttando Orbot (non incorporato nell'app)
- Hidden service (.onion) per ricevere, proxy SOCKS5 per inviare
- L'app blocca l'accesso se Orbot non è installato o non risulta attivo
- Nessun ban/moderazione centralizzata possibile, per costruzione

## 5. Rubrica contatti

- Nessuna sincronizzazione con la rubrica del telefono
- Aggiunta contatto tramite QR code (scansione integrata) o link condivisibile
- Ogni link/QR ricevuto viene validato rigorosamente prima di essere accettato

## 6. Sicurezza del dispositivo

- Blocco screenshot (FLAG_SECURE)
- Storage cifrato dal sistema (EncryptedSharedPreferences / Android Keystore)
- Nessun backup automatico (allowBackup disattivato)
- Solo permessi Internet e Fotocamera (quest'ultima solo per scansione QR)

## 7. Compatibilità

- Solo Android, nessuna versione iOS prevista
- Compatibile con GrapheneOS senza modifiche (nessuna dipendenza Google)

## 8. Distribuzione

- Non pubblicata su alcuno store: installazione manuale (sideload) via APK
- Compilazione automatica tramite GitHub Actions

---

## Stato attuale

Tutte le funzionalità sono implementate. Non è ancora stata effettuata una
compilazione reale: resta il prossimo passo necessario. Problema noto da
correggere: nel modulo di rete, il proprio nickname trasmesso al contatto
usa ancora un valore segnaposto invece di quello reale.
