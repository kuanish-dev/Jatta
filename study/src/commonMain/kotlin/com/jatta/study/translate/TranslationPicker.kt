package com.jatta.study.translate

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.StudyConfig
import com.jatta.study.store.TranslationEntry
import kotlin.random.Random

enum class Direction(val logName: String) { EnRu("en-ru"), RuEn("ru-en") }

/** One sentence to translate: [source] in one language, [references] — human translations into the other. */
data class TranslationTask(
    val sentenceId: Int,
    val direction: Direction,
    val source: String,
    val references: List<String>,
    /** The English sentence, to play aloud. */
    val english: String,
    /** The sentence's page on Tatoeba, which names its authors. */
    val sourceUrl: String,
)

/**
 * М25: picks sentences to translate at the end of a session. A sentence qualifies when the learner holds
 * every course word in it (recall tomorrow ≥ 85%): the task is to use words already learned, in a new
 * sentence. The weakest of those words go first, since retrieval strengthens a memory most while it is
 * still weak. Directions alternate, English → Russian first: understanding before producing. A sentence
 * translated right in one direction is not offered in it again; one translated wrong returns after a few days.
 */
class TranslationPicker(private val file: SentencesFile, private val engine: Engine, private val config: StudyConfig) {

    fun pick(learner: Learner, today: Int, history: List<TranslationEntry>): List<TranslationTask> {
        val passed = history.filter { it.passed }.mapTo(HashSet()) { it.sentence to it.direction }
        val lastFailed = history.filter { !it.passed }.associate { (it.sentence to it.direction) to it.day }
        fun isOpen(sentence: SentencePair, direction: Direction): Boolean {
            val key = sentence.id to direction.logName
            if (key in passed) return false
            val failed = lastFailed[key] ?: return true
            return today - failed >= config.translationRetryDays
        }

        val ready = file.sentences
            .filter { sentence -> sentence.items.all { engine.gate.holds(learner.progress[it], today) } }
            .shuffled(Random(today))
            .sortedBy { sentence -> sentence.items.minOf { learner.progress.getValue(it).memory.stability } }
        val tasks = mutableListOf<TranslationTask>()
        val usedItems = HashSet<String>()
        for (slot in 0 until config.translationsPerSession) {
            val direction = if (slot % 2 == 0) Direction.EnRu else Direction.RuEn
            val sentence = ready.firstOrNull { sentence ->
                tasks.none { it.sentenceId == sentence.id } && sentence.items.none { it in usedItems } && isOpen(sentence, direction)
            } ?: continue
            usedItems += sentence.items
            tasks += task(sentence, direction)
        }
        return tasks
    }

    private fun task(sentence: SentencePair, direction: Direction): TranslationTask {
        val english = sentence.en.first()
        val url = file.url + sentence.id
        return when (direction) {
            Direction.EnRu -> TranslationTask(sentence.id, direction, english, sentence.ru, english, url)
            Direction.RuEn -> TranslationTask(sentence.id, direction, sentence.ru.first(), sentence.en, english, url)
        }
    }
}
