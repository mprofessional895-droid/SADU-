package com.jarvis.assistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ChatMsg
import com.jarvis.assistant.JarvisViewModel
import com.jarvis.assistant.Screen
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
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HeaderBar(vm)
        MessageList(vm, Modifier.weight(1f))
        InputBar(vm)
    }
}

@Composable
private fun HeaderBar(vm: JarvisViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JarvisOrb(listening = vm.status.startsWith("Listening"), size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Jarvis",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                vm.status.ifBlank { "Offline assistant - ready" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        ToggleIcon(vm.speakOn, Icons.Filled.VolumeUp, Icons.Filled.VolumeOff, "speak on or off") {
            vm.setSpeak(!vm.speakOn)
        }
        ToggleIcon(vm.internetOn, Icons.Filled.Public, Icons.Filled.Public, "internet tools") {
            vm.setInternet(!vm.internetOn)
        }
        IconButton(onClick = { vm.openSettings() }) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            tint = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JarvisOrb(listening: Boolean, size: Dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "phase"
    )
    val core by animateColorAsState(
        if (listening) Color(0xFF39D2C0) else Color(0xFF4C8DFF),
        label = "core"
    )
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(core.copy(alpha = 0.12f), radius = r)
        for (i in 0..2) {
            val t = (phase + i * (2f * PI.toFloat() / 3f)) % (2f * PI.toFloat())
            val rr = r * (0.55f + 0.4f * (0.5f + 0.5f * sin(t)))
            drawCircle(
                color = core.copy(alpha = 0.30f - i * 0.07f),
                radius = rr,
                style = Stroke(width = 2.2f)
            )
        }
        drawCircle(core, radius = r * 0.34f)
    }
}

@Composable
private fun MessageList(vm: JarvisViewModel, modifier: Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) state.animateScrollToItem(vm.messages.size - 1)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(vm.messages, key = { it.id }) { msg ->
            MessageBubble(msg)
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMsg) {
    val bubbleColor = if (msg.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (msg.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(msg.text, color = textColor, fontSize = 16.sp)
        }
    }
}

@Composable
private fun InputBar(vm: JarvisViewModel) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message Jarvis") },
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (text.isNotBlank()) {
                    vm.submit(text)
                    text = ""
                }
            })
        )
        IconButton(onClick = { vm.startListening() }) {
            Icon(Icons.Filled.Mic, contentDescription = "speak", tint = MaterialTheme.colorScheme.secondary)
        }
        if (vm.busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    vm.submit(text)
                    text = ""
                }
            }) {
                Icon(Icons.Filled.Send, contentDescription = "send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
