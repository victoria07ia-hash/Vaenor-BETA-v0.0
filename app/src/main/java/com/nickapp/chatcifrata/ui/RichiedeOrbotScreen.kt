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
import com.nickapp.chatcifrata.rete.TrasportoTor
import com.nickapp.chatcifrata.rete.orbotInstallato
import com.nickapp.chatcifrata.rete.trovaProxyTor
import kotlinx.coroutines.launch

/**
 * Vaenor funziona solo sopra la rete Tor: senza Orbot attivo, nessun messaggio può essere
 * inviato o ricevuto. Questa schermata blocca l'accesso finché il proxy Tor non risponde.
 *
 * Il criterio che conta è il proxy: se risponde, si entra, indipendentemente dal fatto che
 * Android ci dica o no se Orbot è installato. Il controllo "installato" serve solo a scegliere
 * quale messaggio mostrare quando il proxy NON risponde.
 */
@Composable
fun RichiedeOrbotScreen(onOrbotPronto: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var installato by remember { mutableStateOf(orbotInstallato(context)) }
    var controllando by remember { mutableStateOf(false) }

    fun ricontrolla() {
        installato = orbotInstallato(context)
        controllando = true
        scope.launch {
            val porta = trovaProxyTor()
            controllando = false
            if (porta != null) {
                // Ricordo la porta su cui il proxy risponde davvero, così l'invio dei messaggi usa quella.
                TrasportoTor.portaSocks = porta
                onOrbotPronto()
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
                    "Installala, avviala, poi torna qui e premi \"Ricontrolla\".",
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
                "Orbot risulta installato ma il tunnel Tor non è attivo. " +
                    "Apri Orbot, avvia la connessione e aspetta che risulti connesso, poi torna qui.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Controllo le porte 9050 e 9150 su questo telefono.",
            style = MaterialTheme.typography.labelSmall
        )

        Spacer(Modifier.height(16.dp))
        Button(onClick = { ricontrolla() }, enabled = !controllando, modifier = Modifier.fillMaxWidth()) {
            Text(if (controllando) "Verifica in corso…" else "Ricontrolla")
        }
    }
}
