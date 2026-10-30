package com.sadu.assistant

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sadu.assistant.brain.Brain
import com.sadu.assistant.brain.BrainRouter
import com.sadu.assistant.brain.CloudBrain
import com.sadu.assistant.brain.CloudConfig
import com.sadu.assistant.brain.CloudProvider
import com.sadu.assistant.brain.cloudSkipNote
import com.sadu.assistant.brain.Engine
import com.sadu.assistant.brain.Gguf
import com.sadu.assistant.brain.LlamaEngine
import com.sadu.assistant.brain.LlmRequest
import com.sadu.assistant.brain.LocalEngine
import com.sadu.assistant.brain.OfflineBrain
import com.sadu.assistant.brain.SwitchableBrain
import com.sadu.assistant.prompt.ChatPrompt
import com.sadu.assistant.prompt.ChatStyle
import com.sadu.assistant.prompt.GemmaChatTemplate
import com.sadu.assistant.prompt.ModelChatTemplate
import com.sadu.assistant.control.SaduAccessibilityService
import com.sadu.assistant.voice.Voice
import com.sadu.assistant.voice.WakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.min

const val NEW_CHAT_TITLE = "New chat"
const val GREETING =
    "Hi, I'm Sadu. Tap the microphone and talk, or type below - or just chat with me."
const val READY_NOTE = "Running offline on this phone"
const val NEEDS_MODEL_NOTE = "One-time setup: import a Gemma model file in Settings"
const val NEEDS_GGUF_NOTE = "One-time setup: import a .gguf model file in Settings"

/**
 * Holds every piece of state the UI needs. The composables are stateless and
 * only render what this exposes.
 */
class SaduViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)
    private val memory = Memory(ctx)

    // Both on-device engines are built and kept, so switching between them is a reload and
    // nothing more. Only the chosen one is ever loaded into memory.
    private val mediaPipeEngine = LlmEngine(ctx)
    private val llamaEngine = LlamaEngine(ctx)

    /** The engine the settings point at; the whole model section works through this. */
    private val engine: Engine
        get() = if (prefs.localEngine == LocalEngine.Llama) llamaEngine else mediaPipeEngine

    private val actions = Actions(ctx, memory, prefs) { SaduAccessibilityService.instance }
        .also { a ->
            a.onNeedPermission = { perm -> noteNeeded(perm, lastAsked) }
            a.onChoices = { names -> nameChoices = names }
        }
    private val tools = Tools(actions, memory, { prefs.mode }, { prefs.riskyControls })
    // Starts on whichever engine the settings point at, so a cold start and a switch agree.
    private val localBrain = SwitchableBrain(brainFor(engine))
    private val cloudBrain = CloudBrain { cloudConfig() }
    private val router = BrainRouter(localBrain, cloudBrain) { cloudWanted() }
    private val brain: Brain = router

    /**
     * The on-device brain for one engine: its own template, the mode's sampling, the reply
     * length the user chose. Written once, so both engines answer the same way.
     */
    private fun brainFor(target: Engine): OfflineBrain = OfflineBrain(
        target,
        if (target === llamaEngine) {
            ModelChatTemplate { instructions, turns, text -> llamaEngine.applyTemplate(instructions, turns, text) }
        } else {
            GemmaChatTemplate
        },
        sampling = { mode ->
            if (mode == Mode.Chat) Sampling.chat(prefs.chatTemperature) else Sampling.Command
        },
        length = { prefs.replyLength },
    )
    private val assistant = Assistant(
        brain,
        actions,
        memory,
        tools,
        { prefs.language },
        { prefs.mode },
        { prefs.contextTokens },
        { prefs.replyLength },
        { prefs.chatStyle },
        { prefs.chatInstructions },
        { prefs.internetEnabled && prefs.webAnswers },
        { prefs.internetEnabled },
        WebSearch.live(),
        { prefs.riskyControls },
        { prefs.preferCloudForActions },
        { if (prefs.cloudEnabled) cloudConfig().problem() else null },
    )
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

    /** Which model answered the last reply, so the choice of brain is never a guess. */
    var answeredBy by mutableStateOf<String?>(null)
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
    /** What Sadu is doing while it works - "Searching the web" and the like. */
    var busyNote by mutableStateOf<String?>(null)
        private set

    var streamText by mutableStateOf<String?>(null)
        private set

    /** The text in the composer. Kept here so it survives moving between sections. */
    var composerText by mutableStateOf("")

    /** Similar names Sadu offered, shown as a chooser over the chat. */
    var nameChoices by mutableStateOf<List<String>>(emptyList())
        private set

    /** A permission an action asked for mid-sentence, offered as a button in the chat. */
    var neededPermission by mutableStateOf<String?>(null)
        private set
    private var retryAfterGrant: String? = null
    private var lastAsked: String? = null
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
        // The conversation carries over: only the instructions change with the mode.
    }

    var speakOn by mutableStateOf(prefs.speakEnabled)
        private set
    var internetOn by mutableStateOf(prefs.internetEnabled)
        private set

    /** Messages, calls, app installs, screen taps: off by default, see [ToolPolicy]. */
    var riskyControls by mutableStateOf(prefs.riskyControls)
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

    // --------------------------------------------------------------- context + cloud

    /** The window the model should run with, in tokens. */
    var contextTokens by mutableStateOf(prefs.contextTokens)
        private set

    /** The window the on-device model actually accepted, once it is loaded. */
    var contextEffective by mutableStateOf(0)

    /** How long Sadu should make its answers. */
    var replyLength by mutableStateOf(prefs.replyLength)
        private set

    /** How Chat mode's answers should read. */
    var chatStyle by mutableStateOf(prefs.chatStyle)
        private set

    /** How lively the on-device Chat answers should be. */
    var chatTemperature by mutableStateOf(prefs.chatTemperature)
        private set

    /** The user's own extra instructions for Chat mode. */
    var chatInstructions by mutableStateOf(prefs.chatInstructions)
        private set

    /** True while an answer is being written and can still be stopped. */
    var canStop by mutableStateOf(false)
        private set

    @Volatile
    private var stoppedByUser = false

    var cloudEnabled by mutableStateOf(prefs.cloudEnabled)
        private set
    var cloudProvider by mutableStateOf(prefs.cloudProvider)
        private set
    var cloudBaseUrl by mutableStateOf(prefs.cloudBaseUrl)
        private set
    var cloudModel by mutableStateOf(prefs.cloudModel)
        private set
    var cloudKeySet by mutableStateOf(prefs.cloudApiKey.isNotBlank())
        private set
    var cloudKeyProtected by mutableStateOf(prefs.cloudKeyProtected)
        private set
    var preferCloudForActions by mutableStateOf(prefs.preferCloudForActions)
        private set

    /** Whether "search for ..." is looked up and answered from the results. */
    var webAnswers by mutableStateOf(prefs.webAnswers)
        private set

    /** What still needs fixing in the cloud settings, or null when they are fine. */
    var cloudProblem by mutableStateOf<String?>(null)
        private set

    /** The result of the last connection test, shown under the button. */
    var cloudTest by mutableStateOf<String?>(null)
        private set

    init {
        refreshCloudProblem()
    }

    private fun refreshCloudProblem() {
        cloudProblem = cloudConfig().problem()
    }

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

    /** Which on-device engine the settings are set to. */
    var localEngineChoice by mutableStateOf(prefs.localEngine)
        private set

    /** True when this build really carries llama.cpp, so a .gguf can run at all. */
    var engineAvailable by mutableStateOf(false)
        private set

    /** What the on-device model calls itself, for the engine card. */
    var modelLabel by mutableStateOf("")
        private set

    /** How far a .gguf copy is, or -1 when nothing is being copied. */
    var modelCopyPercent by mutableStateOf(-1)
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
        // loadModel() also words the "which file do you need" note, for whichever engine is set.
        loadModel()
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
        // Reopening a chat carries on where it left off instead of starting blank.
        assistant.loadHistory(turnsOf(sessions.firstOrNull { it.id == id }?.messages.orEmpty()))
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
        lastAsked = text
        if (text.isEmpty() || aiState == AiState.Processing || aiState == AiState.Listening) return
        errorText = null
        answeredBy = null
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
        stoppedByUser = false
        canStop = true

        val buffer = StreamBuffer()
        val state = StreamState()
        viewModelScope.launch {
            val ticker = launch { paceStream(buffer, state) }
            // The assistant says what it is doing on a worker thread; this puts it on screen.
            val busy = launch {
                while (true) {
                    busyNote = assistant.stage
                    delay(140)
                }
            }
            val reply = withContext(Dispatchers.IO) {
                try {
                    assistant.respond(text) { delta -> buffer.append(delta) }
                } catch (e: Throwable) {
                    "Sorry, something went wrong: ${e.message}"
                }
            }
            state.markFinished()
            busy.cancel()
            busyNote = null
            canStop = false
            // A rule answer, a tool result, or the whole on-device reply: reveal it smoothly.
            if (buffer.length == 0) buffer.append(reply)
            ticker.join()
            push(ChatMsg(reply, fromUser = false))
            answeredBy = if (assistant.answeredWithModel) "Answered by " + router.lastUsed else null
            // Only a model answer can be "the phone instead of the cloud"; a built-in
            // rule is not a model at all, so it explains nothing.
            val usedCloud = assistant.answeredWithModel && router.lastUsed != localBrain.name
            val fallback = router.lastFallback
            errorText = listOfNotNull(
                if (stoppedByUser) "Stopped at your request." else null,
                if (assistant.lastActionClaim) ChatPolicy.CLAIM_NOTE else null,
                if (!stoppedByUser && assistant.lastReplyTruncated) {
                    "This answer stopped at the length limit. Choose Long under Reply length, " +
                        "or ask for the rest."
                } else {
                    null
                },
                when {
                    fallback != null ->
                        "Cloud model unavailable ($fallback) - answered on the device."

                    else -> cloudSkipNote(
                        cloudEnabled = prefs.cloudEnabled,
                        problem = cloudConfig().problem(),
                        usedCloud = usedCloud,
                    )
                },
            ).joinToString(" ").ifEmpty { null }
            persist()
            when (ReplyClassifier.of(reply)) {
                ReplyKind.Failure -> fail(reply, keepReply = true)
                ReplyKind.Notice -> aiState = AiState.Idle
                ReplyKind.Answer -> speak(reply)
            }
        }
    }

    /**
     * Shows the reply as it arrives, at a steady pace. Cloud text arrives in bursts,
     * while the rule layer and the on-device model answer in one piece.
     */
    private suspend fun paceStream(buffer: StreamBuffer, state: StreamState) {
        if (animationsOff()) {
            while (!state.finished) delay(40)
            streamText = null
            return
        }
        var shown = 0
        try {
            while (true) {
                val available = buffer.length
                if (Pacer.done(shown, available, state.finished)) return
                val next = Pacer.next(shown, available)
                if (next != shown) {
                    shown = next
                    streamText = buffer.snapshot().take(shown)
                }
                delay(Pacer.Tick.toLong())
            }
        } finally {
            streamText = null
        }
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

    fun changeRiskyControls(on: Boolean) {
        riskyControls = on
        prefs.riskyControls = on
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
            modelReady = false
            modelNote = if (prefs.localEngine == LocalEngine.Llama) NEEDS_GGUF_NOTE else NEEDS_MODEL_NOTE
            return
        }
        modelNote = "Loading the AI model..."
        viewModelScope.launch {
            val problem = withContext(Dispatchers.IO) {
                try {
                    engine.load(contextTokens)
                    null
                } catch (e: Throwable) {
                    e.message ?: e.toString()
                }
            }
            contextEffective = engine.contextTokens
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

    /** Copies the `.task` bundle the MediaPipe engine runs. */
    fun importModel(uri: Uri) {
        modelReady = false
        modelCopyPercent = -1
        modelNote = "Copying the model file (this happens once)..."
        viewModelScope.launch {
            val problem = withContext(Dispatchers.IO) {
                try {
                    mediaPipeEngine.importModel(uri) { }
                    null
                } catch (e: Throwable) {
                    e.message ?: e.toString()
                }
            }
            if (problem == null) loadModel() else modelNote = "Copying the file failed: $problem"
        }
    }

    /**
     * Copies a chosen `.gguf`. Its header is checked first, so the wrong file is refused in a
     * second instead of after a long copy, and the note carries the percentage while it runs.
     */
    fun importGguf(uri: Uri) {
        modelReady = false
        modelCopyPercent = 0
        modelNote = "Copying the .gguf model (this happens once)..."
        val total = pickedBytes(uri)
        viewModelScope.launch {
            val problem = withContext(Dispatchers.IO) {
                try {
                    var reported = -1
                    llamaEngine.importModel(uri) { done ->
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != reported) {
                                reported = percent
                                viewModelScope.launch { modelCopyPercent = percent }
                            }
                        }
                    }
                    null
                } catch (e: Throwable) {
                    e.message ?: e.toString()
                }
            }
            modelCopyPercent = -1
            when {
                problem != null -> modelNote = "Copying the file failed: $problem"
                prefs.localEngine == LocalEngine.Llama -> loadModel()
                else -> {
                    modelNote = "Model saved. Switching to llama.cpp (.gguf)."
                    chooseEngine(LocalEngine.Llama)
                }
            }
        }
    }

    /**
     * Switches the on-device engine. The engine being left behind lets go of its model, so only
     * one is in memory at a time; both model files stay on the phone, so switching back is quick.
     */
    fun chooseEngine(next: LocalEngine) {
        if (next == prefs.localEngine) return
        prefs.localEngine = next
        localEngineChoice = next
        modelReady = false
        contextEffective = 0
        modelNote = null
        val wanted = engine
        (if (wanted === llamaEngine) mediaPipeEngine else llamaEngine).close()
        localBrain.switchTo(brainFor(wanted))
        refreshRuntime()
        loadModel()
    }

    /** The size behind a `content://` uri, when the picker will say. */
    private fun pickedBytes(uri: Uri): Long = runCatching {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use 0L
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index < 0 || cursor.isNull(index)) 0L else cursor.getLong(index)
        } ?: 0L
    }.getOrDefault(0L)

    // ------------------------------------------------------------------ runtime

    /** Called by [Actions] when an action cannot run without a permission. */
    fun noteNeeded(permission: String, forText: String?) {
        neededPermission = permission
        retryAfterGrant = forText
    }

    /** A name tapped in the chooser: do the call or message the user meant. */
    fun chooseName(name: String) {
        nameChoices = emptyList()
        val reply = assistant.chooseContact(name) ?: return
        push(ChatMsg(reply, fromUser = false))
        persist()
    }

    fun dismissChoices() {
        nameChoices = emptyList()
        assistant.clearContactChoice()
    }

    fun clearNeededPermission() {
        neededPermission = null
        retryAfterGrant = null
    }

    /**
     * After the permission dialog: if it was granted, run the sentence that needed it again,
     * so the user does not have to repeat themselves.
     */
    fun permissionAnswered() {
        refreshRuntime()
        val permission = neededPermission
        val text = retryAfterGrant
        neededPermission = null
        retryAfterGrant = null
        val ok = permission != null &&
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
        if (ok && text != null) submit(text)
    }

    fun refreshRuntime() {
        val sdk = Build.VERSION.SDK_INT
        granted = buildSet {
            PhoneActionCatalog.runtimePermissions(sdk).forEach { p ->
                if (ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED) add(p)
            }
        }
        a11yConnected = SaduAccessibilityService.isConnected
        speechAvailable = voice.isSpeechRecognitionAvailable()
        ttsReady = voice.isTtsReady
        if (ttsReady) ttsVoiceMissing = !voice.languageAvailable(language)
        engineAvailable = LlamaEngine.isAvailable
        localEngineChoice = prefs.localEngine
        modelLabel = if (prefs.localEngine == LocalEngine.Llama) ggufLabel() else "Gemma .task"
        modelPresent = engine.isModelPresent()
        modelBytes = engine.modelBytes()
        chatBytes = store.sizeBytes()
        versionLine = appVersion()
    }

    /** What the `.gguf` on disk calls itself, read from its own header. */
    private fun ggufLabel(): String {
        if (!llamaEngine.isModelPresent()) return "no .gguf model yet"
        val file = File(ctx.filesDir, LlamaEngine.ModelName)
        return (Gguf.inspect(file) as? Gguf.Check.Ok)?.info?.label ?: "gguf model"
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
        mediaPipeEngine.close()
        llamaEngine.close()
    }

    fun chooseReplyLength(length: ReplyLength) {
        if (length == replyLength) return
        replyLength = length
        prefs.replyLength = length
    }

    fun chooseChatStyle(style: ChatStyle) {
        if (style == chatStyle) return
        chatStyle = style
        prefs.chatStyle = style
    }

    fun chooseChatTemperature(value: Float) {
        val next = Sampling.clampTemperature(value)
        if (next == chatTemperature) return
        chatTemperature = next
        // The sampler is read when the next answer starts, so nothing reloads here.
        prefs.chatTemperature = next
    }

    fun editChatInstructions(text: String) {
        chatInstructions = text.take(ChatPrompt.MAX_EXTRA)
        prefs.chatInstructions = chatInstructions
    }

    /** Stops the on-device model mid-answer, when the user asks for that. */
    fun stopGenerating() {
        if (aiState != AiState.Processing) return
        stoppedByUser = engine.cancel()
    }

    fun chooseContextTokens(tokens: Int) {
        val clamped = ContextBudget.clamp(tokens)
        if (clamped == contextTokens) return
        contextTokens = clamped
        prefs.contextTokens = clamped
        // The window is fixed when the model loads, so reload it in the background.
        if (modelReady) loadModel()
    }

    fun choosePreferCloudForActions(on: Boolean) {
        preferCloudForActions = on
        prefs.preferCloudForActions = on
    }

    fun chooseCloudEnabled(on: Boolean) {
        cloudEnabled = on
        prefs.cloudEnabled = on
        cloudTest = null
        refreshCloudProblem()
    }

    fun chooseCloudProvider(next: CloudProvider) {
        cloudProvider = next
        prefs.cloudProvider = next
        cloudTest = null
        refreshCloudProblem()
    }


    fun editCloudBaseUrl(value: String) {
        cloudBaseUrl = value
        prefs.cloudBaseUrl = value
        cloudTest = null
        refreshCloudProblem()
    }

    fun editCloudModel(value: String) {
        cloudModel = value
        prefs.cloudModel = value
        cloudTest = null
        refreshCloudProblem()
    }

    fun editCloudApiKey(value: String) {
        prefs.cloudApiKey = value
        cloudKeySet = value.isNotBlank()
        cloudKeyProtected = prefs.cloudKeyProtected
        cloudTest = null
        refreshCloudProblem()
    }

    /** Turns background web answers on or off. */
    fun chooseWebAnswers(on: Boolean) {
        webAnswers = on
        prefs.webAnswers = on
    }

    fun clearCloudApiKey() = editCloudApiKey("")


    /** Sends one tiny request, so the user can see whether the settings work. */
    fun testCloud() {
        if (!cloudEnabled) {
            cloudTest = "Turn the cloud brain on first."
            return
        }
        refreshCloudProblem()
        cloudProblem?.let { why ->
            cloudTest = why
            return
        }
        cloudTest = "Testing..."
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val request = LlmRequest(
                        system = "Reply with one short friendly sentence.",
                        turns = emptyList(),
                        user = "Are you there?",
                        mode = mode,
                    )
                    val reply = cloudBrain.generate(request).trim().take(120)
                    "Connected. The model said: $reply"
                } catch (e: Throwable) {
                    "Could not reach the model: ${e.message}"
                }
            }
            cloudTest = result
        }
    }


    private fun cloudConfig() = CloudConfig(
        provider = prefs.cloudProvider,
        baseUrl = prefs.cloudBaseUrl,
        model = prefs.cloudModel,
        apiKey = prefs.cloudApiKey,
        maxTokens = replyLength.tokens,
    )

    /**
     * Switching the cloud model on is itself the consent to send chats to that provider,
     * so it does not also need the "allow internet access" switch - that one is for the
     * phone tools (search, links, installing apps).
     */
    private fun cloudWanted(): Boolean = cloudConfig().shouldAnswer(prefs.cloudEnabled)

}