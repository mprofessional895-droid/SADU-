package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var engine: LlmEngine
    private lateinit var memory: Memory
    private lateinit var actions: Actions

    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var modelButton: Button

    private val worker = Executors.newSingleThreadExecutor()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    private val history = ArrayList<Pair<String, String>>()
    private var modelReady = false
    private var busy = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val pickModel = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importModel(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = LlmEngine(this)
        memory = Memory(this)
        actions = Actions(this, memory)
        buildUi()

        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }

        permLauncher.launch(
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        )

        addBubble(
            "Hi, I'm Jarvis. Talk to me or type. Try: \"open camera\", \"set an alarm for 6:30 am\", " +
                "\"call Mom\", \"turn on the flashlight\", or \"remember that I like chai\".",
            false
        )
        if (engine.isModelPresent()) loadModel() else showNeedModel()
    }

    // ------------------------------------------------------------ UI

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(px(10), px(10), px(10), px(10))
        }

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#9AA5B1"))
            textSize = 13f
        }
        root.addView(status)

        modelButton = Button(this).apply {
            text = "Choose model file"
            visibility = View.GONE
            setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        }
        root.addView(modelButton)

        chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(chat) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Message Jarvis"
            setHintTextColor(Color.parseColor("#6B7785"))
            setTextColor(Color.WHITE)
            maxLines = 4
        }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val mic = Button(this).apply {
            text = "Mic"
            setOnClickListener { listen() }
        }
        bar.addView(mic)

        val send = Button(this).apply {
            text = "Send"
            setOnClickListener {
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    input.setText("")
                    submit(t)
                }
            }
        }
        bar.addView(send)

        root.addView(bar)
        setContentView(root)
    }

    private fun setStatus(s: String) {
        status.text = s
    }

    private fun addBubble(text: String, mine: Boolean): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(px(14), px(10), px(14), px(10))
            maxWidth = (resources.displayMetrics.widthPixels * 0.8).toInt()
            background = GradientDrawable().apply {
                cornerRadius = px(16).toFloat()
                setColor(Color.parseColor(if (mine) "#2B5CE6" else "#222A33"))
            }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            topMargin = px(6)
        }
        chat.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    // ------------------------------------------------------------ Model

    private fun showNeedModel() {
        setStatus("AI model file needed (one time)")
        modelButton.visibility = View.VISIBLE
        addBubble(
            "To chat with me I need my AI model file (a .task file for Gemma 3 1B). " +
                "Tap \"Choose model file\" above and pick it from your Downloads. " +
                "Commands like alarms and opening apps already work without it.",
            false
        )
    }

    private fun loadModel() {
        setStatus("Loading AI model...")
        worker.execute {
            val err = try {
                engine.load()
                null
            } catch (e: Throwable) {
                e.message ?: e.toString()
            }
            runOnUiThread {
                if (err == null) {
                    modelReady = true
                    modelButton.visibility = View.GONE
                    setStatus("Ready - running offline on your phone")
                } else {
                    setStatus("Model failed to load")
                    modelButton.text = "Choose a different model file"
                    modelButton.visibility = View.VISIBLE
                    addBubble("I couldn't load the model: $err", false)
                }
            }
        }
    }

    private fun importModel(uri: Uri) {
        modelReady = false
        modelButton.visibility = View.GONE
        setStatus("Copying model (one time only)...")
        worker.execute {
            val err = try {
                engine.importModel(uri) { bytes ->
                    if (bytes % (50L shl 20) < (1L shl 20)) {
                        runOnUiThread { setStatus("Copying model... ${bytes shr 20} MB") }
                    }
                }
                null
            } catch (e: Throwable) {
                e.message ?: e.toString()
            }
            runOnUiThread {
                if (err != null) {
                    setStatus("Copy failed")
                    modelButton.visibility = View.VISIBLE
                    addBubble("I couldn't copy that file: $err", false)
                } else {
                    loadModel()
                }
            }
        }
    }

    // ------------------------------------------------------------ Chat

    private fun submit(text: String) {
        tts?.stop()
        addBubble(text, true)

        val quick = try {
            actions.tryHandle(text)
        } catch (e: Exception) {
            "That action failed: ${e.message}"
        }
        if (quick != null) {
            addBubble(quick, false)
            speak(quick)
            return
        }

        if (!modelReady) {
            val m = if (engine.isModelPresent()) {
                "I'm still waking up. Try again in a moment."
            } else {
                "I need my AI model file first. Tap \"Choose model file\" at the top."
            }
            addBubble(m, false)
            return
        }
        if (busy) {
            addBubble("One moment, I'm still thinking.", false)
            return
        }

        busy = true
        val placeholder = addBubble("...", false)
        val prompt = buildPrompt(text)
        worker.execute {
            val reply = try {
                engine.generate(prompt)
                    .replace("<end_of_turn>", "")
                    .trim()
                    .ifEmpty { "Hmm, I'm not sure what to say to that." }
            } catch (e: Throwable) {
                "Sorry, my brain hit an error: ${e.message}"
            }
            runOnUiThread {
                placeholder.text = reply
                history.add(text to reply)
                busy = false
                speak(reply)
            }
        }
    }

    private fun buildPrompt(userText: String): String {
        val persona = StringBuilder(
            "You are Jarvis, the user's warm, friendly personal assistant and close friend, " +
                "running privately on their phone. Talk casually like a friend. " +
                "Keep answers short: 1 to 3 sentences. Reply in the same language the user uses."
        )
        val facts = memory.asContext()
        if (facts.isNotEmpty()) persona.append(" Things you know about the user: ").append(facts).append(".")

        val sb = StringBuilder()
        var first = true
        fun userTurn(t: String) {
            sb.append("<start_of_turn>user\n")
            if (first) {
                sb.append(persona).append("\n\n")
                first = false
            }
            sb.append(t).append("<end_of_turn>\n")
        }
        for ((u, a) in history.takeLast(2)) {
            userTurn(u.take(200))
            sb.append("<start_of_turn>model\n").append(a.take(200)).append("<end_of_turn>\n")
        }
        userTurn(userText.take(500))
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    // ------------------------------------------------------------ Voice input

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition is not available on this phone", Toast.LENGTH_LONG).show()
            return
        }
        tts?.stop()
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                setStatus("Listening...")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                setStatus("Got it...")
            }

            override fun onError(error: Int) {
                setStatus(
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        "Didn't catch that. Tap Mic and try again."
                    } else {
                        "Voice input failed (code $error). For offline use, download the offline speech pack in your phone's Google app settings."
                    }
                )
            }

            override fun onResults(results: Bundle?) {
                setStatus(if (modelReady) "Ready - running offline on your phone" else "Ready")
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) submit(t)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        r.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        recognizer?.destroy()
        worker.shutdown()
        engine.close()
    }
}
