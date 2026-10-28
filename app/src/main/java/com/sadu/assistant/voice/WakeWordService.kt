package com.sadu.assistant.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.sadu.assistant.MainActivity

/**
 * Experimental always-listening mode. Runs the phone's speech recognizer in a loop
 * and opens Sadu when it hears the wake phrase ("sadu").
 * Kept off by default; enable it from Settings.
 */
class WakeWordService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        if (!running) {
            running = true
            startLoop()
        }
        return START_STICKY
    }

    private fun startForegroundSafely() {
        val channelId = "sadu_wake"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Sadu listening", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sadu is listening")
            .setContentText("Say \"Hey Sadu\" to start")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notif)
        }
    }

    private fun startLoop() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                val said = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: return
                if (said.contains("sadu")) onWake()
            }
            override fun onResults(results: Bundle?) {
                val said = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase()
                if (said != null && said.contains("sadu")) onWake() else restart()
            }
            override fun onError(error: Int) = restart()
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            r.startListening(intent)
        } catch (_: Throwable) {
            restart()
        }
    }

    private fun onWake() {
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_WAKE, true)
        startActivity(i)
        restart()
    }

    private fun restart() {
        if (!running) return
        handler.postDelayed({
            if (running) startLoop()
        }, 600)
    }

    override fun onDestroy() {
        running = false
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null
        super.onDestroy()
    }
}
