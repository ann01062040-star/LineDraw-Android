package com.linedraw.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linedraw.app.ui.LineDrawScreen
import com.linedraw.app.ui.UsageDeclarationScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as LineDrawApp
        setContent {
            val accepted by app.usageConsent.accepted.collectAsStateWithLifecycle()
            var consentError by remember { mutableStateOf("") }
            if (accepted) LineDrawScreen(app)
            else {
                val prefs = remember { app.getSharedPreferences("preferences", 0) }
                val appearance = prefs.getString("appearance", "系統")
                UsageDeclarationScreen(dark = appearance == "深色" || (appearance == "系統" && isSystemInDarkTheme()),
                    opaque = prefs.getBoolean("opaque", false), error = consentError,
                    onAccept = { if (!app.acceptUsage()) consentError = "無法儲存同意紀錄，請重試。" },
                    onDecline = { app.declineUsage(); finishAndRemoveTask() })
            }
        }
    }
}
