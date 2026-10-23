package com.jatta.study.reading

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.content.KindFile
import com.jatta.study.content.ReadingText
import com.jatta.study.content.StudyCourse

/** A text picked for reading, with the words to point out. */
data class Reading(
    val text: ReadingText,
    /** Words the learner has met in the course: meeting them again in a real text strengthens them. */
    val courseWords: Set<String>,
    /** Words to guess from context; at most a few by construction (М11). */
    val unknownWords: Set<String>,
)

/**
 * М11: picks a real text the learner can read, at least 95% of known words and at most 3 unknown
 * ones, checked by the engine. Among those it prefers texts with more words the learner is
 * learning, since every encounter in context adds to the memory of a word.
 */
class ReadingPicker(content: StudyCourse, private val engine: Engine) {
    /** Word item id → its forms as they appear in text: "colleague", "colleagues". */
    private val forms: Map<String, Set<String>> = content.items.values
        .filter { it.kind != KindFile.Rule }
        .associate { item ->
            val texts = listOf(item.en) + item.also + item.examples.mapNotNull { it.gap }
            item.id to texts.flatMapTo(HashSet()) { engine.coverage.words(it) }
        }

    fun pick(texts: List<ReadingText>, learner: Learner, today: Int, alreadyRead: Set<String>): Reading? {
        val held = forms.filterKeys { engine.gate.holds(learner.progress[it], today) }.values.flatten().toSet()
        val met = forms.filterKeys { it in learner.progress }.values.flatten().toSet()
        return texts.asSequence()
            .filter { it.id !in alreadyRead }
            .mapNotNull { text ->
                val words = engine.coverage.words(text.text).toSet()
                val rare = text.rare.toSet()
                val report = engine.coverage.check(text.text, (words - rare) + held)
                if (!report.acceptable) null else Reading(text, words intersect met, report.unknownWords)
            }
            .sortedByDescending { it.courseWords.size }
            .firstOrNull()
    }
}
