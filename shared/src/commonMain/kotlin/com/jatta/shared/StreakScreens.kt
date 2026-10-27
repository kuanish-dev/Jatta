package com.jatta.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jatta.study.Screen
import com.jatta.study.progress.DayMark
import com.jatta.study.progress.Streak
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SPARKS = 18
private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
internal fun DailySummaryScreen(screen: Screen.DailySummary, onContinue: () -> Unit) = Page {
    val yesterday = screen.yesterday
    val streak = screen.streak
    Label("Итоги вчера", color = Neon.Cyan)
    if (yesterday != null) {
        Text("Вчера вы занимались", style = MaterialTheme.typography.headlineMedium)
        Panel(Modifier.fillMaxWidth().appear(1), accent = Neon.Cyan.copy(alpha = 0.45f)) {
            val share = if (yesterday.answered == 0) 0 else yesterday.rightFirstTime * 100 / yesterday.answered
            SummaryRow("Ответов", yesterday.answered)
            SummaryRow("С первого раза", yesterday.rightFirstTime, "  ·  $share%")
            SummaryRow("Новых", yesterday.newItems)
            SummaryRow("Повторов", yesterday.reviews)
            if (yesterday.translated > 0) SummaryRow("Переводов", yesterday.translatedRight, " из ${yesterday.translated}")
            SummaryRow("Время", yesterday.minutes, " мин")
        }
    } else {
        Text("Вчера занятий не было", style = MaterialTheme.typography.headlineMedium)
        Muted(
            when {
                screen.restDay -> "Повторять и учить было нечего — серия не прервалась."
                streak.days == 0 -> "Ничего страшного: сегодняшнее занятие начнёт новую серию."
                else -> "Серия держится: пропусков не было."
            },
        )
    }
    if (streak.days > 0) {
        StreakBurst(streak.days, grew = false, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    WeekStrip(streak)
    if (!streak.studiedToday) Muted("Сегодняшнее занятие продлит серию до ${streak.days + 1} ${daysWord(streak.days + 1)}.")
    NeonButton(onContinue, Modifier.fillMaxWidth().pulse(scaleBy = 0.025f)) { Text("ПОЕХАЛИ") }
}

@Composable
private fun SummaryRow(label: String, value: Int, suffix: String = "") {
    val count = countUp(value)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text("$count$suffix", style = MaterialTheme.typography.titleMedium, color = Neon.Cyan)
    }
}

@Composable
internal fun StreakChip(streak: Streak, onOpen: () -> Unit) {
    Row(
        Modifier.pressable(onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlameIcon(22.dp)
        Label(if (streak.days > 0) "${streak.days} ${daysWord(streak.days)} подряд" else "Серия начнётся с занятия", color = Neon.Acid)
        Muted(if (streak.days > 0 && !streak.studiedToday) "— продлите сегодня ›" else "прогресс ›")
    }
}

@Composable
internal fun StreakBurst(days: Int, grew: Boolean, modifier: Modifier = Modifier, ringSize: Dp = 230.dp) {
    val palette = LocalNeon.current
    val ring = remember { Animatable(0f) }
    val count = remember { Animatable(if (grew) (days - 1).coerceAtLeast(0).toFloat() else 0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(days, grew) {
        delay(300)
        launch { ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        count.animateTo(days.toFloat(), tween(1100, easing = FastOutSlowInEasing))
        burst.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
    }
    val forever = rememberInfiniteTransition()
    val spin by forever.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)))
    val pulse by forever.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Reverse))
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.055f
            val radius = size.minDimension / 2 - stroke * 2.5f
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val colors = listOf(palette.cyan, palette.magenta, palette.acid, palette.cyan)
            val brush = Brush.sweepGradient(colors, center)
            drawCircle(palette.line, radius, style = Stroke(stroke))
            rotate(spin) {
                drawCircle(
                    palette.cyan.copy(alpha = 0.4f), radius + stroke * 1.6f,
                    style = Stroke(stroke * 0.22f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 14f))),
                )
            }
            rotate(-90f) {
                drawArc(brush, 0f, 360f * ring.value, false, topLeft, arcSize, alpha = palette.glow * 0.35f * pulse, style = Stroke(stroke * 3f, cap = StrokeCap.Round))
                drawArc(brush, 0f, 360f * ring.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            sparks(burst.value, radius, stroke, colors)
        }
        val compact = ringSize < 200.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FlameIcon(if (compact) 26.dp else 34.dp)
            val style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium
            NeonNumber(count.value.roundToInt().toString(), color = Neon.Acid, style = style)
            Label(if (compact) daysWord(days) else "${daysWord(days)} подряд", color = Neon.Muted)
        }
        if (grew) PlusOne(burst.value)
    }
}

private fun DrawScope.sparks(progress: Float, radius: Float, stroke: Float, colors: List<Color>) {
    if (progress <= 0f || progress >= 1f) return
    repeat(SPARKS) { index ->
        val angle = (index * 360f / SPARKS + (index % 3) * 7f) * PI.toFloat() / 180f
        val reach = 0.35f + (index % 4) * 0.08f
        val from = radius + stroke + radius * reach * progress
        val to = from + stroke * (1.6f - progress)
        val direction = Offset(cos(angle), sin(angle))
        drawLine(
            colors[index % (colors.size - 1)].copy(alpha = 1f - progress),
            center + direction * from, center + direction * to,
            strokeWidth = stroke * 0.45f, cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun PlusOne(progress: Float) {
    Text(
        "+1",
        Modifier.offset(y = (-70 - 50 * progress).dp).alpha(if (progress >= 1f) 0f else 1f - progress * 0.6f),
        style = MaterialTheme.typography.headlineMedium,
        color = Neon.Magenta,
    )
}

@Composable
internal fun FlameIcon(iconSize: Dp) {
    val palette = LocalNeon.current
    val forever = rememberInfiniteTransition()
    val flicker by forever.animateFloat(0.9f, 1.08f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse))
    val glow by forever.animateFloat(0.75f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse))
    Canvas(Modifier.size(iconSize)) {
        val warm = palette.flame[1]
        drawCircle(
            Brush.radialGradient(listOf(warm.copy(alpha = 0.55f * glow), Color.Transparent), Offset(center.x, size.height * 0.68f), size.minDimension * 0.75f),
            size.minDimension * 0.75f, Offset(center.x, size.height * 0.68f),
        )
        scale(1f, flicker, pivot = Offset(center.x, size.height)) {
            drawPath(flame(size.width, size.height, 1f), Brush.verticalGradient(palette.flame))
            drawPath(flame(size.width, size.height, 0.55f), Brush.verticalGradient(palette.flameCore, startY = size.height * 0.45f))
        }
    }
}

private fun flame(width: Float, height: Float, scale: Float): Path = Path().apply {
    val w = width * scale
    val h = height * scale
    val left = (width - w) / 2
    val top = height - h
    moveTo(left + w / 2, top)
    cubicTo(left + w * 0.95f, top + h * 0.35f, left + w, top + h * 0.75f, left + w / 2, top + h)
    cubicTo(left, top + h * 0.75f, left + w * 0.05f, top + h * 0.45f, left + w * 0.35f, top + h * 0.3f)
    cubicTo(left + w * 0.38f, top + h * 0.45f, left + w * 0.45f, top + h * 0.5f, left + w * 0.5f, top + h * 0.48f)
    cubicTo(left + w * 0.45f, top + h * 0.3f, left + w * 0.5f, top + h * 0.15f, left + w / 2, top)
    close()
}

@Composable
internal fun WeekStrip(streak: Streak) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        streak.week.forEachIndexed { index, mark ->
            DayCell(WEEKDAYS[(streak.today - 6 + index + 3).mod(7)], mark, index, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayCell(weekday: String, mark: DayMark, index: Int, modifier: Modifier) {
    val palette = LocalNeon.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120L * index)
        appear.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
    }
    val forever = rememberInfiniteTransition()
    val pulse by forever.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
    Column(modifier.alpha(appear.value).scale(0.6f + 0.4f * appear.value), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().size(34.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val corner = size.minDimension * 0.28f
                val cell = Path().apply {
                    moveTo(corner, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - corner)
                    lineTo(size.width - corner, size.height); lineTo(0f, size.height); lineTo(0f, corner); close()
                }
                when (mark) {
                    DayMark.Studied -> drawPath(cell, Brush.linearGradient(listOf(palette.cyan, palette.magenta)))
                    DayMark.Rest -> drawPath(cell, palette.cyan.copy(alpha = 0.6f), style = Stroke(2.dp.toPx()))
                    DayMark.Missed -> drawPath(cell, palette.line)
                    DayMark.Today -> drawPath(cell, palette.acid.copy(alpha = pulse), style = Stroke(2.dp.toPx()))
                    DayMark.Before -> drawPath(cell, palette.line.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
                }
            }
            val sign = when (mark) {
                DayMark.Studied -> "✓"
                DayMark.Rest -> "–"
                DayMark.Missed -> "×"
                else -> ""
            }
            Text(sign, color = if (mark == DayMark.Studied) palette.void else Neon.Muted, textAlign = TextAlign.Center)
        }
        Label(weekday)
    }
}

internal fun daysWord(n: Int) = plural(n, "день", "дня", "дней")
