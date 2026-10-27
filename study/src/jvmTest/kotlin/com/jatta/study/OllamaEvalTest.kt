package com.jatta.study

import com.jatta.study.judge.EvalResult
import com.jatta.study.judge.FreeAnswerJudge
import com.jatta.study.judge.JudgeEval
import com.jatta.study.judge.JudgeRequest
import com.jatta.study.judge.ModelState
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.translate.TranslationScore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test

/** A model served by Ollama on this computer, asked the way the app asks Gemini Nano: temperature 0, top-k 1, 80 tokens. */
class OllamaModel(private val name: String) : OnDeviceModel {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    var calls = 0
    var totalMs = 0L

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
        val started = System.currentTimeMillis()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        totalMs += System.currentTimeMillis() - started
        calls++
        if (response.statusCode() != 200) error("Ollama ${response.statusCode()}: ${response.body()}")
        return (Json.parseToJsonElement(response.body()).jsonObject["response"] as? JsonPrimitive)?.content
    }
}

/**
 * Measures a local model on the eval sets before it may check answers in the app (М21, М25):
 * `./gradlew :study:jvmTest --tests '*OllamaEvalTest*' --rerun -PollamaModel=gemma4:e2b`.
 * Without -PollamaModel it does nothing.
 */
class OllamaEvalTest {
    private val name = System.getProperty("ollamaModel").orEmpty()
    private val contentDir = System.getProperty("contentDir")

    @Test
    fun measuresALocalModelWhenOneIsNamed() = runBlocking {
        if (name.isBlank()) return@runBlocking
        val model = OllamaModel(name)
        val judge = FreeAnswerJudge(model, StudyConfig())
        val passScore = StudyConfig().translationPassScore
        val free = JudgeEval.run(judge, JudgeEval.readCases(File(contentDir, "eval/free-answers.json").readText())) {}
        val all = JudgeEval.readTranslationCases(File(contentDir, "eval/translations.json").readText())
        val below = JudgeEval.belowThreshold(all, passScore)
        val translations = JudgeEval.runTranslations(judge, below, passScore) {}
        val report = buildString {
            appendLine("model $name")
            appendLine("free answers (lessons, strict): ${line(free)}")
            appendLine("translations below the threshold (lenient): ${line(translations)}")
            below.forEach { case ->
                val closest = TranslationScore.of(case.response, case.references, passScore).closest
                val judgment = judge.judgeTranslation(JudgeRequest(case.source, closest, case.response))
                if (judgment != null && judgment.correct != case.correct) {
                    appendLine("  ${if (judgment.correct) "accepted wrong" else "rejected right"}: ${case.response} | ${case.references.first()}")
                }
            }
            appendLine("average reply ${model.totalMs / model.calls} ms over ${model.calls} calls")
        }
        println(report)
        File(System.getProperty("java.io.tmpdir"), "jatta-ollama-${name.replace(':', '-')}.txt").writeText(report)
    }

    private fun line(result: EvalResult) =
        "agreement ${(result.agreement * 1000).toInt() / 10.0}% of ${result.total}; wrong accepted ${result.wrongAccepted}, " +
            "right rejected ${result.rightRejected}, unusable ${result.unusable}"
}
