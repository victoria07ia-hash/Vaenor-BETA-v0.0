package com.nickapp.chatcifrata.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Blocco automatico: quando l'app esce dallo schermo (home, un'altra app, schermo spento) e poi
 * ci si torna, viene chiesto di nuovo il PIN. L'activity segnala l'uscita, la schermata principale reagisce.
 */
object BloccoApp {
    /** Diventa true quando l'app è uscita dallo schermo: al ritorno va chiesto il PIN. */
    var daBloccare by mutableStateOf(false)

    private var sospesoFinoA = 0L

    /** Da chiamare subito prima di aprire una schermata di sistema richiesta dall'app stessa
     *  (galleria, scanner QR, condivisione): l'uscita successiva non fa scattare il blocco,
     *  altrimenti ti chiederebbe il PIN a ogni foto scelta. Vale una volta sola, al massimo 10 minuti. */
    fun sospendiPerUscitaVolontaria() {
        sospesoFinoA = SystemClock.elapsedRealtime() + 10 * 60 * 1000L
    }

    /** Chiamata dall'activity quando l'app non è più visibile. */
    fun alleUscita() {
        if (SystemClock.elapsedRealtime() < sospesoFinoA) {
            sospesoFinoA = 0L
        } else {
            daBloccare = true
        }
    }
}
