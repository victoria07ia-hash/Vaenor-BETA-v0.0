package com.nickapp.chatcifrata

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nickapp.chatcifrata.ui.AppChatCifrata
import com.nickapp.chatcifrata.ui.BloccoApp
import com.nickapp.chatcifrata.ui.leggiContattoDaLink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val contattoDaLink = intent?.dataString?.let { leggiContattoDaLink(it) }

        setContent {
            AppChatCifrata(contattoDaLink = contattoDaLink)
        }
    }

    // L'app è tornata davanti: ogni permesso di uscita senza PIN scade qui.
    override fun onResume() {
        super.onResume()
        BloccoApp.alRitorno()
    }

    // Quando l'app non è più visibile (home, altra app, schermo spento) al ritorno servirà di nuovo il PIN.
    override fun onStop() {
        super.onStop()
        BloccoApp.alleUscita()
    }
}
