package com.sadu.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sadu.assistant.ChatMsg
import com.sadu.assistant.Mode
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.components.ModeChip

/** Openers offered on an empty chat; tapping one fills the composer. */
object ChatSuggestions {
    val all = listOf(
        "Explain a topic in simple words",
        "Summarise this in three points",
        "Help me write a short reply",
    )
}

/**
 * The conversation as it is read: a quiet top bar, the messages between it and the composer,
 * and the composer fixed at the bottom above the keyboard.
 *
 * The message list has its own scroll container and is clipped, so a long answer scrolls
 * inside it instead of growing the screen or sliding under the input card.
 */
@Composable
fun ChatView(
    title: String,
    messages: List<ChatMsg>,
    mode: Mode,
    draft: String,
    streamText: String?,
    streamThought: String?,
    thoughtLive: Boolean,
    thinkingNote: String?,
    busy: Boolean,
    canStop: Boolean,
    internetOn: Boolean,
    answeredBy: String?,
    error: String?,
    suggestions: List<String>,
    focusToken: Int,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onMode: (Mode) -> Unit,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onInternet: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onCopy: (String) -> Unit,
    onRead: (String) -> Unit,
    onRate: (Long, Boolean) -> Unit,
    onRegenerate: () -> Unit,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(focusToken) {
        if (focusToken > 0) focus.requestFocus()
    }
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 2
        }
    }

    // Following is the reader's choice: drag up once and the list stays put until they come back.
    androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !atBottom) follow = false
        if (!listState.isScrollInProgress && atBottom) follow = true
    }

    val showingWelcome = messages.isEmpty() && streamText == null && !busy

    // One scroll per new message and per ~120 characters of streaming text: enough to keep the
    // end in view, few enough that it never fights the reader's finger or animates per token.
    val streamBucket = streamText?.length?.div(120) ?: -1
    androidx.compose.runtime.LaunchedEffect(messages.size, streamBucket, showingWelcome) {
        if (showingWelcome || !follow) return@LaunchedEffect
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Background)
            // The bottom bar already sits below this view, so only the keyboard needs room
            // here; padding for the navigation bar too would leave a dead gap under the card.
            .imePadding(),
    ) {
        ChatTopBar(
            title = if (showingWelcome) "Sadu" else title,
            mode = mode,
            onBack = onBack,
            onNewChat = onNewChat,
            onMode = onMode,
        )

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            if (showingWelcome) {
                Welcome(
                    suggestions = suggestions,
                    onPick = {
                        onDraft(it)
                        focus.requestFocus()
                    },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Dimens.gutter,
                        end = Dimens.gutter,
                        top = 26.dp,
                        bottom = 34.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                        if (msg.fromUser) {
                            UserMessage(msg.text)
                        } else {
                            AssistantMessage(
                                msg = msg,
                                canRegenerate = index == messages.lastIndex && !busy,
                                onCopy = onCopy,
                                onRead = onRead,
                                onRate = onRate,
                                onRegenerate = onRegenerate,
                            )
                        }
                    }

                    if (streamText != null || streamThought != null || busy) {
                        item(key = "stream") {
                            StreamingMessage(
                                answer = streamText,
                                thought = streamThought,
                                thoughtLive = thoughtLive,
                                note = thinkingNote,
                            )
                        }
                    }
                }
            }

            // The conversation dissolves into the page instead of starting and stopping on a
            // hard edge - the answer reads as text on the screen, not as a card in a box.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Ink.Background,
                            0.55f to Ink.Background.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        )
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Ink.Background.copy(alpha = 0.72f),
                            1f to Ink.Background,
                        )
                    ),
            )
        }

        if (error != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = error,
                    color = Ink.Text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Dismiss",
                    color = Ink.Accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Dismiss", onClick = onDismissError)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        if (answeredBy != null && !busy) {
            Text(
                text = answeredBy,
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Dimens.gutter + 4.dp, bottom = 2.dp),
            )
        }

        Composer(
            draft = draft,
            onDraft = onDraft,
            onSend = {
                follow = true
                onSend()
            },
            onStop = onStop,
            onMic = onMic,
            canStop = canStop,
            internetOn = internetOn,
            onInternet = onInternet,
            modifier = Modifier.focusRequester(focus),
        )
    }

    val itemCount = messages.size + (if (streamText != null || streamThought != null || busy) 1 else 0)
    // A new turn lands at the bottom only while the reader is following; scrolling up stops it,
    // and streaming is handled by the bucketed effect above rather than once per token.
    androidx.compose.runtime.LaunchedEffect(messages.size) {
        if (follow && itemCount > 0) listState.scrollToItem(itemCount - 1)
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    mode: Mode,
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onMode: (Mode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ArrowBack,
            contentDescription = "All conversations",
            tint = Ink.Text,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "All conversations", onClick = onBack)
                .padding(11.dp),
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
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "New chat",
            tint = Ink.Text,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "New chat", onClick = onNewChat)
                .padding(11.dp),
        )
    }
}

@Composable
private fun Welcome(suggestions: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "What can I help with?",
            color = Ink.Text,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ask anything, or start with one of these.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                suggestions.forEach { line ->
                    SuggestionCard(text = line, onClick = { onPick(line) })
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(Ink.Card)
            .clickable(onClickLabel = "Use this prompt", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Ink.Text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = "+", color = Ink.Accent, style = MaterialTheme.typography.titleMedium)
    }
}
