package com.jatta.shared

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Android's TextToSpeech with an American English voice, and MediaPlayer for recordings. */
class AndroidSpeaker(context: Context) : Speaker, TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var player: MediaPlayer? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS && tts.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
    }

    override fun speak(text: String) {
        stopRecording()
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jatta")
    }

    override fun play(recording: ByteArray) {
        tts.stop()
        stopRecording()
        player = MediaPlayer().apply {
            setDataSource(BytesSource(recording))
            setOnPreparedListener { it.start() }
            prepareAsync()
        }
    }

    fun shutdown() {
        stopRecording()
        tts.shutdown()
    }

    private fun stopRecording() {
        player?.release()
        player = null
    }

    private class BytesSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size.toLong(), bytes.size - position).toInt()
            bytes.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit
    }
}
