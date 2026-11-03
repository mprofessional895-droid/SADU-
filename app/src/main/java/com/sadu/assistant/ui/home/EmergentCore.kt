package com.sadu.assistant.ui.home

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.sadu.assistant.ui.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (PI * 2).toFloat()

private val Ice = Color(0xFF8FD0E0)

/**
 * The hero art from the "Emergent Mind" design: a wireframe sphere inside three slanted
 * orbits, lit from below, with a slow aurora, drifting nebula, stars, rays and embers.
 *
 * Everything is drawn procedurally, so it stays sharp at any density and costs no bitmaps.
 * With reduced motion on, the scene is a still frame.
 */
@Composable
fun EmergentCore(
    accent: Color,
    modifier: Modifier = Modifier,
    glow: Float = 1f,
) {
    val reduced = rememberReducedMotion()
    val t by if (!reduced) {
        rememberInfiniteTransition(label = "core").animateFloat(
            initialValue = 0f,
            targetValue = 60f,
            animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
            label = "coreTime",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(9f) }
    }
    val stars = remember { buildStars() }
    val motes = remember { buildMotes() }
    val embers = remember { buildEmbers() }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val centre = Offset(w / 2f, h * 0.5f)
        val sphereR = w * 0.19f * 1.04f

        aurora(w, h, centre, accent, t)
        nebula(w, h, centre, accent, t)
        stars(stars, w, h, t)
        halo(centre, w, accent, glow, t)
        rays(centre, w, accent, t, glow)
        plinth(w, h, accent, t, glow)
        hudRings(centre, w, accent, t)
        orbits(centre, w, accent, t, glow)
        wireSphere(centre, sphereR, accent, t, glow)
        dust(motes, w, h, t)
        embers(embers, w, h, accent, t, glow)
    }
}

private class Star(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)
private class Mote(val x: Float, val y: Float, val speed: Float, val phase: Float)
private class Ember(val off: Float, val speed: Float, val phase: Float, val size: Float)

private fun buildStars(): List<Star> {
    val rnd = Random(7)
    return List(46) {
        Star(rnd.nextFloat(), rnd.nextFloat() * 0.9f, 0.7f + rnd.nextFloat() * 0.9f,
            rnd.nextFloat() * 1.4f + 0.3f, rnd.nextFloat() * 6.28f)
    }
}

private fun buildMotes(): List<Mote> {
    val rnd = Random(19)
    return List(26) {
        Mote(rnd.nextFloat(), rnd.nextFloat(), 0.004f + rnd.nextFloat() * 0.01f,
            rnd.nextFloat() * 6.28f)
    }
}

private fun buildEmbers(): List<Ember> {
    val rnd = Random(31)
    return List(14) {
        Ember((rnd.nextFloat() - 0.5f) * 0.8f, 0.06f + rnd.nextFloat() * 0.1f,
            rnd.nextFloat() * 6.28f, 0.8f + rnd.nextFloat() * 1.2f)
    }
}

/** A slow band of light sliding across the top half. */
private fun DrawScope.aurora(w: Float, h: Float, centre: Offset, accent: Color, t: Float) {
    val drift = sin(t * 0.18f) * w * 0.15f
    rotate(degrees = sin(t * 0.14f) * 22f) {
        val band = w * 1.4f
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    accent.copy(alpha = 0.05f),
                    accent.copy(alpha = 0.09f),
                    accent.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
                startX = centre.x + drift - band / 2f,
                endX = centre.x + drift + band / 2f,
            ),
            topLeft = Offset(centre.x - band / 2f, centre.y - h * 0.06f - w * 0.06f),
            size = Size(band, w * 0.12f),
        )
    }
}

private fun DrawScope.nebula(w: Float, h: Float, centre: Offset, accent: Color, t: Float) {
    val clouds = listOf(
        Triple(0.62f, 0.075f, 0.0f),
        Triple(0.45f, 0.045f, 2.1f),
        Triple(0.80f, 0.035f, 4.2f),
    )
    for ((scale, alpha, phase) in clouds) {
        val x = centre.x + sin(t * 0.13f + phase) * w * 0.3f
        val y = centre.y + cos(t * 0.11f + phase * 1.7f) * h * 0.16f
        val r = w * scale * 0.5f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = alpha * (0.75f + 0.25f * sin(t * 0.4f + phase))), Color.Transparent),
                center = Offset(x, y),
                radius = r,
            ),
            radius = r,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.stars(stars: List<Star>, w: Float, h: Float, t: Float) {
    for (s in stars) {
        val twinkle = 0.18f + 0.5f * (0.5f + 0.5f * sin(t * s.speed + s.phase))
        drawCircle(Ice.copy(alpha = twinkle), s.r, Offset(s.x * w, s.y * h))
    }
}

private fun DrawScope.halo(centre: Offset, w: Float, accent: Color, glow: Float, t: Float) {
    val breathe = 0.2f * glow + 0.03f * sin(t * 0.9f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = breathe), accent.copy(alpha = breathe * 0.25f), Color.Transparent),
            center = centre,
            radius = w * 0.46f,
        ),
        radius = w * 0.46f,
        center = centre,
    )
}

/** Soft wedges of light turning around the core. */
private fun DrawScope.rays(centre: Offset, w: Float, accent: Color, t: Float, glow: Float) {
    rotate(degrees = t * 6f, pivot = centre) {
        for (i in 0 until 5) {
            val a = i * (360f / 5f)
            rotate(degrees = a, pivot = centre) {
                val reach = w * 0.42f
                val path = Path().apply {
                    moveTo(centre.x, centre.y)
                    lineTo(centre.x - w * 0.05f, centre.y - reach)
                    lineTo(centre.x + w * 0.05f, centre.y - reach)
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.09f * glow), Color.Transparent),
                        startY = centre.y,
                        endY = centre.y - reach,
                    ),
                )
            }
        }
    }
}

/** The lit floor: ripples, the bright plinth line and a reflection of the core. */
private fun DrawScope.plinth(w: Float, h: Float, accent: Color, t: Float, glow: Float) {
    val fy = h * 0.86f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(accent.copy(alpha = 0.30f * glow), accent.copy(alpha = 0.10f * glow), Color.Transparent),
            center = Offset(w / 2f, fy),
            radius = w * 0.46f,
        ),
        radius = w * 0.46f,
        center = Offset(w / 2f, fy),
        // squash the glow into an ellipse for a floor-plane look
        style = Stroke(width = w * 0.92f, cap = StrokeCap.Butt),
    )
    for (i in 0 until 6) {
        val phase = (t * 0.22f / 6f + i / 6f) % 1f
        val rx = w * (0.08f + 0.38f * phase)
        val alpha = 0.42f * (1f - phase) * glow
        drawOval(
            color = accent.copy(alpha = alpha),
            topLeft = Offset(w / 2f - rx, fy - rx * 0.15f),
            size = Size(rx * 2f, rx * 0.30f),
            style = Stroke(1f),
        )
    }
    drawLine(
        brush = Brush.horizontalGradient(
            listOf(Color.Transparent, accent.copy(alpha = 0.38f * glow), Color.Transparent),
            startX = w * 0.12f,
            endX = w * 0.88f,
        ),
        start = Offset(w * 0.12f, fy),
        end = Offset(w * 0.88f, fy),
        strokeWidth = 1.4f,
    )
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.18f * glow), Color.Transparent),
            startY = fy,
            endY = h,
        ),
        radius = w * 0.16f,
        center = Offset(w / 2f, fy + w * 0.02f),
    )
}

/** Three dashed HUD rings around the sphere. */
private fun DrawScope.hudRings(centre: Offset, w: Float, accent: Color, t: Float) {
    val rings = listOf(
        Triple(0.43f, 0.22f, 1f),
        Triple(0.38f, 0.20f, 2f),
        Triple(0.46f, 0.14f, 1f),
    )
    for ((scale, alpha, dash) in rings) {
        val r = w * scale
        drawCircle(
            color = Ice.copy(alpha = alpha * 0.9f),
            radius = r,
            center = centre,
            style = Stroke(
                width = 1f,
                pathEffect = PathEffect.dashPathEffect(
                    if (dash == 1f) floatArrayOf(2f, 12f) else floatArrayOf(4f, 18f)
                ),
            ),
        )
    }
    drawCircle(
        color = accent.copy(alpha = 0.12f),
        radius = w * 0.46f,
        center = centre,
        style = Stroke(1f),
    )
}

/** Three elliptical orbits, each with a bright satellite and a short trail. */
private fun DrawScope.orbits(centre: Offset, w: Float, accent: Color, t: Float, glow: Float) {
    data class Orbit(val a: Float, val b: Float, val angle: Float, val speed: Float, val phase: Float)
    val list = listOf(
        Orbit(0.36f, 0.12f, -18f, 0.6f, 0f),
        Orbit(0.30f, 0.095f, 24f, -0.85f, 2.2f),
        Orbit(0.42f, 0.155f, 5f, 0.42f, 4.1f),
    )
    for ((i, o) in list.withIndex()) {
        rotate(degrees = o.angle, pivot = centre) {
            drawOval(
                color = Ice.copy(alpha = 0.14f + 0.05f * i),
                topLeft = Offset(centre.x - w * o.a, centre.y - w * o.b),
                size = Size(w * o.a * 2f, w * o.b * 2f),
                style = Stroke(1f),
            )
            val start = t * o.speed + o.phase
            val dir = if (o.speed < 0) -1f else 1f
            for (k in 1..8) {
                val tb = start - k * 0.05f * dir
                val p = Offset(
                    centre.x + cos(tb) * w * o.a,
                    centre.y + sin(tb) * w * o.b,
                )
                drawCircle(
                    accent.copy(alpha = 0.45f * (1f - k / 9f) * glow),
                    1.7f * (1f - k / 10f),
                    p,
                )
            }
            val head = Offset(
                centre.x + cos(start) * w * o.a,
                centre.y + sin(start) * w * o.b,
            )
            drawCircle(accent.copy(alpha = 0.22f * glow), 9f, head)
            drawCircle(Color.White, 1.9f, head)
        }
    }
}

/** The wireframe sphere: latitudes and meridians, back half faint, front half lit. */
private fun DrawScope.wireSphere(
    centre: Offset,
    r: Float,
    accent: Color,
    t: Float,
    glow: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Ice.copy(alpha = 0.13f),
                Ice.copy(alpha = 0.04f),
                accent.copy(alpha = 0.16f * glow),
                Color.Transparent,
            ),
            center = centre,
            radius = r * 1.06f,
        ),
        radius = r * 1.06f,
        center = centre,
    )

    val tilt = 0.34f
    val spin = t * 0.5f
    fun project(lat: Float, lon: Float): Triple<Float, Float, Float> {
        val y0 = sin(lat) * r
        val ring = cos(lat) * r
        val x0 = cos(lon) * ring
        val z0 = sin(lon) * ring
        val y1 = y0 * cos(tilt) - z0 * sin(tilt)
        val z1 = y0 * sin(tilt) + z0 * cos(tilt)
        val x2 = x0 * cos(spin) + z1 * sin(spin)
        val z2 = -x0 * sin(spin) + z1 * cos(spin)
        return Triple(x2, y1, z2)
    }

    val lats = listOf(-0.72f, -0.48f, -0.24f, 0f, 0.24f, 0.48f, 0.72f)
    val segments = 40
    for (lat in lats) {
        for (i in 0 until segments) {
            val l0 = i / segments.toFloat() * TAU
            val l1 = (i + 1) / segments.toFloat() * TAU
            val a = project(lat, l0)
            val b = project(lat, l1)
            val front = (a.third + b.third) / 2f > 0f
            val alpha = if (front) 0.24f else 0.07f
            drawLine(
                color = accent.copy(alpha = alpha * glow),
                start = Offset(centre.x + a.first, centre.y + a.second),
                end = Offset(centre.x + b.first, centre.y + b.second),
                strokeWidth = 1f,
            )
        }
    }
    val meridians = 7
    for (m in 0 until meridians) {
        val lon0 = m / meridians.toFloat() * PI.toFloat()
        for (i in 0 until segments) {
            val p0 = -PI.toFloat() / 2f + i / segments.toFloat() * PI.toFloat()
            val p1 = -PI.toFloat() / 2f + (i + 1) / segments.toFloat() * PI.toFloat()
            val a = project(p0, lon0)
            val b = project(p1, lon0)
            val front = (a.third + b.third) / 2f > 0f
            drawLine(
                color = Ice.copy(alpha = (if (front) 0.20f else 0.06f) * glow),
                start = Offset(centre.x + a.first, centre.y + a.second),
                end = Offset(centre.x + b.first, centre.y + b.second),
                strokeWidth = 1f,
            )
        }
    }
    drawCircle(
        color = accent.copy(alpha = 0.35f * glow),
        radius = r,
        center = centre,
        style = Stroke(1.1f),
    )
}

private fun DrawScope.dust(motes: List<Mote>, w: Float, h: Float, t: Float) {
    for (m in motes) {
        val y = ((m.y - t * m.speed) % 1f + 1f) % 1f * h
        val alpha = 0.1f + 0.3f * (0.5f + 0.5f * sin(t + m.phase))
        drawCircle(Ice.copy(alpha = alpha), 0.95f, Offset(m.x * w, y))
    }
}

private fun DrawScope.embers(
    embers: List<Ember>,
    w: Float,
    h: Float,
    accent: Color,
    t: Float,
    glow: Float,
) {
    val fy = h * 0.86f
    for (e in embers) {
        val pr = (t * e.speed / 6f + e.phase) % 1f
        val x = w / 2f + e.off * w + sin(t * 0.7f + e.phase * 9f) * w * 0.02f
        val y = fy - pr * h * 0.26f
        drawCircle(accent.copy(alpha = (1f - pr) * 0.6f * glow), e.size, Offset(x, y))
    }
}
