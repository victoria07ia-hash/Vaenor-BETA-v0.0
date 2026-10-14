package com.nickapp.chatcifrata.rete

import android.content.Context
import android.content.pm.PackageManager

/** Nome pacchetto ufficiale di Orbot sul Play Store/F-Droid. */
private const val PACCHETTO_ORBOT = "org.torproject.android"

/** Porte SOCKS su cui cercare il proxy Tor: 9050 è il default di Orbot, 9150 quello di Tor Browser. */
private val PORTE_SOCKS_DA_PROVARE = listOf(9050, 9150)

/** Verifica se l'app Orbot risulta installata (serve la dichiarazione <queries> nel Manifest).
 *  Usato solo per scegliere il messaggio giusto da mostrare: l'accesso all'app dipende
 *  dal fatto che il proxy Tor risponda davvero, non da questo controllo. */
fun orbotInstallato(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(PACCHETTO_ORBOT, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

/** Cerca un proxy SOCKS5 di Tor in ascolto sul dispositivo. Restituisce la porta su cui
 *  risponde, oppure null se nessuna delle porte note è raggiungibile (Orbot spento,
 *  non connesso, o configurato su una porta diversa). */
suspend fun trovaProxyTor(): Int? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    for (porta in PORTE_SOCKS_DA_PROVARE) {
        try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", porta), 1500)
            }
            return@withContext porta
        } catch (e: Exception) {
            // nessuno in ascolto su questa porta: provo la successiva
        }
    }
    null
}

/** Versione semplificata di [trovaProxyTor]: dice solo se un proxy Tor risponde, senza dire su quale porta. */
suspend fun proxyOrbotRaggiungibile(): Boolean = trovaProxyTor() != null
