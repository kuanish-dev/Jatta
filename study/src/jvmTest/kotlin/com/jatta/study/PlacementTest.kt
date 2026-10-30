package com.jatta.study

import com.jatta.study.placement.Placement
import com.jatta.study.placement.PlacementFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacementTest {
    private val content = RealContent.load()
    private val file = PlacementFile.read(File(System.getProperty("contentDir"), "en/placement.json").readText())
    private val test = Placement(content, file, StudyConfig())
    private val allRight = test.grammar.map { true }

    @Test
    fun knowingEveryRealWordAndNoPseudowordPlacesTheWholeVocabulary() {
        val result = test.result(test.words.associate { it.word to (it.band != null) }, allRight)
        assertEquals(2809, result.estimatedWords)
        assertTrue(result.reliable)
        val ngslWords = content.items.values.filter { it.rank != null }.map { it.id }
        assertTrue(result.knownItems.containsAll(ngslWords))
        assertFalse("w-deadline" in result.knownItems, "a word outside NGSL is never placed")
        assertEquals(24, result.knownTopics.size)
    }

    @Test
    fun sayingYesToEverythingIsCorrectedToNothing() {
        val result = test.result(test.words.associate { it.word to true }, allRight.map { false })
        assertEquals(0, result.estimatedWords)
        assertFalse(result.reliable)
        assertTrue(result.knownItems.isEmpty())
    }

    @Test
    fun knowingOnlyTheMostFrequentBandPlacesOnlyItsWords() {
        val result = test.result(test.words.associate { it.word to (it.band == 0) }, allRight.map { false })
        assertEquals(500, result.estimatedWords)
        val firstBand = content.items.values.filter { (it.rank ?: Int.MAX_VALUE) <= 500 }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "w-experience", "w-suggest", "w-require", "w-decision", "w-account", "w-forward", "w-charge",
                "w-order", "w-term", "w-pass", "w-research", "w-practice", "w-train", "w-close", "w-support",
            ),
            firstBand,
        )
        assertEquals(firstBand, result.knownItems)
    }

    @Test
    fun grammarCountsOnlyWhenBothQuestionsOfATopicAreRight() {
        val firstWrong = allRight.mapIndexed { index, _ -> index != 0 }
        val result = test.result(emptyMap(), firstWrong)
        assertEquals(23, result.knownTopics.size)
        assertFalse(content.topicOf(test.grammar[0].exercise.itemId).title in result.knownTopics)
    }

    @Test
    fun theWordListIsSound() {
        val ranks = mutableMapOf<String, Int>()
        File(System.getProperty("sourcesDir"), "ngsl/NGSL_12_stats.csv").readLines().drop(1).forEach { line ->
            val (lemma, rank) = line.split(',')
            ranks.getOrPut(lemma.lowercase()) { rank.toInt() }
        }
        file.bands.forEach { band ->
            band.words.forEach { word -> assertTrue(ranks[word] in band.from..band.to, "$word is not in band ${band.from}-${band.to}") }
        }
        file.pseudowords.forEach { word -> assertFalse(word in ranks || content.items.values.any { it.en == word }, "$word is a real word") }
    }
}
