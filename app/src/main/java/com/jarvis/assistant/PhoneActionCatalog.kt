package com.jarvis.assistant

/**
 * Everything Jarvis can actually do on the phone, and what each one needs first.
 * Deliberately free of Android imports so it can be unit tested on the JVM.
 *
 * Permission names are written as literals rather than Manifest constants so this
 * file stays pure; the values are identical to Android's.
 */
data class PhoneAction(
    val id: String,
    val title: String,
    val detail: String,
    val group: String,
    val permission: String? = null,
    val accessibility: Boolean = false,
    val minSdk: Int = 0,
    val example: String? = null,
)

object PhoneActionCatalog {

    const val PERM_MIC = "android.permission.RECORD_AUDIO"
    const val PERM_CONTACTS = "android.permission.READ_CONTACTS"
    const val PERM_CALL = "android.permission.CALL_PHONE"
    const val PERM_SMS = "android.permission.SEND_SMS"
    const val PERM_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    const val GROUP_VOICE = "Voice"
    const val GROUP_MESSAGES = "Messages and calls"
    const val GROUP_DEVICE = "Device control"
    const val GROUP_SYSTEM = "System"

    val all: List<PhoneAction> = listOf(
        PhoneAction(
            id = "voice",
            title = "Voice input",
            detail = "Speak to Jarvis instead of typing.",
            group = GROUP_VOICE,
            permission = PERM_MIC,
            example = "Hey Jarvis, what's the weather like?",
        ),
        PhoneAction(
            id = "notifications",
            title = "Status notifications",
            detail = "Keep the listening service visible while it runs.",
            group = GROUP_VOICE,
            permission = PERM_NOTIFICATIONS,
            minSdk = 33,
        ),
        PhoneAction(
            id = "contacts",
            title = "Look up contacts",
            detail = "Turn a name into a phone number.",
            group = GROUP_MESSAGES,
            permission = PERM_CONTACTS,
        ),
        PhoneAction(
            id = "sms",
            title = "Send text messages",
            detail = "Compose and send an SMS on your behalf.",
            group = GROUP_MESSAGES,
            permission = PERM_SMS,
            example = "Text Mom that I'm on my way",
        ),
        PhoneAction(
            id = "calls",
            title = "Make phone calls",
            detail = "Call a saved contact directly.",
            group = GROUP_MESSAGES,
            permission = PERM_CALL,
            example = "Call Ravi",
        ),
        PhoneAction(
            id = "flashlight",
            title = "Flashlight",
            detail = "Turn the torch on and off.",
            group = GROUP_DEVICE,
            example = "Turn on the flashlight",
        ),
        PhoneAction(
            id = "volume",
            title = "Media volume",
            detail = "Turn the volume up, down or mute.",
            group = GROUP_DEVICE,
            example = "Mute the volume",
        ),
        PhoneAction(
            id = "alarms",
            title = "Alarms",
            detail = "Hand a time to the clock app.",
            group = GROUP_DEVICE,
            example = "Set an alarm for 6:30 am",
        ),
        PhoneAction(
            id = "apps",
            title = "Open apps",
            detail = "Launch any app installed on the phone.",
            group = GROUP_DEVICE,
            example = "Open camera",
        ),
        PhoneAction(
            id = "screen",
            title = "Screen control",
            detail = "Tap, type, scroll and read what is on screen.",
            group = GROUP_SYSTEM,
            accessibility = true,
            example = "Read the screen",
        ),
        PhoneAction(
            id = "settings_radios",
            title = "Wi-Fi and Bluetooth",
            detail = "Open the Wi-Fi or Bluetooth settings screen.",
            group = GROUP_SYSTEM,
            example = "Open Wi-Fi settings",
        ),
        PhoneAction(
            id = "status",
            title = "Time, date and battery",
            detail = "Answer questions about the phone itself.",
            group = GROUP_SYSTEM,
            example = "What's my battery level?",
        ),
    )

    fun forSdk(sdk: Int): List<PhoneAction> = all.filter { sdk >= it.minSdk }

    fun groups(actions: List<PhoneAction> = all): List<String> =
        actions.map { it.group }.distinct()

    /** Why an action is not usable yet, or null when it is ready. */
    fun blocker(
        action: PhoneAction,
        granted: Set<String>,
        accessibilityOn: Boolean,
        sdk: Int,
    ): String? = when {
        sdk < action.minSdk -> "Not available on this Android version"
        action.accessibility && !accessibilityOn -> "Screen control is off"
        action.permission != null && action.permission !in granted -> "Permission needed"
        else -> null
    }

    fun ready(
        action: PhoneAction,
        granted: Set<String>,
        accessibilityOn: Boolean,
        sdk: Int,
    ): Boolean = blocker(action, granted, accessibilityOn, sdk) == null

    /** Permissions worth asking for, in the order they are requested. */
    fun runtimePermissions(sdk: Int): List<String> {
        val perms = all.mapNotNull { it.permission }.distinct().toMutableList()
        if (sdk < 33) perms.remove(PERM_NOTIFICATIONS)
        return perms
    }
}
