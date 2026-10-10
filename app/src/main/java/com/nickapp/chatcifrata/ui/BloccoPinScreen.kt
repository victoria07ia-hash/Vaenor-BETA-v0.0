package com.nickapp.chatcifrata.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nickapp.chatcifrata.data.RisultatoPin
import com.nickapp.chatcifrata.data.StorageLocale

@Composable
fun BloccoPinScreen(
    storage: StorageLocale,
    modalitaImpostazione: Boolean,
    onSbloccato: () -> Unit,
    onDatiCancellati: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var conferma by remember { mutableStateOf("") }
    var messaggio by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))

        if (modalitaImpostazione) {
            Text("Imposta un PIN", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ti verrà richiesto a ogni apertura dell'app. Dopo 3 tentativi sbagliati, " +
                    "tutti i dati (chiavi, chat, identità) vengono cancellati per sempre.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text("Inserisci il PIN", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tentativi rimasti prima della cancellazione: ${storage.tentativiRimasti()}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
            label = { Text("PIN (4-6 cifre)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (modalitaImpostazione) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = conferma,
                onValueChange = { if (it.length <= 6) conferma = it.filter(Char::isDigit) },
                label = { Text("Conferma PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        messaggio?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (modalitaImpostazione) {
                    when {
                        pin.length < 4 -> messaggio = "Il PIN deve avere almeno 4 cifre"
                        pin != conferma -> messaggio = "I due PIN non coincidono"
                        else -> {
                            storage.impostaPin(pin)
                            onSbloccato()
                        }
                    }
                } else {
                    when (storage.verificaPin(pin)) {
                        RisultatoPin.CORRETTO -> onSbloccato()
                        RisultatoPin.SBAGLIATO -> {
                            messaggio = "PIN errato. Tentativi rimasti: ${storage.tentativiRimasti()}"
                            pin = ""
                        }
                        RisultatoPin.DATI_CANCELLATI -> onDatiCancellati()
                        RisultatoPin.NESSUN_PIN_IMPOSTATO -> onSbloccato()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (modalitaImpostazione) "Imposta PIN" else "Sblocca")
        }
    }
}
