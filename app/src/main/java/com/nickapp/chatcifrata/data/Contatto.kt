package com.nickapp.chatcifrata.data

/** Un contatto della rubrica. Per iniziare una chat servono tutti e tre i campi:
 *  il nickname (etichetta comoda), la chiave pubblica (per cifrare/verificare) e
 *  l'indirizzo .onion (per raggiungerlo in rete tramite Tor/Orbot). */
data class Contatto(
    val nickname: String,
    val chiavePubblicaBase64: String,
    val indirizzoOnion: String
)
