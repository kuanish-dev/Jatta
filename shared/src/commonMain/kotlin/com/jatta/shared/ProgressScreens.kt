package com.jatta.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.jatta.study.progress.DayActivity
import com.jatta.study.progress.DayMark
import com.jatta.study.progress.ProgressReport
import kotlinx.coroutines.delay

@Composable
internal fun ProgressScreen(report: ProgressReport, onBack: () -> Unit) = Page {
    BackBar("Мой прогресс", Neon.Cyan, onBack)
    Text("Весь путь", style = MaterialTheme.typography.headlineMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StreakBurst(report.streak.days, grew = false, ringSize = 170.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigStat("Рекорд", report.longestStreak, Neon.Magenta, suffix = " ${daysWord(report.longestStreak)}")
            BigStat("Дней занятий", report.studyDays, Neon.Cyan)
        }
    }
    Panel(Modifier.fillMaxWidth().appear(1)) {
        Label("Занятия · ${ProgressReport.CALENDAR_WEEKS} недель")
        Heatmap(report.calendar)
        Muted("Ярче — дольше занимались. Рамка — отдыхали: учить было нечего.")
    }
    MemoryPanel(report)
    Panel(Modifier.fillMaxWidth().appear(3)) {
        Label("По разделам · в памяти")
        report.parts.forEach { part ->
            Row(Modifier.fillMaxWidth()) {
                Text(part.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text("${countUp(part.held)} / ${part.total}", style = MaterialTheme.typography.titleMedium, color = Neon.Cyan)
            }
            NeonBar(if (part.total == 0) 0f else part.held.toFloat() / part.total, height = 6.dp)
            Muted("встречено: ${part.met}")
        }
    }
    Panel(Modifier.fillMaxWidth().appear(4)) {
        Label("За всё время")
        val share = if (report.answers == 0) 0 else report.rightFirstTime * 100 / report.answers
        TotalRow("Ответов", countUp(report.answers).toString())
        TotalRow("С первого раза", "${countUp(share)}%")
        TotalRow("Время", hours(countUp(report.minutes)))
        if (report.translated > 0) TotalRow("Переводов", "${countUp(report.translatedRight)} из ${report.translated}")
    }
}

@Composable
private fun BigStat(label: String, value: Int, color: Color, modifier: Modifier = Modifier, suffix: String = "") {
    Column(modifier) {
        Label(label)
        NeonNumber("${countUp(value)}$suffix", color = color, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Neon.Cyan)
    }
}

private fun hours(minutes: Int) = if (minutes < 60) "$minutes мин" else "${minutes / 60} ч ${minutes % 60} мин"

@Composable
private fun Heatmap(days: List<DayActivity>) {
    val palette = LocalNeon.current
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(days) {
        delay(200)
        reveal.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }
    val forever = rememberInfiniteTransition()
    val pulse by forever.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse))
    val most = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    val weeks = (days.size + 6) / 7
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.height(132.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("Пн", "", "Ср", "", "Пт", "", "Вс").forEach { Label(it) }
        }
        Canvas(Modifier.fillMaxWidth().height(132.dp)) {
            val gap = 3.dp.toPx()
            val cell = minOf((size.width - gap * (weeks - 1)) / weeks, (size.height - gap * 6) / 7)
            val corner = CornerRadius(cell * 0.22f)
            days.forEachIndexed { index, day ->
                val week = index / 7
                val alpha = (reveal.value * weeks - week).coerceIn(0f, 1f)
                if (alpha == 0f) return@forEachIndexed
                val topLeft = Offset(week * (cell + gap), (index % 7) * (cell + gap))
                val area = Size(cell, cell)
                when (day.mark) {
                    DayMark.Studied -> {
                        val strength = 0.3f + 0.7f * day.minutes / most
                        drawRoundRect(lerp(palette.cyan, palette.magenta, strength), topLeft, area, corner, alpha = alpha * (0.45f + 0.55f * strength))
                    }
                    DayMark.Rest -> drawRoundRect(palette.cyan, topLeft, area, corner, alpha = alpha * 0.7f, style = Stroke(1.5.dp.toPx()))
                    DayMark.Missed -> drawRoundRect(palette.line, topLeft, area, corner, alpha = alpha)
                    DayMark.Today -> drawRoundRect(palette.acid, topLeft, area, corner, alpha = alpha * pulse, style = Stroke(2.dp.toPx()))
                    DayMark.Before -> drawRoundRect(palette.line, topLeft, area, corner, alpha = alpha * 0.35f, style = Stroke(1.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun MemoryPanel(report: ProgressReport) = Panel(Modifier.fillMaxWidth().appear(2), accent = Neon.Cyan.copy(alpha = 0.45f)) {
    val held = report.heldByDay
    val met = report.metByDay
    Label("Память по дням")
    Row(Modifier.fillMaxWidth()) {
        BigStat("В памяти", held.lastOrNull() ?: 0, Neon.Cyan, Modifier.weight(1f))
        BigStat("Встречено", met.lastOrNull() ?: 0, Neon.Muted, Modifier.weight(1f))
        val share = if (met.isEmpty() || met.last() == 0) 0 else held.last() * 100 / met.last()
        BigStat("Держится", share, Neon.Magenta, Modifier.weight(1f), suffix = "%")
    }
    if (held.size < 2) {
        Muted("График появится со второго дня занятий.")
        return@Panel
    }
    MemoryChart(held, met, report.firstDay)
    MemoryLegend()
    val back = (held.size - 8).coerceAtLeast(0)
    Muted(
        "За неделю: +${held.last() - held[back]} в памяти, +${met.last() - met[back]} встречено. " +
            "Разрыв между линиями — то, что вы видели, но ещё не закрепили. Коснитесь графика, чтобы увидеть любой день.",
    )
}
