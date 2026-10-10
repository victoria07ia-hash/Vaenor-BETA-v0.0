# Vaenor — prototipo

App Android di chat cifrata: identità via nickname, generazione chiavi locale,
cifratura end-to-end (ECDH + HKDF + AES-GCM), codice di sicurezza per-chat,
eliminazione messaggi per tutti (reale, comunicata al contatto), foto viste
una volta, blocco screenshot, PIN con reset automatico dopo 3 tentativi,
rubrica contatti via QR code o link, trasporto tramite Tor/Orbot, controllo
che blocca l'app finché Orbot non è installato e attivo.

**Stato**: tutte le funzionalità sono implementate nel codice. Non è stata
ancora effettuata una compilazione reale — è il prossimo passo necessario
prima di qualunque uso pratico.

## Come ottenere l'APK installabile

1. Crea un account gratuito su [github.com](https://github.com).
2. Crea un nuovo repository (anche privato).
3. Carica dentro il repository tutti i file e le cartelle di questo progetto,
   mantenendo la stessa struttura (incluso `.github`).
4. Vai nella scheda "Actions": la compilazione parte da sola.
5. Scarica l'APK dal risultato dell'esecuzione ("chat-cifrata-apk").
6. Trasferisci l'APK sul telefono e installalo (serve concedere il permesso
   "Installa app da fonti sconosciute").

## Requisiti per l'uso

- **Orbot** installato e avviato (F-Droid consigliato), con un hidden service
  V3 configurato che punta alla porta locale 8080 dell'app.
- Senza Orbot attivo, l'app stessa blocca l'accesso con una schermata dedicata.
