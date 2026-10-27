package com.jatta.study.translate

import com.jatta.study.exercise.AnswerCheck

object Chrf {
    private const val CHAR_ORDER = 6
    private const val WORD_ORDER = 2
    private const val BETA = 2.0

    fun score(hypothesis: String, reference: String): Double {
        val hyp = AnswerCheck.normalize(hypothesis)
        val ref = AnswerCheck.normalize(reference)
        if (hyp == ref) return 1.0
        val hypChars = hyp.replace(" ", "").map { it.toString() }
        val refChars = ref.replace(" ", "").map { it.toString() }
        val hypWords = hyp.split(' ').filter { it.isNotEmpty() }
        val refWords = ref.split(' ').filter { it.isNotEmpty() }
        val orders = (1..CHAR_ORDER).map { counts(hypChars, refChars, it) } + (1..WORD_ORDER).map { counts(hypWords, refWords, it) }
        val counted = orders.filter { it.hypothesis > 0 && it.reference > 0 }
        if (counted.isEmpty()) return 0.0
        val precision = counted.sumOf { it.matched.toDouble() / it.hypothesis } / counted.size
        val recall = counted.sumOf { it.matched.toDouble() / it.reference } / counted.size
        if (precision + recall == 0.0) return 0.0
        val b2 = BETA * BETA
        return (1 + b2) * precision * recall / (b2 * precision + recall)
    }

    private class Counts(val hypothesis: Int, val reference: Int, val matched: Int)

    private fun counts(hyp: List<String>, ref: List<String>, n: Int): Counts {
        val hypGrams = grams(hyp, n)
        val refGrams = grams(ref, n)
        val matched = hypGrams.entries.sumOf { (gram, count) -> minOf(count, refGrams[gram] ?: 0) }
        return Counts(hypGrams.values.sum(), refGrams.values.sum(), matched)
    }

    private fun grams(tokens: List<String>, n: Int): Map<List<String>, Int> =
        tokens.windowed(n).groupingBy { it }.eachCount()
}
