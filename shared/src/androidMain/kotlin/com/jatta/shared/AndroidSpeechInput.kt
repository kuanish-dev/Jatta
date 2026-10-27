package com.jatta.shared

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidSpeechInput(private val context: Context, private val askPermission: suspend () -> Boolean) : SpeechInput {
    private var recognizer: SpeechRecognizer? = null

    override val available: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): Spoken {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !available) return Spoken.Failed
        if (!hasMicrophone() && !askPermission()) return Spoken.NoMicrophone
        return withContext(Dispatchers.Main.immediate) { recognize(onPartial, onLevel) }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    private fun hasMicrophone() =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.S)
    private suspend fun recognize(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): Spoken =
        suspendCancellableCoroutine { result ->
            val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = engine
            val finish = { spoken: Spoken ->
                if (recognizer === engine) recognizer = null
                engine.destroy()
                if (result.isActive) result.resume(spoken)
            }
            engine.setRecognitionListener(Callbacks(onPartial, onLevel, onDone = finish, onFailure = { finish(spokenFor(it, engine)) }))
            engine.startListening(ENGLISH)
            result.invokeOnCancellation { Handler(Looper.getMainLooper()).post { engine.destroy() } }
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun spokenFor(error: Int, engine: SpeechRecognizer): Spoken = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Spoken.Silence
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Spoken.NoMicrophone
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                engine.triggerModelDownload(ENGLISH)
                Spoken.LanguageLoading
            } else {
                Spoken.Failed
            }
        else -> Spoken.Failed
    }

    private class Callbacks(
        private val onPartial: (String) -> Unit,
        private val onLevel: (Float) -> Unit,
        private val onDone: (Spoken) -> Unit,
        private val onFailure: (Int) -> Unit,
    ) : RecognitionListener {
        override fun onResults(results: Bundle) {
            val variants = variants(results)
            onDone(if (variants.isEmpty()) Spoken.Silence else Spoken.Words(variants))
        }

        override fun onPartialResults(partialResults: Bundle) {
            variants(partialResults).firstOrNull()?.let(onPartial)
        }

        override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))

        override fun onError(error: Int) = onFailure(error)

        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun variants(bundle: Bundle) =
            bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
    }

    private companion object {
        val ENGLISH: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
    }
}
