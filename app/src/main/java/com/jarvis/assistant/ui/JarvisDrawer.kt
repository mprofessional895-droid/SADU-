package com.jarvis.assistant.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ChatSession
import com.jarvis.assistant.JarvisViewModel

/** The sidebar: every saved chat, plus the main buttons, like the big chat apps. */
@Composable
fun JarvisDrawer(vm: JarvisViewModel, close: () -> Unit) {
    val ctx = LocalContext.current
    ModalDrawerSheet(
        modifier = Modifier.width(308.dp),
        drawerContainerColor = Palette.Ink2,
        drawerContentColor = Palette.Text,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JarvisOrb(diameter = 36.dp, active = vm.busy)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Jarvis", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        if (vm.modelReady) "Offline - ready" else "Model not loaded",
                        color = if (vm.modelReady) Palette.Lime else Palette.Amber,
                        fontSize = 11.sp,
                    )
                }
                IconButton(onClick = close) {
                    Icon(Icons.Filled.Close, "close menu", tint = Palette.TextDim)
                }
            }

            Button(
                onClick = {
                    vm.newChat()
                    close()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Violet,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Filled.AddComment, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New chat", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "CHATS",
                color = Palette.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 22.dp, bottom = 6.dp),
            )

            LazyColumn(Modifier.weight(1f)) {
                items(vm.sessions, key = { it.id }) { s ->
                    ChatRow(
                        session = s,
                        selected = s.id == vm.currentId,
                        onOpen = {
                            vm.openChat(s.id)
                            close()
                        },
                        onDelete = { vm.deleteChat(s.id) },
                    )
                }
            }

            HorizontalDivider(color = Palette.Line)
            ToggleRow(Icons.AutoMirrored.Filled.VolumeUp, Icons.AutoMirrored.Filled.VolumeOff, "Speak replies", vm.speakOn) {
                vm.setSpeak(it)
            }
            ToggleRow(Icons.Filled.Language, Icons.Filled.Language, "Internet tools", vm.internetOn) {
                vm.setInternet(it)
            }
            ActionRow(
                icon = Icons.Filled.Accessibility,
                label = if (vm.a11yConnected) "Screen control: on" else "Screen control: off",
                tint = if (vm.a11yConnected) Palette.Lime else Palette.TextDim,
            ) {
                close()
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            ActionRow(Icons.Filled.Settings, "Settings", Palette.TextDim) {
                vm.openSettings()
                close()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChatRow(
    session: ChatSession,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Palette.Card2 else Color.Transparent)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ChatBubbleOutline,
            null,
            Modifier.size(16.dp),
            tint = if (selected) Palette.Cyan else Palette.TextDim,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.title,
                color = if (selected) Palette.Text else Palette.TextDim,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${session.messages.count { it.fromUser }} messages",
                color = Palette.TextDim.copy(alpha = 0.65f),
                fontSize = 11.sp,
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.DeleteOutline,
                "delete chat",
                Modifier.size(18.dp),
                tint = Palette.TextDim,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    onIcon: ImageVector,
    offIcon: ImageVector,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (checked) onIcon else offIcon,
            null,
            Modifier.size(18.dp),
            tint = if (checked) Palette.Cyan else Palette.TextDim,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = Palette.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Palette.Text, fontSize = 14.sp)
    }
}
