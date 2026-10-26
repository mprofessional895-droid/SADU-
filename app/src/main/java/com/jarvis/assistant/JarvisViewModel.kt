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

class JarvisViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val prefs = Prefs(ctx)
    private val memory = Memory(ctx)
    private val engine = LlmEngine(ctx)
    private val actions = Actions(ctx, memory, prefs) { JarvisAccessibilityService.instance }
    private val tools = Tools(actions, memory)
    private val brain: Brain = OfflineBrain(engine)
    private val assistant = Assistant(brain, actions, memory, tools)

    var messages by mutableStateOf<List<ChatMsg>>(emptyList())
        private set
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
        onError = { e -> messages = messages + ChatMsg(e, false) },
    )

    init {
        voice.init()
        modelPresent = engine.isModelPresent()
        if (modelPresent) {
            loadModel()
        } else {
            status = "AI model file needed (one time)"
        }
        messages = messages + ChatMsg(
            "Hi, I'm Jarvis. Talk or type. Try: \"set an alarm for 6:30 am\", " +
                "\"text Mom that I'm on my way\", \"open camera\", \"read the screen\", " +
                "or just chat with me.",
            false
        )
    }

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
                status = "Ready - running offline on your phone"
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

    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        voice.stopSpeaking()
        messages = messages + ChatMsg(t, true)
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
            messages = messages + ChatMsg(reply, false)
            busy = false
            status = if (modelReady) "Ready - running offline on your phone" else ""
            voice.speak(reply, speakOn)
        }
    }

    fun startListening() = voice.listen()

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
        messages = emptyList()
        assistant.resetHistory()
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
