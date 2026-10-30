package com.jatta.shared

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.jatta.study.judge.ChatTurn
import com.jatta.study.judge.ModelState
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.judge.transcript
import kotlin.coroutines.cancellation.CancellationException

class GeminiNanoModel : OnDeviceModel {
    private val model by lazy { Generation.getClient() }

    override val name = "Gemini Nano"
    override val downloadBytes: Long? = null

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
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = 0f
            topK = 1
            maxOutputTokens = 80
        }
        model.generateContent(request).candidates.firstOrNull()?.text
    }

    override suspend fun chat(system: String, turns: List<ChatTurn>): String? = guarded(null) {
        val request = generateContentRequest(TextPart(transcript(system, turns))) {
            temperature = 0.8f
            topK = 40
            maxOutputTokens = 80
        }
        model.generateContent(request).candidates.firstOrNull()?.text
    }

    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }
}
