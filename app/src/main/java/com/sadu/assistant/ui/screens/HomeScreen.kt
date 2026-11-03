package com.sadu.assistant.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
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
import com.sadu.assistant.ui.home.EmergentCore
import com.sadu.assistant.ui.rememberReducedMotion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs

// The design's own palette, so the screen matches the prototype exactly.
private val Bg = Color(0xFF050607)
private val Card = Color(0xFF0A0D0E)
private val Glass = Color(0xB80B0F11)
private val Line = Color(0xFF1F2629)
private val Fg = Color(0xFFEAF2F3)
private val Muted = Color(0xFF848E92)
private val Teal = Color(0xFF2FD8C8)
private val Ice = Color(0xFF8FD0E0)
private val Amber = Color(0xFFFFB23E)
private val TagDim = Color(0xFF5E676B)
private val Mono = FontFamily.Monospace
private const val VERSION = "v0.37.1"

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
        AiState.Listening, AiState.Speaking -> Ice
        AiState.Processing -> Amber
        AiState.Error -> Color(0xFFE06A5A)
        else -> Teal
    }
    val glow = when (state) {
        AiState.Processing -> 1.35f
        AiState.Listening, AiState.Speaking -> 1.2f
        AiState.Error -> 0.5f
        else -> 1f
    }
    val statusLine = error ?: note ?: when {
        modelReady -> "On-device model loaded - answers stay on this phone"
        else -> greeting.ifBlank { "Ready when you are." }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        AmbientLight(accent)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Header(online = engine != "No model", onSettings = onSettings, accent = accent)
            Spacer(Modifier.height(14.dp))
            Head()
            Spacer(Modifier.height(14.dp))
            HeroBlock(
                state = state,
                accent = accent,
                glow = glow,
                engine = engine,
                stats = stats,
                modelReady = modelReady,
                cloudReady = cloudReady,
            )
            Spacer(Modifier.height(14.dp))
            TypedStatus(statusLine)
            Spacer(Modifier.height(14.dp))
            PromptCard(
                turn = lastTurn,
                fallback = greeting.ifBlank { "Ask me anything." },
                onOpen = onOpenChat,
            )
            Spacer(Modifier.height(14.dp))
            Pills(state = state, engine = engine, accent = accent, onChat = onOpenChat,
                onSettings = onSettings)
            Spacer(Modifier.height(18.dp))
            SectionLabel("TRY SAYING")
            Spacer(Modifier.height(10.dp))
            SuggestionChips(onPick = onTrySaying)
            Spacer(Modifier.height(14.dp))
            ThroughputCard(stats = stats, history = tokHistory, engine = engine)
            Spacer(Modifier.height(14.dp))
            ActivityCard(events = events, engine = engine, modelReady = modelReady)
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** The page-level light: a wide teal bloom at the sphere's height, plus the bottom underglow. */
@Composable
private fun AmbientLight(accent: Color) {
    val reduced = rememberReducedMotion()
    val breathe by if (!reduced) {
        rememberInfiniteTransition(label = "ambient").animateFloat(
            initialValue = 0.75f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse),
            label = "ambientAlpha",
        )
    } else {
        remember { mutableFloatStateOf(0.9f) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1F6E78).copy(alpha = 0.30f * breathe),
                            Color(0xFF1F6E78).copy(alpha = 0.10f * breathe),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 0.34f),
                        radius = size.width * 1.35f,
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.22f),
                            accent.copy(alpha = 0.10f),
                            accent.copy(alpha = 0.03f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height * 1.02f),
                        radius = size.width * 0.9f,
                    )
                )
            }
    )
}

@Composable
private fun Header(online: Boolean, onSettings: () -> Unit, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoMark(accent)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(1.dp).height(38.dp).background(Line))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "SADU",
                color = Fg,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 8.4.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                ),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "YOUR PERSONAL AI",
                color = Muted,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    letterSpacing = 2.6.sp,
                ),
            )
        }
        OnlinePill(online)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Settings", onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = Color(0xFF9AA3A7),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

/** The orbit mark: two rings and a teal satellite that slowly circles the mark. */
@Composable
private fun LogoMark(accent: Color) {
    val reduced = rememberReducedMotion()
    val spin by if (!reduced) {
        rememberInfiniteTransition(label = "logo").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
            label = "logoSpin",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(Modifier.size(46.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Ice.copy(alpha = 0.28f), size.minDimension * 0.47f, c, style = Stroke(1.dp.toPx()))
        drawCircle(Color(0xFFE9F1F2), size.minDimension * 0.283f, c, style = Stroke(1.4.dp.toPx()))
        rotate(degrees = -14f + spin * 0.5f, pivot = c) {
            drawOval(
                color = Color(0xFFE9F1F2),
                topLeft = Offset(c.x - size.width * 0.41f, c.y - size.height * 0.152f),
                size = Size(size.width * 0.82f, size.height * 0.304f),
                style = Stroke(1.4.dp.toPx()),
            )
            val a = Math.toRadians(-14.0 + spin * 0.5)
            val rx = size.width * 0.41f
            val ry = size.height * 0.152f
            val x = c.x + (cos(a) * rx).toFloat()
            val y = c.y + (sin(a) * ry).toFloat()
            drawCircle(accent, 1.8.dp.toPx(), Offset(x, y))
        }
    }
}

private fun cos(a: Double) = Math.cos(a)
private fun sin(a: Double) = Math.sin(a)

@Composable
private fun OnlinePill(online: Boolean) {
    val reduced = rememberReducedMotion()
    val pulse by if (!reduced) {
        rememberInfiniteTransition(label = "online").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
            label = "onlinePulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF0B0E0F))
            .border(1.dp, Line, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (online) Teal.copy(alpha = pulse) else Muted)
                .drawBehind {
                    if (online) {
                        drawCircle(Teal.copy(alpha = 0.35f * pulse), size.minDimension)
                    }
                }
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = if (online) "Online" else "Offline",
            color = Fg,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 12.5.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
            ),
        )
    }
}

@Composable
private fun Head() {
    val reduced = rememberReducedMotion()
    val shine by if (!reduced) {
        rememberInfiniteTransition(label = "rule").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
            label = "shine",
        )
    } else {
        remember { mutableFloatStateOf(0.4f) }
    }
    val cycle by if (!reduced) {
        rememberInfiniteTransition(label = "words").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
            label = "wordCycle",
        )
    } else {
        remember { mutableFloatStateOf(0.5f) }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "The Emergent Mind",
            color = Fg,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 27.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 31.sp,
                letterSpacing = 0.3.sp,
            ),
        )
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.width(112.dp).height(2.dp)) {
            val w = size.width
            val start = shine * (w + 90f) - 90f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF6F787C), Color.Transparent),
                    startX = 0f,
                    endX = w * 2f,
                ),
                topLeft = Offset.Zero,
                size = Size(w, size.height),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color(0xFFC9D2D5), Color.Transparent),
                    startX = start,
                    endX = start + 90f,
                ),
                topLeft = Offset.Zero,
                size = Size(w, size.height),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row {
            listOf("THINK", "ASSIST", "EVOLVE").forEachIndexed { i, word ->
                if (i > 0) {
                    Text(
                        text = "  ·  ",
                        color = TagDim,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            fontFamily = Mono,
                            letterSpacing = 3.6.sp,
                        ),
                    )
                }
                val phase = (cycle + i / 3f) % 1f
                val lit = (1f - abs(phase * 2f - 1f)).coerceIn(0f, 1f)
                Text(
                    text = word,
                    color = lerp(TagDim, Color(0xFFD6E2E4), lit),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontFamily = Mono,
                        letterSpacing = 3.6.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HeroBlock(
    state: AiState,
    accent: Color,
    glow: Float,
    engine: String,
    stats: TurnStats?,
    modelReady: Boolean,
    cloudReady: Boolean,
) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f / 0.98f)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val h = maxHeight
            EmergentCore(accent = accent, modifier = Modifier.fillMaxSize(), glow = glow)

            CornerBracket(Alignment.TopStart)
            CornerBracket(Alignment.BottomEnd)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Reading(
                    label = "TOK/S",
                    value = stats?.genTokS?.takeIf { it > 0 }?.let { one(it) } ?: "--",
                    accent = accent,
                )
                Reading(
                    label = "LAT",
                    value = stats?.ttftMs?.let { "$it" } ?: "--",
                    suffix = "MS",
                    accent = accent,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 2.dp)
                    .offset(y = h * 0.56f),
            ) {
                Text(
                    text = "POWERED BY",
                    color = Muted,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 9.5.sp,
                        fontFamily = Mono,
                        letterSpacing = 2.3.sp,
                    ),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = if (modelReady) "LOCAL AI" else "CLOUD",
                    color = Fg,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.4.sp,
                    ),
                )
                Spacer(Modifier.height(11.dp))
                SegmentedBar(loaded = modelReady, cloud = cloudReady, accent = accent)
            }

            NeuralCoreCard(
                engine = engine,
                state = state,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = h * 0.44f),
            )
        }
    }
}

/** One 14dp teal corner mark, as the design has at the top-left and bottom-right of the hero. */
@Composable
private fun CornerBracket(alignment: Alignment) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(0.dp)
    ) {
        Canvas(
            Modifier
                .align(alignment)
                .size(14.dp)
        ) {
            val stroke = 1.dp.toPx()
            val len = size.minDimension
            if (alignment == Alignment.TopStart) {
                drawLine(Teal.copy(alpha = 0.55f), Offset(0f, 0f), Offset(len, 0f), stroke)
                drawLine(Teal.copy(alpha = 0.55f), Offset(0f, 0f), Offset(0f, len), stroke)
            } else {
                drawLine(Teal.copy(alpha = 0.55f), Offset(len, len), Offset(0f, len), stroke)
                drawLine(Teal.copy(alpha = 0.55f), Offset(len, len), Offset(len, 0f), stroke)
            }
        }
    }
}

@Composable
private fun Reading(
    label: String,
    value: String,
    suffix: String? = null,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$label ",
            color = Muted,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
            ),
        )
        Text(
            text = value,
            color = accent,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.6.sp,
            ),
        )
        if (suffix != null) {
            Text(
                text = " $suffix",
                color = Muted,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp,
                ),
            )
        }
    }
}

@Composable
private fun SegmentedBar(loaded: Boolean, cloud: Boolean, accent: Color) {
    val reduced = rememberReducedMotion()
    val pulse by if (!reduced) {
        rememberInfiniteTransition(label = "segs").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "segPulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val loading = cloud && !loaded
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { i ->
            val lit = when {
                loaded -> true
                cloud -> i < 4
                else -> false
            }
            Box(
                Modifier
                    .width(15.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            lit && loading -> accent.copy(alpha = pulse)
                            lit -> accent
                            else -> Color(0xFF222A2D)
                        }
                    )
                    .drawBehind {
                        if (lit) {
                            drawCircle(accent.copy(alpha = 0.18f), size.width)
                        }
                    }
            )
        }
    }
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
            .widthIn(min = 112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Glass)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "NEURAL CORE",
            color = Muted,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 9.5.sp,
                fontFamily = Mono,
                letterSpacing = 2.1.sp,
            ),
        )
        CoreRow(engine, live = engine != "No model", accent = accent)
        CoreRow(stateName(state), live = state != AiState.Idle, accent = accent)
        CoreRow(VERSION, live = false, accent = accent)
    }
}

@Composable
private fun CoreRow(text: String, live: Boolean, accent: Color) {
    val reduced = rememberReducedMotion()
    val glow by if (!reduced && live) {
        rememberInfiniteTransition(label = "coreRow").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "coreDot",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (live) accent.copy(alpha = glow) else Color(0xFF5D676B))
                .drawBehind {
                    if (live) drawCircle(accent.copy(alpha = 0.3f * glow), size.width)
                }
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = Fg,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 11.5.sp,
                fontFamily = Mono,
                letterSpacing = 1.1.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

private fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

/** The status line, typed out with a blinking block at the end. */
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
    val blink by if (!reduced) {
        rememberInfiniteTransition(label = "caret").animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Restart),
            label = "caretAlpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 45.dp)) {
        Text(
            text = text.take(shown),
            color = Color(0xFF8B9498),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                fontFamily = Mono,
                lineHeight = 15.sp,
                letterSpacing = 0.6.sp,
            ),
        )
        Box(
            Modifier
                .padding(top = 2.dp, start = 2.dp)
                .width(5.dp)
                .height(10.dp)
                .background(Teal.copy(alpha = blink))
        )
    }
}

/** The last exchange, with the light that runs around the card edge. */
@Composable
private fun PromptCard(turn: List<ChatMsg>, fallback: String, onOpen: () -> Unit) {
    val question = turn.firstOrNull { it.fromUser }?.text
    val answer = turn.lastOrNull { !it.fromUser }?.text
    val headline = answer?.lineSequence()?.firstOrNull { it.isNotBlank() }
    val rest = headline?.let { answer.removePrefix(it).trim() }

    val reduced = rememberReducedMotion()
    val spin by if (!reduced) {
        rememberInfiniteTransition(label = "cardBorder").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
            label = "borderRun",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val lift by animateFloatAsState(if (pressed) 1f else 0f, label = "cardLift")
    val flash = remember { Animatable(0f) }
    LaunchedEffect(answer) {
        if (answer != null) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1200))
        }
    }
    val radius = 22.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(radius))
            .drawWithContent {
                drawContent()
                rotate(spin) {
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                0.5f to Teal.copy(alpha = 0.75f + 0.25f * flash.value),
                                0.58f to Color.Transparent,
                                1f to Color.Transparent,
                            ),
                            center = center,
                        ),
                        cornerRadius = CornerRadius(radius.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
                if (flash.value > 0.01f) {
                    drawRoundRect(
                        color = Teal.copy(alpha = 0.30f * flash.value),
                        cornerRadius = CornerRadius(radius.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .clickable(
                interactionSource = press,
                indication = null,
                onClickLabel = "Open chat",
                onClick = onOpen,
            )
            .padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = question ?: "START A CONVERSATION",
                color = Muted,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.5.sp,
                    fontFamily = Mono,
                    letterSpacing = 1.3.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = headline ?: fallback,
                color = Fg,
                style = androidx.compose.ui.text.TextStyle(
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
                    color = Muted,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF0E1112))
                .border(1.dp, lerp(Line, Teal, lift), CircleShape)
                .drawBehind {
                    if (lift > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Teal.copy(alpha = 0.35f * lift), Color.Transparent),
                                center = center,
                                radius = size.minDimension,
                            ),
                            radius = size.minDimension,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = Fg,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun Pills(
    state: AiState,
    engine: String,
    accent: Color,
    onChat: () -> Unit,
    onSettings: () -> Unit,
) {
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
            onClick = onChat,
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
            onClick = onChat,
        )
        Pill(
            icon = Icons.Rounded.Memory,
            label = engine,
            live = engine == "Local",
            accent = accent,
            modifier = Modifier.weight(1f),
            onClick = onSettings,
        )
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
    val reduced = rememberReducedMotion()
    val glow by if (!reduced && live) {
        rememberInfiniteTransition(label = "pill").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "pillDot",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "pillScale")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Card)
            .border(1.dp, if (live) Teal.copy(alpha = 0.45f) else Line, RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = press,
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(top = 15.dp, bottom = 13.dp, start = 6.dp, end = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Fg,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (live) accent.copy(alpha = glow) else Color(0xFF66707A))
                    .drawBehind {
                        if (live) drawCircle(accent.copy(alpha = 0.3f * glow), size.width)
                    }
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = Fg,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.5.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.9.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = Muted,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.6.sp,
            ),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Line, Color.Transparent)))
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
            val press = remember { MutableInteractionSource() }
            val down by press.collectIsPressedAsState()
            val lift by animateFloatAsState(if (down) 1f else 0f, label = "chipGlow")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Card)
                    .border(1.dp, lerp(Line, Teal, lift), RoundedCornerShape(50))
                    .clickable(
                        interactionSource = press,
                        indication = null,
                        onClickLabel = text,
                    ) { onPick(text) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "+",
                    color = Teal,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    color = Fg,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The last measured speed and the shape of the recent ones. */
@Composable
private fun ThroughputCard(stats: TurnStats?, history: List<Float>, engine: String) {
    val value = stats?.genTokS?.takeIf { it > 0 }?.let { one(it) } ?: "--"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "THROUGHPUT",
                color = Muted,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    letterSpacing = 2.2.sp,
                ),
            )
            Text(
                text = engine.uppercase(Locale.US) + " · " + VERSION.uppercase(Locale.US),
                color = Teal,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = Fg,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 38.sp,
                ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "TOK/S",
                color = Muted,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    letterSpacing = 1.8.sp,
                ),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Sparkline(history = history)
    }
}

@Composable
private fun Sparkline(history: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        val top = size.height * 0.15f
        val bottom = size.height * 0.88f
        drawLine(
            color = Line,
            start = Offset(0f, bottom),
            end = Offset(size.width, bottom),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
        )
        if (history.size < 2) return@Canvas
        val high = history.max()
        val low = history.min()
        val span = (high - low).coerceAtLeast(high * 0.15f)
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
                listOf(Teal.copy(alpha = 0.18f), Color.Transparent)
            ),
        )
        drawPath(path = line, color = Teal, style = Stroke(1.6f, cap = StrokeCap.Round))
        drawCircle(Teal, 2.2f, Offset(size.width, y(history.last())))
    }
}

/** What actually happened on this phone, newest first. */
@Composable
private fun ActivityCard(events: List<HomeEvent>, engine: String, modelReady: Boolean) {
    val clock = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ACTIVITY",
                color = Muted,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    letterSpacing = 2.4.sp,
                ),
            )
            Text(
                text = if (modelReady) "ON DEVICE" else engine.uppercase(Locale.US),
                color = Muted,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 10.sp,
                    fontFamily = Mono,
                    letterSpacing = 2.4.sp,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (events.isEmpty()) {
            Text(
                text = "Nothing yet. Load a model or connect a cloud key in Settings, and each " +
                    "event lands here.",
                color = Muted,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 11.5.sp,
                    fontFamily = Mono,
                    lineHeight = 16.sp,
                ),
            )
        } else {
            for (event in events.take(6)) {
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        text = clock.format(Date(event.at)),
                        color = Amber,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.5.sp,
                            fontFamily = Mono,
                            lineHeight = 16.sp,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = event.text,
                        color = Fg,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.5.sp,
                            fontFamily = Mono,
                            lineHeight = 16.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
