package com.jarvis.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ChatMsg
import com.jarvis.assistant.JarvisViewModel
import com.jarvis.assistant.Screen
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun JarvisApp(vm: JarvisViewModel) {
    when (vm.screen) {
        Screen.Chat -> ChatScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
    }
}

@Composable
fun ChatScreen(vm: JarvisViewModel) {
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            JarvisDrawer(vm) { scope.launch { drawer.close() } }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppBackground)
        ) {
            Column(Modifier.fillMaxSize()) {
                HeaderBar(vm, onMenu = { scope.launch { drawer.open() } })
                MessageList(vm, Modifier.weight(1f))
                InputBar(vm)
            }
        }
    }
}

@Composable
private fun HeaderBar(vm: JarvisViewModel, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Menu, "open chats", tint = Palette.Text)
        }
        JarvisOrb(diameter = 34.dp, active = vm.busy)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Jarvis", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Palette.Text)
            Text(
                vm.status.ifBlank { "Offline assistant" },
                fontSize = 11.sp,
                color = if (vm.busy) Palette.Cyan else Palette.TextDim,
                maxLines = 1,
            )
        }
        ToggleIcon(vm.speakOn, Icons.AutoMirrored.Filled.VolumeUp, Icons.AutoMirrored.Filled.VolumeOff, "speak on or off") {
            vm.setSpeak(!vm.speakOn)
        }
        IconButton(onClick = { vm.newChat() }) {
            Icon(Icons.Filled.AddComment, "new chat", tint = Palette.Cyan)
        }
    }
}

@Composable
private fun ToggleIcon(
    active: Boolean,
    onIcon: ImageVector,
    offIcon: ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (active) onIcon else offIcon,
            contentDescription = desc,
            tint = if (active) Palette.Cyan else Palette.TextDim,
        )
    }
}

@Composable
private fun MessageList(vm: JarvisViewModel, modifier: Modifier) {
    val state = rememberLazyListState()
    val rows = vm.messages.size + if (vm.busy) 1 else 0
    LaunchedEffect(rows) {
        if (rows > 0) state.animateScrollToItem(rows - 1)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        state = state,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(vm.messages, key = { it.id }) { msg -> MessageBubble(msg) }
        if (vm.busy) {
            item(key = "typing") { TypingBubble() }
        }
        item(key = "tail") { Spacer(Modifier.size(6.dp)) }
    }
}

@Composable
private fun MessageBubble(msg: ChatMsg) {
    val shape = if (msg.fromUser) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .widthIn(max = 312.dp)
                .clip(shape)
                .then(
                    if (msg.fromUser) {
                        Modifier.background(UserBubble)
                    } else {
                        Modifier
                            .background(Palette.Card)
                            .border(1.dp, Palette.Line.copy(alpha = 0.7f), shape)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            if (msg.fromUser) {
                Text(msg.text, color = Color.White, fontSize = 16.sp, lineHeight = 23.sp)
            } else {
                MarkdownText(msg.text, color = Palette.Text, accent = Palette.Cyan)
            }
        }
    }
}

/** Three coloured dots bobbing up and down while Jarvis is thinking. */
@Composable
private fun TypingBubble() {
    val twoPi = (2f * PI).toFloat()
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "phase",
    )
    val shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val dots = listOf(Palette.Violet, Palette.Cyan, Palette.Pink)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier
                .clip(shape)
                .background(Palette.Card)
                .border(1.dp, Palette.Line.copy(alpha = 0.7f), shape)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dots.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .offset(y = (sin(phase + i * 0.8f) * 4f).dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(c)
                )
                if (i < dots.lastIndex) Spacer(Modifier.width(7.dp))
            }
        }
    }
}

@Composable
private fun InputBar(vm: JarvisViewModel) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message Jarvis", color = Palette.TextDim) },
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (text.isNotBlank()) {
                    vm.submit(text)
                    text = ""
                }
            }),
        )
        IconButton(onClick = { vm.startListening() }) {
            Icon(Icons.Filled.Mic, "speak", tint = Palette.Teal)
        }
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    vm.submit(text)
                    text = ""
                }
            },
            enabled = !vm.busy,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                "send",
                tint = if (vm.busy) Palette.TextDim else Palette.Pink,
            )
        }
    }
}
