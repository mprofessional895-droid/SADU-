package com.sadu.assistant.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.sadu.assistant.AiState
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Neural = Color(0xFF9EEBFF)
private val NeuralDeep = Color(0xFF39D8F0)

/**
 * The home screen's centre piece: a glass sphere holding a constellation brain, two slanted
 * orbit rings and a lit plinth. Everything is drawn, so it stays crisp at any density and
 * there is no bitmap to ship.
 *
 * It turns very slowly and pulses with the assistant's state; with reduced motion on it is
 * a still frame.
 */
@Composable
fun NeuralSphere(
    state: AiState,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val turn by if (!reduced) {
        rememberInfiniteTransition(label = "sphere").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(96_000, easing = LinearEasing)),
            label = "turn",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }
    val pulse by if (!reduced) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
            label = "pulse",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(0.88f) }
    }

    val nodes = remember { brainNodes() }

    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.42f
        val r = minOf(size.width * 0.34f, size.height * 0.34f)
        val centre = Offset(cx, cy)

        beams(cx, cy, r, pulse)
        halo(centre, r, pulse, state)
        glass(centre, r)
        orbitRings(centre, r, turn)
        brain(centre, r, nodes, turn, pulse)
        plinth(centre, r, pulse)
    }
}

private fun DrawScope.halo(centre: Offset, r: Float, pulse: Float, state: AiState) {
    val strength = when (state) {
        AiState.Listening, AiState.Processing, AiState.Speaking -> 0.34f
        AiState.Error -> 0.10f
        else -> 0.22f
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                NeuralDeep.copy(alpha = strength * pulse),
                NeuralDeep.copy(alpha = strength * 0.35f * pulse),
                Color.Transparent,
            ),
            center = centre,
            radius = r * 1.75f,
        ),
        radius = r * 1.75f,
        center = centre,
    )
}

private fun DrawScope.glass(centre: Offset, r: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.03f),
                Color.Transparent,
            ),
            center = Offset(centre.x - r * 0.32f, centre.y - r * 0.38f),
            radius = r * 1.35f,
        ),
        radius = r,
        center = centre,
    )
    drawCircle(Color.White.copy(alpha = 0.30f), radius = r, center = centre, style = Stroke(1.1f))
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.12f)),
            startY = centre.y,
            endY = centre.y + r,
        ),
        radius = r,
        center = centre,
        style = Stroke(2f),
    )
}

private fun DrawScope.beams(cx: Float, cy: Float, r: Float, pulse: Float) {
    val count = 9
    for (i in 0 until count) {
        val x = cx + (i - (count - 1) / 2f) * (size.width * 0.075f)
        val spread = 1f - (i - (count - 1) / 2f).let { kotlin.math.abs(it) } / (count / 2f + 1f)
        drawLine(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.10f * spread * pulse),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = cy - r * 0.2f,
            ),
            start = Offset(x, 0f),
            end = Offset(x, cy - r * 0.2f),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.orbitRings(centre: Offset, r: Float, turn: Float) {
    listOf(-18f to 1.24f, 16f to 1.10f).forEach { (angle, scale) ->
        rotate(angle, centre) {
            val w = r * 2f * scale
            val h = r * 0.52f * scale
            drawOval(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.26f), Color.Transparent),
                ),
                topLeft = Offset(centre.x - w / 2f, centre.y - h / 2f),
                size = Size(w, h),
                style = Stroke(1.2f),
            )
        }
    }
    // A dashed arc and a few specks keep the orbit from feeling mechanical.
    drawCircle(
        color = Color.White.copy(alpha = 0.16f),
        radius = r * 1.42f,
        center = centre,
        style = Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 14f), turn * 0.2f),
        ),
    )
    val seed = 1
    val rand = Random(seed)
    repeat(26) {
        val a = rand.nextFloat() * 2f * PI.toFloat()
        val dist = r * (1.05f + rand.nextFloat() * 0.85f)
        val p = Offset(centre.x + cos(a) * dist, centre.y + sin(a) * dist * 0.62f)
        drawCircle(Color.White.copy(alpha = 0.10f + rand.nextFloat() * 0.25f), 1.2f, p)
    }
}

private fun DrawScope.brain(
    centre: Offset,
    r: Float,
    nodes: List<Offset>,
    turn: Float,
    pulse: Float,
) {
    val rad = Math.toRadians(turn.toDouble())
    val scale = r * 0.80f

    fun place(p: Offset): Offset {
        val x = p.x * scale
        val y = p.y * scale
        return Offset(
            centre.x + (x * cos(rad) - y * sin(rad)).toFloat(),
            centre.y + (x * sin(rad) + y * cos(rad)).toFloat() * 0.92f,
        )
    }

    val placed = nodes.map(::place)

    placed.forEachIndexed { i, p ->
        for (j in i + 1 until placed.size) {
            val d = (placed[j] - p).getDistance()
            if (d < scale * 0.34f) {
                drawLine(
                    color = Neural.copy(alpha = 0.10f + 0.20f * (1f - d / (scale * 0.34f))),
                    start = p,
                    end = placed[j],
                    strokeWidth = 0.8f,
                )
            }
        }
    }
    placed.forEachIndexed { i, p ->
        val bright = 0.35f + (i % 5) * 0.13f
        drawCircle(Neural.copy(alpha = bright * pulse), 1.6f + (i % 3) * 0.5f, p)
    }

    // The star at the centre of the brain.
    val spark = Offset(centre.x, centre.y + scale * 0.02f)
    drawCircle(Color.White.copy(alpha = 0.95f), 2.4f, spark)
    drawCircle(Neural.copy(alpha = 0.55f * pulse), r * 0.16f, spark)
    listOf(0f, 90f).forEach { a ->
        rotate(a, spark) {
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(spark.x - r * 0.13f, spark.y),
                end = Offset(spark.x + r * 0.13f, spark.y),
                strokeWidth = 1f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.plinth(centre: Offset, r: Float, pulse: Float) {
    val base = centre.y + r * 1.02f
    listOf(1.05f to 0.16f, 0.82f to 0.24f, 0.6f to 0.14f).forEach { (scale, alpha) ->
        val w = r * 2f * scale
        val h = r * 0.26f * scale
        drawOval(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, NeuralDeep.copy(alpha = alpha * pulse), Color.Transparent),
            ),
            topLeft = Offset(centre.x - w / 2f, base - h / 2f),
            size = Size(w, h),
            style = Stroke(1.1f),
        )
    }
    drawLine(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Neural.copy(alpha = 0.22f * pulse), Color.Transparent),
            startY = base - r * 0.5f,
            endY = base + r * 0.9f,
        ),
        start = Offset(centre.x, base - r * 0.5f),
        end = Offset(centre.x, base + r * 0.9f),
        strokeWidth = 2f,
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Neural.copy(alpha = 0.30f * pulse), Color.Transparent),
            center = Offset(centre.x, base),
            radius = r * 0.55f,
        ),
        radius = r * 0.55f,
        center = Offset(centre.x, base),
    )
}

/**
 * A stable cloud of points inside an ellipse - broad at the back, tapering at the front -
 * so the constellation reads as a head from every angle while it turns.
 */
private fun brainNodes(): List<Offset> {
    val rand = Random(7)
    return buildList {
        repeat(64) {
            while (true) {
                val x = rand.nextFloat() * 2f - 1f
                val y = rand.nextFloat() * 2f - 1f
                val inside = (x * x) / (0.92f * 0.92f) + (y * y) / (0.62f * 0.62f)
                if (inside > 1f) continue
                add(Offset(x, y))
                break
            }
        }
    }
}
