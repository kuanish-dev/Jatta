package com.jatta.study.judge

import com.jatta.study.translate.TranslationScore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EvalCase(val task: String, val reference: String, val response: String, val correct: Boolean)

@Serializable
private data class EvalFile(val cases: List<EvalCase>)

@Serializable
data class TranslationCase(val source: String, val references: List<String>, val response: String, val correct: Boolean)

@Serializable
private data class TranslationFile(val about: String, val cases: List<TranslationCase>)

data class EvalResult(
    val total: Int,
    val agreed: Int,
    val wrongAccepted: Int,
    val rightRejected: Int,
    val unusable: Int,
) {
    val agreement: Double get() = if (total == 0) 0.0 else agreed.toDouble() / total
}

object JudgeEval {
    fun readCases(text: String): List<EvalCase> = Json.decodeFromString<EvalFile>(text).cases

    fun readTranslationCases(text: String): List<TranslationCase> = Json.decodeFromString<TranslationFile>(text).cases

    fun belowThreshold(cases: List<TranslationCase>, passScore: Double): List<TranslationCase> =
        cases.filterNot { TranslationScore.of(it.response, it.references, passScore).passed }

    suspend fun run(judge: FreeAnswerJudge, cases: List<EvalCase>, onProgress: (done: Int) -> Unit): EvalResult =
        measure(cases, { it.correct }, { judge.judge(JudgeRequest(it.task, it.reference, it.response)) }, onProgress)

    suspend fun runTranslations(
        judge: FreeAnswerJudge,
        cases: List<TranslationCase>,
        passScore: Double,
        onProgress: (done: Int) -> Unit,
    ): EvalResult = measure(cases, { it.correct }, { case ->
        val closest = TranslationScore.of(case.response, case.references, passScore).closest
        judge.judgeTranslation(JudgeRequest(case.source, closest, case.response))
    }, onProgress)

    private suspend fun <T> measure(
        cases: List<T>,
        correct: (T) -> Boolean,
        verdict: suspend (T) -> Judgment?,
        onProgress: (done: Int) -> Unit,
    ): EvalResult {
        var agreed = 0
        var wrongAccepted = 0
        var rightRejected = 0
        var unusable = 0
        cases.forEachIndexed { index, case ->
            val judgment = verdict(case)
            when {
                judgment == null -> unusable++
                judgment.correct == correct(case) -> agreed++
                judgment.correct -> wrongAccepted++
                else -> rightRejected++
            }
            onProgress(index + 1)
        }
        return EvalResult(cases.size, agreed, wrongAccepted, rightRejected, unusable)
    }
}
