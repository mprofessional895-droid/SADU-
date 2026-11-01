package com.sadu.assistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sadu.assistant.AiState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * "Thought Stream" - the quiet particle field behind a conversation.
 *
 * It is deliberately faint: this sits under text, so it may never compete with a message.
 * It carries state instead of decorating - while Sadu is thinking the dust gathers toward
 * a point that drifts, while a reply is arriving it streams upward like a current, and a
 * message being sent throws a short burst out of the composer.
 */
@Composable
fun ThoughtField(
    state: AiState,
    streaming: Boolean,
    burstToken: Int,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    particleCount: Int = 110,
) {
    val clock = remember { mutableFloatStateOf(0f) }
    var burstAt by remember { mutableFloatStateOf(-1f) }
    val ai = rememberUpdatedState(state)
    val live = rememberUpdatedState(streaming)

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
        }
    }

    // Token, not a callback: a new conversation or a re-composition cannot re-trigger it.
    LaunchedEffect(burstToken) {
        if (burstToken > 0) burstAt = clock.floatValue
    }

    val rng = remember(particleCount) { Random(particleCount * 31 + 7) }
    val px = remember(particleCount) { FloatArray(particleCount) { rng.nextFloat() } }
    val py = remember(particleCount) { FloatArray(particleCount) { rng.nextFloat() } }
    val sp = remember(particleCount) { FloatArray(particleCount) { 0.02f + rng.nextFloat() * 0.06f } }
    val sz = remember(particleCount) { FloatArray(particleCount) { 0.6f + rng.nextFloat() * 1.6f } }
    val ph = remember(particleCount) { FloatArray(particleCount) { rng.nextFloat() * 6.283f } }
    val bright = remember(particleCount) { BooleanArray(particleCount) { rng.nextFloat() < 0.16f } }

    Canvas(modifier) {
        val t = if (reducedMotion) 0f else clock.floatValue
        val s = ai.value
        val thinking = s == AiState.Processing && !live.value
        val flowing = live.value || s == AiState.Speaking
        if (size.height <= 1f) return@Canvas

        // Where thought gathers: a point that wanders slowly through the upper half.
        val focusX = size.width * (0.5f + 0.22f * cos(t * 0.07f))
        val focusY = size.height * (0.38f + 0.10f * sin(t * 0.11f))

        for (i in 0 until particleCount) {
            val rise = sp[i] * (if (flowing) 3.1f else 1f)
            var y = (py[i] - t * rise).mod(1f + 0.16f)
            var x = px[i] + sin(t * 0.25f + ph[i]) * 0.012f
            var alpha = if (bright[i]) 0.34f else 0.14f
            var radius = sz[i]

            if (thinking) {
                // Gathering: each particle is pulled a little way towards the focus.
                val pull = 0.30f + 0.10f * sin(t * 0.6f + ph[i])
                x += ((focusX / size.width) - x) * pull
                y += ((focusY / size.height) - y) * pull
                alpha += 0.20f * pull
                radius += 0.35f * pull
            } else if (flowing) {
                alpha += 0.12f
            }

            val sx = x * size.width
            val sy = y * size.height
            if (sx < -4f || sx > size.width + 4f || sy < -4f || sy > size.height + 4f) continue
            drawCircle(
                color = Dot.copy(alpha = alpha.coerceIn(0.05f, 0.72f)),
                radius = radius.coerceIn(0.4f, 2.6f),
                center = Offset(sx, sy),
            )
        }

        // The send burst: a rim of sparks thrown out of the composer, gone in a moment.
        val since = if (burstAt < 0f) -1f else (t - burstAt) / 0.9f
        if (since in 0f..1f) {
            val fade = (1f - since) * (1f - since)
            val originX = size.width * 0.86f
            val originY = size.height - 34.dp.toPx()
            val reach = 26.dp.toPx() + since * 150.dp.toPx()
            for (i in 0 until 12) {
                val a = -1.6f + i * 0.26f + sin(t + i) * 0.05f
                val r = reach * (0.65f + 0.35f * ((i % 3) / 2f))
                drawCircle(
                    color = Dot.copy(alpha = 0.8f * fade),
                    radius = 1.4f,
                    center = Offset(originX + cos(a) * r, originY + sin(a) * r),
                )
            }
        }

        // A faint thread from the composer up into the field while a reply arrives.
        val thread = when {
            live.value -> 0.22f
            thinking -> 0.14f
            else -> 0f
        }
        if (thread > 0f) {
            val x = size.width * 0.86f
            drawLine(
                color = Dot.copy(alpha = thread * 1.6f),
                start = Offset(x, size.height),
                end = Offset(
                    focusX + (x - focusX) * 0.35f,
                    if (thinking) focusY else size.height * 0.25f,
                ),
                strokeWidth = 1f,
            )
        }
    }
}

private val Dot = Color(0xFFF2F6FF)
