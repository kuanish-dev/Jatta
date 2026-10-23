package com.jatta.shared

import androidx.compose.runtime.staticCompositionLocalOf

/** The platform's text-to-speech for English words and sentences: on the device, offline, no keys. */
interface Speaker {
    fun speak(text: String)
}

/** Null where the platform gave none; then the app shows no listen buttons. */
val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }
