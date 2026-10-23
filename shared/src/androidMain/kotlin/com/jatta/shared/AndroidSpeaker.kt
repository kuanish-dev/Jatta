package com.jatta.shared

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Android's TextToSpeech with an American English voice. Silent until the engine is ready. */
class AndroidSpeaker(context: Context) : Speaker, TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jatta")
    }

    fun shutdown() = tts.shutdown()
}
