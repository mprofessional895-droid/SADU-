package com.sadu.assistant.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sadu.assistant.AssistantText
import com.sadu.assistant.SaduViewModel
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.components.EmptyState
import com.sadu.assistant.ui.components.GhostButton
import com.sadu.assistant.ui.components.SaduTextField
import com.sadu.assistant.ui.components.SectionLabel
import com.sadu.assistant.ui.components.SurfaceCard

@Composable
fun MemoryScreen(vm: SaduViewModel) {
    MemoryContent(
        facts = vm.facts,
        name = AssistantText.nameFrom(vm.facts),
        onAdd = { vm.addFact(it) },
        onUpdate = { old, new -> vm.updateFact(old, new) },
        onDelete = { vm.removeFact(it) },
        onClearAll = { vm.clearMemory() },
    )
}

@Composable
fun MemoryContent(
    facts: List<String>,
    name: String?,
    onAdd: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.padding(horizontal = Dimens.gutter)) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Memory")
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (name != null) "You are $name to Sadu." else "What Sadu knows about you.",
                color = Ink.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Kept in this app on this phone. It is never uploaded. Sadu only writes here " +
                    "when you ask it to remember something.",
                color = Ink.TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SaduTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = "Something to remember",
                    maxLines = 3,
                    onSend = {
                        val t = draft.trim()
                        if (t.isNotEmpty()) {
                            onAdd(t)
                            draft = ""
                        }
                    },
                    contentDescription = "New memory",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                GhostButton(
                    text = "Save",
                    onClick = {
                        val t = draft.trim()
                        if (t.isNotEmpty()) {
                            onAdd(t)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (facts.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Psychology,
                title = "Nothing saved yet",
                body = "Ask Sadu to remember something - \"remember that I take my coffee black\" - " +
                    "or add it above. It will show up here so you can edit or delete it.",
            )
            Spacer(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.gap),
            ) {
                items(facts, key = { it }) { fact ->
                    if (editing == fact) {
                        EditRow(
                            value = editText,
                            onValueChange = { editText = it },
                            onSave = {
                                onUpdate(fact, editText)
                                editing = null
                            },
                            onCancel = { editing = null },
                        )
                    } else {
                        FactRow(
                            fact = fact,
                            onEdit = {
                                editText = fact
                                editing = fact
                            },
                            onDelete = { onDelete(fact) },
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = Dimens.gutter)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${facts.size} item${if (facts.size == 1) "" else "s"} stored",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(10.dp))
                GhostButton(text = "Delete everything", onClick = onClearAll)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun FactRow(fact: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    SurfaceCard(contentPadding = PaddingValues(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fact,
                color = Ink.Text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Edit memory",
                tint = Ink.TextDim,
                modifier = Modifier
                    .size(Dimens.touch)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Edit", onClick = onEdit)
                    .padding(14.dp),
            )
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete memory",
                tint = Ink.TextDim,
                modifier = Modifier
                    .size(Dimens.touch)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Delete", onClick = onDelete)
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun EditRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Dimens.cardRadius))
            .background(Ink.Card)
            .border(1.dp, Ink.Text, RoundedCornerShape(Dimens.cardRadius))
            .padding(12.dp),
    ) {
        Column {
            SaduTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "Memory",
                maxLines = 3,
                onSend = onSave,
                contentDescription = "Edit memory",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton(text = "Save", onClick = onSave, icon = Icons.Rounded.Check)
                GhostButton(text = "Cancel", onClick = onCancel, icon = Icons.Rounded.Close)
            }
        }
    }
}
