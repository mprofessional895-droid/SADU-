package com.jarvis.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColors = darkColorScheme(
    primary = Color(0xFF4C8DFF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF39D2C0),
    onSecondary = Color(0xFF04110F),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF121821),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1B2430),
    onSurfaceVariant = Color(0xFF9AA7B4),
    outline = Color(0xFF2A3644),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisColors, content = content)
}
