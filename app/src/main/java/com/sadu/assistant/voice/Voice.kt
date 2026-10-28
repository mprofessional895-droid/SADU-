package com.sadu.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speech in and speech out.
 *
 * Reports real microphone level while listening ([onLevel]) so the orb can react
 * to an actual voice, and reports when speech starts and finishes so the UI can
 * show a speaking state.
 */
class Voice(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onListenStart: () -> Unit,
    private val onError: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onSpeakStart: () -> Unit,
    private val onSpeakDone: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var languageTag = "auto"

    val isTtsReady: Boolean get() = ttsReady

    fun isSpeechRecognitionAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(ctx) }.getOrDefault(false)

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(ctx) { status ->
            onMain {
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    applyLanguage()
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = onMain(onSpeakStart)
                        override fun onDone(utteranceId: String?) = onMain(onSpeakDone)
                        override fun onError(utteranceId: String?) = onMain(onSpeakDone)
                        override fun onError(utteranceId: String?, errorCode: Int) = onMain(onSpeakDone)
                    })
                }
            }
        }
    }

    fun setLanguage(tag: String) {
        languageTag = tag
        applyLanguage()
    }

    fun locale(): Locale =
        if (languageTag == "auto" || languageTag.isBlank()) Locale.getDefault()
        else Locale.forLanguageTag(languageTag)

    /** Whether a text-to-speech voice exists for this language on this phone. */
    fun languageAvailable(tag: String): Boolean = try {
        val loc = if (tag == "auto" || tag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(tag)
        (tts?.isLanguageAvailable(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
    } catch (_: Throwable) {
        false
    }

    private fun applyLanguage() {
        try {
            tts?.language = locale()
        } catch (_: Throwable) {
        }
    }

    fun speak(text: String, enabled: Boolean) {
        if (!enabled || !ttsReady) return
        val clean = text.replace(Regex("[*_#`>]"), "").take(600)
        if (clean.isBlank()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "sadu")
    }

    fun isSpeaking(): Boolean = runCatching { tts?.isSpeaking == true }.getOrDefault(false)

    fun stopSpeaking() {
        runCatching { tts?.stop() }
    }

    fun listen() {
        if (!isSpeechRecognitionAvailable()) {
            onError("Speech recognition isn't available on this phone.")
            return
        }
        stopSpeaking()
        runCatching { recognizer?.destroy() }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListenStart()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Real microphone level. Android reports roughly -2 dB (silence) to 10 dB (loud).
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onLevel(0f)

            override fun onError(error: Int) {
                onLevel(0f)
                onError(messageFor(error))
            }

            override fun onResults(results: Bundle?) {
                onLevel(0f)
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                if (heard != null) onText(heard)
                else onError("I didn't catch that. Tap the microphone and try again.")
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        try {
            r.startListening(intent)
        } catch (e: Throwable) {
            onError("I couldn't start listening: ${e.message}")
        }
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Tap the microphone and try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "I need microphone permission to listen."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "I'm still finishing the last thing you said."
        else -> "Voice input failed (code $error)."
    }

    fun destroy() {
        runCatching { tts?.shutdown() }
        runCatching { recognizer?.destroy() }
        tts = null
        recognizer = null
        ttsReady = false
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
