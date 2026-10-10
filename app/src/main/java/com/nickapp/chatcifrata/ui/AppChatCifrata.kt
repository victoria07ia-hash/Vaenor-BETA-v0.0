package com.nickapp.chatcifrata.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nickapp.chatcifrata.data.StorageLocale

sealed class Schermata {
    object Caricamento : Schermata()
    object CreazioneIdentita : Schermata()
    object ImpostaPin : Schermata()
    object RichiediPin : Schermata()
    object RichiedeOrbot : Schermata()
    object ListaChat : Schermata()
    object AggiungiContatto : Schermata()
    data class Conversazione(val contatto: com.nickapp.chatcifrata.data.Contatto) : Schermata()
}

@Composable
fun AppChatCifrata(contattoDaLink: com.nickapp.chatcifrata.data.Contatto? = null) {
    val context = LocalContext.current
    val storage = remember { StorageLocale(context) }
    val trasporto = remember { com.nickapp.chatcifrata.rete.TrasportoTor() }

    var schermata by remember {
        mutableStateOf<Schermata>(
            when {
                !storage.haIdentita() -> Schermata.CreazioneIdentita
                !storage.haPinImpostato() -> Schermata.ImpostaPin
                else -> Schermata.RichiediPin
            }
        )
    }
    var contatti by remember { mutableStateOf(storage.leggiContatti()) }
    val messaggiPerContatto = remember { mutableStateMapOf<String, List<Messaggio>>() }

    // Avvia il trasporto Tor una sola volta, appena arriviamo alla lista chat (cioè dopo
    // che il controllo Orbot è già passato).
    LaunchedEffect(schermata) {
        if (schermata == Schermata.ListaChat) {
            try { trasporto.avvia() } catch (e: Exception) { /* già avviato o errore gestito altrove */ }
        }
    }

    LaunchedEffect(contattoDaLink, schermata) {
        if (contattoDaLink != null && schermata == Schermata.ListaChat) {
            schermata = Schermata.AggiungiContatto
        }
    }

    MaterialTheme {
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
                    onDatiCancellati = { schermata = Schermata.CreazioneIdentita }
                )
                is Schermata.RichiediPin -> BloccoPinScreen(
                    storage = storage,
                    modalitaImpostazione = false,
                    onSbloccato = { schermata = Schermata.RichiedeOrbot },
                    onDatiCancellati = { schermata = Schermata.CreazioneIdentita }
                )
                is Schermata.RichiedeOrbot -> RichiedeOrbotScreen(
                    onOrbotPronto = { schermata = Schermata.ListaChat }
                )
                is Schermata.ListaChat -> ListaChatScreen(
                    contatti = contatti,
                    onApriChat = { nickname ->
                        contatti.find { it.nickname == nickname }?.let { schermata = Schermata.Conversazione(it) }
                    },
                    onAggiungiContatto = { schermata = Schermata.AggiungiContatto }
                )
                is Schermata.AggiungiContatto -> AggiungiContattoScreen(
                    onIndietro = { schermata = Schermata.ListaChat },
                    onContattoAggiunto = {
                        contatti = storage.leggiContatti()
                        schermata = Schermata.ListaChat
                    },
                    contattoPrecompilato = contattoDaLink
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
