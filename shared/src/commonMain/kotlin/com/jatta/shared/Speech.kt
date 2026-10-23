package com.jatta.shared

import androidx.compose.runtime.staticCompositionLocalOf
import com.jatta.shared.resources.Res

/** The platform's voice for English: speech synthesis on the device, and playback of real recordings. */
interface Speaker {
    fun speak(text: String)

    /** Plays the bytes of an .m4a recording, stopping whatever was sounding. */
    fun play(recording: ByteArray)
}

/** Null where the platform gave none; then the app shows no listen buttons. */
val LocalSpeaker = staticCompositionLocalOf<Speaker?> { null }

/** Says [text] in the real voice of [recording], a file of the course's audio folder, or by synthesis without one. */
suspend fun Speaker.say(text: String, recording: String?) {
    if (recording == null) speak(text) else play(Res.readBytes("$CONTENT_DIR/en/audio/$recording"))
}
