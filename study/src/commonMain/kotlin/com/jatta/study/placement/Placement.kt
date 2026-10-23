package com.jatta.study.placement

import com.jatta.engine.session.Rung
import com.jatta.study.StudyConfig
import com.jatta.study.content.KindFile
import com.jatta.study.content.StudyCourse
import com.jatta.study.exercise.Exercise
import com.jatta.study.exercise.ExerciseFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.math.roundToInt

/** content/en/placement.json, built by scripts/build_placement.py. */
@Serializable
data class PlacementFile(val source: String, val bands: List<PlacementBand>, val pseudowords: List<String>) {
    companion object {
        fun read(text: String): PlacementFile = Json.decodeFromString(text)
    }
}

/** Real words sampled from one NGSL frequency band, ranks [from]..[to]. */
@Serializable
data class PlacementBand(val from: Int, val to: Int, val words: List<String>)

/** One yes/no question; [band] is null for a word that doesn't exist. */
data class WordProbe(val word: String, val band: Int?)

/** A grammar question: the first rule items of each grammar topic, as choice exercises. */
data class GrammarProbe(val topicId: String, val exercise: Exercise.Choice)

data class PlacementResult(
    /** Estimated number of the [totalWords] most frequent words the learner knows. */
    val estimatedWords: Int,
    val totalWords: Int,
    /** Share of pseudowords the learner said they know. */
    val falseAlarms: Double,
    /** False when the learner claimed too many pseudowords: then single "yes" answers are not trusted. */
    val reliable: Boolean,
    /** Course items to start as already known; they come back for a check within days. */
    val knownItems: Set<String>,
    /** Titles of grammar topics placed as known. */
    val knownTopics: List<String>,
)

/**
 * Placement test: where the course starts for this learner (М23).
 *
 * Words: a yes/no test with pseudowords (Meara & Buxton 1987; LexTALE works the same way). The hit
 * rate in each frequency band is corrected for guessing with the false-alarm rate, (h − f) / (1 − f).
 * A course word the learner was asked about directly counts as known on their "yes"; other course
 * words count as known when their band is mostly known.
 *
 * Grammar: two choice questions per grammar topic; both right places the topic as known.
 *
 * Known items start with a fresh memory, so the scheduler checks them within days: a wrong guess of
 * the test costs one review, not a gap in the course.
 */
class Placement(private val content: StudyCourse, private val file: PlacementFile, private val config: StudyConfig) {

    val words: List<WordProbe> = (
        file.bands.flatMapIndexed { index, band -> band.words.map { WordProbe(it, index) } } +
            file.pseudowords.map { WordProbe(it, null) }
        ).shuffled(Random(SEED))

    val grammar: List<GrammarProbe> = run {
        val factory = ExerciseFactory(content)
        content.course.topics
            .filter { topic -> content.topics.getValue(topic.id).items.all { it.kind == KindFile.Rule } }
            .flatMap { topic ->
                content.course.itemsOf(topic.id).take(config.placementQuestionsPerTopic).mapIndexed { i, item ->
                    val exercise = factory.build(item.id, Rung.Recognize, seed = SEED + i) as Exercise.Choice
                    GrammarProbe(topic.id, exercise)
                }
            }
    }

    /** [knows]: word → the learner's yes/no; [grammarRight]: one entry per question of [grammar]. */
    fun result(knows: Map<String, Boolean>, grammarRight: List<Boolean>): PlacementResult {
        val falseAlarms = file.pseudowords.count { knows[it] == true }.toDouble() / file.pseudowords.size
        val reliable = falseAlarms <= config.placementMaxFalseAlarms
        val bandKnown = file.bands.map { band ->
            val hits = band.words.count { knows[it] == true }.toDouble() / band.words.size
            if (falseAlarms >= 1.0) 0.0 else ((hits - falseAlarms) / (1 - falseAlarms)).coerceIn(0.0, 1.0)
        }
        val estimated = file.bands.indices.sumOf { bandKnown[it] * (file.bands[it].to - file.bands[it].from + 1) }

        val asked = file.bands.flatMap { it.words }.toSet()
        val knownWords = content.items.values
            .filter { it.kind != KindFile.Rule && it.rank != null }
            .filter { item ->
                if (item.en in asked) {
                    reliable && knows[item.en] == true
                } else {
                    val band = file.bands.indexOfFirst { item.rank!! in it.from..it.to }
                    band >= 0 && bandKnown[band] >= config.placementKnownBandShare
                }
            }
            .map { it.id }

        val knownTopics = grammar.indices.groupBy { grammar[it].topicId }
            .filterValues { questions -> questions.all { grammarRight.getOrElse(it) { false } } }
            .keys
        val knownRules = knownTopics.flatMap { topic -> content.course.itemsOf(topic).map { it.id } }

        return PlacementResult(
            estimatedWords = estimated.roundToInt(),
            totalWords = file.bands.last().to,
            falseAlarms = falseAlarms,
            reliable = reliable,
            knownItems = (knownWords + knownRules).toSet(),
            knownTopics = knownTopics.map { content.topics.getValue(it).title },
        )
    }

    private companion object {
        const val SEED = 2026
    }
}
