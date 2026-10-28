package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.Sampling
import com.jarvis.assistant.prompt.ChatPrompt
import com.jarvis.assistant.prompt.ChatStyle
import com.jarvis.assistant.ui.Ink
import com.jarvis.assistant.ui.components.JarvisTextField
import com.jarvis.assistant.ui.components.StatusPill
import com.jarvis.assistant.ui.components.SurfaceCard

/**
 * Chat mode's own settings.
 *
 * Everything here changes only Chat mode. Control mode has its own instructions in the code
 * and keeps them, so turning the answer style up cannot make a tool call misfire.
 */
@Composable
fun ChatBrainCard(
    style: ChatStyle,
    temperature: Float,
    instructions: String,
    onStyle: (ChatStyle) -> Unit,
    onTemperature: (Float) -> Unit,
    onInstructions: (String) -> Unit,
) {
    SurfaceCard {
        Text("Chat brain", color = Ink.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "How Jarvis talks when you are just talking. This is all of Chat mode - nothing " +
                "here can touch the phone, and nothing here is said to the model twice.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(14.dp))
        Text("Personality", color = Ink.Text, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatStyle.Choices.forEach { option ->
                StatusPill(
                    text = option.label,
                    active = option == style,
                    modifier = Modifier.clickable { onStyle(option) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (style) {
                ChatStyle.Minimal ->
                    "No personality imposed - the model simply answers your question. " +
                        "Closest to a plain chat app."
                ChatStyle.Friendly ->
                    "The same freedom, with a warmer voice."
            },
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Creativity", color = Ink.Text, style = MaterialTheme.typography.labelMedium)
            Text(
                text = Sampling.temperatureLabel(temperature) + " - " +
                    Sampling.temperatureHint(temperature),
                color = Ink.TextDim,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = temperature,
            onValueChange = onTemperature,
            valueRange = Sampling.MinTemperature..Sampling.MaxTemperature,
            steps = 4,
            colors = SliderDefaults.colors(
                thumbColor = Ink.Accent,
                activeTrackColor = Ink.Accent,
                inactiveTrackColor = Ink.Border,
                activeTickColor = Ink.Border,
                inactiveTickColor = Ink.Border,
            ),
        )
        Text(
            "Lower keeps the on-device model on the subject; higher makes it livelier and " +
                "more likely to wander. Control mode is not affected.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(14.dp))
        Text("Your own instructions", color = Ink.Text, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        JarvisTextField(
            value = instructions,
            onValueChange = onInstructions,
            placeholder = "Optional - for example: answer in Hinglish, keep it casual",
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            contentDescription = "Chat instructions",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Added to Chat mode only, up to ${ChatPrompt.MAX_EXTRA} characters.",
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
