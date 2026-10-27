package com.jatta.study.judge

import com.jatta.study.exercise.AnswerCheck

enum class DiffKind { Same, Removed, Added }

/** A word or words of the difference; a Same part keeps both spellings: [text] as corrected, [written] as the learner wrote it. */
data class DiffPart(val text: String, val kind: DiffKind, val written: String = text)

/**
 * Word-level difference between the learner's sentence and its correction, for showing exactly what changed.
 * Words are compared as [AnswerCheck.normalize] reads them, so "does not" and "doesn't" are the same words.
 */
object WordDiff {

    private class Token(val word: Int, val key: String, val last: Boolean)

    private sealed interface Step {
        class Same(val a: Token, val b: Token) : Step
        class Removed(val a: Token) : Step
        class Added(val b: Token) : Step
    }

    fun parts(from: String, to: String): List<DiffPart> {
        val a = words(from)
        val b = words(to)
        val steps = align(tokens(a), tokens(b))
        val matchedA = IntArray(a.size)
        val matchedB = IntArray(b.size)
        steps.filterIsInstance<Step.Same>().forEach { matchedA[it.a.word]++; matchedB[it.b.word]++ }
        val fullA = a.indices.map { matchedA[it] == keys(a[it]).size }
        val fullB = b.indices.map { matchedB[it] == keys(b[it]).size }

        val result = mutableListOf<DiffPart>()
        val groupA = mutableListOf<Int>()
        val groupB = mutableListOf<Int>()
        val doneA = HashSet<Int>()
        val doneB = HashSet<Int>()
        // Matched words go out together once both sides reach a word end: "does not" against "doesn't".
        fun flush(same: Boolean) {
            if (same) {
                result += DiffPart(groupB.joinToString(" ") { b[it] }, DiffKind.Same, groupA.joinToString(" ") { a[it] })
            } else {
                groupA.forEach { result += DiffPart(a[it], DiffKind.Removed) }
                groupB.forEach { result += DiffPart(b[it], DiffKind.Added) }
            }
            doneA += groupA
            doneB += groupB
            groupA.clear()
            groupB.clear()
        }
        steps.forEach { step ->
            when (step) {
                is Step.Same -> {
                    if (step.a.word !in doneA && groupA.lastOrNull() != step.a.word) groupA += step.a.word
                    if (step.b.word !in doneB && groupB.lastOrNull() != step.b.word) groupB += step.b.word
                    if (step.a.last && step.b.last) flush(groupA.isNotEmpty() && groupB.isNotEmpty() && groupA.all { fullA[it] } && groupB.all { fullB[it] })
                }
                is Step.Removed -> {
                    if (groupA.isNotEmpty() || groupB.isNotEmpty()) flush(same = false)
                    if (step.a.last && doneA.add(step.a.word)) result += DiffPart(a[step.a.word], DiffKind.Removed)
                }
                is Step.Added -> {
                    if (groupA.isNotEmpty() || groupB.isNotEmpty()) flush(same = false)
                    if (step.b.last && doneB.add(step.b.word)) result += DiffPart(b[step.b.word], DiffKind.Added)
                }
            }
        }
        if (groupA.isNotEmpty() || groupB.isNotEmpty()) flush(same = false)
        return result
    }

    /** Number of word edits; a replaced word counts once. */
    fun edits(from: String, to: String): Int {
        val parts = parts(from, to)
        return maxOf(parts.count { it.kind == DiffKind.Removed }, parts.count { it.kind == DiffKind.Added })
    }

    private fun words(text: String) = text.split(' ').filter { it.isNotBlank() }

    /** A word as compared: "doesn't" is two keys, "does" and "not"; punctuation alone is one empty key. */
    private fun keys(word: String) = AnswerCheck.normalize(word).split(' ').filter { it.isNotEmpty() }.ifEmpty { listOf("") }

    private fun tokens(words: List<String>) = words.flatMapIndexed { index, word ->
        val keys = keys(word)
        keys.mapIndexed { k, key -> Token(index, key, last = k == keys.lastIndex) }
    }

    /** Longest common subsequence of the keys, walked from the start. */
    private fun align(a: List<Token>, b: List<Token>): List<Step> {
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i].key == b[j].key) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val steps = mutableListOf<Step>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i].key == b[j].key -> steps += Step.Same(a[i++], b[j++])
                i < a.size && (j == b.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> steps += Step.Removed(a[i++])
                else -> steps += Step.Added(b[j++])
            }
        }
        return steps
    }
}
