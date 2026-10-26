package com.jarvis.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Wraps text-to-speech and speech-to-text.
 * Everything here must be created and used on the main thread.
 */
class Voice(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    fun init() {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    fun speak(text: String, enabled: Boolean) {
        if (!enabled || !ttsReady) return
        val clean = text.replace(Regex("[*_#`>]"), "").take(600)
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onError("Speech recognition is not available on this phone.")
            return
        }
        stopSpeaking()
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onStatus("Listening...")
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onStatus("Got it...")
            override fun onError(error: Int) {
                onStatus("")
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        onError("I didn't catch that. Tap the mic and try again.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        onError("I need microphone permission to listen.")
                    else ->
                        onError("Voice input failed (code $error).")
                }
            }
            override fun onResults(results: Bundle?) {
                onStatus("")
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) onText(t)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        r.startListening(intent)
    }

    fun destroy() {
        try { tts?.shutdown() } catch (_: Throwable) {}
        try { recognizer?.destroy() } catch (_: Throwable) {}
        tts = null
        recognizer = null
        ttsReady = false
    }
}
