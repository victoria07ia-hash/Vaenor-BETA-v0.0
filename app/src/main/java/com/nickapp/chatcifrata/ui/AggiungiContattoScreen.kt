package com.nickapp.chatcifrata.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.nickapp.chatcifrata.crypto.CryptoManager
import com.nickapp.chatcifrata.data.Contatto
import com.nickapp.chatcifrata.data.StorageLocale
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

private fun costruisciLinkInvito(nickname: String, chiavePubblicaBase64: String, onion: String): String {
    fun codifica(testo: String) = URLEncoder.encode(testo, "UTF-8")
    return "vaenor://aggiungi?nick=${codifica(nickname)}&chiave=${codifica(chiavePubblicaBase64)}&onion=${codifica(onion)}"
}

/** Estrae e VALIDA un contatto da un testo ricevuto (link incollato, deep link o QR scansionato).
 *  Rifiuta (restituisce null) qualsiasi input che non rispetti esattamente il formato atteso. */
fun leggiContattoDaLink(testo: String): Contatto? {
    return try {
        val uri = android.net.Uri.parse(testo.trim())
        if (uri.scheme != "vaenor" || uri.host != "aggiungi") return null

        val nick = uri.getQueryParameter("nick")?.takeIf { it.isNotBlank() && it.length <= 20 } ?: return null
        val chiaveBase64 = uri.getQueryParameter("chiave") ?: return null
        val onion = uri.getQueryParameter("onion")?.takeIf { it.isNotBlank() } ?: return null

        val bytes = Base64.getDecoder().decode(chiaveBase64)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))

        Contatto(nickname = nick, chiavePubblicaBase64 = chiaveBase64, indirizzoOnion = onion)
    } catch (e: Exception) {
        null
    }
}

private fun generaBitmapQr(testo: String, dimensione: Int = 480): Bitmap {
    val matrice = QRCodeWriter().encode(testo, BarcodeFormat.QR_CODE, dimensione, dimensione)
    val bitmap = Bitmap.createBitmap(dimensione, dimensione, Bitmap.Config.RGB_565)
    for (x in 0 until dimensione) {
        for (y in 0 until dimensione) {
            bitmap.setPixel(x, y, if (matrice[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}

@Composable
fun AggiungiContattoScreen(
    onIndietro: () -> Unit,
    onContattoAggiunto: () -> Unit,
    contattoPrecompilato: Contatto? = null
) {
    val context = LocalContext.current
    val storage = remember { StorageLocale(context) }
    val crypto = remember { CryptoManager() }

    var indirizzoOnionProprio by remember { mutableStateOf(storage.leggiIndirizzoOnionProprio() ?: "") }
    var linkIncollato by remember { mutableStateOf("") }
    var errore by remember { mutableStateOf<String?>(null) }
    var contattoDaConfermare by remember { mutableStateOf(contattoPrecompilato) }

    val lanciaScansione = rememberLauncherForActivityResult(ScanContract()) { risultato ->
        val testoLetto = risultato.contents
        if (testoLetto != null) {
            val contatto = leggiContattoDaLink(testoLetto)
            if (contatto == null) {
                errore = "Il QR scansionato non è un codice Vaenor valido"
            } else {
                contattoDaConfermare = contatto
            }
        }
    }

    val nickname = storage.leggiNickname() ?: ""
    val chiavePubblica = storage.leggiChiavePubblica()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        TextButton(onClick = onIndietro) { Text("← Indietro") }

        contattoDaConfermare?.let { contatto ->
            Text("Aggiungere questo contatto?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Nickname: ${contatto.nickname}")
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    storage.aggiungiContatto(contatto)
                    onContattoAggiunto()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Aggiungi") }
            Spacer(Modifier.height(28.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
        }

        Text("Il tuo codice di invito", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (indirizzoOnionProprio.isBlank()) {
            Text(
                "Prima serve l'indirizzo .onion che Orbot ti ha assegnato per l'hidden service " +
                    "(vedi la schermata di Orbot dove l'hai configurato).",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = indirizzoOnionProprio,
                onValueChange = { indirizzoOnionProprio = it },
                label = { Text("Indirizzo .onion (da Orbot)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { storage.salvaIndirizzoOnionProprio(indirizzoOnionProprio) }) {
                Text("Salva indirizzo")
            }
        } else if (chiavePubblica != null) {
            val link = costruisciLinkInvito(nickname, crypto.chiavePubblicaComeTesto(chiavePubblica), indirizzoOnionProprio)
            val bitmapQr = remember(link) { generaBitmapQr(link) }

            Image(
                bitmap = bitmapQr.asImageBitmap(),
                contentDescription = "QR code del tuo contatto",
                modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Se siete vicini, fai scansionare questo QR. Altrimenti condividi il link qui sotto.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            SelectionContainer { Text(link, style = MaterialTheme.typography.labelSmall) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, link)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Condividi link di invito"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Condividi link") }
        }

        Spacer(Modifier.height(28.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("Aggiungi un contatto", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                errore = null
                lanciaScansione.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Inquadra il QR del contatto")
                        .setBeepEnabled(false)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Scansiona QR del contatto") }

        Spacer(Modifier.height(12.dp))
        Text("Oppure incolla qui il link ricevuto:", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = linkIncollato,
            onValueChange = { linkIncollato = it; errore = null },
            label = { Text("Link del contatto") },
            modifier = Modifier.fillMaxWidth()
        )
        errore?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val contatto = leggiContattoDaLink(linkIncollato)
                if (contatto == null) {
                    errore = "Link non valido: controlla di averlo copiato per intero"
                } else {
                    storage.aggiungiContatto(contatto)
                    onContattoAggiunto()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aggiungi contatto")
        }
    }
}
