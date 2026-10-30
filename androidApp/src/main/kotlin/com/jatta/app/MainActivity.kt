package com.jatta.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.jatta.shared.AndroidFiles
import com.jatta.shared.AndroidReminders
import com.jatta.shared.AndroidSpeechInput
import com.jatta.shared.AndroidSpeaker
import com.jatta.shared.App
import com.jatta.shared.GeminiNanoModel
import com.jatta.shared.GemmaModel
import com.jatta.shared.PhoneModel
import com.jatta.shared.TodayWidget
import kotlinx.coroutines.CompletableDeferred
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { microphoneAnswer?.complete(it) }
    private var microphoneAnswer: CompletableDeferred<Boolean>? = null
    private val saveTarget = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { pickedUri?.complete(it) }
    private val openSource = registerForActivityResult(ActivityResultContracts.OpenDocument()) { pickedUri?.complete(it) }
    private var pickedUri: CompletableDeferred<Uri?>? = null
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
                version = packageManager.getPackageInfo(packageName, 0).versionName,
                speechInput = AndroidSpeechInput(applicationContext, askPermission = ::askMicrophone),
                files = AndroidFiles(applicationContext, pickSaveTarget = ::pickSaveTarget, pickOpenSource = ::pickOpenSource),
                onDarkTheme = ::matchSystemBars,
            )
        }
    }

    override fun onStop() {
        TodayWidget.refresh(applicationContext)
        super.onStop()
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

    private suspend fun pickSaveTarget(name: String): Uri? {
        val answer = CompletableDeferred<Uri?>().also { pickedUri = it }
        saveTarget.launch(name)
        return answer.await()
    }

    private suspend fun pickOpenSource(): Uri? {
        val answer = CompletableDeferred<Uri?>().also { pickedUri = it }
        openSource.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        return answer.await()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
