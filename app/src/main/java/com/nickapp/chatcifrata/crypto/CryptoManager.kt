package com.nickapp.chatcifrata.crypto

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

/**
 * Gestisce l'identità crittografica locale dell'utente (coppia di chiavi ECDH),
 * la cifratura/decifratura end-to-end dei messaggi (AES-GCM su chiave condivisa)
 * e il calcolo del codice di sicurezza per verificare una chat contro intercettazioni.
 *
 * Nessun dato passa da un server: tutto viene generato e calcolato sul dispositivo.
 */
class CryptoManager {

    /** Genera una nuova coppia di chiavi ECDH (curva P-256). Da chiamare una sola volta,
     *  al primo avvio dell'app, per creare l'identità locale dell'utente. */
    fun generaCoppiaChiavi(): java.security.KeyPair {
        val generatore = KeyPairGenerator.getInstance("EC")
        generatore.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        return generatore.generateKeyPair()
    }

    /** Converte la chiave pubblica in una stringa Base64, così può essere condivisa
     *  via QR code o link con un contatto. */
    fun chiavePubblicaComeTesto(chiave: PublicKey): String =
        Base64.getEncoder().encodeToString(chiave.encoded)

    /** Deriva la chiave segreta condivisa tra due utenti (Diffie-Hellman).
     *  Chiamata da entrambe le parti: A usa (privataA, pubblicaB), B usa (privataB, pubblicaA).
     *  Il risultato è identico su entrambi i dispositivi, senza che la chiave viaggi mai in rete.
     *
     *  Il segreto grezzo ECDH viene passato attraverso HKDF (RFC 5869) invece di un semplice
     *  hash SHA-256: è lo standard corretto per derivare una chiave da un segreto Diffie-Hellman,
     *  perché separa correttamente "estrazione" (uniforma l'entropia del segreto) da "espansione"
     *  (genera l'output della lunghezza voluta, legato a un contesto specifico tramite l'info tag). */
    fun derivaChiaveCondivisa(privata: PrivateKey, pubblicaAltro: PublicKey): SecretKeySpec {
        val accordo = KeyAgreement.getInstance("ECDH")
        accordo.init(privata)
        accordo.doPhase(pubblicaAltro, true)
        val segretoGrezzo = accordo.generateSecret()

        val chiaveAes = hkdf(segretoGrezzo, info = "vaenor-chiave-messaggi".toByteArray(Charsets.UTF_8), lunghezzaOutput = 32)
        return SecretKeySpec(chiaveAes, "AES")
    }

    /** Implementazione minimale di HKDF-SHA256 (RFC 5869): estrazione (HMAC con sale fisso)
     *  seguita da espansione (HMAC ripetuto con l'info tag) fino alla lunghezza richiesta. */
    private fun hkdf(materialeSegreto: ByteArray, info: ByteArray, lunghezzaOutput: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")

        mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
        val chiavePseudocasuale = mac.doFinal(materialeSegreto)

        var bloccoPrecedente = ByteArray(0)
        val output = java.io.ByteArrayOutputStream()
        var contatore = 1
        while (output.size() < lunghezzaOutput) {
            mac.init(SecretKeySpec(chiavePseudocasuale, "HmacSHA256"))
            mac.update(bloccoPrecedente)
            mac.update(info)
            mac.update(contatore.toByte())
            bloccoPrecedente = mac.doFinal()
            output.write(bloccoPrecedente)
            contatore++
        }
        return output.toByteArray().copyOf(lunghezzaOutput)
    }

    fun cifraMessaggio(testo: String, chiaveCondivisa: SecretKeySpec): PacchettoCifrato =
        cifraBytes(testo.toByteArray(Charsets.UTF_8), chiaveCondivisa)

    fun decifraMessaggio(pacchetto: PacchettoCifrato, chiaveCondivisa: SecretKeySpec): String =
        String(decifraBytes(pacchetto, chiaveCondivisa), Charsets.UTF_8)

    fun cifraBytes(dati: ByteArray, chiaveCondivisa: SecretKeySpec): PacchettoCifrato {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, chiaveCondivisa, GCMParameterSpec(128, iv))
        return PacchettoCifrato(cipher.doFinal(dati), iv)
    }

    fun decifraBytes(pacchetto: PacchettoCifrato, chiaveCondivisa: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, chiaveCondivisa, GCMParameterSpec(128, pacchetto.iv))
        return cipher.doFinal(pacchetto.datiCifrati)
    }

    /** Calcola il codice di sicurezza di una chat combinando le due chiavi pubbliche. */
    fun calcolaCodiceSicurezza(pubblicaA: PublicKey, pubblicaB: PublicKey): String {
        val testoA = chiavePubblicaComeTesto(pubblicaA)
        val testoB = chiavePubblicaComeTesto(pubblicaB)
        val (prima, seconda) = if (testoA < testoB) testoA to testoB else testoB to testoA

        val digest = MessageDigest.getInstance("SHA-256")
            .digest((prima + seconda).toByteArray(Charsets.UTF_8))

        return digest.take(6).joinToString(" ") { (it.toInt() and 0xFF).rem(100).toString().padStart(2, '0') }
    }
}

/** Un messaggio cifrato pronto per essere trasmesso: dati cifrati + IV, entrambi non sensibili da soli. */
data class PacchettoCifrato(
    val datiCifrati: ByteArray,
    val iv: ByteArray
)
