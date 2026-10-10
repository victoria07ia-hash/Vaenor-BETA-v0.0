package com.nickapp.chatcifrata.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nickapp.chatcifrata.crypto.CryptoManager
import com.nickapp.chatcifrata.data.Contatto
import com.nickapp.chatcifrata.data.StorageLocale
import com.nickapp.chatcifrata.rete.TrasportoMessaggi
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.launch

enum class TipoMessaggio { TESTO, FOTO }

data class Messaggio(
    val id: Long,
    val idCondiviso: String,
    val tipo: TipoMessaggio,
    val testoChiaro: String = "",
    val datiFoto: ByteArray? = null,
    val fotoVista: Boolean = false,
    val orario: String,
    val inviatoDaMe: Boolean
)

private fun decodificaChiavePubblica(base64: String): PublicKey {
    val bytes = Base64.getDecoder().decode(base64)
    return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
}

@Composable
fun ConversazioneScreen(
    contatto: Contatto,
    trasporto: TrasportoMessaggi,
    messaggi: List<Messaggio>,
    onMessaggiAggiornati: (List<Messaggio>) -> Unit,
    onIndietro: () -> Unit
) {
    val context = LocalContext.current
    val storage = remember { StorageLocale(context) }
    val crypto = remember { CryptoManager() }
    val scope = rememberCoroutineScope()

    val chiavePrivataMia = remember { storage.leggiChiavePrivata() }
    val chiavePubblicaMia = remember { storage.leggiChiavePubblica() }
    val chiavePubblicaContatto = remember(contatto) { decodificaChiavePubblica(contatto.chiavePubblicaBase64) }

    val chiaveCondivisa = remember(chiavePrivataMia, chiavePubblicaContatto) {
        if (chiavePrivataMia != null) crypto.derivaChiaveCondivisa(chiavePrivataMia, chiavePubblicaContatto) else null
    }

    val codiceSicurezza = remember(chiavePubblicaMia, chiavePubblicaContatto) {
        if (chiavePubblicaMia != null) crypto.calcolaCodiceSicurezza(chiavePubblicaMia, chiavePubblicaContatto) else "------"
    }

    var testoInput by remember { mutableStateOf("") }
    var mostraCodice by remember { mutableStateOf(false) }
    var statoInvio by remember { mutableStateOf<String?>(null) }
    var fotoInVisualizzazione by remember { mutableStateOf<Messaggio?>(null) }

    fun eliminaPerTutti(messaggio: Messaggio) {
        onMessaggiAggiornati(messaggi.filterNot { it.id == messaggio.id })
        val chiave = chiaveCondivisa ?: return
        scope.launch {
            val pacchettoVuoto = crypto.cifraBytes(ByteArray(0), chiave)
            trasporto.invia(contatto.indirizzoOnion, "elimina", messaggio.idCondiviso, pacchettoVuoto)
        }
    }

    val selezionaFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val chiave = chiaveCondivisa
        if (uri != null && chiave != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val pacchetto = crypto.cifraBytes(bytes, chiave)
                val idCondiviso = UUID.randomUUID().toString()
                onMessaggiAggiornati(
                    messaggi + Messaggio(
                        id = System.currentTimeMillis(), idCondiviso = idCondiviso, tipo = TipoMessaggio.FOTO,
                        datiFoto = bytes, orario = "ora", inviatoDaMe = true
                    )
                )
                scope.launch {
                    val inviato = trasporto.invia(contatto.indirizzoOnion, "foto", idCondiviso, pacchetto)
                    statoInvio = if (inviato) null else "Non recapitato: verifica che Orbot sia attivo e il contatto raggiungibile"
                }
            }
        }
    }

    // Ogni pacchetto in arrivo passa da un blocco try/catch: se la decifratura fallisce
    // (pacchetto corrotto, manomesso, o cifrato con una chiave diversa da quella attesa),
    // viene scartato invece di far crashare l'app.
    DisposableEffect(contatto, chiaveCondivisa) {
        trasporto.alRicevimento { mittente, tipo, idCondiviso, pacchetto ->
            val chiave = chiaveCondivisa
            if (mittente == contatto.nickname && chiave != null) {
                try {
                    when (tipo) {
                        "foto" -> {
                            val bytes = crypto.decifraBytes(pacchetto, chiave)
                            onMessaggiAggiornati(
                                messaggi + Messaggio(
                                    id = System.currentTimeMillis(), idCondiviso = idCondiviso, tipo = TipoMessaggio.FOTO,
                                    datiFoto = bytes, orario = "ora", inviatoDaMe = false
                                )
                            )
                        }
                        "elimina" -> {
                            onMessaggiAggiornati(messaggi.filterNot { it.idCondiviso == idCondiviso })
                        }
                        else -> {
                            val testo = crypto.decifraMessaggio(pacchetto, chiave)
                            onMessaggiAggiornati(
                                messaggi + Messaggio(
                                    id = System.currentTimeMillis(), idCondiviso = idCondiviso, tipo = TipoMessaggio.TESTO,
                                    testoChiaro = testo, orario = "ora", inviatoDaMe = false
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Pacchetto non decifrabile: scartato silenziosamente, senza crash.
                }
            }
        }
        onDispose { }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onIndietro) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(contatto.nickname, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("cifrato end-to-end · via Tor", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            TextButton(onClick = { mostraCodice = !mostraCodice }) {
                Text("codice", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (mostraCodice) {
            Text(
                codiceSicurezza,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFEEF0FF)).padding(8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
        }
        statoInvio?.let {
            Text(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFB45309))
        }

        Divider()

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(messaggi, key = { it.id }) { messaggio ->
                BollaMessaggio(
                    messaggio = messaggio,
                    onElimina = { eliminaPerTutti(messaggio) },
                    onAperturaFoto = {
                        if (messaggio.tipo == TipoMessaggio.FOTO && !messaggio.fotoVista) {
                            fotoInVisualizzazione = messaggio
                        }
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                selezionaFoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Filled.AddCircle, contentDescription = "Allega foto (vista una volta)")
            }
            OutlinedTextField(
                value = testoInput,
                onValueChange = { testoInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Scrivi un messaggio…") }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                val chiave = chiaveCondivisa
                val testo = testoInput
                if (testo.isNotBlank() && chiave != null) {
                    val pacchetto = crypto.cifraMessaggio(testo, chiave)
                    val idCondiviso = UUID.randomUUID().toString()
                    onMessaggiAggiornati(
                        messaggi + Messaggio(
                            id = System.currentTimeMillis(), idCondiviso = idCondiviso, tipo = TipoMessaggio.TESTO,
                            testoChiaro = testo, orario = "ora", inviatoDaMe = true
                        )
                    )
                    testoInput = ""
                    scope.launch {
                        val inviato = trasporto.invia(contatto.indirizzoOnion, "testo", idCondiviso, pacchetto)
                        statoInvio = if (inviato) null else "Non recapitato: verifica che Orbot sia attivo e il contatto raggiungibile"
                    }
                }
            }) {
                Icon(Icons.Filled.Send, contentDescription = "Invia")
            }
        }
    }

    fotoInVisualizzazione?.let { messaggio ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable {
                    onMessaggiAggiornati(
                        messaggi.map {
                            if (it.id == messaggio.id) it.copy(datiFoto = null, fotoVista = true) else it
                        }
                    )
                    fotoInVisualizzazione = null
                },
            contentAlignment = Alignment.Center
        ) {
            val bytes = messaggio.datiFoto
            if (bytes != null) {
                val bitmap = remember(messaggio.id) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                if (bitmap != null) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Foto ricevuta")
                }
            }
            Text(
                "Tocca per chiudere — la foto non sarà più visibile",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BollaMessaggio(messaggio: Messaggio, onElimina: () -> Unit, onAperturaFoto: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (messaggio.inviatoDaMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!messaggio.inviatoDaMe) {
            IconButton(onClick = onElimina, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Elimina per tutti", modifier = Modifier.size(14.dp), tint = Color.Gray)
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .background(
                    color = if (messaggio.inviatoDaMe) Color(0xFFE6F1FB) else Color(0xFFF1EFE8),
                    shape = RoundedCornerShape(12.dp)
                )
                .then(if (messaggio.tipo == TipoMessaggio.FOTO && !messaggio.fotoVista) Modifier.clickable { onAperturaFoto() } else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                when {
                    messaggio.tipo == TipoMessaggio.FOTO && messaggio.fotoVista ->
                        Text("📷 Foto visualizzata", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    messaggio.tipo == TipoMessaggio.FOTO ->
                        Text("📷 Foto · tocca per vedere una volta", style = MaterialTheme.typography.bodySmall)
                    else -> Text(messaggio.testoChiaro)
                }
                Text(messaggio.orario, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        if (messaggio.inviatoDaMe) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onElimina, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Elimina per tutti", modifier = Modifier.size(14.dp), tint = Color.Gray)
            }
        }
    }
}
