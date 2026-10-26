package com.jarvis.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** The Jarvis orb. Spins faster while it is thinking. */
@Composable
fun JarvisOrb(diameter: Dp, active: Boolean = false) {
    val twoPi = (2f * PI).toFloat()
    val transition = rememberInfiniteTransition(label = "orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(tween(if (active) 1400 else 4200, easing = LinearEasing)),
        label = "phase",
    )
    val rings = listOf(Palette.Violet, Palette.Cyan, Palette.Pink)
    Canvas(Modifier.size(diameter)) {
        val r = this.size.minDimension / 2f
        drawCircle(Palette.Violet.copy(alpha = 0.16f), radius = r)
        rings.forEachIndexed { i, c ->
            val t = (phase + i * (twoPi / 3f)) % twoPi
            val rr = r * (0.5f + 0.45f * (0.5f + 0.5f * sin(t)))
            drawCircle(color = c.copy(alpha = 0.5f), radius = rr, style = Stroke(width = 2.4f))
        }
        drawCircle(brush = OrbGradient, radius = r * 0.34f)
    }
}
