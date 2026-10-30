package com.jatta.study.translate

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.StudyConfig
import com.jatta.study.store.TranslationEntry
import kotlin.random.Random

data class TranslationTask(
    val sentenceId: Int,
    val source: String,
    val references: List<String>,
    val sourceUrl: String,
)

class TranslationPicker(private val file: SentencesFile, private val engine: Engine, private val config: StudyConfig) {

    fun pick(learner: Learner, today: Int, history: List<TranslationEntry>): List<TranslationTask> {
        val ready = open(today, history)
            .filter { sentence -> sentence.items.all { engine.gate.holds(learner.progress[it], today) } }
            .shuffled(Random(today))
            .sortedBy { sentence -> weakestStability(sentence, learner) }
        val tasks = mutableListOf<TranslationTask>()
        val usedItems = HashSet<String>()
        for (sentence in ready) {
            if (tasks.size == config.translationsPerSession) break
            if (sentence.items.any { it in usedItems }) continue
            usedItems += sentence.items
            tasks += taskOf(sentence)
        }
        return tasks
    }

    fun practice(learner: Learner, today: Int, history: List<TranslationEntry>): List<TranslationTask> {
        val pool = open(today, history).ifEmpty { file.sentences }.shuffled(Random(today))
        val (met, fresh) = pool.partition { sentence -> sentence.items.all { it in learner.progress } }
        val ordered = met.sortedBy { weakestStability(it, learner) } +
            fresh.sortedWith(compareBy(nullsLast()) { sentence -> sentence.items.mapNotNull { engine.course.item(it).frequencyRank }.maxOrNull() })
        return ordered.map(::taskOf)
    }

    private fun open(today: Int, history: List<TranslationEntry>): List<SentencePair> {
        val passed = history.filter { it.passed }.mapTo(HashSet()) { it.sentence }
        val lastFailed = history.filter { !it.passed }.associate { it.sentence to it.day }
        return file.sentences.filter { sentence ->
            if (sentence.id in passed) return@filter false
            val failed = lastFailed[sentence.id] ?: return@filter true
            today - failed >= config.translationRetryDays
        }
    }

    private fun weakestStability(sentence: SentencePair, learner: Learner) =
        sentence.items.minOf { learner.progress.getValue(it).memory.stability }

    private fun taskOf(sentence: SentencePair) =
        TranslationTask(sentence.id, sentence.ru.first(), sentence.en, file.url + sentence.id)
}
