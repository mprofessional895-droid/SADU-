package com.sadu.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadu.assistant.perf.Heat
import com.sadu.assistant.perf.PerfMode
import com.sadu.assistant.perf.PerfMonitor
import java.util.Locale

/**
 * The performance panel: what the last turn actually did, and the three switches that change it.
 *
 * It reads one snapshot the inference path left behind, so opening it costs nothing while a
 * model is writing.
 */
@Composable
fun PerformancePanel(
    onClose: () -> Unit,
    onMode: (PerfMode) -> Unit,
    backgroundOn: Boolean,
    onBackground: (Boolean) -> Unit,
    onUnload: () -> Unit,
) {
    val s = PerfMonitor.snapshot
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Ink.Card, Ink.Background)))
                .border(1.dp, Ink.Border, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState())
                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "PERFORMANCE",
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (s.running) "Writing a reply" else "Idle",
                        color = Ink.Text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Ink.Elevated)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Ink.TextDim, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerfMode.entries.forEach { mode ->
                    val on = mode == s.mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) Ink.Elevated else Ink.Card.copy(alpha = 0.5f))
                            .border(1.dp, if (on) Ink.Text.copy(alpha = 0.35f) else Ink.BorderSoft, RoundedCornerShape(12.dp))
                            .clickable { onMode(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            color = if (on) Ink.Text else Ink.TextDim,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Metric("Generation", if (s.genTokS > 0) "${one(s.genTokS)} tok/s" else "-")
            Metric("Prompt processing", if (s.promptTokS > 0) "${one(s.promptTokS)} tok/s" else "-")
            Metric("Tokens written", if (s.genTokens > 0) "${s.genTokens}" else "-")
            Metric("Prefill / generate", "${s.promptMs} ms / ${s.genMs} ms")
            Metric("Last turn", if (s.lastTurnMs > 0) "${s.lastTurnMs / 1000.0}s" else "-")
            Metric("Threads", if (s.threads > 0) "${s.threads} of ${Runtime.getRuntime().availableProcessors()}" else "-")
            Metric("Context window", if (s.contextTokens > 0) "${s.contextTokens} tokens" else "-")
            Metric("Model file", if (s.modelMb > 0) "${s.modelMb / (1024 * 1024)} MB" else "-")
            Metric("App memory", if (s.processMb > 0) "${s.processMb} MB" else "-")
            Metric("Phone memory free", if (s.totalMb > 0) "${s.availableMb} of ${s.totalMb} MB" else "-")
            Metric("Heat", heatLabel(s.heat))
            Metric("Background", if (s.background) "Kept alive" else "In app only")

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Generation speed is what llama.cpp measured for the last reply. A cold " +
                    "phone writes faster than a warm one; that is the phone, not a bug. Heat is " +
                    "the platform's thermal band, not a °C reading.",
                color = Ink.TextDim.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.Card.copy(alpha = 0.6f))
                    .border(1.dp, Ink.BorderSoft, RoundedCornerShape(14.dp))
                    .clickable { onBackground(!backgroundOn) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep writing in the background", color = Ink.Text, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "A notification stays up and the reply finishes with the screen off.",
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (backgroundOn) Color(0xFF2EE6D6).copy(alpha = 0.9f) else Ink.Border),
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Ink.Border, RoundedCornerShape(14.dp))
                    .clickable(onClick = onUnload)
                    .padding(14.dp),
            ) {
                Text(
                    "Unload the model now (frees memory; it reloads when you ask something)",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink.TextDim, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
        Spacer(Modifier.weight(1f))
        Text(value, color = Ink.Text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
    }
}

private fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun heatLabel(heat: Heat): String = when (heat) {
    Heat.None -> "Normal"
    Heat.Light -> "Light"
    Heat.Moderate -> "Moderate"
    Heat.Severe -> "Severe - threads reduced"
    Heat.Critical -> "Critical - reply shortened"
}
