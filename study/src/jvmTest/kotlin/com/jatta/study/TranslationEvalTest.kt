package com.jatta.study

import com.jatta.study.judge.JudgeEval
import com.jatta.study.translate.TranslationScore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * М25: what the 80% threshold does with translations of known verdict. Accepting a wrong translation
 * teaches the mistake; rejecting a right one only sends it to the model or the learner, so the threshold
 * must be precise, and may miss many right translations in other words.
 */
class TranslationEvalTest {
    private val cases = JudgeEval.readTranslationCases(File(System.getProperty("contentDir"), "eval/translations.json").readText())

    @Test
    fun theThresholdRarelyAcceptsAWrongTranslation() {
        val accepted = cases.filter { TranslationScore.of(it.response, it.references, StudyConfig().translationPassScore).passed }
        val wrongAccepted = accepted.count { !it.correct }
        val rightAccepted = accepted.count { it.correct }
        val right = cases.count { it.correct }
        println("М25 eval: ${cases.size} cases, accepted ${accepted.size}, wrong among them $wrongAccepted, right accepted $rightAccepted of $right")
        assertTrue(cases.size >= 90)
        assertTrue(rightAccepted.toDouble() / accepted.size >= 0.8, "precision ${rightAccepted.toDouble() / accepted.size}")
        assertTrue(rightAccepted.toDouble() / right >= 0.3, "recall ${rightAccepted.toDouble() / right}")
        assertTrue(cases.filter { it.response == it.references.first() }.all { it in accepted }, "a human translation is always accepted")
    }
}
