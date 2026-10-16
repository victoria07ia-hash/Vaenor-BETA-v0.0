package com.nickapp.chatcifrata.rete

import com.nickapp.chatcifrata.crypto.PacchettoCifrato
import kotlinx.coroutines.launch

interface TrasportoMessaggi {

    suspend fun avvia()

    /** Motivo dell'ultimo invio fallito (null se l'ultimo invio è riuscito). Serve a mostrare
     *  all'utente perché un messaggio non è partito. */
    val ultimoErrore: String?

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
    private val connessioniAttive = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var ultimoErroreInterno: String? = null
    override val ultimoErrore: String? get() = ultimoErroreInterno

    companion object {
        // Limiti sui dati in arrivo: chi si collega all'indirizzo .onion può mandare qualsiasi cosa,
        // quindi nessuna dimensione annunciata viene creduta senza controllo.
        private const val MAX_CAMPO_TESTO = 512                 // mittente, tipo, identificativo
        private const val MAX_IV = 32
        private const val MAX_DATI = 25 * 1024 * 1024           // circa 25 MB, foto comprese
        private const val TIMEOUT_LETTURA_MS = 30_000           // una connessione muta viene chiusa
        private const val MAX_CONNESSIONI_CONTEMPORANEE = 8

        const val PORTA_LOCALE = 8080

        /** Porta SOCKS predefinita di Orbot. */
        const val PORTA_SOCKS_ORBOT = 9050

        /** Porta del proxy SOCKS di Tor in uso: parte dal valore predefinito e viene aggiornata
         *  dal controllo all'avvio (vedi ControlloOrbot.kt) con la porta su cui il proxy risponde davvero. */
        @Volatile
        var portaSocks: Int = PORTA_SOCKS_ORBOT

        /** Un solo trasporto per tutta la vita del processo. Se l'activity viene ricreata (esci con
         *  "indietro" e riapri l'app) la nuova schermata riusa lo stesso server sulla porta 8080,
         *  invece di crearne un secondo che non riuscirebbe a occupare la porta. */
        val istanza: TrasportoTor by lazy { TrasportoTor() }
    }

    override suspend fun avvia() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (serverLocale?.isClosed == false) return@withContext
            serverLocale = java.net.ServerSocket(PORTA_LOCALE, 50, java.net.InetAddress.getLoopbackAddress())

            jobAscolto = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                while (true) {
                    val socket = (try { serverLocale?.accept() } catch (e: Exception) { null }) ?: break
                    if (connessioniAttive.incrementAndGet() > MAX_CONNESSIONI_CONTEMPORANEE) {
                        connessioniAttive.decrementAndGet()
                        try { socket.close() } catch (e: Exception) { }
                        continue
                    }
                    launch {
                        try { gestisciConnessioneInEntrata(socket) } finally { connessioniAttive.decrementAndGet() }
                    }
                }
            }
        }
    }

    private fun gestisciConnessioneInEntrata(socket: java.net.Socket) {
        try {
            socket.use {
                it.soTimeout = TIMEOUT_LETTURA_MS
                val input = it.getInputStream()
                val mittente = leggiCampoTesto(input)
                val tipo = leggiCampoTesto(input)
                val idCondiviso = leggiCampoTesto(input)
                val pacchetto = leggiPacchetto(input)
                callbackRicezione?.invoke(mittente, tipo, idCondiviso, pacchetto)
            }
        } catch (e: Exception) {
            // Connessione non valida (dati incoerenti, troppo grandi, interrotta o troppo lenta): scartata.
        }
    }

    override suspend fun invia(indirizzoDestinatario: String, mittente: String, tipo: String, idCondiviso: String, pacchetto: PacchettoCifrato): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (pacchetto.datiCifrati.size > MAX_DATI) {
                    ultimoErroreInterno = "contenuto troppo grande (max 25 MB)"
                    return@withContext false
                }
                // Ripulisco l'indirizzo da spazi, "http://" e barre finali che possono restare dopo un copia-incolla.
                val destinazione = indirizzoDestinatario.trim()
                    .removePrefix("http://").removePrefix("https://")
                    .trimEnd('/').lowercase()
                val proxy = java.net.Proxy(
                    java.net.Proxy.Type.SOCKS,
                    java.net.InetSocketAddress("127.0.0.1", portaSocks)
                )
                java.net.Socket(proxy).use { socket ->
                    socket.connect(
                        java.net.InetSocketAddress.createUnresolved(destinazione, PORTA_LOCALE),
                        60000
                    )
                    val output = socket.getOutputStream()
                    scriviCampoTesto(output, mittente)
                    scriviCampoTesto(output, tipo)
                    scriviCampoTesto(output, idCondiviso)
                    scriviPacchetto(output, pacchetto)
                }
                ultimoErroreInterno = null
                true
            } catch (e: Exception) {
                ultimoErroreInterno = "${e.javaClass.simpleName} ${e.message ?: ""}".trim().take(100)
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
        if (lunghezza < 0 || lunghezza > MAX_CAMPO_TESTO) throw java.io.IOException("campo non valido")
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
        val lunghezzaIv = flusso.readInt()
        if (lunghezzaIv < 0 || lunghezzaIv > MAX_IV) throw java.io.IOException("iv non valido")
        val iv = ByteArray(lunghezzaIv).also { flusso.readFully(it) }
        val lunghezzaDati = flusso.readInt()
        if (lunghezzaDati < 0 || lunghezzaDati > MAX_DATI) throw java.io.IOException("dati non validi")
        val dati = ByteArray(lunghezzaDati).also { flusso.readFully(it) }
        return PacchettoCifrato(dati, iv)
    }
}
