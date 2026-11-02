package com.sadu.assistant.perf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.sadu.assistant.MainActivity

/**
 * Keeps the process alive while a reply is being written, and says so in the shade.
 *
 * It exists for one reason: without a foreground service Android is free to kill the app the
 * moment the screen goes off, which loses the answer mid-sentence. It does not run the model -
 * the engine keeps its own thread - and it does not hold the CPU awake beyond the turn.
 *
 * START_NOT_STICKY on purpose: a resurrected service with no turn behind it would show a
 * notification for work that no longer exists.
 */
class GenerationService : Service() {

    private var wake: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        channel()
        wake = runCatching {
            (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sadu:generation")
                .apply {
                    setReferenceCounted(false)
                    acquire(MAX_HOLD_MS)
                }
        }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                onStopRequested?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                notify(text.ifBlank { "Writing a reply…" })
                return START_NOT_STICKY
            }
            else -> notify(intent?.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Writing a reply…" })
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        wake = null
        super.onDestroy()
    }

    private fun channel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Answers being written", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when Sadu is generating a reply"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun notify(text: String) {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, GenerationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Sadu is thinking")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, built, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, built)
        }
    }

    companion object {
        private const val CHANNEL = "sadu_generation"
        private const val NOTIFICATION_ID = 4242
        private const val ACTION_STOP = "com.sadu.assistant.perf.STOP"
        private const val ACTION_UPDATE = "com.sadu.assistant.perf.UPDATE"
        private const val EXTRA_TEXT = "text"
        private const val MAX_HOLD_MS = 15L * 60 * 1000

        /** Set by the view model: the notification's Stop button lands here. */
        @Volatile
        var onStopRequested: (() -> Unit)? = null

        fun start(ctx: Context, text: String) {
            runCatching {
                ctx.startForegroundService(
                    Intent(ctx, GenerationService::class.java).putExtra(EXTRA_TEXT, text)
                )
            }
            PerfMonitor.setBackground(true)
        }

        fun update(ctx: Context, text: String) {
            runCatching {
                ctx.startService(
                    Intent(ctx, GenerationService::class.java)
                        .setAction(ACTION_UPDATE)
                        .putExtra(EXTRA_TEXT, text)
                )
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, GenerationService::class.java)) }
            PerfMonitor.setBackground(false)
        }
    }
}
