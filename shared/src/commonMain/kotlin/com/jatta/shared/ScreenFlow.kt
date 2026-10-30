package com.jatta.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.jatta.study.Screen
import com.jatta.study.Steps
import com.jatta.study.StudyController
import com.jatta.study.canGoBack

private const val SLIDE_MS = 340

private data class Session(val counter: String, val steps: Steps, val stoppable: Boolean)

@Composable
fun StudyScreens(controller: StudyController, nowMs: () -> Long, onReload: () -> Unit = {}) {
    val screen by controller.screen.collectAsState()
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = screen.canGoBack) {
        controller.goBack()
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        AnimatedContent(
            sessionOf(screen),
            Modifier.fillMaxWidth(),
            contentKey = { it != null },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { session ->
            if (session != null) SessionHeader(session, controller::stopSession)
        }
        AnimatedContent(
            screen,
            Modifier.weight(1f),
            contentKey = ::screenKey,
            transitionSpec = { slide(back = !targetState.canGoBack) },
        ) { ScreenContent(it, controller, nowMs, onReload) }
    }
}

@Composable
private fun SessionHeader(session: Session, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(session.counter, color = Neon.Cyan)
                val steps = session.steps
                RollingText(" ${steps.done + 1}/${steps.done + steps.remaining}", MaterialTheme.typography.labelMedium, Neon.Cyan)
                Spacer(Modifier.weight(1f))
                if (session.stoppable) NeonTextButton(onStop) { Text("ЗАКОНЧИТЬ") }
            }
            NeonBar(session.steps.fraction, height = 6.dp)
        }
    }
}

private fun sessionOf(screen: Screen): Session? = when (screen) {
    is Screen.PlacementWord -> Session("Тест уровня", screen.steps, stoppable = false)
    is Screen.PlacementGrammar -> Session("Тест уровня", screen.steps, stoppable = false)
    is Screen.Translate -> Session("Перевод", screen.steps, stoppable = true)
    is Screen.Translated -> Session("Перевод", screen.steps, stoppable = true)
    is Screen.Present -> Session("Шаг", screen.steps, stoppable = true)
    is Screen.Question -> Session("Шаг", screen.steps, stoppable = true)
    is Screen.Checking -> Session("Шаг", screen.steps, stoppable = true)
    is Screen.Judged -> Session("Шаг", screen.steps, stoppable = true)
    is Screen.SelfCheck -> Session("Шаг", screen.steps, stoppable = true)
    is Screen.Heard -> Session("Шаг", screen.steps, stoppable = true)
    is Screen.Reveal -> Session("Шаг", screen.steps, stoppable = true)
    else -> null
}

private fun screenKey(screen: Screen): Any = screen::class to when (screen) {
    is Screen.Question -> screen.exercise
    is Screen.Present -> screen.item.id
    is Screen.PlacementWord -> screen.word
    is Screen.PlacementGrammar -> screen.id
    is Screen.Translate -> screen.id
    is Screen.Translated -> screen.task.sentenceId
    is Screen.Topic -> screen.topic.id
    else -> Unit
}

private fun slide(back: Boolean): ContentTransform {
    val sign = if (back) -1 else 1
    val enter = slideInHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { sign * it / 3 } +
        fadeIn(tween(SLIDE_MS - 80, delayMillis = 80))
    val exit = slideOutHorizontally(tween(SLIDE_MS, easing = FastOutSlowInEasing)) { -sign * it / 3 } +
        fadeOut(tween(SLIDE_MS - 140))
    return enter togetherWith exit
}
