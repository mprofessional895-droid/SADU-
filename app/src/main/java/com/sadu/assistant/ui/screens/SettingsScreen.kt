package com.sadu.assistant.ui.screens

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
import com.sadu.assistant.AssistantText
import com.sadu.assistant.SaduViewModel
import com.sadu.assistant.Mode
import com.sadu.assistant.ReplyLength
import com.sadu.assistant.Sampling
import com.sadu.assistant.brain.CloudProvider
import com.sadu.assistant.brain.LocalEngine
import com.sadu.assistant.prompt.ChatStyle
import com.sadu.assistant.READY_NOTE
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.components.ActionRow
import com.sadu.assistant.ui.components.GhostButton
import com.sadu.assistant.ui.components.HairlineDivider
import com.sadu.assistant.ui.components.KeyValueRow
import com.sadu.assistant.ui.components.ModeSwitch
import com.sadu.assistant.ui.components.SectionLabel
import com.sadu.assistant.ui.components.SurfaceCard
import com.sadu.assistant.ui.components.ToggleRow

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
    val engine: LocalEngine = LocalEngine.Default,
    val engineAvailable: Boolean = false,
    val modelLabel: String = "",
    val modelCopyPercent: Int = -1,
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
    val gpuEnabled: Boolean = false,
    val gpuAvailable: Boolean = false,
    val backend: String = "",
    val contextTokens: Int = 4096,
    val contextEffective: Int = 0,
    val replyLength: ReplyLength = ReplyLength.Default,
    val chatStyle: ChatStyle = ChatStyle.Default,
    val chatTemperature: Float = Sampling.DefaultTemperature,
    val chatInstructions: String = "",
    /** TEMPORARY: the hand-written prompts from Settings, for manual testing. */
    val customPromptOn: Boolean = false,
    val customChatPrompt: String = "",
    val customControlPrompt: String = "",
    val cloudEnabled: Boolean = false,
    val cloudProvider: CloudProvider = CloudProvider.Compatible,
    val cloudBaseUrl: String = "",
    val cloudModel: String = "",
    val cloudKeySet: Boolean = false,
    val cloudKeyProtected: Boolean = false,
    val cloudTest: String? = null,
    val cloudProblem: String? = null,
    val webAnswers: Boolean = true,
)

@Composable
fun SettingsScreen(vm: SaduViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    // Its own picker: the MediaPipe one and the llama.cpp one accept different files.
    val ggufPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importGguf(uri)
    }
    LaunchedEffect(Unit) { vm.refreshRuntime() }

    SettingsContent(
        state = SettingsUiState(
            mode = vm.mode,
            modelReady = vm.modelReady,
            modelPresent = vm.modelPresent,
            modelNote = vm.modelNote,
            modelBytes = vm.modelBytes,
            engine = vm.localEngineChoice,
            engineAvailable = vm.engineAvailable,
            modelLabel = vm.modelLabel,
            modelCopyPercent = vm.modelCopyPercent,
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
            permissionCount = com.sadu.assistant.PhoneActionCatalog.runtimePermissions(Build.VERSION.SDK_INT).size,
            versionLine = vm.versionLine,
            gpuEnabled = vm.gpuEnabled,
            gpuAvailable = vm.gpuAvailable,
            backend = vm.backendName,
            contextTokens = vm.contextTokens,
            contextEffective = vm.contextEffective,
            replyLength = vm.replyLength,
            chatStyle = vm.chatStyle,
            chatTemperature = vm.chatTemperature,
            chatInstructions = vm.chatInstructions,
            customPromptOn = vm.customPromptOn,
            customChatPrompt = vm.customChatPrompt,
            customControlPrompt = vm.customControlPrompt,
            cloudEnabled = vm.cloudEnabled,
            cloudProvider = vm.cloudProvider,
            cloudBaseUrl = vm.cloudBaseUrl,
            cloudModel = vm.cloudModel,
            cloudKeySet = vm.cloudKeySet,
            cloudKeyProtected = vm.cloudKeyProtected,
            cloudTest = vm.cloudTest,
            cloudProblem = vm.cloudProblem,
            webAnswers = vm.webAnswers,
        ),
        onMode = { vm.chooseMode(it) },
        onImportModel = { picker.launch(arrayOf("*/*")) },
        onImportGguf = { ggufPicker.launch(arrayOf("*/*")) },
        onChooseEngine = { vm.chooseEngine(it) },
        onGpu = { vm.chooseGpu(it) },
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
        onReplyLength = { vm.chooseReplyLength(it) },
        onChatStyle = { vm.chooseChatStyle(it) },
        onChatTemperature = { vm.chooseChatTemperature(it) },
        onChatInstructions = { vm.editChatInstructions(it) },
        onCloudEnabled = { vm.chooseCloudEnabled(it) },
        onCloudProvider = { vm.chooseCloudProvider(it) },
        onCloudBaseUrl = { vm.editCloudBaseUrl(it) },
        onCloudModel = { vm.editCloudModel(it) },
        onCloudKey = { vm.editCloudApiKey(it) },
        onClearCloudKey = { vm.clearCloudApiKey() },
        onTestCloud = { vm.testCloud() },
        onWebAnswers = { vm.chooseWebAnswers(it) },
        onCustomPrompt = { vm.useCustomPrompt(it) },
        onCustomChatPrompt = { vm.editCustomChatPrompt(it) },
        onCustomControlPrompt = { vm.editCustomControlPrompt(it) },
        onBuiltInPrompt = { vm.builtInPrompt(it) },
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onMode: (Mode) -> Unit,
    onImportModel: () -> Unit,
    onReloadModel: () -> Unit,
    onImportGguf: () -> Unit = {},
    onChooseEngine: (LocalEngine) -> Unit = {},
    onGpu: (Boolean) -> Unit = {},
    onSpeak: (Boolean) -> Unit,
    onWake: (Boolean) -> Unit,
    onInternet: (Boolean) -> Unit,
    onConfirmSms: (Boolean) -> Unit,
    onDirectCall: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    onDeleteEverything: () -> Unit,
    onContextTokens: (Int) -> Unit = {},
    onReplyLength: (ReplyLength) -> Unit = {},
    onChatStyle: (ChatStyle) -> Unit = {},
    onChatTemperature: (Float) -> Unit = {},
    onChatInstructions: (String) -> Unit = {},
    onCloudEnabled: (Boolean) -> Unit = {},
    onCloudProvider: (CloudProvider) -> Unit = {},
    onCloudBaseUrl: (String) -> Unit = {},
    onCloudModel: (String) -> Unit = {},
    onCloudKey: (String) -> Unit = {},
    onClearCloudKey: () -> Unit = {},
    onTestCloud: () -> Unit = {},
    onWebAnswers: (Boolean) -> Unit = {},
    onCustomPrompt: (Boolean) -> Unit = {},
    onCustomChatPrompt: (String) -> Unit = {},
    onCustomControlPrompt: (String) -> Unit = {},
    onBuiltInPrompt: (Mode) -> String = { "" },
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
                Text("How Sadu behaves", color = Ink.Text, style = MaterialTheme.typography.titleLarge)
            }
        }

        // ------------------------------------------------------------------- model
        // -------------------------------------------------------------------- mode
        item(key = "mode") {
            Column {
                SectionLabel("What Sadu may do", modifier = Modifier.padding(bottom = 8.dp))
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
                        text = "Chat keeps the assistant's full attention on talking with you " +
                            "and it cannot touch the phone. Control gives it your phone: it can " +
                            "read the screen, navigate, set alarms, open apps, call, message and " +
                            "install. The mode is the permission.",
                        color = Ink.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item(key = "model") {
            Column {
                SectionLabel("The assistant's brain", modifier = Modifier.padding(bottom = 8.dp))
                EngineCard(
                    choice = state.engine,
                    available = state.engineAvailable,
                    ready = state.modelReady,
                    present = state.modelPresent,
                    bytes = state.modelBytes,
                    label = state.modelLabel,
                    note = state.modelNote?.takeIf {
                        it != READY_NOTE && !(state.cloudEnabled && state.cloudProblem == null)
                    },
                    copyPercent = state.modelCopyPercent,
                    onChoose = onChooseEngine,
                    onImportTask = onImportModel,
                    onImportGguf = onImportGguf,
                    onReload = onReloadModel,
                )
                if (state.engine == LocalEngine.Llama) {
                    Spacer(Modifier.height(12.dp))
                    GpuCard(
                        enabled = state.gpuEnabled,
                        available = state.gpuAvailable,
                        backend = state.backend,
                        onToggle = onGpu,
                    )
                }
            }
        }

        // ------------------------------------------------------------- chat brain
        item(key = "chat") {
            Column {
                SectionLabel("Chat mode", modifier = Modifier.padding(bottom = 8.dp))
                ChatBrainCard(
                    style = state.chatStyle,
                    temperature = state.chatTemperature,
                    instructions = state.chatInstructions,
                    onStyle = onChatStyle,
                    onTemperature = onChatTemperature,
                    onInstructions = onChatInstructions,
                )
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
                    length = state.replyLength,
                    onLength = onReplyLength,
                )
            }
        }

        // ------------------------------------------------------- TEMPORARY prompt editor
        item(key = "prompt") {
            PromptEditorCard(
                on = state.customPromptOn,
                chatText = state.customChatPrompt,
                controlText = state.customControlPrompt,
                onToggle = onCustomPrompt,
                onChatText = onCustomChatPrompt,
                onControlText = onCustomControlPrompt,
                builtIn = onBuiltInPrompt,
            )
        }

        // ------------------------------------------------------------------- cloud
        item(key = "cloud") {
            Column {
                SectionLabel("Cloud brain", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    text = "When this is on it answers in both modes, Chat and Control - the " +
                        "phone tools work the same, because the tool menu is built by Sadu, " +
                        "not by the model.",
                    color = Ink.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                CloudCard(
                    enabled = state.cloudEnabled,
                    provider = state.cloudProvider,
                    baseUrl = state.cloudBaseUrl,
                    model = state.cloudModel,
                    keySet = state.cloudKeySet,
                    keyProtected = state.cloudKeyProtected,
                    test = state.cloudTest,
                    problem = state.cloudProblem,
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
                    HairlineDivider()
                    ToggleRow(
                        title = "Answer from the web",
                        subtitle = "Looks up \"search for ...\" and answers from the results",
                        checked = state.webAnswers,
                        onCheckedChange = onWebAnswers,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "With internet access off, Sadu still chats, remembers and controls the " +
                            "phone, but web search, opening links and app installation are blocked. With " +
                            "\"Answer from the web\" off, a search opens your browser instead. The " +
                            "cloud model has its own switch under Cloud brain.",
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
                        subtitle = "Sadu repeats the message and waits for your yes",
                        checked = state.confirmSms,
                        onCheckedChange = onConfirmSms,
                    )
                    HairlineDivider()
                    ToggleRow(
                        title = "Place calls without asking",
                        subtitle = "Off means Sadu opens the dialler instead",
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
                    KeyValueRow("Sadu version", state.versionLine)
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
                    text = "Sadu is a personal assistant that runs on your own phone. It is not a " +
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
