package com.jatta.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jatta.shared.resources.Res
import com.jatta.shared.resources.exo2
import com.jatta.shared.resources.jetbrains_mono
import org.jetbrains.compose.resources.Font

/** Neon on near-black. One look for the app, whatever the system theme. */
object Neon {
    val Void = Color(0xFF07070D)
    val Panel = Color(0xFF0E0E19)
    val PanelHigh = Color(0xFF161627)
    val Line = Color(0xFF26264A)
    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2E88)
    val Acid = Color(0xFFF5E500)
    val Danger = Color(0xFFFF3B5C)
    val Text = Color(0xFFE6E6F0)
    val Muted = Color(0xFF9A9AB8)
}

private val colors = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Color(0xFF001418),
    primaryContainer = Color(0xFF00313A),
    onPrimaryContainer = Neon.Cyan,
    secondary = Neon.Magenta,
    onSecondary = Color(0xFF1A0010),
    secondaryContainer = Color(0xFF241030),
    onSecondaryContainer = Color(0xFFFFC7E4),
    tertiary = Neon.Acid,
    onTertiary = Color(0xFF141200),
    error = Neon.Danger,
    onError = Color(0xFF1A0006),
    background = Neon.Void,
    onBackground = Neon.Text,
    surface = Neon.Void,
    onSurface = Neon.Text,
    surfaceVariant = Neon.PanelHigh,
    onSurfaceVariant = Neon.Muted,
    surfaceContainerLowest = Neon.Void,
    surfaceContainerLow = Neon.Panel,
    surfaceContainer = Neon.Panel,
    surfaceContainerHigh = Neon.PanelHigh,
    surfaceContainerHighest = Neon.PanelHigh,
    outline = Color(0xFF1F7A8C),
    outlineVariant = Neon.Line,
)

/** Chamfered corners, the cyberpunk panel shape: two opposite corners cut. */
val ChamferSmall = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
val ChamferLarge = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)

private val shapes = Shapes(
    extraSmall = ChamferSmall,
    small = ChamferSmall,
    medium = ChamferLarge,
    large = ChamferLarge,
    extraLarge = ChamferLarge,
)

/** Exo 2 for reading and headings, JetBrains Mono for numbers and labels; both have Cyrillic. */
@Composable
private fun typography(): Typography {
    val exo = FontFamily(
        Font(Res.font.exo2, FontWeight.Normal),
        Font(Res.font.exo2, FontWeight.Medium),
        Font(Res.font.exo2, FontWeight.SemiBold),
        Font(Res.font.exo2, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.jetbrains_mono, FontWeight.Normal),
        Font(Res.font.jetbrains_mono, FontWeight.Medium),
        Font(Res.font.jetbrains_mono, FontWeight.Bold),
    )
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = mono, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = mono, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = mono, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = exo, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = exo, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = exo, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = exo, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = exo, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = exo, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = exo),
        bodyMedium = base.bodyMedium.copy(fontFamily = exo),
        bodySmall = base.bodySmall.copy(fontFamily = exo),
        labelLarge = base.labelLarge.copy(fontFamily = mono, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
        labelMedium = base.labelMedium.copy(fontFamily = mono, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp),
        labelSmall = base.labelSmall.copy(fontFamily = mono, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp),
    )
}

/** The app's theme with a faint grid behind every screen. */
@Composable
fun JattaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography(), shapes = shapes) {
        CompositionLocalProvider(LocalContentColor provides Neon.Text) {
            Box(Modifier.fillMaxSize().background(Neon.Void).drawBehind { drawGrid() }) {
                content()
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid() {
    val step = 28.dp.toPx()
    val line = Neon.Cyan.copy(alpha = 0.045f)
    var x = 0f
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height))
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y))
        y += step
    }
}

@Composable
fun NeonButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    Button(onClick, modifier, enabled = enabled, shape = ChamferSmall, content = content)

@Composable
fun NeonOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    OutlinedButton(
        onClick,
        modifier,
        shape = ChamferSmall,
        border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon.Text),
        content = content,
    )

/** A panel with a chamfered neon edge; [accent] colours the edge. */
@Composable
fun Panel(modifier: Modifier = Modifier, accent: Color = Neon.Line, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(ChamferLarge)
            .background(Neon.Panel)
            .border(1.dp, accent, ChamferLarge)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A small uppercase monospace label: "ПАМЯТЬ", "ШАГ 4/24". */
@Composable
fun Label(text: String, color: Color = Neon.Muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

/**
 * Progress as a neon bar. Segmented for course progress, so each block reads as a piece of the course;
 * continuous for the session. The fill animates and glows from cyan to magenta.
 */
@Composable
fun NeonBar(fraction: Float, modifier: Modifier = Modifier, segments: Int = 0, height: Dp = 10.dp, dim: Boolean = false) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f))
    val fill = if (dim) Brush.horizontalGradient(listOf(Neon.Muted, Neon.Muted)) else null
    Canvas(modifier.fillMaxWidth().height(height + 6.dp)) {
        val top = 3.dp.toPx()
        val h = height.toPx()
        val gradient = fill ?: Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Magenta), startX = 0f, endX = size.width)
        if (segments <= 1) {
            drawPath(chamfered(0f, top, size.width, h), Neon.PanelHigh)
            val width = size.width * animated
            if (width > 0f) {
                drawRect(gradient, Offset(0f, top - 3.dp.toPx()), Size(width, h + 6.dp.toPx()), alpha = 0.18f)
                drawPath(chamfered(0f, top, width, h), gradient)
            }
        } else {
            val gap = 3.dp.toPx()
            val segment = (size.width - gap * (segments - 1)) / segments
            val filled = animated * segments
            for (i in 0 until segments) {
                val x = i * (segment + gap)
                drawRect(Neon.PanelHigh, Offset(x, top), Size(segment, h))
                val part = (filled - i).coerceIn(0f, 1f)
                if (part > 0f) {
                    if (!dim) drawRect(gradient, Offset(x, top - 2.dp.toPx()), Size(segment * part, h + 4.dp.toPx()), alpha = 0.2f)
                    drawRect(gradient, Offset(x, top), Size(segment * part, h))
                }
            }
        }
    }
}

private fun chamfered(x: Float, y: Float, width: Float, height: Float): Path {
    val cut = minOf(height / 2, width / 2)
    return Path().apply {
        moveTo(x + cut, y)
        lineTo(x + width, y)
        lineTo(x + width, y + height - cut)
        lineTo(x + width - cut, y + height)
        lineTo(x, y + height)
        lineTo(x, y + cut)
        close()
    }
}

/** Big glowing number in the monospace face. */
@Composable
fun NeonNumber(text: String, color: Color = Neon.Cyan, style: TextStyle = MaterialTheme.typography.displaySmall) {
    Text(text, style = style.copy(shadow = androidx.compose.ui.graphics.Shadow(color.copy(alpha = 0.7f), blurRadius = 18f)), color = color)
}
