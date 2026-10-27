package com.jatta.study

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.jatta.study.judge.EvalResult
import com.jatta.study.judge.FreeAnswerJudge
import com.jatta.study.judge.JudgeEval
import com.jatta.study.judge.JudgeRequest
import com.jatta.study.judge.ModelState
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.translate.TranslationScore
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A model served by Ollama on this computer, asked the way the app asks Gemini Nano: temperature 0, top-k 1, 80 tokens. */
class OllamaModel(override val name: String) : OnDeviceModel {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    override val downloadBytes: Long? = null

    override suspend fun state() = ModelState.Ready

    override suspend fun download(onProgress: (Long) -> Unit) = true

    override suspend fun generate(prompt: String): String? {
        val body = buildJsonObject {
            put("model", name)
            put("prompt", prompt)
            put("stream", false)
            put("think", false)
            putJsonObject("options") {
                put("temperature", 0)
                put("top_k", 1)
                put("num_predict", 80)
            }
        }
        val request = HttpRequest.newBuilder(URI("http://localhost:11434/api/generate"))
            .timeout(Duration.ofMinutes(2))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) error("Ollama ${response.statusCode()}: ${response.body()}")
        return (Json.parseToJsonElement(response.body()).jsonObject["response"] as? JsonPrimitive)?.content
    }
}

/** The phone's model file run by LiteRT-LM on this computer, with the same settings as GemmaModel in the app. */
class LiteRtModel(path: String) : OnDeviceModel {
    private val engine = Engine(EngineConfig(modelPath = path, backend = Backend.CPU())).apply { initialize() }
    private val conversation = ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0), maxOutputToken = 80)
    override val name = File(path).name
    override val downloadBytes: Long? = null

    override suspend fun state() = ModelState.Ready

    override suspend fun download(onProgress: (Long) -> Unit) = true

    override suspend fun generate(prompt: String): String? = engine.createConversation(conversation).use { chat ->
        chat.sendMessage(prompt).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
    }
}

/** Counts calls and time to reply. */
class Timed(private val model: OnDeviceModel) : OnDeviceModel by model {
    var calls = 0
    var totalMs = 0L

    override suspend fun generate(prompt: String): String? {
        val started = System.currentTimeMillis()
        return model.generate(prompt).also {
            totalMs += System.currentTimeMillis() - started
            calls++
        }
    }
}

/**
 * Measures a local model on the eval sets before it may check answers in the app (М21, М25), with the app's own
 * judging code. Through Ollama: `./gradlew :study:jvmTest --tests '*LocalModelEvalTest*' --rerun -PollamaModel=gemma4:e2b`;
 * the phone's LiteRT-LM file: `... -PlitertModel=build/models/gemma-4-E2B-it.litertlm`. Without either it does nothing.
 */
class LocalModelEvalTest {
    private val contentDir = System.getProperty("contentDir")

    @Test
    fun measuresAnOllamaModelWhenOneIsNamed() = runBlocking {
        val name = System.getProperty("ollamaModel").orEmpty()
        if (name.isNotBlank()) report(Timed(OllamaModel(name)))
    }

    @Test
    fun measuresTheAppsLiteRtModelWhenOneIsNamed() = runBlocking {
        val path = System.getProperty("litertModel").orEmpty()
        if (path.isNotBlank()) report(Timed(LiteRtModel(path)))
    }

    private suspend fun report(model: Timed) {
        val judge = FreeAnswerJudge(model, StudyConfig())
        val passScore = StudyConfig().translationPassScore
        val free = JudgeEval.run(judge, JudgeEval.readCases(File(contentDir, "eval/free-answers.json").readText())) {}
        val all = JudgeEval.readTranslationCases(File(contentDir, "eval/translations.json").readText())
        val below = JudgeEval.belowThreshold(all, passScore)
        val translations = JudgeEval.runTranslations(judge, below, passScore) {}
        val report = buildString {
            appendLine("model ${model.name}")
            appendLine("free answers (lessons, strict): ${line(free)}")
            appendLine("translations below the threshold (lenient): ${line(translations)}")
            appendLine("average reply ${model.totalMs / model.calls} ms over ${model.calls} calls")
            below.forEach { case ->
                val closest = TranslationScore.of(case.response, case.references, passScore).closest
                val judgment = judge.judgeTranslation(JudgeRequest(case.source, closest, case.response))
                if (judgment != null && judgment.correct != case.correct) {
                    appendLine("  ${if (judgment.correct) "accepted wrong" else "rejected right"}: ${case.response} | ${case.references.first()}")
                }
            }
        }
        println(report)
        File(System.getProperty("java.io.tmpdir"), "jatta-model-${model.name.replace(':', '-')}.txt").writeText(report)
    }

    private fun line(result: EvalResult) =
        "agreement ${(result.agreement * 1000).toInt() / 10.0}% of ${result.total}; wrong accepted ${result.wrongAccepted}, " +
            "right rejected ${result.rightRejected}, unusable ${result.unusable}"
}
