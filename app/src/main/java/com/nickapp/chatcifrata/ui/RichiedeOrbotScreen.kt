package com.nickapp.chatcifrata.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nickapp.chatcifrata.rete.orbotInstallato
import com.nickapp.chatcifrata.rete.proxyOrbotRaggiungibile
import kotlinx.coroutines.launch

/**
 * Vaenor funziona solo sopra la rete Tor: senza Orbot attivo, nessun messaggio può essere
 * inviato o ricevuto. Invece di lasciare che l'utente scopra il problema solo quando un
 * invio fallisce, questa schermata blocca l'accesso finché Orbot non risulta installato
 * E il suo proxy SOCKS non risponde davvero.
 */
@Composable
fun RichiedeOrbotScreen(onOrbotPronto: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var installato by remember { mutableStateOf(orbotInstallato(context)) }
    var controllandoProxy by remember { mutableStateOf(false) }
    var proxyOk by remember { mutableStateOf(false) }

    fun ricontrolla() {
        installato = orbotInstallato(context)
        if (installato) {
            controllandoProxy = true
            scope.launch {
                proxyOk = proxyOrbotRaggiungibile()
                controllandoProxy = false
                if (proxyOk) onOrbotPronto()
            }
        }
    }

    LaunchedEffect(Unit) { ricontrolla() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))

        if (!installato) {
            Text("Serve Orbot", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Vaenor funziona solo attraverso la rete Tor, tramite l'app Orbot. " +
                    "Installala, poi torna qui e premi \"Ricontrolla\".",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://f-droid.org/packages/org.torproject.android/")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Apri pagina di Orbot (F-Droid)") }
        } else {
            Text("Orbot non risponde", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Orbot risulta installato ma il tunnel Tor non è ancora attivo. " +
                    "Apri Orbot, avvia la connessione, poi torna qui.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { ricontrolla() }, enabled = !controllandoProxy, modifier = Modifier.fillMaxWidth()) {
            Text(if (controllandoProxy) "Verifica in corso…" else "Ricontrolla")
        }
    }
}
