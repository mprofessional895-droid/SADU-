package com.jarvis.assistant.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.AssistantText
import com.jarvis.assistant.JarvisViewModel
import com.jarvis.assistant.Mode
import com.jarvis.assistant.brain.CloudProvider
import com.jarvis.assistant.READY_NOTE
import com.jarvis.assistant.ui.Dimens
import com.jarvis.assistant.ui.Ink
import com.jarvis.assistant.ui.components.ActionRow
import com.jarvis.assistant.ui.components.GhostButton
import com.jarvis.assistant.ui.components.HairlineDivider
import com.jarvis.assistant.ui.components.KeyValueRow
import com.jarvis.assistant.ui.components.ModeSwitch
import com.jarvis.assistant.ui.components.SectionLabel
import com.jarvis.assistant.ui.components.SurfaceCard
import com.jarvis.assistant.ui.components.ToggleRow

data class LanguageOption(val tag: String, val label: String)

val Languages = listOf(
    LanguageOption("auto", "Automatic (phone language)"),
    LanguageOption("en-US", "English (US)"),
    LanguageOption("en-IN", "English (India)"),
    LanguageOption("hi-IN", "Hindi"),
    LanguageOption("bn-IN", "Bengali"),
    LanguageOption("gu-IN", "Gujarati"),
    LanguageOption("kn-IN", "Kannada"),
    LanguageOption("ml-IN", "Malayalam"),
    LanguageOption("mr-IN", "Marathi"),
    LanguageOption("ta-IN", "Tamil"),
    LanguageOption("te-IN", "Telugu"),
    LanguageOption("ur-PK", "Urdu"),
    LanguageOption("ar-SA", "Arabic"),
    LanguageOption("de-DE", "German"),
    LanguageOption("es-ES", "Spanish"),
    LanguageOption("fr-FR", "French"),
    LanguageOption("ja-JP", "Japanese"),
    LanguageOption("zh-CN", "Chinese"),
)

/** Everything the Settings screen shows. Plain data so it can be rendered without a device. */
data class SettingsUiState(
    val mode: Mode = Mode.Default,
    val modelReady: Boolean = false,
    val modelPresent: Boolean = false,
    val modelNote: String? = null,
    val modelBytes: Long = 0L,
    val speakOn: Boolean = true,
    val internetOn: Boolean = false,
    val wakeOn: Boolean = false,
    val confirmSms: Boolean = true,
    val directCall: Boolean = false,
    val language: String = "auto",
    val ttsReady: Boolean = false,
    val ttsVoiceMissing: Boolean = false,
    val speechAvailable: Boolean = false,
    val a11yConnected: Boolean = false,
    val conversationCount: Int = 0,
    val memoryCount: Int = 0,
    val chatBytes: Long = 0L,
    val grantedCount: Int = 0,
    val permissionCount: Int = 0,
    val versionLine: String = "",
    val contextTokens: Int = 4096,
    val contextEffective: Int = 0,
    val cloudEnabled: Boolean = false,
    val cloudProvider: CloudProvider = CloudProvider.Compatible,
    val cloudBaseUrl: String = "",
    val cloudModel: String = "",
    val cloudKeySet: Boolean = false,
    val cloudKeyProtected: Boolean = false,
    val cloudTest: String? = null,
)

@Composable
fun SettingsScreen(vm: JarvisViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    LaunchedEffect(Unit) { vm.refreshRuntime() }

    SettingsContent(
        state = SettingsUiState(
            mode = vm.mode,
            modelReady = vm.modelReady,
            modelPresent = vm.modelPresent,
            modelNote = vm.modelNote,
            modelBytes = vm.modelBytes,
            speakOn = vm.speakOn,
            internetOn = vm.internetOn,
            wakeOn = vm.wakeOn,
            confirmSms = vm.confirmSms,
            directCall = vm.directCall,
            language = vm.language,
            ttsReady = vm.ttsReady,
            ttsVoiceMissing = vm.ttsVoiceMissing,
            speechAvailable = vm.speechAvailable,
            a11yConnected = vm.a11yConnected,
            conversationCount = vm.sessions.count { it.askedCount > 0 },
            memoryCount = vm.facts.size,
            chatBytes = vm.chatBytes,
            grantedCount = vm.granted.size,
            permissionCount = com.jarvis.assistant.PhoneActionCatalog.runtimePermissions(Build.VERSION.SDK_INT).size,
            versionLine = vm.versionLine,
            contextTokens = vm.contextTokens,
            contextEffective = vm.contextEffective,
            cloudEnabled = vm.cloudEnabled,
            cloudProvider = vm.cloudProvider,
            cloudBaseUrl = vm.cloudBaseUrl,
            cloudModel = vm.cloudModel,
            cloudKeySet = vm.cloudKeySet,
            cloudKeyProtected = vm.cloudKeyProtected,
            cloudTest = vm.cloudTest,
        ),
        onMode = { vm.chooseMode(it) },
        onImportModel = { picker.launch(arrayOf("*/*")) },
        onReloadModel = { vm.loadModel() },
        onSpeak = { vm.setSpeak(it) },
        onWake = { vm.setWake(it) },
        onInternet = { vm.setInternet(it) },
        onConfirmSms = { vm.changeConfirmSms(it) },
        onDirectCall = { vm.changeDirectCall(it) },
        onLanguage = { vm.chooseLanguage(it) },
        onDeleteEverything = {
            vm.clearMemory()
            vm.deleteAllChats()
        },
        onContextTokens = { vm.chooseContextTokens(it) },
        onCloudEnabled = { vm.chooseCloudEnabled(it) },
        onCloudProvider = { vm.chooseCloudProvider(it) },
        onCloudBaseUrl = { vm.editCloudBaseUrl(it) },
        onCloudModel = { vm.editCloudModel(it) },
        onCloudKey = { vm.editCloudApiKey(it) },
        onClearCloudKey = { vm.clearCloudApiKey() },
        onTestCloud = { vm.testCloud() },
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onMode: (Mode) -> Unit,
    onImportModel: () -> Unit,
    onReloadModel: () -> Unit,
    onSpeak: (Boolean) -> Unit,
    onWake: (Boolean) -> Unit,
    onInternet: (Boolean) -> Unit,
    onConfirmSms: (Boolean) -> Unit,
    onDirectCall: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onDeleteEverything: () -> Unit,
    onContextTokens: (Int) -> Unit = {},
    onCloudEnabled: (Boolean) -> Unit = {},
    onCloudProvider: (CloudProvider) -> Unit = {},
    onCloudBaseUrl: (String) -> Unit = {},
    onCloudModel: (String) -> Unit = {},
    onCloudKey: (String) -> Unit = {},
    onClearCloudKey: () -> Unit = {},
    onTestCloud: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item(key = "title") {
            Column {
                SectionLabel("Settings")
                Spacer(Modifier.height(6.dp))
                Text("How Jarvis behaves", color = Ink.Text, style = MaterialTheme.typography.titleLarge)
            }
        }

        // ------------------------------------------------------------------- model
        // -------------------------------------------------------------------- mode
        item(key = "mode") {
            Column {
                SectionLabel("What Jarvis may do", modifier = Modifier.padding(bottom = 8.dp))
                SurfaceCard {
                    Text(
                        text = AssistantText.modeLabel(state.mode) + " mode",
                        color = Ink.Text,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = AssistantText.modeLine(state.mode),
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    ModeSwitch(mode = state.mode, onSelect = onMode)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Chat keeps the assistant's full attention on talking with you. " +
                            "Control gives it your phone as well, so it can send messages, call, " +
                            "set alarms, open apps and work the screen.",
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item(key = "model") {
            Column {
                SectionLabel("The assistant's brain", modifier = Modifier.padding(bottom = 8.dp))
                SurfaceCard {
                    KeyValueRow("Model", if (state.modelReady) "Loaded and running" else "Not running")
                    HairlineDivider()
                    KeyValueRow("Model file", if (state.modelPresent) AssistantText.bytes(state.modelBytes) else "Not imported")
                    if (state.modelNote != null && state.modelNote != READY_NOTE) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = state.modelNote,
                            color = Ink.TextDim,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton(
                            text = if (state.modelPresent) "Replace file" else "Import model",
                            onClick = onImportModel,
                        )
                        GhostButton(text = "Reload", onClick = onReloadModel, enabled = state.modelPresent)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Jarvis runs a Gemma model on this phone, so it works with no connection. " +
                            "Import the .task file once and it stays here.",
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // -------------------------------------------------------- memory window
        item(key = "context") {
            Column {
                SectionLabel("Conversation memory", modifier = Modifier.padding(bottom = 8.dp))
                ContextWindowCard(
                    current = state.contextTokens,
                    effective = state.contextEffective,
                    onPick = onContextTokens,
                )
            }
        }

        // ------------------------------------------------------------------- cloud
        item(key = "cloud") {
            Column {
                SectionLabel("Cloud brain", modifier = Modifier.padding(bottom = 8.dp))
                CloudCard(
                    enabled = state.cloudEnabled,
                    provider = state.cloudProvider,
                    baseUrl = state.cloudBaseUrl,
                    model = state.cloudModel,
                    keySet = state.cloudKeySet,
                    keyProtected = state.cloudKeyProtected,
                    test = state.cloudTest,
                    onEnabled = onCloudEnabled,
                    onProvider = onCloudProvider,
                    onBaseUrl = onCloudBaseUrl,
                    onModel = onCloudModel,
                    onKey = onCloudKey,
                    onClearKey = onClearCloudKey,
                    onTest = onTestCloud,
                )
            }
        }

        // ------------------------------------------------------------------- voice
        item(key = "voice") {
            Column {
                SectionLabel("Voice", modifier = Modifier.padding(bottom = 8.dp))
                SurfaceCard {
                    ToggleRow(
                        title = "Speak replies out loud",
                        subtitle = "Turn this off to read answers without sound",
                        checked = state.speakOn,
                        onCheckedChange = onSpeak,
                    )
                    HairlineDivider()
                    ToggleRow(
                        title = "Listen for a wake word",
                        subtitle = "Keeps the microphone open in the background (experimental)",
                        checked = state.wakeOn,
                        onCheckedChange = onWake,
                    )
                    HairlineDivider()
                    LanguageRow(
                        current = state.language,
                        onLanguage = onLanguage,
                    )
                    if (state.ttsVoiceMissing) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "This phone has no voice installed for that language, so replies stay silent. " +
                                "Add it in Android's text-to-speech settings.",
                            color = Ink.TextDim,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------- internet
        item(key = "internet") {
            Column {
                SectionLabel("Connection", modifier = Modifier.padding(bottom = 8.dp))
                SurfaceCard {
                    ToggleRow(
                        title = "Allow internet access",
                        subtitle = "Off by default. Only used for the tools that need it",
                        checked = state.internetOn,
                        onCheckedChange = onInternet,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "With this off, Jarvis still chats, remembers and controls the phone, but " +
                            "web search, opening links and app installation are blocked.",
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // -------------------------------------------------------- messages & calls
        item(key = "calls") {
            Column {
                SectionLabel("Messages and calls", modifier = Modifier.padding(bottom = 8.dp))
                SurfaceCard {
                    ToggleRow(
                        title = "Ask before sending a message",
                        subtitle = "Jarvis repeats the message and waits for your yes",
                        checked = state.confirmSms,
                        onCheckedChange = onConfirmSms,
                    )
                    HairlineDivider()
                    ToggleRow(
                        title = "Place calls without asking",
                        subtitle = "Off means Jarvis opens the dialler instead",
                        checked = state.directCall,
                        onCheckedChange = onDirectCall,
                    )
                }
            }
        }

        // ----------------------------------------------------------------- privacy
        item(key = "privacy") {
            Column {
                SectionLabel("Your data", modifier = Modifier.padding(bottom = 8.dp))
                SurfaceCard {
                    KeyValueRow("Memories", state.memoryCount.toString())
                    HairlineDivider()
                    KeyValueRow("Conversations", state.conversationCount.toString())
                    HairlineDivider()
                    KeyValueRow("Chat history file", AssistantText.bytes(state.chatBytes))
                    HairlineDivider()
                    KeyValueRow("Internet", if (state.internetOn) "Allowed" else "Blocked")
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Your conversations and memories are files inside this app. They are not " +
                            "sent anywhere. Deleting them below cannot be undone.",
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Delete all memories and chats", onClick = onDeleteEverything)
                }
            }
        }

        // ----------------------------------------------------------------- runtime
        item(key = "runtime") {
            Column {
                SectionLabel("Runtime status", modifier = Modifier.padding(bottom = 8.dp))
                SurfaceCard {
                    KeyValueRow("Android", "API ${Build.VERSION.SDK_INT}")
                    HairlineDivider()
                    KeyValueRow("Jarvis version", state.versionLine)
                    HairlineDivider()
                    KeyValueRow(
                        "Screen control",
                        if (state.a11yConnected) "On" else "Off",
                    )
                    HairlineDivider()
                    KeyValueRow(
                        "Speech recognition",
                        if (state.speechAvailable) "Available" else "Not available",
                    )
                    HairlineDivider()
                    KeyValueRow(
                        "Speech output",
                        when {
                            !state.ttsReady -> "Loading"
                            state.ttsVoiceMissing -> "No voice for this language"
                            else -> "Ready"
                        },
                    )
                    HairlineDivider()
                    KeyValueRow(
                        "Permissions granted",
                        "${state.grantedCount} of ${state.permissionCount}",
                    )
                }
            }
        }

        item(key = "about") {
            Column {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Jarvis is a personal assistant that runs on your own phone. It is not a " +
                        "store app: it needs accessibility and message permissions, which you grant by hand.",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun LanguageRow(current: String, onLanguage: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = Languages.firstOrNull { it.tag == current }?.label ?: current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.touch + 10.dp)
                .clickable(onClickLabel = "Choose reply language") { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reply language", color = Ink.Text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(label, color = Ink.TextDim, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(
                imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Ink.TextDim,
                modifier = Modifier.size(20.dp),
            )
        }
        if (open) {
            Spacer(Modifier.height(4.dp))
            Languages.forEach { option ->
                val selected = option.tag == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .clickable {
                            onLanguage(option.tag)
                            open = false
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.label,
                        color = if (selected) Ink.Text else Ink.TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Ink.Text,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
