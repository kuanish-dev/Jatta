package com.jatta.study.reading

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.content.BasicWords
import com.jatta.study.content.ItemFile
import com.jatta.study.content.KindFile
import com.jatta.study.content.StudyCourse

data class TextReport(
    val text: String,
    val words: Int,
    val knownShare: Double,
    val heldItems: List<String>,
    val metItems: List<String>,
    val newItems: List<String>,
    val courseWords: Set<String>,
    val otherWords: List<String>,
)

class TextAnalyzer(private val content: StudyCourse, private val engine: Engine, basic: BasicWords?) {
    private val vocabulary = content.items.values.filter { it.kind != KindFile.Rule && it.audio == null }
    private val basicForms = basic?.forms?.toHashSet().orEmpty()
    private val singleForms: Map<String, List<String>> = vocabulary
        .flatMap { item -> formsOf(item).filter { ' ' !in it }.map { it to item.id } }
        .groupBy({ it.first }, { it.second })
    private val phrases: List<Pair<String, Regex>> = vocabulary
        .flatMap { item -> formsOf(item).filter { ' ' in it }.map { item.id to Regex("\\b${Regex.escape(it)}\\b") } }

    fun analyze(text: String, learner: Learner, today: Int): TextReport {
        val lower = text.lowercase()
        val tokens = engine.coverage.words(text)
        val distinct = tokens.distinct()
        val matchedTokens = distinct.filter { it in singleForms }.toMutableSet()
        val items = distinct.flatMap { singleForms[it].orEmpty() }.toMutableSet()
        phrases.forEach { (id, regex) ->
            regex.find(lower)?.let { match ->
                items += id
                matchedTokens += engine.coverage.words(match.value)
            }
        }
        val held = items.filter { engine.gate.holds(learner.progress[it], today) }
        val met = items.filter { it in learner.progress }
        val new = items.filter { it !in learner.progress }
        val heldTokens = held.flatMapTo(HashSet()) { id -> formsOf(content.items.getValue(id)).flatMap { engine.coverage.words(it) } }
        val knownTokens = tokens.count { it in basicForms || it in heldTokens || isName(it, text) }
        val other = distinct.filter { it !in basicForms && it !in matchedTokens && !isName(it, text) }
        return TextReport(
            text = text,
            words = tokens.size,
            knownShare = if (tokens.isEmpty()) 1.0 else knownTokens.toDouble() / tokens.size,
            heldItems = held,
            metItems = met.sortedBy { engine.memory.recallOn(learner.progress.getValue(it), today) },
            newItems = new.sortedWith(compareBy(nullsLast()) { content.course.item(it).frequencyRank }),
            courseWords = matchedTokens,
            otherWords = other,
        )
    }

    private fun formsOf(item: ItemFile): List<String> =
        (listOf(item.en) + item.also + item.examples.mapNotNull { it.gap }).map { it.lowercase().trim() }.distinct()

    private fun isName(token: String, text: String): Boolean {
        val capitalized = Regex("(?<![.!?]\\s)(?<!^)\\b${token.replaceFirstChar { it.uppercase() }}\\b")
        return capitalized.containsMatchIn(text) && !Regex("\\b$token\\b").containsMatchIn(text)
    }
}
