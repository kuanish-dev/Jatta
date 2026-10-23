package com.jatta.shared

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jatta.shared.resources.Res
import com.jatta.study.StudyController
import com.jatta.study.content.ContentLoader
import com.jatta.study.content.StudyCourse
import com.jatta.study.judge.EvalCase
import com.jatta.study.judge.JudgeEval
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.store.LearnerStore

private const val CONTENT_DIR = "files/content"

/**
 * The whole app. Platform code passes where to keep the learner's data, the clock, the time zone and
 * the on-device model (null where there is none), so everything below is the same on Android and iOS.
 */
@Composable
fun App(dataDir: String, nowMs: () -> Long, utcOffsetMinutes: () -> Int, model: OnDeviceModel?) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var controller by remember { mutableStateOf<StudyController?>(null) }
            var failure by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                try {
                    controller = StudyController(
                        content = loadCourse(),
                        store = LearnerStore(dataDir),
                        nowMs = nowMs,
                        utcOffsetMinutes = utcOffsetMinutes,
                        scope = scope,
                        model = model,
                        evalCases = loadEvalCases(),
                    )
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
    val courseFile = ContentLoader.readCourseFile(Res.readBytes("$CONTENT_DIR/en/course.json").decodeToString())
    val topics = courseFile.topics.associateWith { Res.readBytes("$CONTENT_DIR/en/topics/$it.json").decodeToString() }
    return ContentLoader.load(courseFile, topics)
}

private suspend fun loadEvalCases(): List<EvalCase> =
    JudgeEval.readCases(Res.readBytes("$CONTENT_DIR/eval/free-answers.json").decodeToString())

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
