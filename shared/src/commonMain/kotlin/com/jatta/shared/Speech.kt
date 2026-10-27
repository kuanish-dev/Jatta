package com.jatta.shared

import androidx.compose.runtime.staticCompositionLocalOf
import com.jatta.shared.resources.Res

interface Speaker {
    fun speak(text: String)

    fun play(recording: ByteArray)
}

val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }

suspend fun Speaker.say(text: String, recording: String?) {
    if (recording == null) speak(text) else play(Res.readBytes("$CONTENT_DIR/en/audio/$recording"))
}
