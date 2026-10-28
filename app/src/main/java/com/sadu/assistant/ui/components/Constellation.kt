package com.sadu.assistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import com.sadu.assistant.AiState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * "The Emergent Mind" - a real three-dimensional particle constellation, drawn with
 * Canvas and sorted by a perspective divide.
 *
 * It is not a picture and not a loop of frames: every node and every line is projected
 * from 3D on each frame, so orbiting it with a finger, the parallax and the way lines
 * form and dissolve all come out of the geometry. The cloud is deliberately uneven - a
 * dense core, sparse filaments and a few metal-bright nodes - so it reads as something
 * organising itself rather than as a sphere or a ball of dots.
 *
 * Cost is bounded on purpose: the node and edge counts are fixed, neighbour pairs are
 * found once (the cloud only rotates, so what is close to what never changes), and every
 * per-frame array is allocated in [remember], never inside the draw block.
 */
@Composable
fun Constellation(
    state: AiState,
    level: Float,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    nodeCount: Int = 460,
) {
    val cloud = remember(nodeCount) { ConstellationCloud(nodeCount) }
    val current = rememberUpdatedState(state)
    val sound = rememberUpdatedState(level)

    val clock = remember { mutableFloatStateOf(0f) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var tapWave by remember { mutableStateOf<TapWave?>(null) }

    // The clock is read only inside the draw block, so a new frame invalidates the draw
    // phase alone - the screen around it is not recomposed sixty times a second.
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> clock.floatValue = (now - start) / 1_000_000_000f }
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(nodeCount) {
                detectDragGestures { change, drag ->
                    change.consume()
                    yaw += drag.x * 0.006f
                    pitch = (pitch + drag.y * 0.004f).coerceIn(-0.5f, 0.5f)
                }
            }
            .pointerInput(nodeCount) {
                detectTapGestures { at ->
                    tapWave = cloud.waveNearest(at, clock.floatValue)
                }
            },
    ) {
        val t = if (reducedMotion) 0f else clock.floatValue
        val ai = current.value
        val mic = sound.value.coerceIn(0f, 1f)

        val radius = min(size.width, size.height) * 0.5f
        if (radius <= 1f) return@Canvas
        val focal = radius * 2.8f
        val cx = size.width / 2f
        val cy = size.height * 0.44f

        // Thinking speeds the drift up; listening breathes with the microphone.
        val drift = when (ai) {
            AiState.Processing -> 2.3f
            AiState.Listening -> 1.12f
            AiState.Speaking -> 0.82f
            else -> 1f
        }
        val spin = t * 0.05f * drift + yaw
        val tilt = sin(t * 0.085f) * 0.10f + pitch
        val breath = 1f + sin(t * 0.6f) * 0.008f +
            (if (ai == AiState.Listening) mic * 0.09f else 0f)

        cloud.project(cx, cy, radius * breath, focal, spin, tilt)

        // Two waves travel through the network: one on a slow cycle, one from the last tap.
        val cycle = (t + cloud.phase) % 13f
        val auto = if (cycle < 3.2f) cycle / 3.2f else -1f
        val manual = tapWave?.progress(t) ?: -1f
        cloud.light(t, auto, tapWave, manual)

        cloud.paint(this, ai)
    }
}

/** One expanding shell of light, started by a tap on a node. */
private class TapWave(val index: Int, private val started: Float) {
    fun progress(now: Float): Float {
        val p = (now - started) / 2.4f
        return if (p < 0f || p > 1f) -1f else p
    }
}

/**
 * The point cloud. Positions live in a unit-ish sphere; [project] turns them into screen
 * coordinates, [light] works out which of them a travelling wave is touching, and [paint]
 * draws the edges and then the nodes.
 */
private class ConstellationCloud(val count: Int) {

    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val z = FloatArray(count)
    private val nodeSize = FloatArray(count)
    private val metal = FloatArray(count)

    /** Screen-space scratch, reused every frame. */
    private val sx = FloatArray(count)
    private val sy = FloatArray(count)
    private val depth = FloatArray(count)
    private val visible = BooleanArray(count)
    private val glow = FloatArray(count)

    private val edges = ArrayList<IntArray>(count * 2)
    val phase = Random(count).nextFloat() * 13f

    init {
        val rng = Random(7 * count + 13)
        // An organic cluster: a dense middle, filaments outward, and a spiral bias so the
        // silhouette never reads as a plain ball.
        for (i in 0 until count) {
            val u = rng.nextFloat()
            val r = when {
                u < 0.34f -> 0.30f * sqrt(rng.nextFloat())
                u < 0.82f -> 0.30f + 0.46f * sqrt(rng.nextFloat())
                else -> 0.76f + 0.24f * sqrt(rng.nextFloat())
            }
            val theta = rng.nextFloat() * TWO_PI
            val cosPhi = 2f * rng.nextFloat() - 1f
            val sinPhi = sqrt(max(0f, 1f - cosPhi * cosPhi))
            val arm = 1f + 0.22f * sin(theta * 3f + r * 6f)
            x[i] = r * sinPhi * cos(theta) * arm
            y[i] = r * cosPhi * 0.78f
            z[i] = r * sinPhi * sin(theta) * arm
            nodeSize[i] = 0.55f + rng.nextFloat() * 1.15f
            metal[i] = if (rng.nextFloat() < 0.05f) 1f else 0f
        }

        // Each node keeps at most two neighbours, found once: the cloud only rotates, so
        // what is close to what never changes after this.
        val limit = 0.34f
        val taken = HashSet<Long>(count * 3)
        for (i in 0 until count) {
            var bestA = -1
            var bestB = -1
            var distA = Float.MAX_VALUE
            var distB = Float.MAX_VALUE
            for (j in 0 until count) {
                if (i == j) continue
                val dx = x[i] - x[j]
                val dy = y[i] - y[j]
                val dz = z[i] - z[j]
                val d = dx * dx + dy * dy + dz * dz
                if (d > limit * limit) continue
                if (d < distA) {
                    distB = distA
                    bestB = bestA
                    distA = d
                    bestA = j
                } else if (d < distB) {
                    distB = d
                    bestB = j
                }
            }
            var added = 0
            for (n in intArrayOf(bestA, bestB)) {
                if (n < 0 || added >= 2) continue
                val lo = min(i, n)
                val hi = max(i, n)
                val id = lo.toLong() * count + hi
                if (taken.add(id)) {
                    edges += intArrayOf(lo, hi)
                    added++
                }
            }
        }
    }

    /** A wave that starts where the user put their finger. */
    fun waveNearest(at: Offset, now: Float): TapWave {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in 0 until count) {
            if (!visible[i]) continue
            val dx = sx[i] - at.x
            val dy = sy[i] - at.y
            val d = dx * dx + dy * dy
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return TapWave(best, now)
    }

    fun project(cx: Float, cy: Float, radius: Float, focal: Float, yaw: Float, tilt: Float) {
        val cosY = cos(yaw)
        val sinY = sin(yaw)
        val cosT = cos(tilt)
        val sinT = sin(tilt)
        for (i in 0 until count) {
            val rx = x[i] * cosY + z[i] * sinY
            val rz = -x[i] * sinY + z[i] * cosY
            val ry = y[i] * cosT - rz * sinT
            val rz2 = y[i] * sinT + rz * cosT
            // A small roll with depth keeps the silhouette from being axis-aligned.
            val roll = rz2 * 0.06f
            val scale = focal / (focal + rz2 * radius)
            sx[i] = cx + (rx + roll) * radius * scale
            sy[i] = cy + ry * radius * scale
            depth[i] = scale
            visible[i] = scale > 0.25f
        }
    }

    /** Works out how strongly each node is lit by the waves, once per frame. */
    fun light(t: Float, auto: Float, tap: TapWave?, manual: Float) {
        java.util.Arrays.fill(glow, 0f)
        if (auto >= 0f) {
            waveInto(
                auto,
                0.5f * cos(t * 0.13f),
                0.32f * sin(t * 0.09f),
                0.5f * sin(t * 0.13f),
            )
        }
        if (manual >= 0f && tap != null) {
            val i = tap.index
            waveInto(manual, x[i], y[i], z[i])
        }
    }

    private fun waveInto(p: Float, ox: Float, oy: Float, oz: Float) {
        val reach = p * 1.5f
        val band = 0.16f + p * 0.22f
        val fade = (1f - p) * (1f - p)
        for (i in 0 until count) {
            val dx = x[i] - ox
            val dy = y[i] - oy
            val dz = z[i] - oz
            val d = sqrt(dx * dx + dy * dy + dz * dz)
            val k = 1f - abs(d - reach) / band
            if (k > 0f) glow[i] = max(glow[i], k * fade)
        }
    }

    fun paint(scope: DrawScope, state: AiState) {
        val lit = when (state) {
            AiState.Processing -> 0.22f
            AiState.Listening -> 0.13f
            AiState.Speaking -> 0.10f
            else -> 0.05f
        }

        // ---- edges: delicate, and the far ones fade out first
        for (e in edges) {
            val a = e[0]
            val b = e[1]
            if (!visible[a] || !visible[b]) continue
            val d = (depth[a] + depth[b]) * 0.5f
            var alpha = ((d - 0.55f) / 0.45f).coerceIn(0f, 1f) * 0.15f + lit * 0.30f
            alpha += (glow[a] + glow[b]) * 0.22f
            if (alpha <= 0.012f) continue
            scope.drawLine(
                color = Line.copy(alpha = alpha.coerceIn(0f, 0.75f)),
                start = Offset(sx[a], sy[a]),
                end = Offset(sx[b], sy[b]),
                strokeWidth = 0.6f + d * 0.7f,
            )
        }

        // ---- nodes: small, silver, brighter in front
        for (i in 0 until count) {
            if (!visible[i]) continue
            val d = depth[i]
            val base = ((d - 0.66f) / 0.34f).coerceIn(0.03f, 1f)
            var alpha = base * 0.5f + lit * 0.2f
            var r = nodeSize[i] * max(0.35f, d) * 0.95f
            if (metal[i] > 0f) {
                alpha = (alpha * 1.5f).coerceAtMost(1f)
                r *= 1.35f
            }
            if (glow[i] > 0f) {
                alpha += glow[i] * 0.7f
                r += glow[i] * 1.6f
            }
            val colour = if (metal[i] > 0f) Metal else Spark
            // A soft bloom on the brightest, so they look lit rather than painted.
            if (alpha > 0.5f) {
                scope.drawCircle(
                    color = colour.copy(alpha = (alpha - 0.5f) * 0.13f),
                    radius = r * 3.1f,
                    center = Offset(sx[i], sy[i]),
                )
            }
            scope.drawCircle(
                color = colour.copy(alpha = alpha.coerceIn(0.04f, 1f)),
                radius = r.coerceIn(0.5f, 2.9f),
                center = Offset(sx[i], sy[i]),
            )
        }
    }
}

private const val TWO_PI = 6.2831855f
private val Line = Color(0xFF9AA0A6)
private val Spark = Color(0xFFF5F5F2)
private val Metal = Color(0xFFE2E6EA)
