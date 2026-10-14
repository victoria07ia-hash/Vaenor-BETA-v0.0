package com.nickapp.chatcifrata.rete

import com.nickapp.chatcifrata.crypto.PacchettoCifrato
import kotlinx.coroutines.launch

interface TrasportoMessaggi {

    suspend fun avvia()

    suspend fun invia(indirizzoDestinatario: String, mittente: String, tipo: String, idCondiviso: String, pacchetto: PacchettoCifrato): Boolean

    fun alRicevimento(callback: (mittente: String, tipo: String, idCondiviso: String, pacchetto: PacchettoCifrato) -> Unit)

    suspend fun ferma()
}

/**
 * Implementazione basata su Orbot (l'app ufficiale del Tor Project per Android).
 * REQUISITI LATO UTENTE: Orbot installato e avviato, con un hidden service V3 configurato
 * che punta alla porta locale PORTA_LOCALE.
 *
 * NOTA: questa logica di rete non è ancora stata testata con una compilazione reale.
 */
class TrasportoTor : TrasportoMessaggi {

    private var callbackRicezione: ((String, String, String, PacchettoCifrato) -> Unit)? = null
    private var serverLocale: java.net.ServerSocket? = null
    private var jobAscolto: kotlinx.coroutines.Job? = null

    companion object {
        const val PORTA_LOCALE = 8080

        /** Porta SOCKS predefinita di Orbot. */
        const val PORTA_SOCKS_ORBOT = 9050

        /** Porta del proxy SOCKS di Tor in uso: parte dal valore predefinito e viene aggiornata
         *  dal controllo all'avvio (vedi ControlloOrbot.kt) con la porta su cui il proxy risponde davvero. */
        @Volatile
        var portaSocks: Int = PORTA_SOCKS_ORBOT
    }

    override suspend fun avvia() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (serverLocale?.isClosed == false) return@withContext
            serverLocale = java.net.ServerSocket(PORTA_LOCALE, 50, java.net.InetAddress.getLoopbackAddress())

            jobAscolto = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                while (true) {
                    val socket = serverLocale?.accept() ?: break
                    launch { gestisciConnessioneInEntrata(socket) }
                }
            }
        }
    }

    private fun gestisciConnessioneInEntrata(socket: java.net.Socket) {
        socket.use {
            val input = it.getInputStream()
            val mittente = leggiCampoTesto(input)
            val tipo = leggiCampoTesto(input)
            val idCondiviso = leggiCampoTesto(input)
            val pacchetto = leggiPacchetto(input)
            callbackRicezione?.invoke(mittente, tipo, idCondiviso, pacchetto)
        }
    }

    override suspend fun invia(indirizzoDestinatario: String, mittente: String, tipo: String, idCondiviso: String, pacchetto: PacchettoCifrato): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val proxy = java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    java.net.InetSocketAddress("127.0.0.1", portaSocks)
                )
                java.net.Socket(proxy).use { socket ->
                    socket.connect(
                        java.net.InetSocketAddress.createUnresolved(indirizzoDestinatario, PORTA_LOCALE),
                        60000
                    )
                    val output = socket.getOutputStream()
                    scriviCampoTesto(output, mittente)
                    scriviCampoTesto(output, tipo)
                    scriviCampoTesto(output, idCondiviso)
                    scriviPacchetto(output, pacchetto)
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    override fun alRicevimento(callback: (String, String, String, PacchettoCifrato) -> Unit) {
        callbackRicezione = callback
    }

    override suspend fun ferma() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            jobAscolto?.cancel()
            serverLocale?.close()
        }
    }

    private fun scriviCampoTesto(out: java.io.OutputStream, testo: String) {
        val bytes = testo.toByteArray(Charsets.UTF_8)
        out.write(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
        out.write(bytes)
    }

    private fun leggiCampoTesto(input: java.io.InputStream): String {
        val flusso = java.io.DataInputStream(input)
        val lunghezza = flusso.readInt()
        val bytes = ByteArray(lunghezza)
        flusso.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun scriviPacchetto(out: java.io.OutputStream, pacchetto: PacchettoCifrato) {
        out.write(java.nio.ByteBuffer.allocate(4).putInt(pacchetto.iv.size).array())
        out.write(pacchetto.iv)
        out.write(java.nio.ByteBuffer.allocate(4).putInt(pacchetto.datiCifrati.size).array())
        out.write(pacchetto.datiCifrati)
    }

    private fun leggiPacchetto(input: java.io.InputStream): PacchettoCifrato {
        val flusso = java.io.DataInputStream(input)
        val iv = ByteArray(flusso.readInt()).also { flusso.readFully(it) }
        val dati = ByteArray(flusso.readInt()).also { flusso.readFully(it) }
        return PacchettoCifrato(dati, iv)
    }
}
