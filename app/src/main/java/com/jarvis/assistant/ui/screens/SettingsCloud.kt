package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ContextBudget
import com.jarvis.assistant.ReplyLength
import com.jarvis.assistant.brain.CloudProvider
import com.jarvis.assistant.ui.Ink
import com.jarvis.assistant.ui.components.GhostButton
import com.jarvis.assistant.ui.components.JarvisTextField
import com.jarvis.assistant.ui.components.StatusPill
import com.jarvis.assistant.ui.components.SurfaceCard
import com.jarvis.assistant.ui.components.ToggleRow

/** How much of the conversation Jarvis keeps in mind, and how long its answers should be. */
@Composable
fun ContextWindowCard(
    current: Int,
    effective: Int,
    onPick: (Int) -> Unit,
    length: ReplyLength = ReplyLength.Default,
    onLength: (ReplyLength) -> Unit = {},
) {
    SurfaceCard {
        Text("Conversation memory", color = Ink.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "How much of this chat Jarvis keeps in mind. Larger remembers more of the " +
                "conversation and uses more memory on the phone.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContextBudget.Choices.forEach { option ->
                StatusPill(
                    text = ContextBudget.label(option),
                    active = option == current,
                    modifier = Modifier.clickable { onPick(option) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (effective > 0) {
                "The on-device model is running with " + ContextBudget.label(effective) + "."
            } else {
                "Jarvis will use this window the next time the model loads."
            },
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(18.dp))
        Text("Reply length", color = Ink.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "How long Jarvis should make its answers. Longer answers take more time on the " +
                "phone and cost more on a paid cloud model.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReplyLength.Choices.forEach { option ->
                StatusPill(
                    text = option.label,
                    active = option == length,
                    modifier = Modifier.clickable { onLength(option) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "The cloud model is allowed about " + length.tokens + " tokens per reply. " +
                "Ask for more or less in the message itself and Jarvis follows you.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Cloud model settings: provider, address, model name and an encrypted API key. */
@Composable
fun CloudCard(
    enabled: Boolean,
    provider: CloudProvider,
    baseUrl: String,
    model: String,
    keySet: Boolean,
    keyProtected: Boolean,
    test: String?,
    problem: String?,
    onEnabled: (Boolean) -> Unit,
    onProvider: (CloudProvider) -> Unit,
    onBaseUrl: (String) -> Unit,
    onModel: (String) -> Unit,
    onKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onTest: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    SurfaceCard {
        Text("Cloud brain", color = Ink.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Use a model on the internet when you want stronger answers. The on-device " +
                "model stays as the fallback when there is no connection.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        ToggleRow(
            title = "Use the cloud model",
            subtitle = if (enabled) "On - replies come from the cloud model" else "Off",
            checked = enabled,
            onCheckedChange = onEnabled,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudProvider.values().forEach { option ->
                StatusPill(
                    text = option.label.split(" ").first(),
                    active = option == provider,
                    modifier = Modifier.clickable { onProvider(option) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(provider.hint, color = Ink.TextDim, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        JarvisTextField(
            value = baseUrl,
            onValueChange = onBaseUrl,
            placeholder = provider.defaultBase,
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Cloud server address",
        )
        Spacer(Modifier.height(10.dp))
        JarvisTextField(
            value = model,
            onValueChange = onModel,
            placeholder = "Model name, for example gpt-4o-mini",
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Cloud model name",
        )
        Spacer(Modifier.height(10.dp))
        JarvisTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = if (keySet) "A key is saved - type to replace it" else "API key",
            modifier = Modifier.fillMaxWidth(),
            contentDescription = "Cloud API key",
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "Save key",
                onClick = { onKey(draft); draft = "" },
                enabled = draft.isNotBlank(),
            )
            GhostButton(text = "Test connection", onClick = onTest, enabled = enabled && problem == null)
            if (keySet) GhostButton(text = "Forget key", onClick = onClearKey)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                !keySet && provider != CloudProvider.Compatible ->
                    "Paste the API key from your provider's dashboard."
                !keySet ->
                    "No API key saved. Leave it empty only for a server on your own network."
                keyProtected -> "The API key is saved, encrypted on this phone."
                else -> "The API key is saved."
            },
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
        if (problem != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Not ready: " + problem,
                color = Ink.Text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (test != null) {
            Spacer(Modifier.height(8.dp))
            Text(test, color = Ink.Text, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Anything you send here leaves the phone, so use it only when you are happy with that.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
