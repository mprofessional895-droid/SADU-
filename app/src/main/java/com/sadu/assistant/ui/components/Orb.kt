package com.sadu.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sadu.assistant.AiState
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = 6.2831855f

/**
 * The assistant. Concentric hairlines around a small lit sphere.
 *
 * Idle breathes slowly, listening draws a ring of bars driven by the real
 * microphone level, processing sweeps a single arc, speaking pushes out quiet
 * ripples, and an error shows a broken ring with no motion at all.
 *
 * Everything stops when the system "remove animations" setting is on.
 */
@Composable
fun SaduOrb(
    state: AiState,
    level: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 216.dp,
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "orb")

    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val slowSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing)),
        label = "slowSpin",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "sweep",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)),
        label = "ripple",
    )
    // Reused as the bar-ring drift while listening.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "drift",
    )

    val safeLevel = level.coerceIn(0f, 1f)

    Canvas(modifier = modifier.size(diameter)) {
        drawOrb(
            state = state,
            level = safeLevel,
            spin = if (reduced) 0f else slowSpin,
            sweep = if (reduced) 0f else sweep,
            ripple = if (reduced) 0f else ripple,
            drift = if (reduced) 0f else drift,
            breathe = if (reduced) 1f else 0.986f + 0.014f * breath,
            cx = size.width / 2f,
            cy = size.height / 2f,
            radius = (minOf(size.width, size.height) / 2f) - 4f,
        )
    }
}

private fun DrawScope.drawOrb(
    state: AiState,
    level: Float,
    spin: Float,
    sweep: Float,
    ripple: Float,
    drift: Float,
    breathe: Float,
    cx: Float,
    cy: Float,
    radius: Float,
) {
    val r = radius * breathe
    val moving = state == AiState.Listening || state == AiState.Speaking || state == AiState.Processing
    val centre = Offset(cx, cy)

    drawCircle(color = Ink.Glow.copy(alpha = 0.05f), radius = r, center = centre)

    rotate(degrees = spin * 57.29578f, pivot = centre) {
        drawCircle(
            color = Ink.Border.copy(alpha = if (moving) 1f else 0.85f),
            radius = r,
            center = centre,
            style = Stroke(
                width = 1.2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f, 11f)),
            ),
        )
    }

    val ringR = r * 0.86f
    drawCircle(
        color = Ink.Text.copy(alpha = if (moving) 0.30f else 0.18f),
        radius = ringR,
        center = centre,
        style = Stroke(width = 1f),
    )

    if (state == AiState.Processing) {
        rotate(degrees = sweep * 57.29578f, pivot = centre) {
            drawArc(
                color = Ink.Text.copy(alpha = 0.92f),
                startAngle = -90f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(cx - ringR, cy - ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(width = 1.6f, cap = StrokeCap.Round),
            )
        }
    }

    if (state == AiState.Error) {
        drawArc(
            color = Ink.TextDim.copy(alpha = 0.85f),
            startAngle = 30f,
            sweepAngle = 296f,
            useCenter = false,
            topLeft = Offset(cx - ringR, cy - ringR),
            size = Size(ringR * 2f, ringR * 2f),
            style = Stroke(width = 1.2f, cap = StrokeCap.Round),
        )
    }

    if (state == AiState.Speaking) {
        for (k in 0..1) {
            val p = (ripple + k * 0.5f) % 1f
            drawCircle(
                color = Ink.Glow.copy(alpha = 0.34f * (1f - p)),
                radius = r * (0.60f + 0.36f * p),
                center = centre,
                style = Stroke(width = 1.2f),
            )
        }
    }

    val coreR = r * 0.50f
    val coreAlpha = when {
        state == AiState.Error -> 0.45f
        state == AiState.Processing -> 1f
        else -> 0.88f
    }
    // Off-centre highlight instead of a glow: gives the sphere depth, stays monochrome.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Ink.Accent.copy(alpha = coreAlpha),
                Color(0xFF2C2C2C).copy(alpha = coreAlpha),
                Color(0xFF090909),
            ),
            center = Offset(cx - coreR * 0.34f, cy - coreR * 0.42f),
            radius = coreR * 1.9f,
        ),
        radius = coreR,
        center = centre,
    )
    drawCircle(
        color = Ink.Glow.copy(alpha = 0.34f),
        radius = coreR,
        center = centre,
        style = Stroke(width = 1f),
    )

    if (state == AiState.Listening) {
        val bars = 48
        val inner = r * 0.70f
        for (i in 0 until bars) {
            val t = (i.toFloat() / bars) * TAU
            // One scalar level, spread into a smooth standing shape - a level meter,
            // not a frequency spectrum.
            val shape = 0.40f + 0.60f * (0.5f + 0.5f * sin(t * 5f - drift * 2f))
            val amp = level * shape
            if (amp > 0.03f) {
                val len = r * (0.012f + 0.26f * amp)
                drawLine(
                    color = Ink.Text.copy(alpha = 0.32f + 0.58f * amp.coerceAtMost(1f)),
                    start = Offset(cx + cos(t) * inner, cy + sin(t) * inner),
                    end = Offset(cx + cos(t) * (inner + len), cy + sin(t) * (inner + len)),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(
            color = Ink.Glow.copy(alpha = 0.20f + 0.45f * level),
            radius = inner + r * 0.05f * level,
            center = centre,
            style = Stroke(width = 1.2f),
        )
    }
}
