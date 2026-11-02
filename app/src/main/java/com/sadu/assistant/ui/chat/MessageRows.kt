package com.sadu.assistant.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sadu.assistant.ChatMsg
import com.sadu.assistant.TurnStats
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.rememberReducedMotion
import java.util.Locale

/** The reader's own line: short, right-aligned, and never mistaken for the assistant's. */
@Composable
fun UserMessage(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = text,
            color = Ink.Text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.Card)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

/** An answer: its working-out folded above it, the text itself, then what can be done with it. */
@Composable
fun AssistantMessage(
    msg: ChatMsg,
    canRegenerate: Boolean,
    onCopy: (String) -> Unit,
    onRead: (String) -> Unit,
    onRate: (Long, Boolean) -> Unit,
    onRegenerate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (!msg.thinking.isNullOrBlank()) {
            ThoughtCard(text = msg.thinking, live = false, durationMs = msg.stats?.thinkingMs)
            Spacer(Modifier.height(10.dp))
        }
        MarkdownBody(text = msg.text)
        msg.stats?.let {
            TimingLine(it)
        }
        Spacer(Modifier.height(6.dp))
        ActionRow(
            rating = msg.rating,
            canRegenerate = canRegenerate,
            onCopy = { onCopy(msg.text) },
            onRead = { onRead(msg.text) },
            onLike = { onRate(msg.id, true) },
            onDislike = { onRate(msg.id, false) },
            onRegenerate = onRegenerate,
        )
    }
}

/** The answer while it is still arriving: the thought card above, the words below. */
@Composable
fun StreamingMessage(
    answer: String?,
    thought: String?,
    thoughtLive: Boolean,
    note: String?,
) {
    Column(Modifier.fillMaxWidth()) {
        if (thought != null || thoughtLive) {
            ThoughtCard(text = thought.orEmpty(), live = thoughtLive, durationMs = null, note = note)
            Spacer(Modifier.height(10.dp))
        }
        if (answer != null) {
            MarkdownBody(text = answer)
        } else if (!thoughtLive && thought == null) {
            Text(
                text = note ?: "Thinking",
                color = Ink.TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ActionRow(
    rating: Int,
    canRegenerate: Boolean,
    onCopy: () -> Unit,
    onRead: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(Icons.Rounded.ContentCopy, "Copy answer", onCopy)
        ActionIcon(Icons.Rounded.VolumeUp, "Read aloud", onRead)
        ActionIcon(
            Icons.Rounded.ThumbUp,
            if (rating == 1) "Remove like" else "Good answer",
            onLike,
            active = rating == 1,
        )
        ActionIcon(
            Icons.Rounded.ThumbDown,
            if (rating == -1) "Remove dislike" else "Bad answer",
            onDislike,
            active = rating == -1,
        )
        if (canRegenerate) ActionIcon(Icons.Rounded.Refresh, "Ask again", onRegenerate)
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (active) Ink.Accent else Ink.TextDim,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(14.dp),
    )
}

/**
 * The model's working-out, folded away. It is open while the model is still thinking and
 * becomes one line the reader can tap once the answer starts.
 */
@Composable
fun ThoughtCard(
    text: String,
    live: Boolean,
    durationMs: Long?,
    note: String? = null,
) {
    var open by rememberSaveable(live) { mutableStateOf(live) }
    androidx.compose.runtime.LaunchedEffect(live) { if (live) open = true }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Card.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (open) "Hide thinking" else "Show thinking",
                    onClick = { open = !open },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThinkingDot(active = live)
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (live) "Thinking" else "Thought" + (durationMs?.let { "  ·  " + seconds(it) } ?: ""),
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelLarge,
            )
            if (note != null && live && note.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = note,
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Ink.TextDim,
                modifier = Modifier.size(17.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    text = text.ifBlank { "…" },
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ThinkingDot(active: Boolean) {
    val reduced = rememberReducedMotion()
    val alpha by if (active && !reduced) {
        val t = rememberInfiniteTransition(label = "thought")
        t.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "dot",
        )
    } else {
        remember { mutableFloatStateOf(0.7f) }
    }
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Ink.Accent.copy(alpha = alpha)),
    )
}

/** The numbers that explain how fast the answer came, small and quiet under it. */
@Composable
private fun TimingLine(stats: TurnStats) {
    val parts = buildList {
        stats.promptMs?.let { add("Tokenization " + millis(it)) }
        stats.ttftMs?.let { add("TTFT " + millis(it)) }
        stats.thinkingMs?.let { add("Thinking " + seconds(it)) }
        stats.genTokS?.let { add(String.format(Locale.US, "%.1f tok/s", it)) }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("   ·   "),
        color = Ink.TextDim,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun seconds(ms: Long): String =
    if (ms < 1000) "${ms}ms" else String.format(Locale.US, "%.1fs", ms / 1000.0)

private fun millis(ms: Long): String =
    if (ms < 1000) "${ms}ms" else String.format(Locale.US, "%.1fs", ms / 1000.0)
