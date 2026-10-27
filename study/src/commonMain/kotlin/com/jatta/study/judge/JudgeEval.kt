package com.jatta.study.judge

import com.jatta.study.translate.TranslationScore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A free answer with a known verdict, for measuring the on-device model before trusting it. */
@Serializable
data class EvalCase(val task: String, val reference: String, val response: String, val correct: Boolean)

@Serializable
private data class EvalFile(val cases: List<EvalCase>)

/** М25: a translation into English with a known verdict under the lenient rule. */
@Serializable
data class TranslationCase(val source: String, val references: List<String>, val response: String, val correct: Boolean)

@Serializable
private data class TranslationFile(val about: String, val cases: List<TranslationCase>)

data class EvalResult(
    val total: Int,
    /** The model's verdict matched the known one. */
    val agreed: Int,
    /** Said "correct" about a wrong sentence: the worst kind of mistake for a learner. */
    val wrongAccepted: Int,
    /** Said "wrong" about a correct sentence. */
    val rightRejected: Int,
    /** No usable reply; in lessons these fall back to self-check. */
    val unusable: Int,
) {
    /** Unusable replies count against the model: trust is earned, not assumed. */
    val agreement: Double get() = if (total == 0) 0.0 else agreed.toDouble() / total
}

/** "Test first, then trust": runs the judge over cases with known answers. */
object JudgeEval {
    fun readCases(text: String): List<EvalCase> = Json.decodeFromString<EvalFile>(text).cases

    fun readTranslationCases(text: String): List<TranslationCase> = Json.decodeFromString<TranslationFile>(text).cases

    /** The translations the threshold leaves to the model: only these does the model see in lessons. */
    fun belowThreshold(cases: List<TranslationCase>, passScore: Double): List<TranslationCase> =
        cases.filterNot { TranslationScore.of(it.response, it.references, passScore).passed }

    suspend fun run(judge: FreeAnswerJudge, cases: List<EvalCase>, onProgress: (done: Int) -> Unit): EvalResult =
        measure(cases, { it.correct }, { judge.judge(JudgeRequest(it.task, it.reference, it.response)) }, onProgress)

    /** М25: each translation is compared with its closest human one, as in lessons. */
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
