package com.jarvis.assistant

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.brain.Brain
import com.jarvis.assistant.brain.OfflineBrain
import com.jarvis.assistant.control.JarvisAccessibilityService
import com.jarvis.assistant.voice.Voice
import com.jarvis.assistant.voice.WakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.min

const val NEW_CHAT_TITLE = "New chat"
const val GREETING =
    "Hi, I'm Jarvis. Tap the microphone and talk, or type below - or just chat with me."
const val READY_NOTE = "Running offline on this phone"
const val NEEDS_MODEL_NOTE = "One-time setup: import a Gemma model file in Settings"

/**
 * Holds every piece of state the UI needs. The composables are stateless and
 * only render what this exposes.
 */
class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)
    private val memory = Memory(ctx)
    private val engine = LlmEngine(ctx)
    private val actions = Actions(ctx, memory, prefs) { JarvisAccessibilityService.instance }
    private val tools = Tools(actions, memory) { prefs.mode }
    private val brain: Brain = OfflineBrain(engine)
    private val assistant = Assistant(brain, actions, memory, tools, { prefs.language }, { prefs.mode })
    private val store = ChatHistory(ctx)

    private var idSeed = System.currentTimeMillis()

    // ------------------------------------------------------------------ sections

    var tab by mutableStateOf(Tab.Home)
        private set

    // --------------------------------------------------------- assistant state

    var aiState by mutableStateOf(AiState.Idle)
        private set
    var micLevel by mutableStateOf(0f)
        private set
    var errorText by mutableStateOf<String?>(null)
        private set
    var modelNote by mutableStateOf<String?>(null)
        private set
    var modelReady by mutableStateOf(false)
        private set
    var modelPresent by mutableStateOf(false)
        private set

    // ------------------------------------------------------------ conversations

    var sessions by mutableStateOf<List<ChatSession>>(emptyList())
        private set
    var currentId by mutableStateOf(0L)
        private set

    /** null while the conversation list is showing. */
    var openChatId by mutableStateOf<Long?>(null)
        private set

    /** The tail of a reply while it is being revealed. */
    var streamText by mutableStateOf<String?>(null)
        private set

    /** The text in the composer. Kept here so it survives moving between sections. */
    var composerText by mutableStateOf("")
        private set
    var composerToken by mutableStateOf(0)
        private set

    val current: ChatSession? get() = sessions.firstOrNull { it.id == currentId }
    val messages: List<ChatMsg> get() = current?.messages ?: emptyList()
    val openChat: ChatSession? get() = sessions.firstOrNull { it.id == openChatId }

    /** Last question and last answer, shown on the home screen. */
    val lastTurn: List<ChatMsg> get() = messages.takeLast(2)

    // ------------------------------------------------------------------- memory

    var facts by mutableStateOf<List<String>>(emptyList())
        private set
    var greeting by mutableStateOf("")
        private set

    // ----------------------------------------------------------------- settings

    /** Chat is conversation only; Control may use the phone. Always visible in the UI. */
    var mode by mutableStateOf(prefs.mode)
        private set

    fun chooseMode(next: Mode) {
        if (next == mode) return
        mode = next
        prefs.mode = next
        // The other mode's prompt and rules must not leak into this one.
        assistant.resetHistory()
    }

    var speakOn by mutableStateOf(prefs.speakEnabled)
        private set
    var internetOn by mutableStateOf(prefs.internetEnabled)
        private set
    var wakeOn by mutableStateOf(prefs.wakeWordEnabled)
        private set
    var confirmSms by mutableStateOf(prefs.confirmSms)
        private set
    var directCall by mutableStateOf(prefs.directCall)
        private set
    var language by mutableStateOf(prefs.language)
        private set
    var ttsVoiceMissing by mutableStateOf(false)
        private set

    // ------------------------------------------------------------------ runtime

    var granted by mutableStateOf<Set<String>>(emptySet())
        private set
    var a11yConnected by mutableStateOf(false)
        private set
    var speechAvailable by mutableStateOf(false)
        private set
    var ttsReady by mutableStateOf(false)
        private set
    var modelBytes by mutableStateOf(0L)
        private set
    var chatBytes by mutableStateOf(0L)
        private set
    var versionLine by mutableStateOf("")
        private set

    private val voice = Voice(
        ctx,
        onText = { heard -> submit(heard) },
        onListenStart = {
            errorText = null
            micLevel = 0f
            aiState = AiState.Listening
        },
        onError = { message -> fail(message) },
        onLevel = { level -> micLevel = level },
        onSpeakStart = { if (aiState != AiState.Error) aiState = AiState.Speaking },
        onSpeakDone = { if (aiState == AiState.Speaking) aiState = AiState.Idle },
    )

    init {
        voice.init()
        voice.setLanguage(language)
        val saved = store.load().sortedByDescending { it.createdAt }
        if (saved.isEmpty()) {
            val s = newSession()
            sessions = listOf(s)
            currentId = s.id
        } else {
            sessions = saved
            currentId = saved.first().id
        }
        refreshFacts()
        refreshRuntime()
        if (modelPresent) loadModel() else modelNote = NEEDS_MODEL_NOTE
    }

    // ------------------------------------------------------------------ sections

    fun selectTab(next: Tab) {
        tab = next
        if (next == Tab.Actions || next == Tab.Settings) refreshRuntime()
        if (next == Tab.Memory) refreshFacts()
    }

    // ------------------------------------------------------------- conversations

    private fun newSession() = ChatSession(
        id = nextId(),
        title = NEW_CHAT_TITLE,
        createdAt = System.currentTimeMillis(),
        messages = listOf(ChatMsg(GREETING, fromUser = false, at = System.currentTimeMillis())),
        mode = mode,
    )

    private fun nextId(): Long = ++idSeed

    /** Only called from the main thread or an already-synchronised place. */
    private fun nextIdSafe(): Long = synchronized(this) { nextId() }

    /** Start a fresh chat. Reuses the current one when nothing has been asked yet. */
    fun newChat() {
        pruneEmpty()
        if (current?.askedCount == 0) {
            assistant.resetHistory()
            openChatId = currentId
            persist()
            return
        }
        val s = ChatSession(
            id = nextIdSafe(),
            title = NEW_CHAT_TITLE,
            createdAt = System.currentTimeMillis(),
            messages = listOf(ChatMsg(GREETING, fromUser = false)),
            mode = mode,
        )
        sessions = listOf(s) + sessions
        currentId = s.id
        openChatId = s.id
        assistant.resetHistory()
        persist()
    }

    fun openChat(id: Long) {
        if (id == currentId && openChatId == id) return
        currentId = id
        openChatId = id
        assistant.resetHistory()
    }

    fun closeChat() {
        openChatId = null
    }

    fun deleteChat(id: Long) {
        sessions = sessions.filterNot { it.id == id }
        if (currentId == id) {
            val fallback = sessions.firstOrNull()
            if (fallback == null) {
                val s = newSession()
                sessions = listOf(s)
                currentId = s.id
                openChatId = null
            } else {
                currentId = fallback.id
                if (openChatId == id) openChatId = null
            }
        }
        assistant.resetHistory()
        persist()
    }

    fun deleteAllChats() {
        val s = newSession()
        sessions = listOf(s)
        currentId = s.id
        openChatId = null
        assistant.resetHistory()
        persist()
    }

    /** Empties the conversation currently open. */
    fun clearCurrentChat() {
        sessions = sessions.map {
            if (it.id == currentId) {
                it.copy(title = NEW_CHAT_TITLE, messages = listOf(ChatMsg(GREETING, fromUser = false)))
            } else {
                it
            }
        }
        assistant.resetHistory()
        persist()
    }

    /** Drops untouched conversations so the list stays tidy. */
    private fun pruneEmpty() {
        val keep = sessions.filter { it.id == currentId || it.askedCount > 0 }
        if (keep.size != sessions.size) sessions = keep
    }

    private fun push(msg: ChatMsg) {
        sessions = sessions.map { if (it.id == currentId) it.copy(messages = it.messages + msg) else it }
    }

    private fun persist() {
        val snapshot = sessions
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    private fun titleOf(text: String): String {
        val one = text.replace(Regex("\\s+"), " ").trim()
        return if (one.length <= 34) one else one.take(33).trimEnd() + "\u2026"
    }

    // ---------------------------------------------------------------- text input

    /**
     * Sends a message. Used by the composer, by voice results and by the
     * quick actions on the home screen.
     */
    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || aiState == AiState.Processing || aiState == AiState.Listening) return
        errorText = null
        voice.stopSpeaking()
        composerText = ""
        push(ChatMsg(text, fromUser = true))
        sessions = sessions.map {
            if (it.id == currentId && it.title == NEW_CHAT_TITLE) it.copy(title = titleOf(text)) else it
        }
        openChatId = currentId
        persist()
        micLevel = 0f
        aiState = AiState.Processing
        viewModelScope.launch {
            val reply = withContext(Dispatchers.IO) {
                try {
                    assistant.respond(text)
                } catch (e: Throwable) {
                    "Sorry, something went wrong: ${e.message}"
                }
            }
            reveal(reply)
            persist()
            when (ReplyClassifier.of(reply)) {
                ReplyKind.Failure -> fail(reply, keepReply = true)
                ReplyKind.Notice -> aiState = AiState.Idle
                ReplyKind.Answer -> speak(reply)
            }
        }
    }

    /**
     * Shows the finished reply progressively.
     * This animates text that already exists - the on-device model returns a whole
     * answer at once and does not stream tokens.
     */
    private suspend fun reveal(full: String) {
        if (full.isBlank() || full.length > 900 || animationsOff()) {
            push(ChatMsg(full, fromUser = false))
            return
        }
        val chunk = maxOf(1, ceil(full.length / 44f).toInt())
        var shown = 0
        try {
            while (shown < full.length) {
                shown = min(full.length, shown + chunk)
                streamText = full.substring(0, shown)
                delay(22)
            }
        } finally {
            streamText = null
        }
        push(ChatMsg(full, fromUser = false))
    }

    fun dismissError() {
        errorText = null
        if (aiState == AiState.Error) aiState = AiState.Idle
    }

    private fun fail(message: String, keepReply: Boolean = false) {
        errorText = message
        aiState = AiState.Error
        micLevel = 0f
        if (!keepReply) {
            // Voice problems still belong in the conversation, so the user can read them.
            if (message.isNotBlank() && openChatId != null) push(ChatMsg(message, fromUser = false))
        }
    }

    private fun speak(text: String) {
        if (!speakOn || !voice.isTtsReady || ReplyClassifier.of(text) != ReplyKind.Answer) {
            aiState = AiState.Idle
            return
        }
        aiState = AiState.Speaking
        voice.speak(text, enabled = true)
        // Safety net: a speech engine that never calls back must not leave the orb stuck.
        viewModelScope.launch {
            delay(30_000)
            if (aiState == AiState.Speaking && !voice.isSpeaking()) aiState = AiState.Idle
        }
    }

    fun startListening() {
        if (PhoneActionCatalog.PERM_MIC !in granted) {
            fail("I need microphone permission before I can listen.")
            return
        }
        errorText = null
        voice.listen()
    }

    fun stopSpeaking() {
        voice.stopSpeaking()
        if (aiState == AiState.Speaking) aiState = AiState.Idle
    }

    /** A wake word was heard. */
    fun wakeUp() {
        tab = Tab.Home
        startListening()
    }

    /** Jump to the composer, optionally with the start of a sentence already filled in. */
    fun askForText(seed: String?) {
        tab = Tab.Conversations
        if (openChatId == null) openChatId = currentId
        composerText = seed.orEmpty()
        composerToken++
    }

    fun onComposerChange(text: String) {
        composerText = text
    }

    /** Sends whatever is in the composer. */
    fun sendComposer() {
        val text = composerText.trim()
        if (text.isEmpty()) return
        composerText = ""
        submit(text)
    }

    /** Run a command straight away (home screen quick actions). */
    fun runNow(command: String) = submit(command)

    // ------------------------------------------------------------------- memory

    fun refreshFacts() {
        facts = memory.facts()
        greeting = AssistantText.greeting(
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            AssistantText.nameFrom(facts),
        )
    }

    fun addFact(text: String) {
        val t = text.replace("\n", " ").trim()
        if (t.isEmpty()) return
        memory.add(t)
        refreshFacts()
    }

    fun updateFact(old: String, replacement: String) {
        memory.update(old, replacement)
        refreshFacts()
    }

    fun removeFact(fact: String) {
        memory.remove(fact)
        refreshFacts()
    }

    fun clearMemory() {
        memory.clear()
        refreshFacts()
    }

    // ----------------------------------------------------------------- settings

    fun setSpeak(on: Boolean) {
        speakOn = on
        prefs.speakEnabled = on
        if (!on) stopSpeaking()
    }

    fun setInternet(on: Boolean) {
        internetOn = on
        prefs.internetEnabled = on
    }

    fun changeConfirmSms(on: Boolean) {
        confirmSms = on
        prefs.confirmSms = on
    }

    fun changeDirectCall(on: Boolean) {
        directCall = on
        prefs.directCall = on
    }

    fun chooseLanguage(tag: String) {
        language = tag
        prefs.language = tag
        voice.setLanguage(tag)
        ttsVoiceMissing = voice.isTtsReady && !voice.languageAvailable(tag)
        assistant.resetHistory()
    }

    fun setWake(on: Boolean) {
        wakeOn = on
        prefs.wakeWordEnabled = on
        val i = Intent(ctx, WakeWordService::class.java)
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } else {
                ctx.stopService(i)
            }
        } catch (_: Throwable) {
        }
    }

    // -------------------------------------------------------------------- model

    fun loadModel() {
        modelPresent = engine.isModelPresent()
        if (!modelPresent) {
            modelNote = NEEDS_MODEL_NOTE
            return
        }
        modelNote = "Loading the AI model..."
        viewModelScope.launch {
            val problem = withContext(Dispatchers.IO) {
                try {
                    engine.load()
                    null
                } catch (e: Throwable) {
                    e.message ?: e.toString()
                }
            }
            if (problem == null) {
                modelReady = true
                modelNote = READY_NOTE
            } else {
                modelReady = false
                modelNote = "The model could not be loaded: $problem"
            }
            refreshRuntime()
        }
    }

    fun importModel(uri: Uri) {
        modelReady = false
        modelNote = "Copying the model file (this happens once)..."
        viewModelScope.launch {
            val problem = withContext(Dispatchers.IO) {
                try {
                    engine.importModel(uri) { }
                    null
                } catch (e: Throwable) {
                    e.message ?: e.toString()
                }
            }
            if (problem == null) loadModel() else modelNote = "Copying the file failed: $problem"
        }
    }

    // ------------------------------------------------------------------ runtime

    fun refreshRuntime() {
        val sdk = Build.VERSION.SDK_INT
        granted = buildSet {
            PhoneActionCatalog.runtimePermissions(sdk).forEach { p ->
                if (ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED) add(p)
            }
        }
        a11yConnected = JarvisAccessibilityService.isConnected
        speechAvailable = voice.isSpeechRecognitionAvailable()
        ttsReady = voice.isTtsReady
        if (ttsReady) ttsVoiceMissing = !voice.languageAvailable(language)
        modelPresent = engine.isModelPresent()
        modelBytes = engine.modelBytes()
        chatBytes = store.sizeBytes()
        versionLine = appVersion()
    }

    fun actionBlockers(): Map<String, String?> = actionsForDevice().associate { it.id to blockerFor(it) }

    fun actionsForDevice(): List<PhoneAction> = PhoneActionCatalog.forSdk(Build.VERSION.SDK_INT)

    fun blockerFor(action: PhoneAction): String? =
        PhoneActionCatalog.blocker(action, granted, a11yConnected, Build.VERSION.SDK_INT)

    private fun appVersion(): String = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName} ($code)"
    } catch (_: Throwable) {
        "unknown"
    }

    private fun animationsOff(): Boolean = try {
        android.provider.Settings.Global.getFloat(
            ctx.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    } catch (_: Throwable) {
        false
    }

    override fun onCleared() {
        super.onCleared()
        voice.destroy()
        engine.close()
    }
}
