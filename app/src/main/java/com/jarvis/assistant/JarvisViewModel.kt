package com.jarvis.assistant

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.brain.Brain
import com.jarvis.assistant.brain.OfflineBrain
import com.jarvis.assistant.control.JarvisAccessibilityService
import com.jarvis.assistant.voice.Voice
import com.jarvis.assistant.voice.WakeWordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMsg(val text: String, val fromUser: Boolean, val id: Long = System.nanoTime())

enum class Screen { Chat, Settings }

private const val NEW_CHAT_TITLE = "New chat"
private const val GREETING =
    "Hi, I'm Jarvis. Talk or type. Try \"set an alarm for 6:30 am\", " +
        "\"text Mom that I'm on my way\", \"open camera\" or \"read the screen\" - or just chat with me."
private const val READY = "Ready - running offline on your phone"

class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)
    private val memory = Memory(ctx)
    private val engine = LlmEngine(ctx)
    private val actions = Actions(ctx, memory, prefs) { JarvisAccessibilityService.instance }
    private val tools = Tools(actions, memory)
    private val brain: Brain = OfflineBrain(engine)
    private val assistant = Assistant(brain, actions, memory, tools)
    private val store = ChatHistory(ctx)

    private var idSeed = System.currentTimeMillis()

    /** Every saved conversation, newest first. */
    var sessions by mutableStateOf<List<ChatSession>>(emptyList())
        private set
    var currentId by mutableStateOf(0L)
        private set

    /** The conversation on screen. Single source of truth is [sessions]. */
    val current: ChatSession? get() = sessions.firstOrNull { it.id == currentId }
    val messages: List<ChatMsg> get() = current?.messages ?: emptyList()

    var status by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var modelReady by mutableStateOf(false)
        private set
    var modelPresent by mutableStateOf(false)
        private set
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
    var screen by mutableStateOf(Screen.Chat)
        private set
    var a11yConnected by mutableStateOf(false)
        private set

    private val voice = Voice(
        ctx,
        onText = { submit(it) },
        onStatus = { s -> status = s },
        onError = { e -> push(ChatMsg(e, false)) },
    )

    init {
        voice.init()
        val saved = store.load().sortedByDescending { it.createdAt }
        if (saved.isEmpty()) {
            val s = newSession()
            sessions = listOf(s)
            currentId = s.id
        } else {
            sessions = saved
            currentId = saved.first().id
        }
        modelPresent = engine.isModelPresent()
        if (modelPresent) {
            loadModel()
        } else {
            status = "AI model file needed (one time)"
        }
    }

    // ------------------------------------------------------------ conversations

    private fun newSession() =
        ChatSession(nextId(), NEW_CHAT_TITLE, System.currentTimeMillis(), listOf(ChatMsg(GREETING, false)))

    private fun nextId(): Long = ++idSeed

    /** Start a fresh chat. Reuses the current one if nothing was said yet. */
    fun newChat() {
        pruneEmpty()
        if (current?.messages?.none { it.fromUser } == true) {
            assistant.resetHistory()
            persist()
            return
        }
        val s = newSession()
        sessions = listOf(s) + sessions
        currentId = s.id
        assistant.resetHistory()
        persist()
    }

    fun openChat(id: Long) {
        if (id == currentId) return
        currentId = id
        assistant.resetHistory()
    }

    fun deleteChat(id: Long) {
        sessions = sessions.filterNot { it.id == id }
        if (currentId == id) {
            currentId = sessions.firstOrNull()?.id ?: 0L
            if (currentId == 0L) {
                val s = newSession()
                sessions = listOf(s)
                currentId = s.id
            }
        }
        assistant.resetHistory()
        persist()
    }

    fun deleteAllChats() {
        val s = newSession()
        sessions = listOf(s)
        currentId = s.id
        assistant.resetHistory()
        persist()
    }

    /** Drops other conversations that were never used, so the sidebar stays tidy. */
    private fun pruneEmpty() {
        val keep = sessions.filter { it.id == currentId || it.messages.any { m -> m.fromUser } }
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
        return if (one.length <= 38) one else one.take(37).trimEnd() + "\u2026"
    }

    // ------------------------------------------------------------ talking

    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        voice.stopSpeaking()
        push(ChatMsg(t, true))
        sessions = sessions.map {
            if (it.id == currentId && it.title == NEW_CHAT_TITLE) it.copy(title = titleOf(t)) else it
        }
        persist()
        busy = true
        status = "Thinking..."
        viewModelScope.launch {
            val reply = withContext(Dispatchers.IO) {
                try {
                    assistant.respond(t)
                } catch (e: Throwable) {
                    "Sorry, something went wrong: ${e.message}"
                }
            }
            push(ChatMsg(reply, false))
            busy = false
            status = if (modelReady) READY else ""
            persist()
            voice.speak(reply, speakOn)
        }
    }

    fun startListening() = voice.listen()

    // ------------------------------------------------------------ model

    fun refreshAccessibility() {
        a11yConnected = JarvisAccessibilityService.isConnected
    }

    fun loadModel() {
        modelPresent = engine.isModelPresent()
        if (!modelPresent) {
            status = "AI model file needed (one time)"
            return
        }
        status = "Loading AI model..."
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    engine.load()
                    null
                } catch (e: Throwable) {
                    e.message ?: e.toString()
                }
            }
            if (err == null) {
                modelReady = true
                status = READY
            } else {
                modelReady = false
                status = "Model failed to load: $err"
            }
        }
    }

    fun importModel(uri: Uri) {
        modelReady = false
        status = "Copying model (one time only)..."
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    engine.importModel(uri) { }
                    null
                } catch (e: Throwable) {
                    e.message ?: e.toString()
                }
            }
            if (err == null) loadModel() else status = "Copy failed: $err"
        }
    }

    // ------------------------------------------------------------ settings

    fun setSpeak(on: Boolean) {
        speakOn = on
        prefs.speakEnabled = on
        if (!on) voice.stopSpeaking()
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

    fun clearMemory() {
        memory.clear()
    }

    fun clearChat() {
        sessions = sessions.map {
            if (it.id == currentId) it.copy(title = NEW_CHAT_TITLE, messages = listOf(ChatMsg(GREETING, false)))
            else it
        }
        assistant.resetHistory()
        persist()
    }

    fun openSettings() {
        screen = Screen.Settings
        refreshAccessibility()
    }

    fun closeSettings() {
        screen = Screen.Chat
    }

    override fun onCleared() {
        super.onCleared()
        voice.destroy()
        engine.close()
    }
}
