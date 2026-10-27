package com.jatta.study.judge

import com.jatta.study.StudyConfig
import com.jatta.study.exercise.AnswerCheck
import kotlin.math.ceil
import kotlin.math.max

data class JudgeRequest(val task: String, val reference: String, val response: String)

data class Judgment(val correct: Boolean, val corrected: String?)

class FreeAnswerJudge(private val model: OnDeviceModel, private val config: StudyConfig) {

    suspend fun judge(request: JudgeRequest): Judgment? = ask(prompt(request), request)

    suspend fun judgeTranslation(request: JudgeRequest): Judgment? = ask(translationPrompt(request), request) { reply, _ ->
        when (field(reply, "MEANING")?.uppercase()?.take(4)) {
            "SAME" -> Judgment(correct = true, corrected = null)
            "DIFF" -> Judgment(correct = false, corrected = null)
            else -> null
        }
    }

    private suspend fun ask(prompt: String, request: JudgeRequest, read: (String, String) -> Judgment? = ::parse): Judgment? {
        if (AnswerCheck.matches(request.response, listOf(request.reference))) return Judgment(correct = true, corrected = null)
        val reply = model.generate(prompt) ?: return null
        return read(reply, request.response)
    }

    fun parse(reply: String, response: String): Judgment? {
        val verdict = field(reply, "VERDICT")?.uppercase() ?: return null
        return when {
            verdict.startsWith("CORRECT") -> Judgment(correct = true, corrected = null)
            verdict.startsWith("WRONG") -> {
                val fixed = field(reply, "FIXED") ?: return null
                if (AnswerCheck.normalize(fixed) == AnswerCheck.normalize(response)) return null
                if (WordDiff.edits(response, fixed) > maxEdits(response)) return null
                Judgment(correct = false, corrected = fixed)
            }
            else -> null
        }
    }

    private fun maxEdits(response: String): Int {
        val words = response.split(' ').count { it.isNotBlank() }
        return max(config.judgeMinEditsAllowed, ceil(words * config.judgeMaxEditShare).toInt())
    }

    private fun field(reply: String, key: String): String? = reply.lineSequence()
        .map { it.trim().trim('*', '-', ' ') }
        .firstOrNull { it.startsWith("$key:", ignoreCase = true) || it.startsWith("$key :", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.trim('*', '"', '“', '”', ' ')
        ?.takeIf { it.isNotBlank() }

    companion object {
        fun prompt(request: JudgeRequest): String = """
            You are checking one sentence written by a learner of English (level B1).
            Task: ${request.task}
            A correct answer could be: ${request.reference}
            Learner's sentence: ${request.response}

            Decide whether the learner's sentence is correct English that does the task. Other wordings than the example are fine. Ignore punctuation and capital letters. Do not improve style.
            Reply with exactly two lines:
            VERDICT: CORRECT or WRONG
            FIXED: the learner's sentence with the fewest changes that make it correct
        """.trimIndent()

        fun translationPrompt(request: JudgeRequest): String = """
            A learner of English (level B1) translated a Russian sentence into English. Decide only whether the meaning is the same. Ignore grammar, spelling and style completely: "he go", "I buyed", a missing article or a wrong tense do not change the meaning.
            Russian sentence: ${request.task}
            A correct translation: ${request.reference}
            Learner's translation: ${request.response}

            The meaning is DIFFERENT if the learner's translation has another action, thing, quality or person, swaps who does what to whom, says the opposite, adds or drops "not", or leaves out the main point. Otherwise it is the SAME.
            Examples:
            «Я вчера купил хлеб.» — "I buyed bread yesterday." — SAME
            «Я вчера купил хлеб.» — "I sold bread yesterday." — DIFFERENT
            «Анна позвонила брату.» — "Anna's brother called her." — DIFFERENT
            Reply with one line:
            MEANING: SAME or DIFFERENT
        """.trimIndent()
    }
}
