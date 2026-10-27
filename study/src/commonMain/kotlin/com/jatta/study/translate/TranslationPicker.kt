package com.jatta.study.translate

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.StudyConfig
import com.jatta.study.store.TranslationEntry
import kotlin.random.Random

/** One sentence to translate into English: [source] in Russian, [references] — human translations. */
data class TranslationTask(
    val sentenceId: Int,
    val source: String,
    val references: List<String>,
    /** The sentence's page on Tatoeba, which names its authors. */
    val sourceUrl: String,
)

/**
 * М25: picks sentences to translate into English at the end of a session. A sentence qualifies when the
 * learner holds every course word in it (recall tomorrow ≥ 85%): the task is to use words already learned,
 * in a new sentence. The weakest of those words go first, since retrieval strengthens a memory most while it
 * is still weak. A sentence translated right is not offered again; one translated wrong returns after a few days.
 */
class TranslationPicker(private val file: SentencesFile, private val engine: Engine, private val config: StudyConfig) {

    fun pick(learner: Learner, today: Int, history: List<TranslationEntry>): List<TranslationTask> {
        val passed = history.filter { it.passed }.mapTo(HashSet()) { it.sentence }
        val lastFailed = history.filter { !it.passed }.associate { it.sentence to it.day }
        fun isOpen(sentence: SentencePair): Boolean {
            if (sentence.id in passed) return false
            val failed = lastFailed[sentence.id] ?: return true
            return today - failed >= config.translationRetryDays
        }

        val ready = file.sentences
            .filter { sentence -> isOpen(sentence) && sentence.items.all { engine.gate.holds(learner.progress[it], today) } }
            .shuffled(Random(today))
            .sortedBy { sentence -> sentence.items.minOf { learner.progress.getValue(it).memory.stability } }
        val tasks = mutableListOf<TranslationTask>()
        val usedItems = HashSet<String>()
        for (sentence in ready) {
            if (tasks.size == config.translationsPerSession) break
            if (sentence.items.any { it in usedItems }) continue
            usedItems += sentence.items
            tasks += TranslationTask(sentence.id, sentence.ru.first(), sentence.en, file.url + sentence.id)
        }
        return tasks
    }
}
