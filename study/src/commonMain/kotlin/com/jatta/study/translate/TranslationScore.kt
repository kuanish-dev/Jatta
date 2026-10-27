package com.jatta.study.translate

import com.jatta.study.exercise.AnswerCheck

/** How close a translation came: the best [score], the human translation it came closest to, and whether that is close enough. */
data class Closeness(val score: Double, val closest: String, val passed: Boolean)

/**
 * М25: a translation is compared with every human translation of the sentence by chrF++ and counts as
 * right when one of them is at least [passScore] close and has as many negations. N-gram scores barely
 * notice a missing "not", which turns the meaning around; so the negations must match.
 */
object TranslationScore {

    fun of(response: String, references: List<String>, passScore: Double): Closeness {
        val scored = references.map { it to Chrf.score(response, it) }
        val (closest, best) = scored.maxBy { it.second }
        val negations = negations(response)
        val passed = scored.any { (reference, score) -> score >= passScore && negations(reference) == negations }
        return Closeness(best, closest, passed)
    }

    private fun negations(text: String) = AnswerCheck.normalize(text).split(' ').count { it in NEGATIONS }

    private val NEGATIONS = setOf(
        "not", "no", "never", "nothing", "nobody", "none", "nowhere", "neither", "nor",
        "не", "ни", "нет", "никогда", "ничего", "ничто", "никто", "никого", "никому", "нигде", "никуда", "нельзя",
    )
}
