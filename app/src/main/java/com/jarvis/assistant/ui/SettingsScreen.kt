package com.jarvis.assistant.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.JarvisViewModel

@Composable
fun SettingsScreen(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.closeSettings() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }

        SectionTitle("Assistant")
        SettingSwitch("Speak replies aloud", vm.speakOn) { vm.setSpeak(it) }
        SettingSwitch("Allow internet tools (search, links, install)", vm.internetOn) { vm.setInternet(it) }
        SettingSwitch("Listen for \"Hey Jarvis\" in the background (experimental)", vm.wakeOn) { vm.setWake(it) }

        SectionTitle("Safety")
        SettingSwitch("Ask before sending a text", vm.confirmSms) { vm.changeConfirmSms(it) }
        SettingSwitch("Call directly (skip the dialer)", vm.directCall) { vm.changeDirectCall(it) }

        SectionTitle("Screen control")
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    if (vm.a11yConnected)
                        "Connected. Jarvis can tap, type, scroll and read the screen when you ask."
                    else
                        "Off. Turn on \"Jarvis screen control\" to let Jarvis tap, type, scroll and read the screen.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Open accessibility settings") }
            }
        }

        SectionTitle("AI model")
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    if (vm.modelPresent) "Model installed. ${vm.status}" else "No model yet. Pick a Gemma 3 1B .task file for offline chat.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose model file") }
                    if (vm.modelPresent) {
                        OutlinedButton(onClick = { vm.loadModel() }) { Text("Reload") }
                    }
                }
            }
        }

        SectionTitle("Memory and chat")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { vm.clearMemory() }, modifier = Modifier.weight(1f)) {
                Text("Forget me")
            }
            OutlinedButton(onClick = { vm.clearChat() }, modifier = Modifier.weight(1f)) {
                Text("Clear chat")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Jarvis runs privately on your phone. Commands never leave the device; " +
                "only the internet tools use the network, and only when you switch Internet on.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title.uppercase(),
        color = MaterialTheme.colorScheme.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
