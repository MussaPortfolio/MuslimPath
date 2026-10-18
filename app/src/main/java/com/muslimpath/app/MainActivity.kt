package com.muslimpath.app

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.muslimpath.app.notifications.PrayerAlarms
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.muslimpath.app.notifications.Reminders
import com.muslimpath.app.ui.AppRoot
import com.muslimpath.app.ui.theme.MuslimPathTheme

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra("open")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.ensureChannel(this)
        PrayerAlarms.ensureChannel(this)
        PrayerAlarms.reschedule(this)
        if (savedInstanceState == null) openRequest.value = intent.getStringExtra("open")
        setContent {
            MuslimPathTheme {
                AppRoot(openRequest = openRequest.value, onOpenHandled = { openRequest.value = null })
            }
        }
    }
}
