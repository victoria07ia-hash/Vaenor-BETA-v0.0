package com.nickapp.chatcifrata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nickapp.chatcifrata.data.Contatto

@Composable
fun ListaChatScreen(
    contatti: List<Contatto>,
    onApriChat: (String) -> Unit,
    onAggiungiContatto: () -> Unit,
    onApriImpostazioni: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chat", style = MaterialTheme.typography.headlineSmall)
            Row {
                IconButton(onClick = onAggiungiContatto) {
                    Icon(Icons.Filled.Add, contentDescription = "Aggiungi contatto")
                }
                IconButton(onClick = onApriImpostazioni) {
                    Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (contatti.isEmpty()) {
            Text(
                "Nessun contatto ancora. Tocca + per aggiungerne uno tramite QR code o link.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        } else {
            LazyColumn {
                items(contatti) { contatto ->
                    RigaContatto(contatto = contatto, onClick = { onApriChat(contatto.nickname) })
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun RigaContatto(contatto: Contatto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(contatto.nickname.take(2).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(10.dp))
        Text(contatto.nickname, style = MaterialTheme.typography.bodyLarge)
    }
}
