package com.jarvis.assistant

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.jarvis.assistant.ui.Ink
import com.jarvis.assistant.ui.JarvisNavBar
import com.jarvis.assistant.ui.JarvisTheme
import com.jarvis.assistant.ui.screens.Chat
import com.jarvis.assistant.ui.screens.ContextWindowCard
import com.jarvis.assistant.ui.screens.ConversationList
import com.jarvis.assistant.ui.screens.HomeContent
import com.jarvis.assistant.ui.screens.MemoryContent
import com.jarvis.assistant.ui.screens.PhoneActionsContent
import com.jarvis.assistant.ui.screens.SettingsContent
import com.jarvis.assistant.ui.screens.SettingsUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen off-device and writes a PNG for each.
 *
 * There is no emulator for this project, so this is how the layout is checked:
 * a real Compose composition at real phone sizes, with reduced motion forced on
 * so the frames are stable. It proves layout, colour and typography - not runtime
 * behaviour such as speech or accessibility.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun stillFrames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            JarvisTheme {
                Box(Modifier.fillMaxSize().background(Ink.Background)) { content() }
            }
        }
    }

    private fun shoot(name: String) {
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    // ------------------------------------------------------------------- home

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun homeSmall() {
        show { HomeAtRest() }
        shoot("home-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun homeLarge() {
        show { HomeAtRest() }
        shoot("home-411x891")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun homeListening() {
        show {
            HomeContent(
                greeting = "Good evening, Ada.",
                state = AiState.Listening,
                level = 0.72f,
                error = null,
                note = null,
                lastTurn = emptyList(),
                mode = Mode.Chat,
                onMode = {},
                onMic = {},
                onType = {},
                onQuickAction = {},
                onDismissError = {},
            )
        }
        shoot("home-listening-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun homeErrorAndNoModel() {
        show {
            HomeContent(
                greeting = "Still up.",
                state = AiState.Error,
                level = 0f,
                error = "I need microphone permission before I can listen.",
                note = "One-time setup: import a Gemma model file in Settings",
                lastTurn = emptyList(),
                mode = Mode.Chat,
                onMode = {},
                onMic = {},
                onType = {},
                onQuickAction = {},
                onDismissError = {},
            )
        }
        shoot("home-error-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w640dp-h360dp-xhdpi")
    fun homeLandscape() {
        show { HomeAtRest() }
        shoot("home-640x360")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun homeControlMode() {
        show { HomeAtRest(mode = Mode.Control) }
        shoot("home-control-360x640")
    }

    @Composable
    private fun HomeAtRest(mode: Mode = Mode.Chat) {
        HomeContent(
            greeting = "Good evening, Ada.",
            state = AiState.Idle,
            level = 0f,
            error = null,
            note = null,
            lastTurn = listOf(
                ChatMsg("What's on my screen?", fromUser = true, at = T0),
                ChatMsg(
                    "You're in Settings, on the Wi-Fi screen. Want me to turn it off?",
                    fromUser = false,
                    at = T0 + 20_000,
                ),
            ),
            mode = mode,
            onMode = {},
            onMic = {},
            onType = {},
            onQuickAction = { },
            onDismissError = {},
        )
    }

    // ---------------------------------------------------------------- chats

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun conversationsList() {
        show {
            ConversationList(
                sessions = sampleSessions(),
                openId = 1L,
                onOpen = {},
                onDelete = {},
                onNew = {},
            )
        }
        shoot("chats-list-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun conversationOpen() {
        show { OpenChat(messages = sampleMessages()) }
        shoot("chat-open-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun conversationThinking() {
        show { OpenChat(messages = sampleMessages(), state = AiState.Processing) }
        shoot("chat-thinking-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun conversationStreaming() {
        show {
            OpenChat(
                messages = sampleMessages(),
                state = AiState.Processing,
                stream = "Sent. Your message to Mom went out a moment ago and s",
            )
        }
        shoot("chat-streaming-360x640")
    }

    @Composable
    private fun OpenChat(
        messages: List<ChatMsg>,
        state: AiState = AiState.Idle,
        stream: String? = null,
        mode: Mode = Mode.Control,
    ) {
        Chat(
            title = "Text Mom that I'm on my way",
            messages = messages,
            state = state,
            streamText = stream,
            error = null,
            draft = "Text Mom that I'm ",
            focusToken = 0,
            mode = mode,
            onMode = {},
            onBack = {},
            onNewChat = {},
            onDraftChange = { },
            onSend = {},
            onMic = {},
            onDismissError = {},
        )
    }

    private fun sampleMessages(): List<ChatMsg> = listOf(
        ChatMsg(
            "Hi, I'm Jarvis. Tap the microphone and talk, or type below - or just chat with me.",
            fromUser = false,
            at = T0,
        ),
        ChatMsg("Text Mom that I'm on my way", fromUser = true, at = T0 + 60_000),
        ChatMsg(
            "Sent.\n\n**Mom** - \"I'm on my way\"",
            fromUser = false,
            at = T0 + 70_000,
        ),
        ChatMsg("Thanks! What's my battery at?", fromUser = true, at = T0 + 600_000),
        ChatMsg(
            "Your battery is at 68% and charging, so you are fine until this evening. " +
                "I can warn you when it drops below 20% if that helps.",
            fromUser = false,
            at = T0 + 610_000,
        ),
    )

    private fun sampleSessions(): List<ChatSession> = listOf(
        ChatSession(
            id = 1L,
            title = "Text Mom that I'm on my way",
            createdAt = T0,
            messages = sampleMessages(),
            mode = Mode.Control,
        ),
        ChatSession(
            id = 2L,
            title = "Set an alarm for 6:30 am",
            createdAt = T0 - 3_600_000,
            mode = Mode.Control,
            messages = listOf(
                ChatMsg("Set an alarm for 6:30 am", fromUser = true, at = T0 - 3_600_000),
                ChatMsg("Alarm set for 6:30 am tomorrow.", fromUser = false, at = T0 - 3_590_000),
            ),
        ),
        ChatSession(
            id = 3L,
            title = "How do I cook pasta?",
            createdAt = T0 - 90_000_000,
            messages = listOf(
                ChatMsg("How do I cook pasta?", fromUser = true, at = T0 - 90_000_000),
                ChatMsg(
                    "Boil plenty of salted water, add the pasta, and stir it once. " +
                        "Check the packet time and taste a piece a minute early.",
                    fromUser = false,
                    at = T0 - 89_990_000,
                ),
            ),
        ),
    )

    // --------------------------------------------------------------- memory

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun memoryFull() {
        show {
            MemoryContent(
                facts = listOf(
                    "The user's name is Ada",
                    "Likes black coffee, no sugar",
                    "Works nights, sleeps late on Fridays",
                    "Sister's birthday is 12 March",
                ),
                name = "Ada",
                onAdd = {},
                onUpdate = { _, _ -> },
                onDelete = {},
                onClearAll = {},
            )
        }
        shoot("memory-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun memoryEmpty() {
        show {
            MemoryContent(
                facts = emptyList(),
                name = null,
                onAdd = {},
                onUpdate = { _, _ -> },
                onDelete = {},
                onClearAll = {},
            )
        }
        shoot("memory-empty-360x640")
    }

    // -------------------------------------------------------- phone actions

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun phoneActions() {
        val actions = PhoneActionCatalog.forSdk(34)
        show {
            PhoneActionsContent(
                actions = actions,
                blockerFor = { action ->
                    when (action.id) {
                        "sms", "calls", "contacts" -> "Permission needed"
                        "screen" -> "Screen control is off"
                        else -> null
                    }
                },
                onAllow = {},
                onOpenScreenControl = {},
                onTry = {},
            )
        }
        shoot("actions-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun phoneActionsAllReady() {
        show {
            PhoneActionsContent(
                actions = PhoneActionCatalog.forSdk(34),
                blockerFor = { null },
                onAllow = {},
                onOpenScreenControl = {},
                onTry = {},
            )
        }
        shoot("actions-ready-360x640")
    }

    // -------------------------------------------------------------- settings

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun settings() {
        show { settingsContent() }
        shoot("settings-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun replyLengthChoices() {
        show {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                ContextWindowCard(
                    current = 4096,
                    effective = 4096,
                    onPick = {},
                    length = ReplyLength.Long,
                    onLength = {},
                )
            }
        }
        shoot("settings-reply-length-360x640")
    }

    @Composable
    private fun settingsContent() {
        SettingsContent(
                onMode = {},
                state = SettingsUiState(
                    modelReady = true,
                    modelPresent = true,
                    modelNote = READY_NOTE,
                    modelBytes = 2L * 1024 * 1024 * 1024,
                    speakOn = true,
                    internetOn = false,
                    wakeOn = false,
                    confirmSms = true,
                    directCall = false,
                    language = "en-IN",
                    ttsReady = true,
                    ttsVoiceMissing = false,
                    speechAvailable = true,
                    a11yConnected = true,
                    conversationCount = 3,
                    memoryCount = 4,
                    chatBytes = 6 * 1024,
                    grantedCount = 4,
                    permissionCount = 5,
                    versionLine = "0.5 (5)",
                ),
                onImportModel = {},
                onReloadModel = {},
                onSpeak = {},
                onWake = {},
                onInternet = {},
                onConfirmSms = {},
                onDirectCall = {},
                onLanguage = {},
                onDeleteEverything = {},
        )
    }

    // ----------------------------------------------------------------- shell

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun shellHome() {
        show {
            Shell(current = Tab.Home) { HomeAtRest() }
        }
        shoot("shell-home-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun shellSettings() {
        show { Shell(current = Tab.Settings) { settingsContent() } }
        shoot("shell-settings-360x640")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w640dp-h360dp-xhdpi")
    fun shellHomeLandscape() {
        show { Shell(current = Tab.Home) { HomeAtRest() } }
        shoot("shell-home-640x360")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
    fun shellChat() {
        show { Shell(current = Tab.Conversations) { OpenChat(messages = sampleMessages()) } }
        shoot("shell-chat-360x640")
    }

    /** What the phone actually shows: content above the hairline navigation bar. */
    @Composable
    private fun Shell(current: Tab, content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.Background)
                .navigationBarsPadding(),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 22.dp)) { content() }
            JarvisNavBar(current = current, onSelect = {})
        }
    }

    private companion object {
        /** Fixed instant so the screenshots are reproducible. */
        const val T0 = 1_764_000_000_000L
    }
}
