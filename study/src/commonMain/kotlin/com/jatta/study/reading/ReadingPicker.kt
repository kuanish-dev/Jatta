package com.jatta.study.reading

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.content.KindFile
import com.jatta.study.content.ReadingText
import com.jatta.study.content.StudyCourse

data class Reading(
    val text: ReadingText,
    val courseWords: Set<String>,
    val unknownWords: Set<String>,
)

class ReadingPicker(content: StudyCourse, private val engine: Engine) {
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
