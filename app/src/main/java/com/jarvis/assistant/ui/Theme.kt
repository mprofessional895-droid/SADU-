package com.jarvis.assistant.ui

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The entire interface is monochrome. There is deliberately no accent colour:
 * hierarchy comes from weight, spacing, contrast and fine 1px borders.
 */
object Ink {
    val Background = Color(0xFF050505)
    val Card = Color(0xFF101010)
    val Elevated = Color(0xFF171717)
    val Text = Color(0xFFF5F5F2)
    val TextDim = Color(0xFF858585)
    val Border = Color(0xFF292929)
    val Accent = Color(0xFFFFFFFF)

    /** Only used for hairlines that need to sit on the elevated surfaces. */
    val BorderSoft = Color(0xFF1F1F1F)
}

object Dimens {
    val gutter: Dp = 20.dp
    val gap: Dp = 12.dp
    val tiny: Dp = 6.dp
    val cardRadius: Dp = 20.dp
    val sheetRadius: Dp = 28.dp
    /** Never smaller than this for anything tappable. */
    val touch: Dp = 48.dp
}

private val JarvisTypography = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Light, letterSpacing = 10.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.7.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.8.sp),
)

private val JarvisScheme = darkColorScheme(
    primary = Ink.Accent,
    onPrimary = Ink.Background,
    primaryContainer = Ink.Elevated,
    onPrimaryContainer = Ink.Text,
    secondary = Ink.Text,
    onSecondary = Ink.Background,
    secondaryContainer = Ink.Elevated,
    onSecondaryContainer = Ink.Text,
    tertiary = Ink.TextDim,
    onTertiary = Ink.Background,
    background = Ink.Background,
    onBackground = Ink.Text,
    surface = Ink.Background,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Card,
    onSurfaceVariant = Ink.TextDim,
    outline = Ink.Border,
    outlineVariant = Ink.BorderSoft,
    error = Ink.Text,
    onError = Ink.Background,
    errorContainer = Ink.Elevated,
    onErrorContainer = Ink.Text,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisScheme, typography = JarvisTypography, content = content)
}

/**
 * Honours the system "remove animations" accessibility setting
 * (Settings > Accessibility > Remove animations, or a 0 animator scale).
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        try {
            Settings.Global.getFloat(
                ctx.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }
}
