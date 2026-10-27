package com.jatta.shared

import androidx.compose.runtime.staticCompositionLocalOf

interface SpeechInput {
    val available: Boolean

    suspend fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit): Spoken

    fun stop()
}

sealed interface Spoken {
    data class Words(val variants: List<String>) : Spoken

    data object Silence : Spoken

    data object NoMicrophone : Spoken

    data object LanguageLoading : Spoken

    data object Failed : Spoken
}

val LocalSpeechInput = staticCompositionLocalOf<SpeechInput?> { null }
