package com.jatta.shared

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.jatta.study.judge.ModelFile
import com.jatta.study.judge.ModelState
import com.jatta.study.judge.OnDeviceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Gemma 4 E2B run inside the app by LiteRT-LM, for phones without Gemini Nano (where the phone has it,
 * Gemini Nano is the same Gemma 4 run by the system). The model file is downloaded once from Hugging Face,
 * pinned to one revision and checked by SHA-256. LiteRT-LM is built for 64-bit ARM only, and phones with less memory
 * than [MIN_MEMORY_BYTES] can't hold the model.
 */
class GemmaModel(private val context: Context) : OnDeviceModel {
    private val file = ModelFile(URL, BYTES, SHA256, File(context.filesDir, "models/gemma-4-E2B-it.litertlm"))
    private val loading = Mutex()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: Engine? = null

    override val name = "Gemma 4 E2B"
    override val downloadBytes: Long = BYTES

    override suspend fun state(): ModelState = when {
        file.isReady -> ModelState.Ready.also { background.launch { guarded(Unit) { engine() } } }
        Build.SUPPORTED_64_BIT_ABIS.none { it == "arm64-v8a" } || totalMemory() < MIN_MEMORY_BYTES -> ModelState.Unavailable
        else -> ModelState.Downloadable
    }

    override suspend fun download(onProgress: (Long) -> Unit): Boolean =
        withContext(Dispatchers.IO) { guarded(false) { file.download(onProgress) } }

    override suspend fun generate(prompt: String): String? = withContext(Dispatchers.Default) {
        guarded(null) {
            engine().createConversation(conversation).use { chat ->
                chat.sendMessage(prompt).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
            }
        }
    }

    /** Loading takes seconds, so it starts as soon as the model is there; the GPU is tried first, then the CPU. */
    private suspend fun engine(): Engine = loading.withLock {
        engine ?: (start(Backend.GPU()) ?: start(Backend.CPU()) ?: error("LiteRT-LM could not load the model")).also { engine = it }
    }

    private fun start(backend: Backend): Engine? = runCatching {
        Engine(EngineConfig(modelPath = file.target.path, backend = backend, cacheDir = context.cacheDir.path)).apply { initialize() }
    }.getOrNull()

    private fun totalMemory(): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return info.totalMem
    }

    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }

    private companion object {
        const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
            "1caa527c1e88b55f046fcd8d2b4265606680663d/gemma-4-E2B-it.litertlm"
        const val BYTES = 2_588_147_712L
        const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

        /** The model needs about 1.7 GB on the CPU besides the app and the system. Weak: a margin, not a measurement. */
        const val MIN_MEMORY_BYTES = 6L * 1024 * 1024 * 1024 - 512L * 1024 * 1024

        /** The same answer for the same sentence, as with Gemini Nano: greedy, short, no thinking. */
        val conversation = ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0), maxOutputToken = 80)
    }
}
