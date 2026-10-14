package com.nickapp.chatcifrata.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nickapp.chatcifrata.data.TemaApp

@Composable
fun ImpostazioniScreen(
    temaAttuale: TemaApp,
    onTemaCambiato: (TemaApp) -> Unit,
    onIndietro: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onIndietro) { Text("← Indietro") }

        Text("Impostazioni", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Text("Aspetto", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OpzioneTema("Sistema (segue il telefono)", temaAttuale == TemaApp.SISTEMA) { onTemaCambiato(TemaApp.SISTEMA) }
        OpzioneTema("Chiaro", temaAttuale == TemaApp.CHIARO) { onTemaCambiato(TemaApp.CHIARO) }
        OpzioneTema("Scuro", temaAttuale == TemaApp.SCURO) { onTemaCambiato(TemaApp.SCURO) }
    }
}

@Composable
private fun OpzioneTema(etichetta: String, selezionata: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selezionata, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(etichetta)
    }
}
