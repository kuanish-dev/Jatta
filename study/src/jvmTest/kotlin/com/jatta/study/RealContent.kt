package com.jatta.study

import com.jatta.study.content.ContentLoader
import com.jatta.study.content.StudyCourse
import com.jatta.study.translate.SentencesFile
import java.io.File

/** The course from content/en in the repository; the path comes from study/build.gradle.kts. */
object RealContent {
    private val dir = File(System.getProperty("contentDir"), "en")

    fun load(): StudyCourse {
        val courseFile = ContentLoader.readCourseFile(File(dir, "course.json").readText())
        val topics = courseFile.topics.associateWith { File(dir, "topics/$it.json").readText() }
        return ContentLoader.load(courseFile, topics)
    }

    fun sentences(): SentencesFile = SentencesFile.read(File(dir, "sentences.json").readText())
}
