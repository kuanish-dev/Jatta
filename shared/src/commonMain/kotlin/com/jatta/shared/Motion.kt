package com.jatta.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val APPEAR_MS = 380
private const val STAGGER_MS = 45L
private const val MAX_STAGGER = 12

internal val LeftCenter = TransformOrigin(0f, 0.5f)

@Composable
internal fun Modifier.appear(index: Int = 0): Modifier {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(STAGGER_MS * index.coerceAtMost(MAX_STAGGER))
        shown.animateTo(1f, tween(APPEAR_MS, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 18.dp.toPx()
    }
}

@Composable
internal fun Modifier.pop(key: Any? = Unit, origin: TransformOrigin = TransformOrigin.Center): Modifier {
    val scale = remember(key) { Animatable(0.5f) }
    LaunchedEffect(key) { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = origin
        alpha = ((scale.value - 0.5f) * 2f).coerceIn(0f, 1f)
    }
}

@Composable
internal fun Modifier.shake(key: Any? = Unit, strength: Float = 1f): Modifier {
    val offset = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        offset.animateTo(
            0f,
            keyframes {
                durationMillis = 460
                -1f at 60
                1f at 130
                -0.75f at 200
                0.55f at 270
                -0.3f at 340
                0.1f at 400
            },
        )
    }
    return graphicsLayer { translationX = offset.value * strength * 9.dp.toPx() }
}

@Composable
internal fun Modifier.pulse(scaleBy: Float = 0f, fadeBy: Float = 0f, periodMs: Int = 1300): Modifier {
    val phase by rememberInfiniteTransition().animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )
    return graphicsLayer {
        scaleX = 1f + scaleBy * phase
        scaleY = 1f + scaleBy * phase
        alpha = 1f - fadeBy * phase
    }
}

@Composable
internal fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.95f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
internal fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return pressScale(interaction, pressed = 0.97f).clickable(interaction, LocalIndication.current, onClick = onClick)
}

@Composable
internal fun countUp(target: Int, durationMs: Int = 900): Int {
    val value = remember { Animatable(0f) }
    LaunchedEffect(target) { value.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing)) }
    return value.value.roundToInt()
}

@Composable
internal fun RollingText(text: String, style: TextStyle, color: Color = Color.Unspecified) {
    Row {
        text.forEach { char ->
            AnimatedContent(char, transitionSpec = { roll(up = targetState > initialState) }) {
                Text(it.toString(), style = style, color = color)
            }
        }
    }
}

private fun roll(up: Boolean): ContentTransform {
    val sign = if (up) 1 else -1
    return ContentTransform(
        slideInVertically { sign * it } + fadeIn(),
        slideOutVertically { -sign * it } + fadeOut(),
        sizeTransform = SizeTransform(clip = false),
    )
}
