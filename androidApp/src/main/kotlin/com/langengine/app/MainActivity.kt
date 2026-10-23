package com.langengine.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.langengine.shared.App
import com.langengine.shared.GeminiNanoModel
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(
                dataDir = filesDir.absolutePath,
                nowMs = System::currentTimeMillis,
                utcOffsetMinutes = { TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 },
                model = GeminiNanoModel(),
            )
        }
    }
}
