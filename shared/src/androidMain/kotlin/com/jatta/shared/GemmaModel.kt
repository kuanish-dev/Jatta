package com.jatta.shared

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
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
 * in the background by [ModelDownloadWorker], pinned to one revision and checked by SHA-256. LiteRT-LM is built
 * for 64-bit ARM only, phones with less memory than [MIN_MEMORY_BYTES] can't hold the model, and a phone where it
 * failed to start on both GPU and CPU doesn't get it.
 */
class GemmaModel(private val context: Context) : OnDeviceModel {
    private val file = file(context)
    private val loading = Mutex()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: Engine? = null

    override val name = "Gemma 4 E2B"
    override val downloadBytes: Long = BYTES

    override suspend fun state(): ModelState = when {
        file.isReady && backends().all { failed(it).exists() } -> ModelState.Unavailable
        file.isReady -> ModelState.Ready.also { if (backends().any { worked(it).exists() }) background.launch { guarded(Unit) { engine() } } }
        Build.SUPPORTED_64_BIT_ABIS.none { it == "arm64-v8a" } || totalMemory() < MIN_MEMORY_BYTES -> ModelState.Unavailable
        ModelDownloadWorker.isRunning(context) -> ModelState.Downloading
        else -> ModelState.Downloadable
    }

    override suspend fun download(onProgress: (Long) -> Unit): Boolean =
        guarded(false) { ModelDownloadWorker.follow(context, onProgress) } && file.isReady

    override suspend fun generate(prompt: String): String? = withContext(Dispatchers.Default) {
        guarded(null) {
            ask(engine(), prompt, conversation)
        }
    }

    /**
     * Loading takes seconds. The first time it happens on the accuracy test; once a backend has worked on this
     * phone, it loads as soon as the app opens. The one that worked goes first, then the GPU, then the CPU.
     */
    private suspend fun engine(): Engine = loading.withLock {
        engine ?: (backends().sortedByDescending { worked(it).exists() }.firstNotNullOfOrNull(::start)
            ?: error("LiteRT-LM could not start on this phone")).also { engine = it }
    }

    /**
     * A GPU engine can load and still fail on the first message (no OpenCL on the phone), so a one-token question
     * proves it works before it is kept. A native library can also kill the app outright, which no catch survives:
     * the attempt is marked as failed before it starts and unmarked once it works, so a backend that failed or
     * crashed is not tried again on this phone.
     */
    private fun start(backend: Backend): Engine? {
        val failed = failed(backend)
        if (failed.exists()) return null
        failed.createNewFile()
        val engine = runCatching {
            Engine(EngineConfig(modelPath = file.target.path, backend = backend, cacheDir = context.cacheDir.path)).apply { initialize() }
        }.getOrElse {
            Log.w("Jatta", "Gemma cannot load on ${backend.name}", it)
            return null
        }
        return runCatching { ask(engine, "Hi", probe) }.fold(
            onSuccess = {
                failed.delete()
                worked(backend).createNewFile()
                engine
            },
            onFailure = {
                Log.w("Jatta", "Gemma cannot answer on ${backend.name}", it)
                engine.close()
                null
            },
        )
    }

    private fun backends(): List<Backend> = listOf(Backend.GPU(), Backend.CPU())

    private fun failed(backend: Backend) = File(file.target.parentFile, "failed-${backend.name}")

    private fun worked(backend: Backend) = File(file.target.parentFile, "worked-${backend.name}")

    private fun ask(engine: Engine, prompt: String, config: ConversationConfig): String =
        engine.createConversation(config).use { chat ->
            chat.sendMessage(prompt).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
        }

    private fun totalMemory(): Long {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return info.totalMem
    }

    /** A failing model means self-checking, not a crash; the reason goes to the log. */
    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Jatta", "Gemma failed", e)
        fallback
    }

    companion object {
        private const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
            "1caa527c1e88b55f046fcd8d2b4265606680663d/gemma-4-E2B-it.litertlm"
        const val BYTES = 2_588_147_712L
        private const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

        fun file(context: Context) = ModelFile(URL, BYTES, SHA256, File(context.filesDir, "models/gemma-4-E2B-it.litertlm"))

        /** The model needs about 1.7 GB on the CPU besides the app and the system. Weak: a margin, not a measurement. */
        private const val MIN_MEMORY_BYTES = 6L * 1024 * 1024 * 1024 - 512L * 1024 * 1024

        /** The same answer for the same sentence, as with Gemini Nano: greedy, short, no thinking. */
        private val conversation = ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0), maxOutputToken = 80)
        private val probe = ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0), maxOutputToken = 1)
    }
}
