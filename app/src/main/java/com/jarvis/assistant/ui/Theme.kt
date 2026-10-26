package com.jarvis.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Vivid, high-contrast dark palette. Several hues on purpose, so different things
 * on screen read differently: violet is "you", cyan is "Jarvis/voice", pink is
 * accent, amber warns, lime means connected.
 */
object Palette {
    val Violet = Color(0xFF8B5CF6)
    val VioletDeep = Color(0xFF5B21B6)
    val Cyan = Color(0xFF22D3EE)
    val Teal = Color(0xFF2DD4BF)
    val Pink = Color(0xFFFF4D8D)
    val Amber = Color(0xFFFFB020)
    val Lime = Color(0xFFA3E635)
    val Blue = Color(0xFF3B82F6)
    val Red = Color(0xFFFF5A6E)

    val Ink = Color(0xFF05060C)
    val Ink2 = Color(0xFF0C1122)
    val Card = Color(0xFF151C36)
    val Card2 = Color(0xFF1D2748)
    val Text = Color(0xFFF3F6FF)
    val TextDim = Color(0xFF9AABD4)
    val Line = Color(0xFF2C3866)
}

private val JarvisColors = darkColorScheme(
    primary = Palette.Violet,
    onPrimary = Color.White,
    primaryContainer = Palette.VioletDeep,
    onPrimaryContainer = Color.White,
    secondary = Palette.Cyan,
    onSecondary = Color(0xFF00202A),
    secondaryContainer = Color(0xFF0A3B48),
    onSecondaryContainer = Palette.Cyan,
    tertiary = Palette.Pink,
    onTertiary = Color(0xFF340018),
    background = Palette.Ink,
    onBackground = Palette.Text,
    surface = Palette.Ink2,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Palette.TextDim,
    outline = Palette.Line,
    error = Palette.Red,
    onError = Color(0xFF2B0006),
)

/** Deep gradient behind the whole app instead of a flat black. */
val AppBackground: Brush
    get() = Brush.verticalGradient(
        listOf(Color(0xFF0B1030), Color(0xFF07091A), Palette.Ink, Color(0xFF0A0716))
    )

/** Gradient behind your own messages. */
val UserBubble: Brush
    get() = Brush.horizontalGradient(listOf(Palette.Violet, Palette.Pink))

/** Gradient used by the Jarvis orb and other accents. */
val OrbGradient: Brush
    get() = Brush.linearGradient(listOf(Palette.Violet, Palette.Cyan, Palette.Pink))

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisColors, content = content)
}
