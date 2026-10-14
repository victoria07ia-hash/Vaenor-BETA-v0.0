package com.nickapp.chatcifrata.data

/** Preferenza di tema dell'app. SISTEMA (predefinito) segue l'impostazione chiaro/scuro del telefono. */
enum class TemaApp(val chiave: String) {
    SISTEMA("sistema"),
    CHIARO("chiaro"),
    SCURO("scuro");

    companion object {
        fun daChiave(chiave: String?): TemaApp =
            values().firstOrNull { it.chiave == chiave } ?: SISTEMA
    }
}
