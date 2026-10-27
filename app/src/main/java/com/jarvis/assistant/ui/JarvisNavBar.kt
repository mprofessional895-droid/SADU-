package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.Tab

private data class NavItem(val tab: Tab, val label: String, val icon: ImageVector)

private val Items = listOf(
    NavItem(Tab.Home, "Home", Icons.Rounded.Home),
    NavItem(Tab.Conversations, "Chats", Icons.Rounded.ChatBubbleOutline),
    NavItem(Tab.Memory, "Memory", Icons.Rounded.Psychology),
    NavItem(Tab.Actions, "Actions", Icons.Rounded.TouchApp),
    NavItem(Tab.Settings, "Settings", Icons.Rounded.Settings),
)

/**
 * Five sections, hairline on top, active item in white with a dot underneath.
 * Each item is a full 48dp-plus touch target.
 */
@Composable
fun JarvisNavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink.Background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Items.forEach { item ->
                val active = item.tab == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClickLabel = item.label) { onSelect(item.tab) }
                        .semantics { selected = active },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (active) Ink.Text else Ink.TextDim,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = item.label,
                        color = if (active) Ink.Text else Ink.TextDim,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(if (active) Ink.Text else Color.Transparent),
                    )
                }
            }
        }
    }
}
