package com.jarvis.assistant

import android.content.Context
import com.jarvis.assistant.brain.CloudProvider
import com.jarvis.assistant.brain.cloudProviderFrom

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

    /**
     * The context window the on-device model should run with. Changing it reloads the
     * model the next time it is needed.
     */
    var contextTokens: Int
        get() = ContextBudget.clamp(p.getInt("context", ContextBudget.Default))
        set(v) = p.edit().putInt("context", ContextBudget.clamp(v)).apply()

    /** Whether a cloud model may be used, when there is internet and it is configured. */
    var cloudEnabled: Boolean
        get() = p.getBoolean("cloud", false)
        set(v) = p.edit().putBoolean("cloud", v).apply()

    var cloudProvider: CloudProvider
        get() = cloudProviderFrom(p.getString("cloud_provider", null))
        set(v) = p.edit().putString("cloud_provider", v.name).apply()

    /** Empty means the provider s own default address. */
    var cloudBaseUrl: String
        get() = p.getString("cloud_url", "").orEmpty()
        set(v) = p.edit().putString("cloud_url", v.trim()).apply()

    var cloudModel: String
        get() = p.getString("cloud_model", "").orEmpty()
        set(v) = p.edit().putString("cloud_model", v.trim()).apply()

    /** Stored encrypted with a Keystore key when the device supports it. */
    var cloudApiKey: String
        get() = SecretBox.read(p, "cloud_key")
        set(v) = SecretBox.write(p, "cloud_key", v.trim())

    val cloudKeyProtected: Boolean get() = SecretBox.isProtected(p, "cloud_key")

    /** How long a cloud reply may be. */
    var cloudMaxTokens: Int
        get() = p.getInt("cloud_max", 512)
        set(v) = p.edit().putInt("cloud_max", v.coerceIn(64, 4096)).apply()
}
