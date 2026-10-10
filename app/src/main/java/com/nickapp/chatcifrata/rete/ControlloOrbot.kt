package com.nickapp.chatcifrata.rete

import android.content.Context
import android.content.pm.PackageManager

/** Nome pacchetto ufficiale di Orbot sul Play Store/F-Droid. */
private const val PACCHETTO_ORBOT = "org.torproject.android"

/** Verifica se l'app Orbot risulta installata sul dispositivo (non dice se è avviata). */
fun orbotInstallato(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(PACCHETTO_ORBOT, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

/** Verifica se il proxy SOCKS5 di Orbot risponde sulla porta attesa: se Orbot è installato
 *  ma non avviato (o il tunnel Tor non è ancora su), questa connessione fallisce — è il modo
 *  più diretto per capire se l'app può davvero contare sulla rete Tor in questo momento. */
suspend fun proxyOrbotRaggiungibile(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        java.net.Socket().use { socket ->
            socket.connect(
                java.net.InetSocketAddress("127.0.0.1", TrasportoTor.PORTA_SOCKS_ORBOT),
                1500
            )
        }
        true
    } catch (e: Exception) {
        false
    }
}
