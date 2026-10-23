package com.jatta.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/** The colours of one theme. [glow] is how strongly big numbers glow: strong on black, faint on white. */
@Immutable
data class NeonPalette(
    val void: Color,
    val panel: Color,
    val panelHigh: Color,
    val line: Color,
    val cyan: Color,
    val magenta: Color,
    val acid: Color,
    val danger: Color,
    val text: Color,
    val muted: Color,
    val gridAlpha: Float,
    val glow: Float,
    val isDark: Boolean,
)

/** Neon on near-black. */
val DarkNeon = NeonPalette(
    void = Color(0xFF07070D),
    panel = Color(0xFF0E0E19),
    panelHigh = Color(0xFF161627),
    line = Color(0xFF26264A),
    cyan = Color(0xFF00E5FF),
    magenta = Color(0xFFFF2E88),
    acid = Color(0xFFF5E500),
    danger = Color(0xFFFF3B5C),
    text = Color(0xFFE6E6F0),
    muted = Color(0xFF9A9AB8),
    gridAlpha = 0.045f,
    glow = 0.7f,
    isDark = true,
)

/**
 * The same look on cool white. Pure neon is unreadable on white, so the accents are deeper shades
 * of the same hues, dark enough for text.
 */
val LightNeon = NeonPalette(
    void = Color(0xFFF2F4F8),
    panel = Color(0xFFFFFFFF),
    panelHigh = Color(0xFFE3E6EF),
    line = Color(0xFFC9CDDD),
    cyan = Color(0xFF007C99),
    magenta = Color(0xFFC8005F),
    acid = Color(0xFF8A6D00),
    danger = Color(0xFFC81E3A),
    text = Color(0xFF12121C),
    muted = Color(0xFF555A75),
    gridAlpha = 0.07f,
    glow = 0.22f,
    isDark = false,
)

val LocalNeon = staticCompositionLocalOf { DarkNeon }

/** The current theme's colours, so screens can write `Neon.Cyan` in either theme. */
object Neon {
    val Void: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.void
    val Panel: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.panel
    val PanelHigh: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.panelHigh
    val Line: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.line
    val Cyan: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.cyan
    val Magenta: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.magenta
    val Acid: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.acid
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.danger
    val Text: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalNeon.current.muted
}

private fun colorScheme(p: NeonPalette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    val onAccent = if (p.isDark) Color(0xFF00141A) else Color.White
    return base.copy(
        primary = p.cyan,
        onPrimary = onAccent,
        primaryContainer = p.panelHigh,
        onPrimaryContainer = p.cyan,
        secondary = p.magenta,
        onSecondary = onAccent,
        secondaryContainer = p.panelHigh,
        onSecondaryContainer = p.text,
        tertiary = p.acid,
        onTertiary = onAccent,
        error = p.danger,
        onError = onAccent,
        background = p.void,
        onBackground = p.text,
        surface = p.void,
        onSurface = p.text,
        surfaceVariant = p.panelHigh,
        onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.void,
        surfaceContainerLow = p.panel,
        surfaceContainer = p.panel,
        surfaceContainerHigh = p.panelHigh,
        surfaceContainerHighest = p.panelHigh,
        outline = p.cyan.copy(alpha = 0.6f),
        outlineVariant = p.line,
    )
}

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

/** The app's theme, dark or light, with a faint grid behind every screen. */
@Composable
fun JattaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkNeon else LightNeon
    MaterialTheme(colorScheme = colorScheme(palette), typography = typography(), shapes = shapes) {
        CompositionLocalProvider(LocalNeon provides palette, LocalContentColor provides palette.text) {
            Box(Modifier.fillMaxSize().background(palette.void).drawBehind { drawGrid(palette.cyan.copy(alpha = palette.gridAlpha)) }) {
                content()
            }
        }
    }
}

private fun DrawScope.drawGrid(line: Color) {
    val step = 28.dp.toPx()
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
    val track = Neon.PanelHigh
    val colors = if (dim) listOf(Neon.Muted, Neon.Muted) else listOf(Neon.Cyan, Neon.Magenta)
    Canvas(modifier.fillMaxWidth().height(height + 6.dp)) {
        val top = 3.dp.toPx()
        val h = height.toPx()
        val gradient = Brush.horizontalGradient(colors, startX = 0f, endX = size.width)
        if (segments <= 1) {
            drawPath(chamfered(0f, top, size.width, h), track)
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
                drawRect(track, Offset(x, top), Size(segment, h))
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
    val glow = LocalNeon.current.glow
    Text(text, style = style.copy(shadow = Shadow(color.copy(alpha = glow), blurRadius = 18f)), color = color)
}
