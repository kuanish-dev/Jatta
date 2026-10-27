package com.jatta.study.translate

import com.jatta.study.exercise.AnswerCheck

data class Closeness(val score: Double, val closest: String, val passed: Boolean)

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
