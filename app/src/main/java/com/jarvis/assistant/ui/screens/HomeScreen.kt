package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.AiState
import com.jarvis.assistant.AssistantText
import com.jarvis.assistant.ChatMsg
import com.jarvis.assistant.JarvisViewModel
import com.jarvis.assistant.Mode
import com.jarvis.assistant.ui.Dimens
import com.jarvis.assistant.ui.Ink
import com.jarvis.assistant.ui.components.FadeIn
import com.jarvis.assistant.ui.components.JarvisOrb
import com.jarvis.assistant.ui.components.ModeSwitch
import com.jarvis.assistant.ui.components.RoundIconButton
import com.jarvis.assistant.ui.components.SectionLabel
import com.jarvis.assistant.ui.components.StatusPill
import com.jarvis.assistant.ui.components.SurfaceCard
import androidx.compose.material.icons.rounded.ChatBubbleOutline

/** A shortcut under the orb. */
data class QuickAction(
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val command: String,
    val runImmediately: Boolean,
)

val QuickActions = listOf(
    QuickAction("Read my screen", "Say it out loud", Icons.Rounded.Visibility, "read the screen", true),
    QuickAction("Send a text", "Then finish it", Icons.Rounded.Sms, "text ", false),
    QuickAction("Call someone", "Then finish it", Icons.Rounded.Call, "call ", false),
    QuickAction("Set an alarm", "Then finish it", Icons.Rounded.Alarm, "set an alarm for ", false),
)

/**
 * Chat mode cannot touch the phone, so it offers conversation instead of shortcuts that
 * would only be refused. Searching the web and remembering facts both work in Chat mode.
 */
val ChatQuickActions = listOf(
    QuickAction("About me", "What can I do?", Icons.Rounded.Psychology, "What can you do?", true),
    QuickAction("Tell me a joke", "Cheer me up", Icons.Rounded.Mood, "Tell me a joke", true),
    QuickAction("Search the web", "For anything", Icons.Rounded.Search, "search the web for ", false),
    QuickAction("Remember", "A fact about me", Icons.Rounded.BookmarkAdd, "remember that ", false),
)

@Composable
fun HomeScreen(vm: JarvisViewModel) {
    HomeContent(
        greeting = vm.greeting,
        state = vm.aiState,
        level = vm.micLevel,
        error = vm.errorText,
        answeredBy = vm.answeredBy,
        note = if (vm.modelReady) null else vm.modelNote,
        lastTurn = vm.lastTurn,
        mode = vm.mode,
        onMode = { vm.chooseMode(it) },
        onMic = { vm.startListening() },
        onType = { vm.askForText(null) },
        onQuickAction = { action ->
            if (action.runImmediately) vm.runNow(action.command) else vm.askForText(action.command)
        },
        onDismissError = { vm.dismissError() },
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
    onQuickAction: (QuickAction) -> Unit,
    onDismissError: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // A short window (landscape, small phone) scrolls instead of clipping; otherwise the
    // orb takes whatever height is left, and is never larger than the box it sits in.
    val short = maxHeight < 520.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = Dimens.gutter),
    ) {
        // ---------------------------------------------------------------- header
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "JARVIS",
                color = Ink.Text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 7.sp,
                ),
                modifier = Modifier.weight(1f),
            )
            StatusPill(
                text = AssistantText.stateLabel(state),
                active = state != AiState.Idle,
            )
        }

        Text(
            text = greeting,
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (note != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = note,
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // ------------------------------------------------------------------- orb
        if (short) {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                JarvisOrb(state = state, level = level, diameter = 168.dp)
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val available = minOf(maxWidth, maxHeight)
                val diameter = available.coerceAtMost(232.dp)
                JarvisOrb(
                    state = state,
                    level = level,
                    diameter = diameter,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        // -------------------------------------------------------- mode and status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeSwitch(mode = mode, onSelect = onMode)
            Spacer(Modifier.width(10.dp))
            Text(
                text = AssistantText.stateLabel(state),
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (answeredBy != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = answeredBy,
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = error,
                        color = Ink.Text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = Ink.TextDim,
                        modifier = Modifier
                            .size(Dimens.touch)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClick = onDismissError)
                            .padding(14.dp),
                    )
                }
            }
        } else if (lastTurn.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FadeIn {
                LastTurnCard(lastTurn)
            }
        }

        // ------------------------------------------------------------------ input
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                icon = Icons.Rounded.Mic,
                contentDescription = if (state == AiState.Listening) {
                    "Listening. Tap to start again"
                } else {
                    "Talk to Jarvis"
                },
                onClick = onMic,
                diameter = 64.dp,
                filled = state == AiState.Listening,
            )
            Spacer(Modifier.width(12.dp))
            TypeShortcut(onClick = onType, modifier = Modifier.weight(1f))
        }

        // --------------------------------------------------------- quick actions
        Spacer(Modifier.height(16.dp))
        SectionLabel("Quick actions")
        Spacer(Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(94.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(if (mode == Mode.Chat) ChatQuickActions else QuickActions) { action ->
                QuickActionCard(action = action, onClick = { onQuickAction(action) })
            }
        }
    }
    }
}

@Composable
private fun TypeShortcut(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = Dimens.touch + 6.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(Ink.Card)
            .then(
                Modifier.clickable(
                    onClickLabel = "Type a message instead",
                    onClick = onClick,
                ),
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ChatBubbleOutline,
            contentDescription = null,
            tint = Ink.TextDim,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Type a message",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LastTurnCard(turn: List<ChatMsg>) {
    val question = turn.firstOrNull { it.fromUser }
    val answer = turn.lastOrNull { !it.fromUser }
    SurfaceCard {
        if (question != null) {
            Text(
                text = question.text,
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (answer != null) {
            Text(
                text = answer.text,
                color = Ink.Text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickActionCard(action: QuickAction, onClick: () -> Unit) {
    SurfaceCard(
        modifier = Modifier.width(152.dp).height(94.dp),
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = Ink.Text,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = action.label,
            color = Ink.Text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = action.hint,
            color = Ink.TextDim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
