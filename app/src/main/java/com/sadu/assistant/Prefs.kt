package com.sadu.assistant

import android.content.Context
import com.sadu.assistant.brain.CloudProvider
import com.sadu.assistant.brain.LocalEngine
import com.sadu.assistant.brain.cloudProviderFrom
import com.sadu.assistant.prompt.ChatPrompt
import com.sadu.assistant.prompt.ChatStyle

/** Small, persistent user settings. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("sadu_prefs", Context.MODE_PRIVATE)

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

    /**
     * TEMPORARY (manual testing): send my own system prompt instead of the built-in one.
     * Off means the shipped prompts are used, exactly as designed.
     */
    var customPromptOn: Boolean
        get() = p.getBoolean("custom_prompt_on", false)
        set(v) = p.edit().putBoolean("custom_prompt_on", v).apply()

    /** Chat mode's own prompt. Empty while ON means no instructions at all. */
    var customChatPrompt: String
        get() = p.getString("custom_chat_prompt", "") ?: ""
        set(v) = p.edit().putString("custom_chat_prompt", v).apply()

    /** Control mode's own prompt. Empty while ON means no instructions at all. */
    var customControlPrompt: String
        get() = p.getString("custom_control_prompt", "") ?: ""
        set(v) = p.edit().putString("custom_control_prompt", v).apply()

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

    /**
     * Which on-device engine runs the model: MediaPipe's `.task` bundle or llama.cpp's `.gguf`
     * file. Both are kept, so switching back is instant. Anything unreadable means MediaPipe.
     */
    var localEngine: LocalEngine
        get() = LocalEngine.of(p.getString("local_engine", null))
        set(v) = p.edit().putString("local_engine", v.key).apply()

    /** Whether Sadu may look something up and answer from it, instead of opening a browser. */
    var webAnswers: Boolean
        get() = p.getBoolean("web_answers", true)
        set(v) = p.edit().putBoolean("web_answers", v).apply()

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

    /** How long Sadu should make its answers. */
    var replyLength: ReplyLength
        get() = ReplyLength.of(p.getString("reply_length", null))
        set(v) = p.edit().putString("reply_length", v.key).apply()

    /** How Chat mode's answers should read: no personality imposed, or a friendly one. */
    var chatStyle: ChatStyle
        get() = ChatStyle.of(p.getString("chat_style", null))
        set(v) = p.edit().putString("chat_style", v.key).apply()

    /** How lively the on-device Chat answers should be. */
    var chatTemperature: Float
        get() = Sampling.clampTemperature(p.getFloat("chat_temp", Sampling.DefaultTemperature))
        set(v) = p.edit().putFloat("chat_temp", Sampling.clampTemperature(v)).apply()

    /** Extra instructions the user wrote for Chat mode, or "". */
    var chatInstructions: String
        get() = p.getString("chat_instructions", "").orEmpty()
        set(v) = p.edit().putString("chat_instructions", v.trim().take(ChatPrompt.MAX_EXTRA)).apply()

    /**
     * Whether a reasoning model may write its working-out before the answer.
     * On by default, which is how every model behaved before this switch existed.
     */
    var thinkingEnabled: Boolean
        get() = p.getBoolean("thinking", true)
        set(v) = p.edit().putBoolean("thinking", v).apply()
}
