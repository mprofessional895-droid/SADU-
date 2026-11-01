package com.sadu.assistant.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.sadu.assistant.Mode
import com.sadu.assistant.PromptOverride
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.components.SectionLabel
import com.sadu.assistant.ui.components.SurfaceCard

/**
 * TEMPORARY prompt editor, for manual testing.
 *
 * Off = the prompts the app ships. On = exactly what is typed here, with nothing added: no
 * facts, no tool menu, no rules. An empty box is sent as no instructions at all.
 */
@Composable
fun PromptEditorCard(
    on: Boolean,
    chatText: String,
    controlText: String,
    onToggle: (Boolean) -> Unit,
    onChatText: (String) -> Unit,
    onControlText: (String) -> Unit,
    builtIn: (Mode) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionLabel("Mere prompt (temporary)", modifier = Modifier.padding(bottom = 8.dp))
        SurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Mere prompt use karo",
                        color = Ink.Text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (on) "Neeche jo likha hai, bas wahi jayega." else
                            "Off: app ka apna prompt jayega, jaisa banaya gaya hai.",
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = on, onCheckedChange = onToggle)
            }

            if (on) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = PromptOverride.NOTE,
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                PromptBox(
                    title = "Chat mode ka prompt",
                    hint = "Khali chhoda to Chat me koi instruction nahi jayegi.",
                    text = chatText,
                    onChange = onChatText,
                    onDefault = { onChatText(builtIn(Mode.Chat)) },
                )
                Spacer(Modifier.height(18.dp))
                PromptBox(
                    title = "Control mode ka prompt",
                    hint = "Khali chhoda to Control me koi instruction nahi jayegi.",
                    text = controlText,
                    onChange = onControlText,
                    onDefault = { onControlText(builtIn(Mode.Control)) },
                )
            }
        }
    }
}

@Composable
private fun PromptBox(
    title: String,
    hint: String,
    text: String,
    onChange: (String) -> Unit,
    onDefault: () -> Unit,
) {
    Column {
        Text(title, color = Ink.Text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(hint, color = Ink.TextDim, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = {
                Text(
                    text = "Yahan likho…",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Default,
            ),
            shape = MaterialTheme.shapes.medium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (text.isEmpty()) "khali" else "${text.length} characters",
                color = Ink.TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
            Row {
                TextButton(onClick = onDefault) { Text("Default bhar do") }
                TextButton(onClick = { onChange("") }) { Text("Khali karo") }
            }
        }
    }
}
