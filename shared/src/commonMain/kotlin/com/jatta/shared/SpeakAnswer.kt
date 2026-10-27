package com.jatta.shared

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.DiffPart
import kotlinx.coroutines.launch

@Composable
internal fun SpeakAnswer(id: Int, onHeard: (List<String>) -> Unit, onSelfCheck: () -> Unit) {
    val input = LocalSpeechInput.current
    if (input == null || !input.available) {
        NeonButton(onSelfCheck, Modifier.fillMaxWidth()) { Text("СКАЗАЛ(А), ПОКАЗАТЬ ОТВЕТ") }
        return
    }
    val scope = rememberCoroutineScope()
    var listening by remember(id) { mutableStateOf(false) }
    var partial by remember(id) { mutableStateOf("") }
    var problem by remember(id) { mutableStateOf<Spoken?>(null) }
    var attempts by remember(id) { mutableIntStateOf(0) }
    var level by remember(id) { mutableFloatStateOf(0f) }
    val start = {
        scope.launch {
            listening = true
            partial = ""
            problem = null
            val spoken = input.listen(onPartial = { partial = it }, onLevel = { level = it })
            listening = false
            level = 0f
            if (spoken is Spoken.Words) onHeard(spoken.variants) else problem = spoken.also { attempts++ }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MicButton(listening, level) { if (listening) input.stop() else start() }
        Label(
            if (listening) "Слушаю… нажмите, когда закончите" else "Нажмите и скажите",
            color = if (listening) Neon.Magenta else Neon.Cyan,
        )
        if (partial.isNotBlank()) {
            Text(partial, style = MaterialTheme.typography.titleMedium, color = Neon.Cyan, textAlign = TextAlign.Center)
        }
        problem?.let { Text(problemText(it), Modifier.shake(attempts), color = Neon.Danger, textAlign = TextAlign.Center) }
        NeonTextButton(onSelfCheck) { Label("Без микрофона — показать ответ") }
    }
}

private fun problemText(problem: Spoken) = when (problem) {
    Spoken.Silence -> "Не расслышал. Скажите ещё раз, чуть громче и ближе к телефону."
    Spoken.NoMicrophone -> "Нет доступа к микрофону. Разрешите его в настройках телефона или сверьте ответ сами."
    Spoken.LanguageLoading -> "Для распознавания без интернета телефону нужен английский пакет — он предложит его скачать. После загрузки нажмите на микрофон ещё раз."
    else -> "Распознать не получилось. Попробуйте ещё раз или сверьте ответ сами."
}

@Composable
internal fun HeardComparison(heard: List<DiffPart>) {
    Muted("Телефон расслышал")
    Text(diffText(heard, shown = DiffKind.Removed), Modifier.appear(1), style = MaterialTheme.typography.bodyLarge)
    Muted("Образец")
    Panel(Modifier.fillMaxWidth().appear(2)) {
        Text(diffText(heard, shown = DiffKind.Added), style = MaterialTheme.typography.bodyLarge, color = Neon.Cyan)
    }
}

@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val color = if (listening) Neon.Magenta else Neon.Cyan
    val loudness by animateFloatAsState(level, tween(120))
    val wave by rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)))
    Canvas(Modifier.size(128.dp).pressable(onClick)) {
        val radius = 36.dp.toPx()
        val line = 2.dp.toPx()
        if (listening) {
            drawCircle(color, radius * (1f + 0.7f * wave), alpha = 0.4f * (1f - wave), style = Stroke(line))
            drawCircle(color, radius * (1f + 0.5f * loudness), alpha = 0.18f)
        }
        drawCircle(color, radius, alpha = if (listening) 0.25f else 0.1f)
        drawCircle(color, radius, style = Stroke(line))
        drawMicrophone(color, radius * 0.55f)
    }
}

internal fun DrawScope.drawMicrophone(color: Color, unit: Float) {
    val c = center
    val stroke = Stroke(unit * 0.16f, cap = StrokeCap.Round)
    drawRoundRect(color, Offset(c.x - unit * 0.35f, c.y - unit * 0.85f), Size(unit * 0.7f, unit * 1.15f), CornerRadius(unit * 0.35f))
    drawArc(color, 0f, 180f, false, Offset(c.x - unit * 0.6f, c.y - unit * 0.6f), Size(unit * 1.2f, unit * 1.2f), style = stroke)
    drawLine(color, Offset(c.x, c.y + unit * 0.6f), Offset(c.x, c.y + unit * 0.9f), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(c.x - unit * 0.35f, c.y + unit * 0.9f), Offset(c.x + unit * 0.35f, c.y + unit * 0.9f), stroke.width, StrokeCap.Round)
}
