package com.langengine.study.judge

import com.langengine.study.exercise.AnswerCheck

enum class DiffKind { Same, Removed, Added }

data class DiffPart(val text: String, val kind: DiffKind)

/** Word-level difference between the learner's sentence and its correction, for showing exactly what changed. */
object WordDiff {

    fun parts(from: String, to: String): List<DiffPart> {
        val a = from.split(' ').filter { it.isNotBlank() }
        val b = to.split(' ').filter { it.isNotBlank() }
        val keyA = a.map { AnswerCheck.normalize(it) }
        val keyB = b.map { AnswerCheck.normalize(it) }
        // Longest common subsequence table, filled from the end.
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (keyA[i] == keyB[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val result = mutableListOf<DiffPart>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && keyA[i] == keyB[j] -> { result += DiffPart(b[j], DiffKind.Same); i++; j++ }
                i < a.size && (j == b.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> { result += DiffPart(a[i], DiffKind.Removed); i++ }
                else -> { result += DiffPart(b[j], DiffKind.Added); j++ }
            }
        }
        return result
    }

    /** Number of word edits; a replaced word counts once. */
    fun edits(from: String, to: String): Int {
        val parts = parts(from, to)
        return maxOf(parts.count { it.kind == DiffKind.Removed }, parts.count { it.kind == DiffKind.Added })
    }
}
