package com.nickapp.chatcifrata

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nickapp.chatcifrata.ui.AppChatCifrata
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
}
