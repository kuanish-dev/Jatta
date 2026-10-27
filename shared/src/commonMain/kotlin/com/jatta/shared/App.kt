package com.jatta.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import com.jatta.study.ThemeChoice
import com.jatta.study.content.ContentLoader
import com.jatta.study.content.ReadingText
import com.jatta.study.content.ReadingTexts
import com.jatta.study.content.StudyCourse
import com.jatta.study.judge.EvalCase
import com.jatta.study.judge.JudgeEval
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.placement.PlacementFile
import com.jatta.study.remind.ReminderScheduler
import com.jatta.study.store.LearnerStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jatta.study.translate.SentencesFile

internal const val CONTENT_DIR = "files/content"

/**
 * The whole app. Platform code passes where to keep the learner's data, the clock, the time zone, the
 * on-device model (null where there is none), reminders and speech, so everything below is the same on Android and iOS.
 */
@Composable
fun App(
    dataDir: String,
    nowMs: () -> Long,
    utcOffsetMinutes: () -> Int,
    model: OnDeviceModel?,
    reminders: ReminderScheduler?,
    speaker: Speaker?,
    /** Lets the platform match its system bars to the theme. */
    onDarkTheme: (Boolean) -> Unit = {},
) = CompositionLocalProvider(LocalSpeaker provides speaker) {
    val scope = rememberCoroutineScope()
    var controller by remember { mutableStateOf<StudyController?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val choice = controller?.theme?.collectAsState()?.value ?: ThemeChoice.System
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    LaunchedEffect(dark) { onDarkTheme(dark) }
    JattaTheme(dark) {
        LaunchedEffect(Unit) {
            try {
                // Reading the course and replaying the log take seconds: off the main thread, so the first screen animates.
                controller = withContext(Dispatchers.Default) {
                    StudyController(
                    content = loadCourse(),
                    store = LearnerStore(dataDir),
                    nowMs = nowMs,
                    utcOffsetMinutes = utcOffsetMinutes,
                    scope = scope,
                    model = model,
                    evalCases = loadEvalCases(),
                    translationCases = JudgeEval.readTranslationCases(Res.readBytes("$CONTENT_DIR/eval/translations.json").decodeToString()),
                    texts = loadTexts(),
                    sentences = SentencesFile.read(Res.readBytes("$CONTENT_DIR/en/sentences.json").decodeToString()),
                    reminders = reminders,
                    placementFile = PlacementFile.read(Res.readBytes("$CONTENT_DIR/en/placement.json").decodeToString()),
                    )
                }
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

private suspend fun loadCourse(): StudyCourse {
    val courseFile = ContentLoader.readCourseFile(Res.readBytes("$CONTENT_DIR/en/course.json").decodeToString())
    val topics = courseFile.topics.associateWith { Res.readBytes("$CONTENT_DIR/en/topics/$it.json").decodeToString() }
    return ContentLoader.load(courseFile, topics)
}

private suspend fun loadEvalCases(): List<EvalCase> =
    JudgeEval.readCases(Res.readBytes("$CONTENT_DIR/eval/free-answers.json").decodeToString())

private suspend fun loadTexts(): List<ReadingText> =
    ReadingTexts.read(Res.readBytes("$CONTENT_DIR/en/texts.json").decodeToString())

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
