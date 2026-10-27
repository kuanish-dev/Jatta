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
import com.jatta.shared.AndroidSpeechInput
import com.jatta.shared.AndroidSpeaker
import com.jatta.shared.App
import com.jatta.shared.GeminiNanoModel
import com.jatta.shared.GemmaModel
import com.jatta.shared.PhoneModel
import kotlinx.coroutines.CompletableDeferred
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { microphoneAnswer?.complete(it) }
    private var microphoneAnswer: CompletableDeferred<Boolean>? = null
    private lateinit var speaker: AndroidSpeaker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val reminders = AndroidReminders(applicationContext, askPermission = ::askNotificationPermission)
        speaker = AndroidSpeaker(this)
        setContent {
            App(
                dataDir = filesDir.absolutePath,
                nowMs = System::currentTimeMillis,
                utcOffsetMinutes = { TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 },
                model = PhoneModel(GeminiNanoModel(), GemmaModel(applicationContext)),
                reminders = reminders,
                speaker = speaker,
                speechInput = AndroidSpeechInput(applicationContext, askPermission = ::askMicrophone),
                onDarkTheme = ::matchSystemBars,
            )
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    private fun matchSystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(style, style)
    }

    private suspend fun askMicrophone(): Boolean {
        val answer = CompletableDeferred<Boolean>().also { microphoneAnswer = it }
        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        return answer.await()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
