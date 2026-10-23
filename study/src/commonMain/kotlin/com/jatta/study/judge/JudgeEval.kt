package com.jatta.study.judge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A free answer with a known verdict, for measuring the on-device model before trusting it. */
@Serializable
data class EvalCase(val task: String, val reference: String, val response: String, val correct: Boolean)

@Serializable
private data class EvalFile(val cases: List<EvalCase>)

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

    suspend fun run(judge: FreeAnswerJudge, cases: List<EvalCase>, onProgress: (done: Int) -> Unit): EvalResult {
        var agreed = 0
        var wrongAccepted = 0
        var rightRejected = 0
        var unusable = 0
        cases.forEachIndexed { index, case ->
            val judgment = judge.judge(JudgeRequest(case.task, case.reference, case.response))
            when {
                judgment == null -> unusable++
                judgment.correct == case.correct -> agreed++
                judgment.correct -> wrongAccepted++
                else -> rightRejected++
            }
            onProgress(index + 1)
        }
        return EvalResult(cases.size, agreed, wrongAccepted, rightRejected, unusable)
    }
}
