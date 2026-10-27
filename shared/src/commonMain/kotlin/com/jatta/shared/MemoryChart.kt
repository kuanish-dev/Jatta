package com.jatta.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.jatta.engine.calendarDate
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

internal fun shortDate(day: Int): String = calendarDate(day).let { "${it.dayOfMonth} ${MONTHS[it.month - 1]}" }

private class ChartFrame(size: Size, density: Density, private val days: Int, val top: Int) {
    val left = with(density) { 38.dp.toPx() }
    val right = size.width - with(density) { 4.dp.toPx() }
    val upper = with(density) { 10.dp.toPx() }
    val lower = size.height - with(density) { 22.dp.toPx() }

    fun x(index: Int) = left + (right - left) * index / (days - 1)
    fun y(value: Int) = lower - (lower - upper) * value / top
    fun index(x: Float) = ((x - left) / (right - left) * (days - 1)).roundToInt().coerceIn(0, days - 1)
}

@Composable
internal fun MemoryChart(held: List<Int>, met: List<Int>, firstDay: Int) {
    val palette = LocalNeon.current
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelSmall.copy(color = palette.muted)
    val tip = MaterialTheme.typography.labelMedium.copy(color = palette.text)
    val draw = remember { Animatable(0f) }
    LaunchedEffect(held) {
        delay(300)
        draw.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
    }
    var selected by remember(held) { mutableStateOf<Int?>(null) }
    val top = niceTop(met.max())
    Canvas(
        Modifier.fillMaxWidth().height(210.dp)
            .pointerInput(held) {
                detectTapGestures { selected = ChartFrame(size.toSize(), this, held.size, top).index(it.x) }
            }
            .pointerInput(held) {
                detectHorizontalDragGestures { change, _ ->
                    selected = ChartFrame(size.toSize(), this, held.size, top).index(change.position.x)
                }
            },
    ) {
        val frame = ChartFrame(size, this, held.size, top)
        grid(frame, measurer, axis)
        dates(frame, held.lastIndex, measurer, axis, firstDay)
        clipRect(right = frame.left + (frame.right - frame.left) * draw.value) {
            lines(frame, held, met, palette)
        }
        val picked = selected
        when {
            picked != null -> pick(frame, picked, held, met, firstDay, measurer, tip, palette)
            draw.value >= 1f -> {
                val last = Offset(frame.x(held.lastIndex), frame.y(held.last()))
                drawCircle(palette.magenta.copy(alpha = 0.35f), 9.dp.toPx(), last)
                drawCircle(palette.magenta, 4.dp.toPx(), last)
            }
        }
    }
}

@Composable
internal fun MemoryLegend() {
    val palette = LocalNeon.current
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(22.dp, 8.dp)) {
                drawLine(Brush.horizontalGradient(listOf(palette.cyan, palette.magenta)), Offset(0f, center.y), Offset(size.width, center.y), 3.dp.toPx(), StrokeCap.Round)
            }
            Muted("в памяти")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(22.dp, 8.dp)) {
                drawLine(palette.muted, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            }
            Muted("встречено")
        }
    }
}

private fun fit(start: Float, width: Float, from: Float, to: Float): Float =
    if (to - from <= width) from else start.coerceIn(from, to - width)

private fun niceTop(value: Int): Int {
    if (value <= 10) return 10
    var magnitude = 1
    while (magnitude * 10 <= value) magnitude *= 10
    return listOf(1, 2, 5, 10).map { it * magnitude }.first { it >= value }
}

private fun DrawScope.grid(frame: ChartFrame, measurer: TextMeasurer, style: TextStyle) {
    listOf(0, frame.top / 2, frame.top).forEach { value ->
        val y = frame.y(value)
        drawLine(style.color.copy(alpha = 0.25f), Offset(frame.left, y), Offset(frame.right, y), 1f)
        val label = measurer.measure(value.toString(), style)
        drawText(label, topLeft = Offset(frame.left - label.size.width - 6.dp.toPx(), y - label.size.height / 2))
    }
}

private fun DrawScope.dates(frame: ChartFrame, last: Int, measurer: TextMeasurer, style: TextStyle, firstDay: Int) {
    listOf(0, last / 2, last).distinct().forEach { index ->
        val text = if (index == last) "сегодня" else shortDate(firstDay + index)
        val label = measurer.measure(text, style)
        val x = fit(frame.x(index) - label.size.width / 2, label.size.width.toFloat(), frame.left, frame.right)
        drawText(label, topLeft = Offset(x, frame.lower + 6.dp.toPx()))
    }
}

private fun DrawScope.lines(frame: ChartFrame, held: List<Int>, met: List<Int>, palette: NeonPalette) {
    fun path(values: List<Int>) = Path().apply {
        values.forEachIndexed { i, v -> if (i == 0) moveTo(frame.x(i), frame.y(v)) else lineTo(frame.x(i), frame.y(v)) }
    }
    val heldLine = path(held)
    val area = Path().apply {
        addPath(heldLine)
        lineTo(frame.x(held.lastIndex), frame.lower)
        lineTo(frame.x(0), frame.lower)
        close()
    }
    drawPath(area, Brush.verticalGradient(listOf(palette.cyan.copy(alpha = 0.3f), Color.Transparent), frame.upper, frame.lower))
    drawPath(path(met), palette.muted, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
    val ink = Brush.horizontalGradient(listOf(palette.cyan, palette.magenta), frame.left, frame.right)
    drawPath(heldLine, ink, alpha = palette.glow * 0.4f, style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(heldLine, ink, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.pick(
    frame: ChartFrame,
    index: Int,
    held: List<Int>,
    met: List<Int>,
    firstDay: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    palette: NeonPalette,
) {
    val x = frame.x(index)
    drawLine(palette.text.copy(alpha = 0.35f), Offset(x, frame.upper), Offset(x, frame.lower), 1.dp.toPx())
    drawCircle(palette.muted, 4.dp.toPx(), Offset(x, frame.y(met[index])))
    drawCircle(palette.magenta, 5.dp.toPx(), Offset(x, frame.y(held[index])))
    val share = if (met[index] == 0) 0 else held[index] * 100 / met[index]
    val text = measurer.measure("${shortDate(firstDay + index)}\nв памяти ${held[index]}\nвстречено ${met[index]} · $share%", style)
    val pad = 8.dp.toPx()
    val card = Size(text.size.width + pad * 2, text.size.height + pad * 2)
    val left = fit(x - card.width / 2, card.width, frame.left, frame.right)
    drawRoundRect(palette.panelHigh, Offset(left, frame.upper), card, CornerRadius(6.dp.toPx()))
    drawRoundRect(palette.line, Offset(left, frame.upper), card, CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
    drawText(text, topLeft = Offset(left + pad, frame.upper + pad))
}
