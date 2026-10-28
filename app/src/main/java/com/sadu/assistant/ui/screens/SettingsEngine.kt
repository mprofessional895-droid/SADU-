package com.sadu.assistant.ui.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sadu.assistant.AssistantText
import com.sadu.assistant.brain.LocalEngine
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.components.GhostButton
import com.sadu.assistant.ui.components.HairlineDivider
import com.sadu.assistant.ui.components.KeyValueRow
import com.sadu.assistant.ui.components.StatusPill
import com.sadu.assistant.ui.components.SurfaceCard

/**
 * The on-device engine, and the model file that goes with it.
 *
 * Both engines are real, and between them they answer "which file do I have?": a `.task` bundle
 * for MediaPipe, a `.gguf` file for llama.cpp. The choice is one setting, the file is separate,
 * and the other engine's file is never touched - so trying the `.gguf` engine cannot cost anyone
 * the setup they already have.
 */
@Composable
fun EngineCard(
    choice: LocalEngine,
    available: Boolean,
    ready: Boolean,
    present: Boolean,
    bytes: Long,
    label: String,
    note: String?,
    copyPercent: Int,
    onChoose: (LocalEngine) -> Unit,
    onImportTask: () -> Unit,
    onImportGguf: () -> Unit,
    onReload: () -> Unit,
) {
    SurfaceCard {
        Text("On-device engine", color = Ink.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Sadu runs a model on this phone, so it works with no connection. Two engines can " +
                "run one: pick the engine, then import that kind of model file once.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocalEngine.entries.forEach { option ->
                StatusPill(
                    text = option.label,
                    active = option == choice,
                    modifier = Modifier.clickable { onChoose(option) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(choice.blurb, color = Ink.TextDim, style = MaterialTheme.typography.bodySmall)

        if (choice == LocalEngine.Llama && !available) {
            Spacer(Modifier.height(8.dp))
            Text(
                "This build was made without the Android NDK, so it has no llama.cpp library in " +
                    "it and cannot run a .gguf file. Switch back to MediaPipe, or build the app " +
                    "with the NDK.",
                color = Ink.Text,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(14.dp))
        KeyValueRow("Model", if (ready) "Loaded and running" else "Not running")
        HairlineDivider()
        KeyValueRow("Model file", if (present) AssistantText.bytes(bytes) else "Not imported")
        HairlineDivider()
        KeyValueRow("This model", label)

        if (copyPercent >= 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Copying the model file... $copyPercent%",
                color = Ink.Text,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (note != null) {
            Spacer(Modifier.height(10.dp))
            Text(note, color = Ink.TextDim, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (choice == LocalEngine.Llama) {
                GhostButton(
                    text = if (present) "Replace .gguf file" else "Import .gguf model",
                    onClick = onImportGguf,
                )
            } else {
                GhostButton(
                    text = if (present) "Replace .task file" else "Import .task model",
                    onClick = onImportTask,
                )
            }
            GhostButton(text = "Reload", onClick = onReload, enabled = present)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "The engine reads one model file. A .task bundle belongs to MediaPipe; a .gguf file " +
                "belongs to llama.cpp. Switching engines keeps both files, so you can go back and " +
                "forth without importing anything again.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
