package com.jatta.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.jatta.shared.AndroidReminders
import com.jatta.shared.App
import com.jatta.shared.GeminiNanoModel
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    // The answer needs no handling: without permission Android simply doesn't show reminders.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so system bar icons are always light.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val reminders = AndroidReminders(applicationContext, askPermission = ::askNotificationPermission)
        setContent {
            App(
                dataDir = filesDir.absolutePath,
                nowMs = System::currentTimeMillis,
                utcOffsetMinutes = { TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 },
                model = GeminiNanoModel(),
                reminders = reminders,
            )
        }
    }

    /** Android 13+ asks the learner; earlier versions allow notifications by default. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
