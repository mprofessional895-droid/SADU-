package com.sadu.assistant.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadu.assistant.AiState
import com.sadu.assistant.ChatMsg
import com.sadu.assistant.SaduViewModel
import com.sadu.assistant.Tab
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.home.NeuralSphere

private val Cyan = Color(0xFF2EE6D6)
private const val VERSION = "v0.35"

/**
 * One quiet instrument panel: who Sadu is, what it is doing right now, and a single door
 * into the conversation.
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
        onOpenChat = { vm.goToChat() },
        onSettings = { vm.selectTab(Tab.Settings) },
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
    onOpenChat: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val engine = when {
        modelReady -> "On Device"
        cloudReady -> "Cloud"
        else -> "No Model"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.gutter),
    ) {
        Header(online = engine != "No Model", onSettings = onSettings)
        Spacer(Modifier.height(22.dp))
        HeroHeading()
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(330.dp),
            contentAlignment = Alignment.Center,
        ) {
            NeuralSphere(state = state, modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 44.dp)
                    .width(94.dp),
            ) {
                MicroLabel("POWERED BY")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "LOCAL AI",
                    color = Ink.Text.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                )
                Spacer(Modifier.height(8.dp))
                SegmentedBar(active = modelReady, cloud = cloudReady)
            }
            NeuralCorePanel(
                engine = engine,
                state = state,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 44.dp),
            )
        }

        if (error != null) {
            Text(
                text = error,
                color = Ink.Text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        } else if (note != null) {
            Text(
                text = note,
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        PromptCard(turn = lastTurn, greeting = greeting, onOpen = onOpenChat)
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusTile(
                icon = Icons.Rounded.Psychology,
                label = if (state == AiState.Processing) "Thinking…" else "Think",
                live = state == AiState.Processing,
                modifier = Modifier.weight(1f),
                onClick = onOpenChat,
            )
            StatusTile(
                icon = Icons.Rounded.GraphicEq,
                label = if (state == AiState.Listening) "Listening" else "Ready",
                live = state == AiState.Listening || state == AiState.Speaking,
                modifier = Modifier.weight(1f),
                onClick = onOpenChat,
            )
            StatusTile(
                icon = Icons.Rounded.Memory,
                label = engine,
                live = engine == "On Device",
                modifier = Modifier.weight(1f),
                onClick = onSettings,
            )
        }
        Spacer(Modifier.height(16.dp))
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
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(56.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "THINK  ·  ASSIST  ·  EVOLVE",
            color = Ink.TextDim.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp),
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
        drawOrbit(c, r)
        drawCircle(Color.White.copy(alpha = 0.9f), r, c, style = Stroke(1.2f))
        drawCircle(Color.White.copy(alpha = 0.22f), r * 1.55f, c, style = Stroke(0.8f))
        drawCircle(Color.White, 1.5f, Offset(c.x + r * 1.55f, c.y))
    }
}

private fun DrawScope.drawOrbit(c: Offset, r: Float) {
    drawOval(
        color = Color.White.copy(alpha = 0.35f),
        topLeft = Offset(c.x - r * 1.6f, c.y - r * 0.45f),
        size = Size(r * 3.2f, r * 0.9f),
        style = Stroke(0.8f),
    )
}

@Composable
private fun OnlinePill(online: Boolean) {
    val dot = if (online) Cyan else Ink.TextDim
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

@Composable
private fun NeuralCorePanel(engine: String, state: AiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.Card.copy(alpha = 0.55f))
            .border(1.dp, Ink.BorderSoft, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
    ) {
        Text(
            text = "NEURAL CORE",
            color = Ink.TextDim,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.sp),
        )
        Spacer(Modifier.height(6.dp))
        BulletRow(engine)
        Spacer(Modifier.height(3.dp))
        BulletRow(if (state == AiState.Idle) "Ready" else "Active")
        Spacer(Modifier.height(3.dp))
        BulletRow(VERSION)
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp).clip(CircleShape).background(Ink.TextDim))
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            color = Ink.Text.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SegmentedBar(active: Boolean, cloud: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(7) { i ->
            val lit = when {
                active -> true
                cloud -> i < 4
                else -> false
            }
            Box(
                Modifier
                    .width(9.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (lit) Cyan.copy(alpha = 0.85f) else Ink.Border)
            )
        }
    }
}

@Composable
private fun MicroLabel(text: String) {
    Text(
        text = text,
        color = Ink.TextDim.copy(alpha = 0.75f),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.2.sp),
    )
}

/**
 * The last exchange, shaped like a prompt: what was asked, the head of the answer, and a
 * way back into it. An empty history shows an invitation instead of invented text.
 */
@Composable
private fun PromptCard(turn: List<ChatMsg>, greeting: String, onOpen: () -> Unit) {
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
                    listOf(Ink.Card.copy(alpha = 0.95f), Ink.Card.copy(alpha = 0.55f))
                )
            )
            .border(1.dp, Ink.Border, RoundedCornerShape(22.dp))
            .clickable(onClickLabel = "Open chat", onClick = onOpen)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (question != null) {
                Text(
                    text = question,
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = "START A CONVERSATION",
                    color = Ink.TextDim.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = headline ?: greeting.ifBlank { "Ask me anything." },
                color = Ink.Text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!rest.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rest,
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Ink.Elevated)
                .border(1.dp, Ink.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = Ink.Text,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun StatusTile(
    icon: ImageVector,
    label: String,
    live: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Card.copy(alpha = 0.5f))
            .border(1.dp, Ink.BorderSoft, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Ink.Text.copy(alpha = 0.9f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (live) Cyan else Ink.TextDim.copy(alpha = 0.6f))
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                color = Ink.Text.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

