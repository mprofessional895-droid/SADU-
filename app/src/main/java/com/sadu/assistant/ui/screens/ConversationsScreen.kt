package com.sadu.assistant.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sadu.assistant.AiState
import com.sadu.assistant.AssistantText
import com.sadu.assistant.ChatMsg
import com.sadu.assistant.ChatSession
import com.sadu.assistant.SaduViewModel
import com.sadu.assistant.Mode
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.components.ThoughtField
import com.sadu.assistant.ui.rememberReducedMotion
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.MarkdownText
import com.sadu.assistant.ui.components.FadeIn
import com.sadu.assistant.ui.components.GhostButton
import com.sadu.assistant.ui.components.SaduTextField
import com.sadu.assistant.ui.components.ModeChip
import com.sadu.assistant.ui.components.RoundIconButton
import com.sadu.assistant.ui.components.StatusPill
import com.sadu.assistant.ui.components.SurfaceCard
import com.sadu.assistant.ui.rememberReducedMotion

@Composable
fun ConversationsScreen(vm: SaduViewModel) {
    if (vm.nameChoices.isNotEmpty()) {
        NameChooser(
            names = vm.nameChoices,
            onPick = { vm.chooseName(it) },
            onDismiss = { vm.dismissChoices() },
        )
    }
    val open = vm.openChat
    if (open == null) {
        ConversationList(
            sessions = vm.sessions,
            openId = vm.currentId,
            onOpen = { vm.openChat(it) },
            onDelete = { vm.deleteChat(it) },
            onNew = { vm.newChat() },
        )
    } else {
        Chat(
            title = open.title,
            messages = open.messages,
            state = vm.aiState,
            streamText = vm.streamText,
            error = vm.errorText,
            answeredBy = vm.answeredBy,
            draft = vm.composerText,
            focusToken = vm.composerToken,
            mode = vm.mode,
            onMode = { vm.chooseMode(it) },
            onBack = { vm.closeChat() },
            onNewChat = { vm.newChat() },
            onDraftChange = { vm.onComposerChange(it) },
            onSend = { vm.sendComposer() },
            onMic = { vm.startListening() },
            onDismissError = { vm.dismissError() },
            canStop = vm.canStop,
            onStop = { vm.stopGenerating() },
            thinkingNote = vm.busyNote,
            neededPermission = vm.neededPermission,
            onAllowPermission = { vm.permissionAnswered() },
            onSkipPermission = { vm.clearNeededPermission() },
        )
    }
}

/* ------------------------------------------------------------------ the list */

@Composable
fun ConversationList(
    sessions: List<ChatSession>,
    openId: Long,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNew: () -> Unit,
) {
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.gutter)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Conversations",
                color = Ink.Text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            NewChatButton(onClick = onNew)
        }

        val used = sessions.filter { it.askedCount > 0 }
        if (used.isEmpty()) {
            Spacer(Modifier.height(28.dp))
            EmptyConversations()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Dimens.gap),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(used, key = { it.id }) { session ->
                    FadeIn {
                        ConversationRow(
                            session = session,
                            now = now,
                            open = session.id == openId,
                            onOpen = { onOpen(session.id) },
                            onDelete = { onDelete(session.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewChatButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = Dimens.touch)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Ink.Border, RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "New chat", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = Ink.Text,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("New chat", color = Ink.Text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ConversationRow(
    session: ChatSession,
    now: Long,
    open: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    SurfaceCard(onClick = onOpen, onClickLabel = "Open ${session.title}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (open) {
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Ink.Text),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = session.title,
                        color = Ink.Text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Which mode this conversation was started in.
                    StatusPill(
                        text = AssistantText.modeLabel(session.mode),
                        active = session.mode == Mode.Control,
                    )
                }
                if (session.preview.isNotBlank() && session.preview != session.title) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = session.preview,
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${AssistantText.dayLabel(session.lastAt, now)} " +
                        "${AssistantText.clock(session.lastAt)}  \u00b7  ${session.askedCount} asked",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${session.title}",
                tint = Ink.TextDim,
                modifier = Modifier
                    .size(Dimens.touch)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Delete ${session.title}", onClick = onDelete)
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun EmptyConversations() {
    SurfaceCard(contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.ChatBubbleOutline,
                contentDescription = null,
                tint = Ink.TextDim,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("No conversations yet", color = Ink.Text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tap New chat, then talk or type. Everything you say is kept here on the phone.",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ the chat */

@Composable
fun Chat(
    title: String,
    messages: List<ChatMsg>,
    state: AiState,
    streamText: String?,
    error: String?,
    answeredBy: String? = null,
    draft: String,
    focusToken: Int,
    mode: Mode,
    onMode: (Mode) -> Unit,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onDismissError: () -> Unit,
    canStop: Boolean = false,
    onStop: () -> Unit = {},
    thinkingNote: String? = null,
    neededPermission: String? = null,
    onAllowPermission: () -> Unit = {},
    onSkipPermission: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val reduced = rememberReducedMotion()

    LaunchedEffect(focusToken) {
        if (focusToken > 0) focus.requestFocus()
    }

    val thinking = state == AiState.Processing && streamText == null
    val itemCount = messages.size + (if (streamText != null) 1 else 0) + (if (thinking) 1 else 0)

    // A message being sent throws a short burst out of the composer.
    var burst by remember { mutableIntStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    val send: () -> Unit = {
        burst++
        follow = true
        onSend()
    }

    // Reading back through the conversation must not be yanked away: the list follows a new
    // answer only while the reader is already at the bottom, or when they sent the message.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(itemCount) {
        if (itemCount > 0 && (follow || atBottom)) listState.animateScrollToItem(itemCount - 1)
        follow = false
    }
    LaunchedEffect(streamText) {
        if (streamText != null && itemCount > 0 && follow) listState.scrollToItem(itemCount - 1)
    }

    Box(Modifier.fillMaxSize()) {
    ThoughtField(
        state = state,
        streaming = streamText != null,
        burstToken = burst,
        reducedMotion = reduced,
        modifier = Modifier.fillMaxSize(),
    )
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Dimens.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to conversations",
                tint = Ink.Text,
                modifier = Modifier
                    .size(Dimens.touch)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Back to conversations", onClick = onBack)
                    .padding(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                color = Ink.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ModeChip(mode = mode, onToggle = { onMode(if (mode == Mode.Control) Mode.Chat else Mode.Control) })
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "New chat",
                tint = Ink.Text,
                modifier = Modifier
                    .size(Dimens.touch)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "New chat", onClick = onNewChat)
                    .padding(14.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                val previous = messages.getOrNull(index - 1)
                val showTime = previous == null ||
                    (msg.at - previous.at) >= AssistantText.TIMESTAMP_GAP_MS
                if (msg.fromUser) UserBubble(msg, showTime) else AssistantBubble(msg, showTime)
            }

            if (streamText != null) {
                item(key = "stream") {
                    AssistantBubble(
                        msg = ChatMsg(streamText, fromUser = false),
                        showTime = false,
                        streaming = true,
                    )
                }
            } else if (thinking) {
                item(key = "thinking") {
                    ThinkingBubble(note = thinkingNote)
                }
            }
        }

        if (answeredBy != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = answeredBy,
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (neededPermission != null) {
            PermissionAsk(
                permission = neededPermission,
                onResult = onAllowPermission,
                onSkip = onSkipPermission,
            )
        }

        if (error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.gutter, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error,
                    color = Ink.Text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                GhostButton("Dismiss", onDismissError)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Dimens.gutter, end = Dimens.gutter, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SaduTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = "Ask me anything",
                maxLines = 4,
                onSend = send,
                contentDescription = "Message",
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            RoundIconButton(
                icon = Icons.Rounded.Mic,
                contentDescription = "Talk to Sadu",
                onClick = onMic,
                diameter = Dimens.touch,
            )
            if (canStop) {
                RoundIconButton(
                    icon = Icons.Rounded.Stop,
                    contentDescription = "Stop answering",
                    onClick = onStop,
                    diameter = Dimens.touch,
                    filled = true,
                )
            } else {
                val ready = draft.isNotBlank()
                RoundIconButton(
                    icon = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    onClick = send,
                    diameter = Dimens.touch,
                    filled = ready,
                    showBorder = !ready,
                    enabled = ready,
                )
            }
        }
    }
    }
}

@Composable
private fun TimeMarker(millis: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = AssistantText.clock(millis),
            color = Ink.TextDim,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun UserBubble(msg: ChatMsg, showTime: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        if (showTime) TimeMarker(msg.at)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(Ink.Elevated)
                    .border(1.dp, Ink.Border, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = msg.text,
                    color = Ink.Text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * Sadu's turn. No bubble and no colour: long answers are what people read most, so the
 * text sits on the background at full width with a hairline rule marking whose turn it
 * is. Only the caret moves while the answer arrives.
 */
@Composable
private fun AssistantBubble(msg: ChatMsg, showTime: Boolean, streaming: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        if (showTime) TimeMarker(msg.at)
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (streaming) Ink.Accent.copy(alpha = 0.6f) else Ink.Border),
            )
            Spacer(Modifier.width(14.dp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                MarkdownText(
                    text = msg.text,
                    color = Ink.Text,
                    accent = Ink.Accent,
                    size = 15,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (streaming) {
                    Spacer(Modifier.width(3.dp))
                    Box(
                        Modifier
                            .size(width = 6.dp, height = 14.dp)
                            .background(Ink.Text),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble(note: String? = null) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(Ink.Card)
                .border(1.dp, Ink.Border, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val rise by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(620, delayMillis = i * 130, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot$i",
                )
                Box(
                    Modifier
                        .offset(y = (-3f * if (reduced) 0f else rise).dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Ink.TextDim),
                )
                if (i < 2) Spacer(Modifier.width(5.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = note?.takeIf { it.isNotBlank() } ?: "Thinking",
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** The plain-English name of a permission, for the one-button ask in the chat. */
fun permissionTitle(permission: String): String = when (permission) {
    "android.permission.READ_CONTACTS" -> "your contacts"
    "android.permission.CALL_PHONE" -> "the phone app, to place calls"
    "android.permission.SEND_SMS" -> "sending text messages"
    "android.permission.RECORD_AUDIO" -> "the microphone"
    else -> "one permission"
}

/**
 * One tap instead of a trip through Settings. Shown only when an action actually needed the
 * permission, so it never asks for anything "just in case".
 */
@Composable
private fun PermissionAsk(permission: String, onResult: () -> Unit, onSkip: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> onResult() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.gutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Sadu needs to read ${permissionTitle(permission)} for that. " +
                "Allow it and I will finish the request.",
            color = Ink.Text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        GhostButton("Allow", { launcher.launch(permission) })
        GhostButton("Not now", onSkip)
    }
}

/**
 * The names Sadu guessed, as a chooser.
 *
 * A wrong name is usually a typo or a misheard word, and a list is faster to read than a
 * sentence - but it is only ever a question. Nothing happens until a name is tapped.
 */
@Composable
private fun NameChooser(names: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Which one?",
                color = Ink.Text,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                Text(
                    text = "More than one contact fits. Pick the right one - numbers are hidden "
                        + "except the last four digits.",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                names.forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(name) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            color = Ink.Text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { GhostButton("Not now", onDismiss) },
    )
}
