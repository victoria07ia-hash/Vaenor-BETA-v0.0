package com.nickapp.chatcifrata.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nickapp.chatcifrata.crypto.CryptoManager
import com.nickapp.chatcifrata.data.StorageLocale

@Composable
fun CreazioneIdentitaScreen(
    storage: StorageLocale,
    onIdentitaCreata: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var errore by remember { mutableStateOf<String?>(null) }
    var inCorso by remember { mutableStateOf(false) }
    val crypto = remember { CryptoManager() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text("Crea la tua identità", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nessuna email o numero di telefono richiesto. Scegli solo un nickname.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it; errore = null },
            label = { Text("Nickname") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        errore?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val pulito = nickname.trim()
                when {
                    pulito.length < 3 -> errore = "Il nickname deve avere almeno 3 caratteri"
                    pulito.length > 20 -> errore = "Il nickname è troppo lungo (max 20 caratteri)"
                    else -> {
                        inCorso = true
                        val coppiaChiavi = crypto.generaCoppiaChiavi()
                        storage.salvaIdentita(pulito, coppiaChiavi)
                        inCorso = false
                        onIdentitaCreata()
                    }
                }
            },
            enabled = !inCorso,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (inCorso) "Generazione chiavi in corso…" else "Crea identità")
        }
    }
}
