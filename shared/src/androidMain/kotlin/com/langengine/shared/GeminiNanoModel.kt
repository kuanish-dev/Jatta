package com.langengine.shared

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.langengine.study.judge.ModelState
import com.langengine.study.judge.OnDeviceModel
import kotlin.coroutines.cancellation.CancellationException

/**
 * Gemini Nano through ML Kit's Prompt API. It runs inside the AICore system app, only on supported
 * phones; anywhere else (an emulator, an older phone) it reports Unavailable and lessons fall back
 * to self-checking.
 */
class GeminiNanoModel : OnDeviceModel {
    private val model by lazy { Generation.getClient() }

    override suspend fun state(): ModelState = guarded(ModelState.Unavailable) {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> ModelState.Ready
            FeatureStatus.DOWNLOADABLE -> ModelState.Downloadable
            FeatureStatus.DOWNLOADING -> ModelState.Downloading
            else -> ModelState.Unavailable
        }
    }

    override suspend fun download(onProgress: (Long) -> Unit): Boolean = guarded(false) {
        var completed = false
        model.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadProgress -> onProgress(status.totalBytesDownloaded)
                DownloadStatus.DownloadCompleted -> completed = true
                else -> Unit
            }
        }
        completed
    }

    override suspend fun generate(prompt: String): String? = guarded(null) {
        // Temperature 0 and top-1: the same answer for the same sentence, no creativity wanted.
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = 0f
            topK = 1
            maxOutputTokens = 80
        }
        model.generateContent(request).candidates.firstOrNull()?.text
    }

    /** AICore errors (no support, not set up yet, quota) all mean "can't use the model right now". */
    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }
}
