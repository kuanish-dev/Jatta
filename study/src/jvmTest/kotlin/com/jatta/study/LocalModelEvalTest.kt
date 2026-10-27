package com.jatta.study

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.jatta.study.chat.ChatMessage
import com.jatta.study.chat.ChatPartner
import com.jatta.study.chat.ChatTopic
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
        val chat = chatReport(judge, ChatPartner(model))
        println(report + chat)
        File(System.getProperty("java.io.tmpdir"), "jatta-model-${model.name.replace(':', '-')}.txt").writeText(report + chat)
    }

    private suspend fun chatReport(judge: FreeAnswerJudge, partner: ChatPartner) = buildString {
        val cases = Json.parseToJsonElement(File(contentDir, "eval/chat-messages.json").readText()).jsonObject.getValue("cases").jsonArray
        var agreed = 0
        cases.forEach { case ->
            val said = case.jsonObject.getValue("said").jsonPrimitive.content
            val message = case.jsonObject.getValue("message").jsonPrimitive.content
            val correct = case.jsonObject.getValue("correct").jsonPrimitive.boolean
            val judgment = judge.reviewChat(said, message)
            if (judgment != null && judgment.correct == correct) agreed++
            appendLine("  chat ${if (judgment?.correct == correct) "ok " else "BAD"}: $message -> ${judgment?.corrected ?: judgment?.correct}")
        }
        appendLine("chat review: agreed ${agreed} of ${cases.size}")
        val topic = ChatTopic("work", "Работа", "the learner's job and a normal working day")
        val words = listOf("deadline", "colleague", "experience")
        val lines = mutableListOf<ChatMessage>()
        listOf(
            null,
            "Hi! I'm fine, thanks.",
            "I work in a leasing company, I am a frontend developer.",
            "Yesterday I go to the office very early because we had a deadline.",
            "I like my job but sometimes it are stressful.",
        ).forEach { said ->
            if (said != null) {
                appendLine("  learner: $said | review: ${judge.reviewChat(lines.lastOrNull()?.text, said)?.let { it.corrected ?: "fine" }}")
                lines += ChatMessage(true, said)
            }
            val reply = partner.reply(topic, words, lines)
            appendLine("  ${ChatPartner.NAME}: $reply")
            if (reply != null) lines += ChatMessage(false, reply)
        }
    }

    private fun line(result: EvalResult) =
        "agreement ${(result.agreement * 1000).toInt() / 10.0}% of ${result.total}; wrong accepted ${result.wrongAccepted}, " +
            "right rejected ${result.rightRejected}, unusable ${result.unusable}"
}
