package com.jarvis.assistant

import android.content.Context

/** Small, persistent user settings. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var speakEnabled: Boolean
        get() = p.getBoolean("speak", true)
        set(v) = p.edit().putBoolean("speak", v).apply()

    var internetEnabled: Boolean
        get() = p.getBoolean("internet", false)
        set(v) = p.edit().putBoolean("internet", v).apply()

    var wakeWordEnabled: Boolean
        get() = p.getBoolean("wake", false)
        set(v) = p.edit().putBoolean("wake", v).apply()

    /** Ask before actually sending an SMS. Safer default. */
    var confirmSms: Boolean
        get() = p.getBoolean("confirm_sms", true)
        set(v) = p.edit().putBoolean("confirm_sms", v).apply()

    /**
     * Chat mode is conversation only; Control mode may use the phone.
     * Anything unreadable (including a missing value) means Chat.
     */
    var mode: Mode
        get() = Mode.from(p.getString("mode", null))
        set(v) = p.edit().putString("mode", v.tag).apply()

    /** Call directly instead of opening the dialer. */
    var directCall: Boolean
        get() = p.getBoolean("direct_call", false)
        set(v) = p.edit().putBoolean("direct_call", v).apply()

    /**
     * Reply language for speech and for the model prompt.
     * "auto" follows the phone's own language.
     */
    var language: String
        get() = p.getString("language", "auto") ?: "auto"
        set(v) = p.edit().putString("language", v).apply()
}
