package com.sadu.assistant.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadu.assistant.AiState
import com.sadu.assistant.AssistantText
import com.sadu.assistant.ChatMsg
import com.sadu.assistant.Mode
import com.sadu.assistant.SaduViewModel
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.components.StatusPill
import com.sadu.assistant.ui.rememberReducedMotion
import kotlin.math.min

/**
 * The home screen is the assistant itself: one hero visual, the wordmark, and a single line
 * of guidance. Everything that used to sit under it - mode switch, last answer, composer,
 * mic, quick actions - now lives in the Chats tab, so this screen has one job: to feel calm
 * and open and to hand over to a conversation when tapped.
 */
@Composable
fun HomeScreen(vm: SaduViewModel) {
    HomeContent(
        greeting = vm.greeting,
        state = vm.aiState,
        level = vm.micLevel,
        error = vm.errorText,
        answeredBy = vm.answeredBy,
        note = vm.statusNote,
        lastTurn = vm.lastTurn,
        mode = vm.mode,
        onMode = { vm.chooseMode(it) },
        onMic = { vm.startListening() },
        onType = { vm.askForText(null) },
        onQuickAction = {},
        onDismissError = { vm.dismissError() },
        onOpenChat = { vm.goToChat() },
    )
}

@Composable
fun HomeContent(
    greeting: String,
    state: AiState,
    level: Float,
    error: String?,
    answeredBy: String? = null,
    note: String?,
    lastTurn: List<ChatMsg>,
    mode: Mode,
    onMode: (Mode) -> Unit,
    onMic: () -> Unit,
    onType: () -> Unit,
    onQuickAction: () -> Unit = {},
    onDismissError: () -> Unit,
    onOpenChat: () -> Unit = {},
) {
    val reduced = rememberReducedMotion()
    val hero = rememberHero()

    // The hero breathes very slowly, so the screen is alive without anything moving fast.
    val breath by if (!reduced) {
        rememberInfiniteTransition(label = "hero").animateFloat(
            initialValue = 1f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Reverse),
            label = "breath",
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Background)
            .clickable(onClickLabel = "Open chat", onClick = onOpenChat),
    ) {
        if (hero != null) {
            Image(
                bitmap = hero,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsScale(breath),
            )
        } else {
            HeroFallback()
        }

        // The image fades into the page at the top and bottom instead of ending on an edge.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink.Background.copy(alpha = 0.92f),
                        0.16f to Color.Transparent,
                        0.72f to Color.Transparent,
                        0.92f to Ink.Background.copy(alpha = 0.88f),
                        1f to Ink.Background,
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.gutter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SADU",
                    color = Ink.Text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = 9.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = AssistantText.stateLabel(state),
                    active = state != AiState.Idle,
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (error != null) error else greeting,
                    color = if (error != null) Ink.Text else Ink.TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (note != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note,
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Tap anywhere to begin",
                    color = Ink.TextDim.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                )
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

private fun Modifier.graphicsScale(scale: Float) =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

/**
 * The home visual when the artwork is not shipped: a quiet lit sphere on black.
 * It is not meant to compete with the illustration - just to keep the screen whole.
 */
@Composable
private fun HeroFallback() {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.44f
        val radius = min(size.width, size.height) * 0.30f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Ink.Accent.copy(alpha = 0.32f),
                    Ink.Accent.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                radius = radius * 2.1f,
            ),
            radius = radius * 2.1f,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Ink.Text.copy(alpha = 0.9f), Ink.Accent.copy(alpha = 0.25f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(cx - radius * 0.2f, cy - radius * 0.25f),
                radius = radius,
            ),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
        )
        listOf(1.14f, 1.32f, 1.5f).forEachIndexed { i, f ->
            drawCircle(
                color = Ink.Accent.copy(alpha = 0.20f - i * 0.045f),
                radius = radius * f,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f),
            )
        }
    }
}

/**
 * Reads the home artwork from the assets folder, highest quality first. Any format
 * BitmapFactory knows works, and a large file is sampled down so the screen never holds more
 * pixels than it can show.
 */
@Composable
private fun rememberHero(): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = LocalContext.current
    return remember(ctx) {
        HERO_FILES.firstNotNullOfOrNull { name -> decode(ctx, name) }
    }
}

private fun decode(ctx: android.content.Context, name: String): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.assets.open(name).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        ctx.assets.open(name).use { BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap() }
    }.getOrNull()

private val HERO_FILES = listOf("hero.jpg", "hero.png", "hero.webp")
