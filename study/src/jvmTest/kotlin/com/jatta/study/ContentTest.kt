package com.jatta.study

import com.jatta.engine.session.Rung
import com.jatta.study.content.ContentError
import com.jatta.study.content.ContentLoader
import com.jatta.engine.EngineConfig
import com.jatta.engine.content.TextCoverage
import com.jatta.study.content.KindFile
import com.jatta.study.content.ReadingTexts
import com.jatta.study.exercise.Exercise
import java.io.File
import com.jatta.study.exercise.ExerciseFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentTest {
    private val content = RealContent.load()

    @Test
    fun realCourseLoads() {
        assertEquals(52, content.course.topics.size)
        assertEquals(1015, content.items.size)
    }

    /** М24: every recorded phrase has its file, a free license and its source, and is studied by ear. */
    @Test
    fun recordedPhrasesAreCreditedAndStudiedByEar() {
        val recorded = content.items.values.filter { it.audio != null }
        assertEquals(251, recorded.size)
        val audioDir = File(System.getProperty("contentDir"), "en/audio")
        val factory = ExerciseFactory(content)
        recorded.forEach { item ->
            val audio = item.audio!!
            assertTrue(File(audioDir, audio.file).length() > 1_000, "${item.id}: no recording ${audio.file}")
            assertTrue(audio.license in setOf("CC BY 4.0", "CC BY-SA 4.0"), "${item.id}: license ${audio.license}")
            assertTrue(audio.source.startsWith("https://tatoeba.org/sentences/show/"), "${item.id}: source ${audio.source}")
            val recognize = factory.build(item.id, Rung.Recognize, seed = 1) as Exercise.Choice
            assertTrue(recognize.question.isEmpty(), "${item.id}: the text would give the sound away")
            val gap = factory.build(item.id, Rung.FillGap, seed = 1) as Exercise.Typed
            assertTrue("___" in gap.question)
            val dictation = factory.build(item.id, Rung.Translate, seed = 1) as Exercise.Typed
            assertTrue(dictation.question.isEmpty() && dictation.accepted.first() == item.en)
            listOf(recognize, gap, dictation).forEach {
                assertTrue(it.byEar, "${item.id} on ${it.rung} is not by ear")
                assertEquals(audio.file, it.recording)
            }
            val speak = factory.build(item.id, Rung.Speak, seed = 1)
            assertTrue(!speak.byEar && speak.recording == audio.file, "${item.id}: the spoken answer is compared with the voice")
        }
        val files = audioDir.listFiles()!!.filter { it.extension == "m4a" }.map { it.name }.toSet()
        assertEquals(recorded.map { it.audio!!.file }.toSet(), files, "every recording belongs to a phrase")
    }

    /** М8: a word's rank is its NGSL 1.2 rank, and a word missing from NGSL has no rank. */
    @Test
    fun wordRanksComeFromNgsl() {
        val ngsl = mutableMapOf<String, Int>()
        File(System.getProperty("sourcesDir"), "ngsl/NGSL_12_stats.csv").readLines().drop(1).forEach { line ->
            val (lemma, rank) = line.split(',')
            ngsl.getOrPut(lemma.lowercase()) { rank.toInt() }
        }
        val words = content.items.values.filter { it.kind != KindFile.Rule }
        words.forEach { word ->
            assertEquals(ngsl[word.en.lowercase()], word.rank, "${word.id}: rank must be the NGSL 1.2 rank of \"${word.en}\"")
        }
    }

    /** М11: every collected text is credited and passes the engine's coverage check once the course words are known. */
    @Test
    fun realTextsAreCreditedAndReadable() {
        val texts = ReadingTexts.read(File(System.getProperty("contentDir"), "en/texts.json").readText())
        assertTrue(texts.size >= 50, "only ${texts.size} texts")
        assertEquals(texts.size, texts.map { it.id }.toSet().size, "duplicate text ids")
        val coverage = TextCoverage(EngineConfig())
        val courseForms = content.items.values
            .filter { it.kind != KindFile.Rule }
            .flatMap { item -> (listOf(item.en) + item.also + item.examples.mapNotNull { it.gap }).flatMap { coverage.words(it) } }
            .toSet()
        texts.forEach { text ->
            with(text.source) {
                assertTrue(url.startsWith("https://") && license.isNotBlank() && author.isNotBlank(), "${text.id}: source not credited")
            }
            val words = coverage.words(text.text).toSet()
            assertTrue(words.containsAll(text.rare), "${text.id}: rare words must come from the text")
            assertTrue(coverage.check(text.text, words - text.rare.toSet() + courseForms).acceptable, "${text.id}: too many unknown words")
        }
    }

    /** М25: every sentence to translate uses words of the course, has translations both ways and its credit. */
    @Test
    fun translationSentencesUseCourseWordsAndAreCredited() {
        val file = RealContent.sentences()
        assertEquals("CC BY 2.0 FR", file.license)
        assertEquals("https://tatoeba.org/sentences/show/", file.url)
        assertTrue(file.sentences.size >= 1500, "only ${file.sentences.size} sentences")
        assertEquals(file.sentences.size, file.sentences.map { it.id }.toSet().size, "duplicate sentence ids")
        file.sentences.forEach { sentence ->
            assertTrue(sentence.items.isNotEmpty(), "${sentence.id}: no course word")
            sentence.items.forEach { id ->
                val item = content.items[id]
                assertTrue(item != null && item.kind != KindFile.Rule && item.audio == null, "${sentence.id}: $id is not a course word")
            }
            assertTrue(sentence.en.isNotEmpty() && sentence.en.none { it.isBlank() }, "${sentence.id}: English")
            assertTrue(sentence.ru.isNotEmpty() && sentence.ru.all { RUSSIAN.matches(it) }, "${sentence.id}: Russian")
        }
    }

    @Test
    fun everyItemHasAWorkingExerciseOnEveryRung() {
        val factory = ExerciseFactory(content)
        for (itemId in content.items.keys) for (rung in Rung.entries) for (seed in 0 until 8) {
            val exercise = factory.build(itemId, rung, seed)
            val where = "$itemId on $rung (seed $seed)"
            assertTrue(exercise.listen.isNotBlank() && exercise.listen.none { it in 'а'..'я' || it in 'А'..'Я' }, "$where: audio must be English")
            when (exercise) {
                is Exercise.Choice -> {
                    assertEquals(exercise.options.size, exercise.options.distinct().size, "$where: repeated options")
                    assertTrue(exercise.options.size >= 3, "$where: too few options")
                    val answer = exercise.options[exercise.correctIndex].trim().lowercase()
                    val right = exercise.options.count { it.trim().lowercase() == answer }
                    assertEquals(1, right, "$where: the right option appears more than once")
                }
                is Exercise.Typed -> assertTrue(exercise.accepted.isNotEmpty(), "$where: nothing accepted")
                is Exercise.Free -> assertTrue(exercise.references.isNotEmpty(), "$where: no model sentences")
                is Exercise.Speak -> assertTrue(exercise.answer.isNotBlank(), "$where: no answer")
            }
        }
    }

    @Test
    fun brokenContentIsRefusedWithAReadableReason() {
        val course = ContentLoader.readCourseFile("""{"language":"en","title":"T","topics":["t"]}""")
        val topic = """
            {"id":"t","title":"T","items":[
              {"id":"r","kind":"rule","en":"x","ru":"x","examples":[
                {"en":"I worked late.","ru":"…","gap":"work","wrong":["works","working"]}
              ]}
            ]}
        """.trimIndent()
        val error = assertFailsWith<ContentError> { ContentLoader.load(course, mapOf("t" to topic)) }
        assertTrue(error.problems.any { "not a whole word" in it }, error.message)
    }

    private companion object {
        val RUSSIAN = Regex("[^A-Za-z]*[А-Яа-яЁё][^A-Za-z]*")
    }
}
