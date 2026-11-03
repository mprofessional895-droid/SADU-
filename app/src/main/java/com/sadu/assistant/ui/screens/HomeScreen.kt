package com.sadu.assistant.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadu.assistant.AiState
import com.sadu.assistant.ChatMsg
import com.sadu.assistant.HomeEvent
import com.sadu.assistant.SaduViewModel
import com.sadu.assistant.Tab
import com.sadu.assistant.TurnStats
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.home.NeuralSphere
import com.sadu.assistant.ui.rememberReducedMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val Teal = Color(0xFF2FD8C8)
private val Ice = Color(0xFFA0F0FF)
private val Amber = Color(0xFFFFB23E)
private val Mono = FontFamily.Monospace
private const val VERSION = "v0.37"

/**
 * The home screen: who Sadu is, what it is doing, the last exchange and the real numbers from
 * the last reply. Every reading comes from the app - the sphere from the assistant's state,
 * tok/s and latency from the last turn's own counters, activity from what actually happened.
 */
@Composable
fun HomeScreen(vm: SaduViewModel) {
    HomeContent(
        state = vm.aiState,
        greeting = vm.greeting,
        lastTurn = vm.lastTurn,
        modelReady = vm.modelReady,
        cloudReady = vm.cloudReady,
        note = vm.statusNote,
        error = vm.errorText,
        stats = vm.lastStats,
        tokHistory = vm.tokHistory,
        events = vm.homeEvents,
        onOpenChat = { vm.goToChat() },
        onSettings = { vm.selectTab(Tab.Settings) },
        onTrySaying = { vm.trySaying(it) },
    )
}

@Composable
fun HomeContent(
    state: AiState,
    greeting: String = "",
    lastTurn: List<ChatMsg> = emptyList(),
    modelReady: Boolean = false,
    cloudReady: Boolean = false,
    note: String? = null,
    error: String? = null,
    stats: TurnStats? = null,
    tokHistory: List<Float> = emptyList(),
    events: List<HomeEvent> = emptyList(),
    onOpenChat: () -> Unit = {},
    onSettings: () -> Unit = {},
    onTrySaying: (String) -> Unit = {},
) {
    val engine = when {
        modelReady -> "Local"
        cloudReady -> "Cloud"
        else -> "No model"
    }
    val accent = when (state) {
        AiState.Listening -> Ice
        AiState.Processing -> Amber
        AiState.Speaking -> Ice
        else -> Teal
    }
    val statusLine = error ?: note ?: when {
        modelReady -> "On-device model loaded - answers stay on this phone"
        else -> greeting.ifBlank { "Ready when you are." }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.gutter),
    ) {
        Header(online = engine != "No model", onSettings = onSettings)
        Spacer(Modifier.height(22.dp))
        HeroHeading()
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(340.dp),
            contentAlignment = Alignment.Center,
        ) {
            NeuralSphere(state = state, modifier = Modifier.fillMaxSize())

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RailReading(
                    label = "TOK/S",
                    value = stats?.genTokS?.takeIf { it > 0 }?.let { one(it) } ?: "--",
                    accent = accent,
                )
                RailReading(
                    label = "LAT",
                    value = stats?.ttftMs?.let { "$it" } ?: "--",
                    suffix = "MS",
                    accent = accent,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 96.dp)
                    .width(104.dp),
            ) {
                MicroLabel("POWERED BY")
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (modelReady) "LOCAL AI" else "CLOUD",
                    color = Ink.Text.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = Mono,
                        letterSpacing = 2.4.sp,
                        fontSize = 12.sp,
                    ),
                )
                Spacer(Modifier.height(9.dp))
                SegmentedBar(loaded = modelReady, cloud = cloudReady)
            }

            NeuralCoreCard(
                engine = engine,
                state = state,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 96.dp),
            )
        }

        TypedStatus(statusLine)
        Spacer(Modifier.height(16.dp))

        PromptCard(
            turn = lastTurn,
            fallback = greeting.ifBlank { "Ask me anything." },
            onOpen = onOpenChat,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pill(
                icon = Icons.Rounded.Psychology,
                label = if (state == AiState.Processing) "Thinking" else "Think",
                live = state == AiState.Processing,
                accent = accent,
                modifier = Modifier.weight(1f),
                onClick = onOpenChat,
            )
            Pill(
                icon = Icons.Rounded.GraphicEq,
                label = when (state) {
                    AiState.Listening -> "Listening"
                    AiState.Speaking -> "Speaking"
                    AiState.Processing -> "Thinking"
                    else -> "Ready"
                },
                live = state != AiState.Idle,
                accent = accent,
                modifier = Modifier.weight(1f),
                onClick = onOpenChat,
            )
            Pill(
                icon = Icons.Rounded.Memory,
                label = engine,
                live = modelReady,
                accent = accent,
                modifier = Modifier.weight(1f),
                onClick = onSettings,
            )
        }
        Spacer(Modifier.height(18.dp))

        SectionLabel("Try saying")
        Spacer(Modifier.height(8.dp))
        SuggestionChips(onPick = onTrySaying)
        Spacer(Modifier.height(18.dp))

        ThroughputCard(stats = stats, history = tokHistory, engine = engine, accent = accent)
        Spacer(Modifier.height(12.dp))

        ActivityCard(events = events, engine = engine, modelReady = modelReady)
        Spacer(Modifier.height(18.dp))
    }
}

private fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

@Composable
private fun RailReading(
    label: String,
    value: String,
    suffix: String? = null,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$label ",
            color = Ink.TextDim,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = Mono,
                letterSpacing = 1.6.sp,
                fontSize = 9.sp,
            ),
        )
        Text(
            text = value,
            color = accent,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = Mono,
                letterSpacing = 0.6.sp,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        if (suffix != null) {
            Text(
                text = " $suffix",
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    letterSpacing = 1.4.sp,
                    fontSize = 9.sp,
                ),
            )
        }
    }
}

/** The status line under the sphere, typed out the way a terminal would. */
@Composable
private fun TypedStatus(text: String) {
    var shown by remember(text) { mutableIntStateOf(0) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(text, reduced) {
        if (reduced) {
            shown = text.length
            return@LaunchedEffect
        }
        shown = 0
        while (shown < text.length) {
            delay(14)
            shown++
        }
    }
    val caret by if (!reduced) {
        rememberInfiniteTransition(label = "caret").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                tween(520, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "caretAlpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Row(modifier = Modifier.heightIn(min = 34.dp)) {
        Text(
            text = "> ",
            color = Teal,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono, fontSize = 10.sp),
        )
        Text(
            text = text.take(shown),
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = Mono,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            ),
        )
        if (shown >= text.length) {
            Box(
                Modifier
                    .padding(start = 3.dp, top = 2.dp)
                    .width(4.dp)
                    .height(10.dp)
                    .background(Teal.copy(alpha = caret))
            )
        }
    }
}

@Composable
private fun HeroHeading() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "The Emergent Mind",
            color = Ink.Text,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 0.4.sp,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(112.dp)
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF6F787C), Color.Transparent)))
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "THINK  ·  ASSIST  ·  EVOLVE",
            color = Ink.TextDim.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = Mono,
                letterSpacing = 3.2.sp,
                fontSize = 10.sp,
            ),
        )
    }
}

@Composable
private fun Header(online: Boolean, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoMark()
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .width(1.dp)
                .height(30.dp)
                .background(Ink.Border)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SADU",
                color = Ink.Text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 7.sp,
                ),
            )
            Text(
                text = "YOUR PERSONAL AI",
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.8.sp,
                    fontSize = 8.sp,
                ),
            )
        }
        OnlinePill(online)
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Settings", onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = Ink.TextDim,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/** The orbit mark beside the wordmark. */
@Composable
private fun LogoMark() {
    Canvas(Modifier.size(38.dp)) {
        val r = size.minDimension * 0.28f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Ice.copy(alpha = 0.28f), r * 1.55f, c, style = Stroke(0.8f))
        drawOval(
            color = Ink.Text.copy(alpha = 0.55f),
            topLeft = Offset(c.x - r * 1.62f, c.y - r * 0.46f),
            size = Size(r * 3.24f, r * 0.92f),
            style = Stroke(1.2f),
        )
        drawCircle(Ink.Text, r, c, style = Stroke(1.3f))
        drawCircle(Teal, 1.6f, Offset(c.x + r * 1.55f, c.y - r * 0.42f))
    }
}

@Composable
private fun OnlinePill(online: Boolean) {
    val dot = if (online) Teal else Ink.TextDim
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Ink.Card)
            .border(1.dp, Ink.Border, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (online) "Online" else "Offline",
            color = Ink.Text.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        )
    }
}

private fun stateName(state: AiState): String = when (state) {
    AiState.Idle -> "Ready"
    AiState.Listening -> "Listening"
    AiState.Processing -> "Thinking"
    AiState.Speaking -> "Speaking"
    AiState.Error -> "Problem"
}

@Composable
private fun NeuralCoreCard(
    engine: String,
    state: AiState,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(108.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.Card.copy(alpha = 0.55f))
            .border(1.dp, Ink.BorderSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 9.dp),
    ) {
        MicroLabel("NEURAL CORE")
        Spacer(Modifier.height(7.dp))
        BulletRow(engine, live = engine != "No model", accent = accent)
        Spacer(Modifier.height(4.dp))
        BulletRow(stateName(state), live = state != AiState.Idle, accent = accent)
        Spacer(Modifier.height(4.dp))
        BulletRow(VERSION, live = false, accent = accent)
    }
}

@Composable
private fun BulletRow(text: String, live: Boolean, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (live) accent else Ink.TextDim.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = Ink.Text.copy(alpha = 0.88f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = Mono,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SegmentedBar(loaded: Boolean, cloud: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { i ->
            val lit = when {
                loaded -> true
                cloud -> i < 4
                else -> false
            }
            Box(
                Modifier
                    .width(9.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (lit) Teal.copy(alpha = 0.85f) else Ink.Border)
            )
        }
    }
}

@Composable
private fun MicroLabel(text: String) {
    Text(
        text = text,
        color = Ink.TextDim.copy(alpha = 0.8f),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = Mono,
            fontSize = 8.sp,
            letterSpacing = 1.6.sp,
        ),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Ink.TextDim,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = Mono,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
        ),
    )
}

/**
 * The last exchange, shaped like a prompt: what was asked, the head of the answer, and a way
 * back into it. With no history it invites instead of inventing text.
 */
@Composable
private fun PromptCard(turn: List<ChatMsg>, fallback: String, onOpen: () -> Unit) {
    val question = turn.firstOrNull { it.fromUser }?.text
    val answer = turn.lastOrNull { !it.fromUser }?.text
    val headline = answer?.lineSequence()?.firstOrNull { it.isNotBlank() }
    val rest = headline?.let { answer.removePrefix(it).trim() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Ink.Card.copy(alpha = 0.95f), Ink.Card.copy(alpha = 0.6f))
                )
            )
            .border(1.dp, Ink.Border, RoundedCornerShape(22.dp))
            .clickable(onClickLabel = "Open chat", onClick = onOpen)
            .padding(start = 18.dp, top = 17.dp, bottom = 17.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = question ?: "START A CONVERSATION",
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    letterSpacing = 0.9.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = headline ?: fallback,
                color = Ink.Text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!rest.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = rest,
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Ink.Elevated)
                .border(1.dp, Ink.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = Ink.Text,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Pill(
    icon: ImageVector,
    label: String,
    live: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Card.copy(alpha = 0.6f))
            .border(1.dp, Ink.BorderSoft, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Ink.Text.copy(alpha = 0.88f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (live) accent else Ink.TextDim.copy(alpha = 0.55f))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = Ink.Text.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val SUGGESTIONS = listOf(
    "Set an alarm for 6:30",
    "Open the camera",
    "Summarize my last note",
    "Turn on the flashlight",
    "What is on my calendar?",
)

@Composable
private fun SuggestionChips(onPick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (text in SUGGESTIONS) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Ink.Card)
                    .border(1.dp, Ink.Border, RoundedCornerShape(50))
                    .clickable(onClickLabel = text) { onPick(text) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+",
                    color = Teal,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = Mono,
                        fontSize = 11.sp,
                    ),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = text,
                    color = Ink.Text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The last measured speed, with the recent ones drawn beside it. With no measurement yet the
 * number is "--" and the line is flat: an empty reading, not a made-up one.
 */
@Composable
private fun ThroughputCard(
    stats: TurnStats?,
    history: List<Float>,
    engine: String,
    accent: Color,
) {
    val value = stats?.genTokS?.takeIf { it > 0 }?.let { one(it) } ?: "--"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(Ink.Card.copy(alpha = 0.85f))
            .border(1.dp, Ink.Border, RoundedCornerShape(Dimens.cardRadius))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "THROUGHPUT",
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    letterSpacing = 2.2.sp,
                ),
            )
            Text(
                text = engine.uppercase(Locale.US) + " · " + VERSION.uppercase(Locale.US),
                color = accent,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = Ink.Text,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 36.sp,
                    letterSpacing = (-0.5).sp,
                    fontWeight = FontWeight.Light,
                ),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "TOK/S",
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp,
                ),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Sparkline(history = history, accent = accent)
    }
}

@Composable
private fun Sparkline(history: List<Float>, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(66.dp)) {
        val top = size.height * 0.12f
        val bottom = size.height * 0.92f
        if (history.size < 2) {
            drawLine(
                color = Ink.Border,
                start = Offset(0f, bottom),
                end = Offset(size.width, bottom),
                strokeWidth = 1f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(6f, 8f)
                ),
            )
            return@Canvas
        }
        val high = history.max().coerceAtLeast(1f)
        val low = history.min().coerceAtMost(high * 0.9f)
        val span = (high - low).coerceAtLeast(0.001f)
        val stepX = size.width / (history.size - 1)
        fun y(v: Float) = bottom - ((v - low) / span) * (bottom - top)

        val line = Path()
        val fill = Path()
        history.forEachIndexed { i, v ->
            val x = i * stepX
            if (i == 0) {
                line.moveTo(x, y(v))
                fill.moveTo(x, bottom)
                fill.lineTo(x, y(v))
            } else {
                line.lineTo(x, y(v))
                fill.lineTo(x, y(v))
            }
        }
        fill.lineTo(size.width, bottom)
        fill.close()

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.22f), Color.Transparent)
            ),
        )
        drawPath(
            path = line,
            color = accent.copy(alpha = 0.9f),
            style = Stroke(width = 1.6f, cap = StrokeCap.Round),
        )
        drawCircle(accent, 2.4f, Offset(size.width, y(history.last())))
    }
}

/** What actually happened, newest first. No seeded filler: an empty list says so. */
@Composable
private fun ActivityCard(events: List<HomeEvent>, engine: String, modelReady: Boolean) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.US) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardRadius))
            .background(Ink.Card.copy(alpha = 0.85f))
            .border(1.dp, Ink.Border, RoundedCornerShape(Dimens.cardRadius))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Activity",
                color = Ink.Text,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            )
            Text(
                text = if (modelReady) "ON DEVICE" else engine.uppercase(Locale.US),
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    letterSpacing = 1.6.sp,
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (events.isEmpty()) {
            Text(
                text = "Nothing yet. Load a model or connect a cloud key in Settings, and each " +
                    "event lands here.",
                color = Ink.TextDim,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            )
        } else {
            for ((i, event) in events.take(6).withIndex()) {
                if (i > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.BorderSoft))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = clock.format(Date(event.at)),
                        color = Ink.TextDim.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            letterSpacing = 0.6.sp,
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = event.text,
                        color = Ink.Text.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
