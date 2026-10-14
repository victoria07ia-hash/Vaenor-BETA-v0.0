package com.nickapp.chatcifrata.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Salva l'identità locale (nickname + coppia di chiavi), il PIN (come hash), la rubrica
 * contatti e l'indirizzo onion proprio in uno storage cifrato dal sistema (Android Keystore).
 * Nessun dato esce dal dispositivo tramite questa classe: è solo persistenza locale.
 */
class StorageLocale(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "identita_sicura",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun haIdentita(): Boolean = prefs.contains("chiave_privata")

    fun salvaIdentita(nickname: String, coppiaChiavi: KeyPair) {
        prefs.edit()
            .putString("nickname", nickname)
            .putString("chiave_privata", Base64.getEncoder().encodeToString(coppiaChiavi.private.encoded))
            .putString("chiave_pubblica", Base64.getEncoder().encodeToString(coppiaChiavi.public.encoded))
            .apply()
    }

    fun leggiNickname(): String? = prefs.getString("nickname", null)

    fun leggiIndirizzoOnionProprio(): String? = prefs.getString("onion_proprio", null)

    fun salvaIndirizzoOnionProprio(indirizzo: String) {
        prefs.edit().putString("onion_proprio", indirizzo).apply()
    }

    fun leggiChiavePrivata(): PrivateKey? {
        val testo = prefs.getString("chiave_privata", null) ?: return null
        val bytes = Base64.getDecoder().decode(testo)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    fun leggiChiavePubblica(): PublicKey? {
        val testo = prefs.getString("chiave_pubblica", null) ?: return null
        val bytes = Base64.getDecoder().decode(testo)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    // --- Gestione PIN di accesso ---

    fun haPinImpostato(): Boolean = prefs.contains("pin_hash")

    fun impostaPin(pin: String) {
        prefs.edit()
            .putString("pin_hash", hashPin(pin))
            .putInt("tentativi_falliti", 0)
            .apply()
    }

    /** Verifica il PIN inserito. Restituisce true se corretto; se sbagliato, incrementa
     *  il contatore e, al terzo errore consecutivo, cancella tutti i dati dell'app. */
    fun verificaPin(pinInserito: String): RisultatoPin {
        val hashSalvato = prefs.getString("pin_hash", null) ?: return RisultatoPin.NESSUN_PIN_IMPOSTATO

        return if (hashPin(pinInserito) == hashSalvato) {
            prefs.edit().putInt("tentativi_falliti", 0).apply()
            RisultatoPin.CORRETTO
        } else {
            val tentativi = prefs.getInt("tentativi_falliti", 0) + 1
            if (tentativi >= 3) {
                cancellaTutto()
                RisultatoPin.DATI_CANCELLATI
            } else {
                prefs.edit().putInt("tentativi_falliti", tentativi).apply()
                RisultatoPin.SBAGLIATO
            }
        }
    }

    fun tentativiRimasti(): Int = 3 - prefs.getInt("tentativi_falliti", 0)

    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return Base64.getEncoder().encodeToString(digest)
    }

    // --- Rubrica contatti ---

    fun leggiContatti(): List<Contatto> {
        val testo = prefs.getString("contatti", null) ?: return emptyList()
        val array = org.json.JSONArray(testo)
        return (0 until array.length()).map { i ->
            val oggetto = array.getJSONObject(i)
            Contatto(
                nickname = oggetto.getString("nickname"),
                chiavePubblicaBase64 = oggetto.getString("chiavePubblica"),
                indirizzoOnion = oggetto.getString("onion")
            )
        }
    }

    fun aggiungiContatto(contatto: Contatto) {
        val attuali = leggiContatti().toMutableList()
        attuali.removeAll { it.nickname == contatto.nickname }
        attuali.add(contatto)

        val array = org.json.JSONArray()
        attuali.forEach {
            array.put(
                org.json.JSONObject()
                    .put("nickname", it.nickname)
                    .put("chiavePubblica", it.chiavePubblicaBase64)
                    .put("onion", it.indirizzoOnion)
            )
        }
        prefs.edit().putString("contatti", array.toString()).apply()
    }

    // --- Preferenze di aspetto ---

    fun leggiTema(): TemaApp = TemaApp.daChiave(prefs.getString("tema", null))

    fun salvaTema(tema: TemaApp) {
        prefs.edit().putString("tema", tema.chiave).apply()
    }

    /** Cancella completamente l'identità e i dati locali. */
    fun cancellaTutto() {
        prefs.edit().clear().apply()
    }
}

/** Esito del controllo di un PIN inserito dall'utente. */
enum class RisultatoPin {
    CORRETTO,
    SBAGLIATO,
    DATI_CANCELLATI,
    NESSUN_PIN_IMPOSTATO
}
