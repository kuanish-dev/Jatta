package com.langengine.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.langengine.shared.resources.Res
import com.langengine.study.StudyController
import com.langengine.study.content.ContentLoader
import com.langengine.study.content.StudyCourse
import com.langengine.study.store.LearnerStore

private const val COURSE_DIR = "files/content/en"

/**
 * The whole app. Platform code passes where to keep the learner's data, the clock and the time zone,
 * so everything below is the same on Android and iOS.
 */
@Composable
fun App(dataDir: String, nowMs: () -> Long, utcOffsetMinutes: () -> Int) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            var controller by remember { mutableStateOf<StudyController?>(null) }
            var failure by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                try {
                    controller = StudyController(loadCourse(), LearnerStore(dataDir), nowMs, utcOffsetMinutes)
                } catch (e: Exception) {
                    failure = e.message ?: e.toString()
                }
            }
            val ready = controller
            when {
                ready != null -> StudyScreens(ready, nowMs)
                failure != null -> Centered("Не удалось открыть курс:\n$failure")
                else -> Centered("Загрузка…")
            }
        }
    }
}

private suspend fun loadCourse(): StudyCourse {
    val courseFile = ContentLoader.readCourseFile(Res.readBytes("$COURSE_DIR/course.json").decodeToString())
    val topics = courseFile.topics.associateWith { Res.readBytes("$COURSE_DIR/topics/$it.json").decodeToString() }
    return ContentLoader.load(courseFile, topics)
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
