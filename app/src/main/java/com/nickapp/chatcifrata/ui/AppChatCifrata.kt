package com.nickapp.chatcifrata.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nickapp.chatcifrata.crypto.CryptoManager
import com.nickapp.chatcifrata.crypto.PacchettoCifrato
import com.nickapp.chatcifrata.data.StorageLocale
import com.nickapp.chatcifrata.data.TemaApp

sealed class Schermata {
    object Caricamento : Schermata()
    object CreazioneIdentita : Schermata()
    object ImpostaPin : Schermata()
    object RichiediPin : Schermata()
    object RichiedeOrbot : Schermata()
    object ListaChat : Schermata()
    object AggiungiContatto : Schermata()
    object Impostazioni : Schermata()
    data class Conversazione(val contatto: com.nickapp.chatcifrata.data.Contatto) : Schermata()
}

@Composable
fun AppChatCifrata(contattoDaLink: com.nickapp.chatcifrata.data.Contatto? = null) {
    val context = LocalContext.current
    val storage = remember { StorageLocale(context) }
    val trasporto = remember { com.nickapp.chatcifrata.rete.TrasportoTor.istanza }

    var schermata by remember {
        mutableStateOf<Schermata>(
            when {
                !storage.haIdentita() -> Schermata.CreazioneIdentita
                !storage.haPinImpostato() -> Schermata.ImpostaPin
                else -> Schermata.RichiediPin
            }
        )
    }
    // Schermata in cui eri quando l'app si è bloccata: dopo il PIN ci torni, invece di ripartire dalla lista.
    var schermataDaRipristinare by remember { mutableStateOf<Schermata?>(null) }
    // Invito arrivato da un link: va mostrato una sola volta, non ogni volta che si torna alla lista chat.
    var linkInSospeso by remember { mutableStateOf(contattoDaLink) }
    var contattoPrecompilato by remember { mutableStateOf<com.nickapp.chatcifrata.data.Contatto?>(null) }
    var contatti by remember { mutableStateOf(storage.leggiContatti()) }
    val messaggiPerContatto = remember { mutableStateMapOf<String, List<Messaggio>>() }
    val crypto = remember { CryptoManager() }

    // Ricezione a livello di app: i messaggi vengono ricevuti e decifrati qualunque schermata
    // sia aperta, e finiscono nella conversazione del contatto giusto.
    DisposableEffect(Unit) {
        trasporto.alRicevimento { mittente, tipo, idCondiviso, pacchetto ->
            elaboraPacchettoInArrivo(storage, crypto, messaggiPerContatto, mittente, tipo, idCondiviso, pacchetto)
        }
        onDispose {
            // Quando la schermata viene distrutta, il ricevitore smette di scrivere nelle chat in memoria:
            // così i messaggi dell'app chiusa non restano raggiungibili né continuano ad accumularsi.
            trasporto.alRicevimento { _, _, _, _ -> }
        }
    }

    // Avvia il trasporto Tor una sola volta, appena arriviamo alla lista chat (cioè dopo
    // che il controllo Orbot è già passato).
    LaunchedEffect(schermata) {
        if (schermata == Schermata.ListaChat) {
            try { trasporto.avvia() } catch (e: Exception) { /* già avviato o errore gestito altrove */ }
        }
    }

    LaunchedEffect(linkInSospeso, schermata) {
        val link = linkInSospeso
        if (link != null && schermata == Schermata.ListaChat) {
            contattoPrecompilato = link
            linkInSospeso = null
            schermata = Schermata.AggiungiContatto
        }
    }

    // Blocco automatico: se l'app è uscita dallo schermo mentre eri dentro, al ritorno chiede il PIN.
    // Le chat in memoria restano com'erano, ma non si vedono finché non sblocchi.
    LaunchedEffect(BloccoApp.daBloccare) {
        if (BloccoApp.daBloccare) {
            BloccoApp.daBloccare = false
            val corrente = schermata
            val bloccabile = corrente is Schermata.ListaChat || corrente is Schermata.AggiungiContatto ||
                corrente is Schermata.Conversazione || corrente is Schermata.Impostazioni ||
                corrente is Schermata.RichiedeOrbot
            if (bloccabile && storage.haPinImpostato()) {
                schermataDaRipristinare = corrente
                schermata = Schermata.RichiediPin
            }
        }
    }

    var tema by remember { mutableStateOf(storage.leggiTema()) }
    val sistemaScuro = isSystemInDarkTheme()
    val usaTemaScuro = when (tema) {
        TemaApp.SISTEMA -> sistemaScuro
        TemaApp.CHIARO -> false
        TemaApp.SCURO -> true
    }

    // Dopo il reset (3 PIN sbagliati) va svuotato anche quello che è ancora in memoria:
    // contatti, chat (foto comprese) e tema, altrimenti ricomparirebbero con la nuova identità.
    fun azzeraMemoriaDopoReset() {
        synchronized(messaggiPerContatto) { messaggiPerContatto.clear() }
        contatti = emptyList()
        tema = storage.leggiTema()
        schermataDaRipristinare = null
        linkInSospeso = null
        contattoPrecompilato = null
    }

    MaterialTheme(colorScheme = if (usaTemaScuro) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val corrente = schermata) {
                is Schermata.Caricamento -> { /* riservato per un futuro splash screen */ }
                is Schermata.CreazioneIdentita -> CreazioneIdentitaScreen(
                    storage = storage,
                    onIdentitaCreata = { schermata = Schermata.ImpostaPin }
                )
                is Schermata.ImpostaPin -> BloccoPinScreen(
                    storage = storage,
                    modalitaImpostazione = true,
                    onSbloccato = { schermata = Schermata.RichiedeOrbot },
                    onDatiCancellati = {
                        azzeraMemoriaDopoReset()
                        schermata = Schermata.CreazioneIdentita
                    }
                )
                is Schermata.RichiediPin -> BloccoPinScreen(
                    storage = storage,
                    modalitaImpostazione = false,
                    onSbloccato = {
                        // Dopo un blocco automatico torni dov'eri; all'avvio normale passi dal controllo Orbot.
                        val ripristino = schermataDaRipristinare
                        schermataDaRipristinare = null
                        schermata = ripristino ?: Schermata.RichiedeOrbot
                    },
                    onDatiCancellati = {
                        azzeraMemoriaDopoReset()
                        schermata = Schermata.CreazioneIdentita
                    }
                )
                is Schermata.RichiedeOrbot -> RichiedeOrbotScreen(
                    onOrbotPronto = { schermata = Schermata.ListaChat }
                )
                is Schermata.ListaChat -> ListaChatScreen(
                    contatti = contatti,
                    onApriChat = { nickname ->
                        contatti.find { it.nickname == nickname }?.let { schermata = Schermata.Conversazione(it) }
                    },
                    onAggiungiContatto = { schermata = Schermata.AggiungiContatto },
                    onApriImpostazioni = { schermata = Schermata.Impostazioni }
                )
                is Schermata.Impostazioni -> ImpostazioniScreen(
                    temaAttuale = tema,
                    onTemaCambiato = { nuovo ->
                        tema = nuovo
                        storage.salvaTema(nuovo)
                    },
                    onIndietro = { schermata = Schermata.ListaChat }
                )
                is Schermata.AggiungiContatto -> AggiungiContattoScreen(
                    onIndietro = {
                        contattoPrecompilato = null
                        schermata = Schermata.ListaChat
                    },
                    onContattoAggiunto = {
                        contattoPrecompilato = null
                        contatti = storage.leggiContatti()
                        schermata = Schermata.ListaChat
                    },
                    contattoPrecompilato = contattoPrecompilato
                )
                is Schermata.Conversazione -> ConversazioneScreen(
                    contatto = corrente.contatto,
                    trasporto = trasporto,
                    messaggi = messaggiPerContatto[corrente.contatto.nickname] ?: emptyList(),
                    onMessaggiAggiornati = { nuovaLista -> messaggiPerContatto[corrente.contatto.nickname] = nuovaLista },
                    onIndietro = { schermata = Schermata.ListaChat }
                )
            }
        }
    }
}

/**
 * Gestisce un pacchetto arrivato dalla rete: riconosce il contatto dal nickname del mittente,
 * ricostruisce la chiave condivisa, decifra e aggiorna la conversazione.
 * Qualsiasi pacchetto che non supera i controlli (mittente sconosciuto, dati corrotti, cifrato
 * con una chiave diversa) viene scartato in silenzio, senza far crashare l'app. Anche i comandi
 * "elimina" vengono verificati con la decifratura, così non possono essere falsificati.
 */
private fun elaboraPacchettoInArrivo(
    storage: StorageLocale,
    crypto: CryptoManager,
    messaggiPerContatto: MutableMap<String, List<Messaggio>>,
    mittente: String,
    tipo: String,
    idCondiviso: String,
    pacchetto: PacchettoCifrato
) {
    try {
        val contatto = storage.leggiContatti().find { it.nickname == mittente } ?: return
        val chiavePrivata = storage.leggiChiavePrivata() ?: return
        val chiavePubblicaContatto = java.security.KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(contatto.chiavePubblicaBase64))
        )
        val chiave = crypto.derivaChiaveCondivisa(chiavePrivata, chiavePubblicaContatto)

        val nuovo: Messaggio? = when (tipo) {
            "foto" -> Messaggio(
                id = GeneratoreId.nuovo(), idCondiviso = idCondiviso, tipo = TipoMessaggio.FOTO,
                datiFoto = crypto.decifraBytes(pacchetto, chiave), orario = oraAttuale(), inviatoDaMe = false
            )
            "testo" -> Messaggio(
                id = GeneratoreId.nuovo(), idCondiviso = idCondiviso, tipo = TipoMessaggio.TESTO,
                testoChiaro = crypto.decifraMessaggio(pacchetto, chiave), orario = oraAttuale(), inviatoDaMe = false
            )
            "elimina" -> {
                crypto.decifraBytes(pacchetto, chiave) // verifica che il comando arrivi davvero dal contatto
                null
            }
            else -> return
        }

        synchronized(messaggiPerContatto) {
            val attuali = messaggiPerContatto[contatto.nickname] ?: emptyList()
            messaggiPerContatto[contatto.nickname] =
                if (tipo == "elimina") attuali.filterNot { it.idCondiviso == idCondiviso }
                else attuali + nuovo!!
        }
    } catch (e: Exception) {
        // Pacchetto non valido: scartato.
    }
}
