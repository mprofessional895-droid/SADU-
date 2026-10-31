package com.sadu.assistant

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import com.sadu.assistant.control.SaduAccessibilityService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Everything Sadu can DO on the phone. Two entry points:
 *  - [tryHandle]: fast, reliable rule matching for common commands (no AI needed).
 *  - the public helpers below: called by the AI tool layer when it decides to act.
 */
class Actions(
    private val ctx: Context,
    private val memory: Memory,
    private val prefs: Prefs,
    private val accessibility: () -> SaduAccessibilityService?,
) {

    /**
     * Called when an action needs a permission the app does not have, so the screen can offer
     * to ask for it instead of only printing a sentence about Settings. Set by the view model.
     */
    var onNeedPermission: ((String) -> Unit)? = null

    /** The names a suggestion offered, so the screen can show them as a chooser. */
    var onChoices: ((List<String>) -> Unit)? = null

    /**
     * The query when the message reads as "search about X" / "google X" / "look up X",
     * or null. It only ever sees text that already looks like a command, so ordinary
     * conversation cannot be mistaken for a search.
     */
    fun searchQuery(raw: String): String? {
        val cmd = CommandParser.commandText(raw.trim()) ?: return null
        val found = Regex("^(?:search(?: the web| online)?(?: for| about)?|google|look up)\\s+(.+)$")
            .find(cmd) ?: return null
        return found.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    /** Returns a reply if the text matched a rule, or null if it is normal chat. */
    fun tryHandle(raw: String): String? = handle(raw, chatOnly = false)

    /**
     * The rules Chat mode may still run: Sadu's own memory, the clock, and the network.
     * Everything that reaches into the phone returns null, so it stays a conversation.
     */
    fun tryHandleChatMode(raw: String): String? = handle(raw, chatOnly = true)

    private fun handle(raw: String, chatOnly: Boolean): String? {
        val clean = normalize(raw)
        if (clean.isEmpty()) return null
        val t = clean.lowercase()
        val ic = RegexOption.IGNORE_CASE

        // A "yes" or a corrected name belongs to the suggestion we just made.
        if (!chatOnly) answerPick(clean)?.let { return it }

        // ---- memory ----
        Regex(
            "^(?:(?:hey |ok |okay )?sadu[ ,]+)?(?:please )?" +
                "(?:remember|note down|note|save|keep in mind|don'?t forget)" +
                "(?: that| this)?[ ,:]+(.+)$",
            ic
        ).find(clean)?.let {
            val fact = it.groupValues[1].trim()
            memory.add(fact)
            return "Got it, I'll remember: $fact."
        }
        Regex("^(?:hi,? |hello,? )?(?:my name is|call me)\\s+(.+)$", ic).find(clean)?.let {
            val name = it.groupValues[1].trim()
            memory.setName(name)
            return "Nice to meet you, $name! I'll remember your name."
        }
        if (Regex("what do you (?:remember|know) about me|what do you remember").containsMatchIn(t)) {
            val f = memory.facts()
            return if (f.isEmpty()) {
                "I don't know anything about you yet. Tell me something with \"remember that ...\"."
            } else {
                "Here's what I know: " + f.joinToString("; ") + "."
            }
        }
        if (t == "forget everything" || t == "forget all" || t == "clear your memory") {
            memory.clear()
            return "Done. I've forgotten everything about you."
        }

        // ---- questions Sadu can always answer, command or not ----
        if (Regex("^(?:sadu[ ,]+)?(?:what(?:'s| is)? the time|what time is it|current time|time)(?: now| please)?$").matches(t)) {
            return "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
        }
        if (Regex("^(?:sadu[ ,]+)?(?:what(?:'s| is)? the date|what day is it|today'?s date|what(?:'s| is)? today)(?: today)?$").matches(t)) {
            return "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
        }
        if (!chatOnly) {
            if (Regex("\\bbattery\\b").containsMatchIn(t)) return battery()
            if (Regex("\\b(read (?:the )?screen|what'?s on (?:the|my) screen|describe (?:the )?screen)\\b")
                    .containsMatchIn(t)
            ) {
                return readScreen()
            }
        }

        // Hindi word order ("papa ko call kar", "mummy ko message kar ki ..."). Checked
        // before commandText, which only accepts English verb-first wording, and only when
        // a real contact is behind the name - otherwise it is conversation, not an order.
        if (!chatOnly) hindiOrder(clean)?.let { return it }

        // Everything past here actually touches the phone, so only run it when the
        // message reads as a command. Otherwise hand it to the brain as conversation.
        val cmd = CommandParser.commandText(clean) ?: return null

        // ---- the network ----
        // Chat mode can reach the web through Sadu itself - the Assistant looks the
        // question up and the model answers from what came back - but it never opens
        // anything on the phone: no browser tab, no Play Store.
        searchQuery(cmd)?.let { query ->
            // Nothing can be looked up with the internet off, so say so - a sentence, not a
            // browser tab, because opening a browser is not an answer.
            if (!prefs.internetEnabled) return INTERNET_OFF
            // With background answers on, the Assistant looks the question up and answers
            // from the results, so the rules step aside for it. Chat mode never opens a
            // browser tab: that is a phone action, and it has none.
            if (chatOnly || prefs.webAnswers) return null
            return webSearch(query)
        }

        // Chat mode stops here. Everything below can change something on the phone.
        if (chatOnly) return null

        Regex("^(?:install|download)\\s+(?:the\\s+)?(?:app\\s+)?(.+)$").find(cmd)?.let {
            return installApp(it.groupValues[1])
        }
        Regex("^(?:open|go to)\\s+((?:https?://|www\\.)\\S+)$").find(cmd)?.let {
            return openUrl(it.groupValues[1])
        }

        // ---- screen navigation ----
        screenCommand(cmd)?.let { return it }

        // ---- flashlight ----
        if (cmd.contains("flashlight") || cmd.contains("torch")) {
            val off = cmd.contains(" off") || cmd.contains("disable") ||
                cmd.startsWith("off") || cmd.startsWith("turn off") || cmd.startsWith("switch off")
            return setTorch(!off)
        }

        // ---- volume ----
        if (cmd.contains("volume") || cmd.contains("louder") || cmd.contains("quieter")) {
            val dir = when {
                cmd.contains("unmute") -> "unmute"
                cmd.contains("mute") -> "mute"
                cmd.contains("down") || cmd.contains("lower") || cmd.contains("decrease") ||
                    cmd.contains("reduce") || cmd.contains("quieter") -> "down"
                else -> "up"
            }
            return volume(dir)
        }

        // ---- music that is already playing ----
        Regex("\\b(pause|resume|stop|unpause)(?: the)? (?:music|song|playback|audio)\\b")
            .find(cmd)?.let { return media(it.groupValues[1]) }
        Regex("\\b(next|previous|last|skip)(?: the)? (?:song|track|music)\\b")
            .find(cmd)?.let { return media(it.groupValues[1]) }

        // ---- wifi / bluetooth settings ----
        val radio = Regex("^(open|show|go to|turn on|turn off|enable|disable|connect)\\b.*\\b(wifi|wi-fi|wi fi|bluetooth)\\b")
            .containsMatchIn(cmd) || Regex("^(wifi|wi-fi|wi fi|bluetooth)$").containsMatchIn(cmd)
        if (radio) {
            return if (cmd.contains("bluetooth")) {
                if (go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) {
                    "Opening Bluetooth settings. Android doesn't let apps flip the switch, so tap it yourself."
                } else "I couldn't open Bluetooth settings."
            } else {
                if (go(Intent(Settings.ACTION_WIFI_SETTINGS))) {
                    "Opening Wi-Fi settings. Android doesn't let apps flip the switch, so tap it yourself."
                } else "I couldn't open Wi-Fi settings."
            }
        }

        // ---- alarm ----
        if (cmd.contains("alarm") || cmd.startsWith("wake me")) {
            Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?").find(cmd)?.let {
                return setAlarm(it.value)
            }
            return "What time should I set it for? For example: set an alarm for 6:30 am."
        }

        // ---- send a message ----
        val sms = Regex(
            "^(?:send|write)\\s+(?:a\\s+)?(?:message|text|sms)\\s+to\\s+(.+?)(?:\\s+(?:saying|that says|say|with message)\\s+(.+))?$"
        ).find(cmd) ?: Regex(
            "^(?:text|message|sms)\\s+(.+?)(?:\\s+(?:saying|that says|say)\\s+(.+))?$"
        ).find(cmd)
        if (sms != null) {
            val who = sms.groupValues[1].trim()
            if (CommandParser.isRealTarget(who)) return sendSms(who, sms.groupValues[2])
        }

        // ---- WhatsApp ----
        whatsappOrder(cmd)?.let { (who, body) ->
            if (CommandParser.isRealTarget(who)) {
                return whatsapp(who, body).display
            }
        }

        // ---- call ----
        Regex("^(?:call|phone|dial|ring)\\s+(.+)$").find(cmd)?.let {
            val who = it.groupValues[1].trim()
            if (CommandParser.isRealTarget(who)) return call(who)
        }

        // ---- open an app (only if we really recognise one) ----
        Regex("^(?:open|launch|start)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$").find(cmd)?.let {
            val name = it.groupValues[1].trim()
            if (CommandParser.isRealTarget(name)) {
                resolveApp(name)?.let { (handle, label) ->
                    return if (go(handle)) "Opening $label." else "I couldn't open $label."
                }
            }
        }

        // ---- play something ----
        Regex("^play\\s+(.+)$").find(cmd)?.let {
            val q = it.groupValues[1].trim()
            if (CommandParser.isRealTarget(q)) return play(q)
        }

        return null
    }

    // ------------------------------------------------------------ screen control

    private fun screenCommand(t: String): String? {
        val nav = Regex(
            "\\b(go back|press back|navigate back|go home|press home|home screen|" +
                "recent apps|show recents|app switcher|scroll down|scroll up)\\b"
        )
        if (nav.containsMatchIn(t)) {
            val action = when {
                t.contains("back") -> "back"
                t.contains("home") -> "home"
                t.contains("recent") || t.contains("switcher") -> "recents"
                t.contains("down") -> "scroll_down"
                else -> "scroll_up"
            }
            return accessibilityAction(action, null)
        }
        Regex("^tap (?:on )?(.+)$").find(t)?.let {
            val label = it.groupValues[1].trim()
            if (label.isNotBlank() && label.split(" ").size <= 6) {
                return accessibilityAction("tap", label)
            }
        }
        Regex("^type (?:the text |this )?(.+)$").find(t)?.let {
            val text = it.groupValues[1].trim()
            if (text.isNotBlank()) return accessibilityAction("type", text)
        }
        return null
    }

    // ------------------------------------------------------------ helpers used by tools

    fun setTorch(on: Boolean): String = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) "This phone doesn't seem to have a flashlight."
        else {
            cm.setTorchMode(id, on)
            if (on) "Flashlight on." else "Flashlight off."
        }
    } catch (e: Exception) {
        "I couldn't control the flashlight: ${e.message}"
    }

    fun battery(): String = try {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val st = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = st?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        "Your battery is at $pct%" + if (charging) " and charging." else "."
    } catch (e: Exception) {
        "I couldn't read the battery: ${e.message}"
    }

    fun volume(direction: String): String = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (direction) {
            "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "unmute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            "down" -> repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
            else -> repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
        }
        when (direction) {
            "mute" -> "Muted."
            "unmute" -> "Unmuted."
            "down" -> "Volume down."
            else -> "Volume up."
        }
    } catch (e: Exception) {
        "I couldn't change the volume: ${e.message}"
    }

    /** Set the media volume to an exact percentage, so "volume 50%" means 50. */
    fun volumeLevel(percent: Int): String = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val level = ((percent.coerceIn(0, 100) / 100f) * max).roundToInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, level, AudioManager.FLAG_SHOW_UI)
        if (percent <= 0) "Muted." else "Volume set to $percent%."
    } catch (e: Exception) {
        "I couldn't set the volume: ${e.message}"
    }

    fun setAlarm(time: String): String {
        val t = time.lowercase()
        val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?").find(t)
            ?: return "What time should I set it for? For example: set an alarm for 6:30 am."
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val suffix = m.groupValues[3].replace(".", "")
        if (min > 59 || h > 23 || (suffix.isNotEmpty() && h > 12)) {
            return "I couldn't understand that time. Try: set an alarm for 6:30 am."
        }
        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val candidates = when {
            suffix == "am" -> listOf(h % 12)
            suffix == "pm" -> listOf(h % 12 + 12)
            h in 1..12 -> listOf(h % 12, h % 12 + 12)
            else -> listOf(h)
        }
        // With no am/pm, choose whichever comes next.
        val hour = candidates.minByOrNull { c ->
            val d = (c * 60 + min - nowMin + 1440) % 1440
            if (d == 0) 1440 else d
        } ?: h
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, min)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Sadu")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
        }
        val label = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
        return if (go(i)) "Alarm set for $label." else "I couldn't open the clock app to set the alarm."
    }

    /**
     * Pause, resume, skip. Sent as media keys, the same thing a headset button sends, so it
     * works whatever is playing and changes nothing that cannot be changed back.
     */
    fun media(action: String): String = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val key = when (action.trim().lowercase()) {
            "resume", "play", "unpause" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "next", "forward", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "last", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PAUSE
        }
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
        when (key) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> "Playing."
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Next track."
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Previous track."
            KeyEvent.KEYCODE_MEDIA_STOP -> "Stopped."
            else -> "Paused."
        }
    } catch (e: Exception) {
        "I couldn't control the music: ${e.message}"
    }

    fun call(who: String): String {
        when (val found = resolve(who)) {
            is Found.One -> return callContact(found.contact)
            is Found.Many -> return askWhich(found.options, What.Call, "", who, "call")
            Found.None -> return contactMiss(who, What.Call, "")
        }
    }

    private fun callContact(hit: Contact): String {
        val direct = prefs.directCall &&
            ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (direct) {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + hit.number.trim()))
            return if (go(i)) "Calling ${hit.name}." else "I couldn't start the call."
        }
        val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + hit.number.trim()))
        return if (go(i)) {
            "Opening ${hit.name}'s number in the dialer - tap the call button to confirm. " +
                "Settings > Messages and calls > Place calls directly lets me call without that tap."
        } else "I couldn't open the dialer. Is a phone app installed?"
    }

    fun sendSms(who: String, body: String): String {
        if (body.isBlank()) return "What should the message to $who say?"
        val hit = when (val found = resolve(who.trim())) {
            is Found.One -> found.contact
            is Found.Many -> return askWhich(found.options, What.Message, body, who.trim(), "text")
            Found.None -> return contactMiss(who.trim(), What.Message, body)
        }
        val canSend = ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (canSend && !prefs.confirmSms) {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val sm = ctx.getSystemService(SmsManager::class.java)
                    sm.sendTextMessage(hit.number, null, body, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault().sendTextMessage(hit.number, null, body, null, null)
                }
                "Message sent to ${hit.name}."
            } catch (e: Exception) {
                "I couldn't send the message: ${e.message}"
            }
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + hit.number.trim()))
            .putExtra("sms_body", body)
        return if (go(i)) {
            "I've written the message to ${hit.name}. Tap send in the Messages app to confirm."
        } else "I couldn't open the messages app."
    }

    /**
     * WhatsApp, the careful way: find the contact, open their chat with the message already
     * typed, and stop. The user taps Send. Nothing is ever sent behind their back, and no
     * accessibility trick is used to press a button inside another app.
     */
    fun whatsapp(who: String, body: String): ToolOutcome {
        if (body.isBlank()) return ToolOutcome("What should the message to $who say?")
        when (val found = resolve(who.trim())) {
            is Found.One -> return message(found.contact, body)
            is Found.Many -> return ToolOutcome(
                askWhich(found.options, What.Message, body, who.trim(), "message"),
            )
            Found.None -> {
                // A WhatsApp group is not in the phone's contacts, so the only way to reach
                // one is to search WhatsApp itself - by hand, with the user's own phone.
                groupSearch(who.trim(), body)?.let { return it }
                return ToolOutcome(contactMiss(who.trim(), What.Message, body))
            }
        }
    }

    /** Opens the chat with a message typed, and stops: the user taps Send. */
    private fun message(contact: Contact, body: String): ToolOutcome {
        if (openWhatsApp(contact, body)) {
            return ToolOutcome(
                display = "I've opened WhatsApp with the message to ${contact.name} ready. Tap Send.",
                awaitingUser = true,
                confirmHint = "Send in WhatsApp",
            )
        }
        return ToolOutcome("I couldn't open WhatsApp. Check that it is installed.")
    }

    /**
     * A WhatsApp group is not a contact - the phone has no list of them - so the only way to
     * reach one is to use WhatsApp's own search, the way a person would: open it, type the
     * group's name, open the chat and leave the message typed for the user to send. It runs
     * only for a name that says "group", so a mistyped contact name can never land here.
     */
    private fun groupSearch(name: String, body: String): ToolOutcome? {
        if (!name.contains("group", ignoreCase = true) && !name.contains("grp", ignoreCase = true)) {
            return null
        }
        val service = accessibility() ?: return ToolOutcome(
            "WhatsApp groups need Screen control. Turn it on in Settings, then ask again.",
        )
        val pkg = WHATSAPP_PACKAGES.firstOrNull {
            ctx.packageManager.getLaunchIntentForPackage(it) != null
        } ?: return null
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        if (!go(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return ToolOutcome("I couldn't open WhatsApp.")
        }
        Thread.sleep(1800)
        if (!service.performAction("tap", "search").startsWith("Tapped")) {
            service.performAction("tap", "chats")
            Thread.sleep(600)
            service.performAction("tap", "search")
        }
        Thread.sleep(700)
        service.performAction("type", name)
        Thread.sleep(1600)
        val opened = service.performAction("tap_result", name)
        if (!opened.startsWith("Opened")) {
            return ToolOutcome(
                "I opened WhatsApp but couldn't find a chat called \"$name\". " +
                    "Check the exact group name and ask again.",
            )
        }
        Thread.sleep(1400)
        service.performAction("type", body)
        Thread.sleep(400)
        return ToolOutcome(
            display = "I've opened the WhatsApp chat \"$name\" with your message typed. Tap Send.",
            awaitingUser = true,
            confirmHint = "Send in WhatsApp",
        )
    }

    /**
     * WhatsApp by the contact's own row first.
     *
     * A `wa.me` link makes WhatsApp look the *number* up on its servers, which fails for a
     * number the account does not use - even though the person is sitting right there in the
     * contact list. Opening the contact's data row instead hands WhatsApp the saved contact,
     * so the chat opens the same way it does from the contact app.
     */
    private fun openWhatsApp(hit: Contact, body: String): Boolean {
        hit.dataId?.let { id ->
            val uri = Uri.parse("content://com.android.contacts/data/" + id)
            for (pkg in WHATSAPP_PACKAGES) {
                if (go(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg))) return true
            }
        }
        val intl = internationalNumber(hit.number) ?: return false
        val url = "https://wa.me/$intl?text=" + Uri.encode(body)
        for (pkg in WHATSAPP_PACKAGES) {
            if (go(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))) return true
        }
        // `smsto:` with the package set also lands in a chat, and some builds prefer it.
        for (pkg in WHATSAPP_PACKAGES) {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + intl))
                .putExtra("sms_body", body)
                .setPackage(pkg)
            if (go(i)) return true
        }
        return go(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun openApp(name: String): String {
        val (handle, label) = resolveApp(name) ?: return "I couldn't find an app called \"$name\"."
        return if (go(handle)) "Opening $label." else "I couldn't open $label."
    }

    /** Finds an installed app by name. Returns its launch intent and label, or null. */
    private fun resolveApp(name: String): Pair<Intent, String>? {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val q = name.lowercase().trim().removeSuffix(" app").trim()
        val hit = apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.firstOrNull { it.first.lowercase().contains(q) }
            ?: apps.firstOrNull { it.first.length > 2 && q.contains(it.first.lowercase()) }
            ?: return null
        val li = pm.getLaunchIntentForPackage(hit.second) ?: return null
        return li to hit.first
    }

    fun play(query: String): String {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, query)
        if (go(i)) return "Looking for $query in your music app."
        if (!prefs.internetEnabled) return "Internet is off. Turn it on to search for music online."
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        )
        return if (go(web)) "Searching YouTube for $query." else "I couldn't find an app to play that."
    }

    fun webSearch(query: String): String {
        if (!prefs.internetEnabled) return "Internet is off. Turn on the Internet toggle and ask me again."
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        return if (go(i)) "Searching the web for $query." else "I couldn't open a browser."
    }

    fun openUrl(url: String): String {
        if (!prefs.internetEnabled) return "Internet is off. Turn on the Internet toggle and ask me again."
        val fixed = if (url.startsWith("http")) url else "https://$url"
        return if (go(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))) "Opening $fixed." else "I couldn't open that link."
    }

    fun installApp(name: String): String {
        if (!prefs.internetEnabled) return "Internet is off. Turn on the Internet toggle to install apps."
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(name)))
        if (go(market)) return "Opening the Play Store for $name. Tap install there."
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(name))
        )
        return if (go(web)) "Opening the Play Store in your browser for $name." else "I couldn't open the app store."
    }

    fun accessibilityAction(action: String, text: String?): String {
        val svc = accessibility()
            ?: return "Screen control is off. Turn on Sadu in Settings > Accessibility first."
        return svc.performAction(action, text)
    }

    fun readScreen(): String {
        val svc = accessibility() ?: return "Screen control is off. Turn on Sadu in Settings > Accessibility first."
        return svc.readScreen()
    }

    // ------------------------------------------------------------ internals

    /**
     * "papa ko call kar", "mummy ko message kar ki main late hoon", "bhai ko whatsapp karo".
     * Returns null when the name is not a contact, so a sentence that merely mentions
     * someone is left to the model as conversation.
     */
    private fun hindiOrder(clean: String): String? {
        val t = clean.lowercase()
        val call = Regex(
            "^(.+?)\\s+ko\\s+(?:call|phone|dial|ring)\\s*(?:kar|karo|kardo|kar do|lagao)?\\s*[.!]?$"
        ).find(t)
        if (call != null) {
            val who = call.groupValues[1].trim()
            if (CommandParser.isRealTarget(who)) return call(who)
        }
        val msg = Regex(
            "^(.+?)\\s+ko\\s+(?:whatsapp|wa|message|msg|text|sms)\\s*(?:me|par|pe|per)?\\s*" +
                "(?:message|msg|text|sms)?\\s*(?:kar de|karde|kar do|kardo|karo|kar|bhej do|bhejdo|bhejo|bhej de|bhejde|bhej)" +
                "\\s*(?:ki|ke|jo|that)\\s+(.+)$"
        ).find(t)
        if (msg != null) {
            val who = msg.groupValues[1].trim()
            val body = msg.groupValues[2].trim().trim('.', '!')
            if (CommandParser.isRealTarget(who) && body.isNotBlank()) {
                return if (t.contains("whatsapp") || Regex("\\bwa\\b").containsMatchIn(t)) {
                    whatsapp(who, body).display
                } else {
                    sendSms(who, body)
                }
            }
        }
        return null
    }

    /** "whatsapp mummy that I am late", "message mummy on whatsapp saying ...". */
    /**
     * wa.me wants digits and a country code, no plus. A contact already saved as +91987...
     * must not get a second country code, which would point WhatsApp at the wrong person.
     */
    private fun internationalNumber(raw: String): String? {
        val kept = raw.filter { it.isDigit() || it == '+' }
        val digits = kept.filter { it.isDigit() }
        if (digits.length < 7) return null
        if (kept.startsWith("+")) return digits
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val cc = tm?.networkCountryIso?.uppercase().orEmpty().ifBlank { "IN" }
        return when {
            // Already carries the country code but no plus, e.g. 919876543210.
            digits.startsWith(cc) && digits.length >= cc.length + 9 -> digits
            digits.startsWith("0") -> cc + digits.drop(1)
            else -> cc + digits
        }
    }

    private fun whatsappOrder(cmd: String): Pair<String, String>? {
        val patterns = listOf(
            // "whatsapp papa that I am late", "whatsapp to papa saying ..."
            "^whatsapp\\s+(?:to\\s+)?(.+?)\\s+(?:that|saying|ki|ke|:)\\s+(.+)$",
            // "message papa on whatsapp that ..."
            "^(?:message|msg|text|send a message to|send message to)\\s+(.+?)\\s+" +
                "(?:on|via|in)\\s+whatsapp\\s*(?:that|saying|ki|ke|:)?\\s*(.+)$",
            // "send papa a whatsapp saying ..."
            "^(?:send|write)\\s+(.+?)\\s+a\\s+whatsapp\\s+" +
                "(?:message\\s+)?(?:that|saying|ki|ke|:)\\s*(.+)$",
            // "papa ko whatsapp par message bhejo ki ..." / "... bhej do ..."
            "^(.+?)\\s+ko\\s+(?:whatsapp|wa)\\s+(?:par|pe|per|me|se)?\\s*" +
                "(?:message|msg|text)?\\s*(?:bhej(?:o| do| de|do|de)?|kar(?:o| do| de|de)?|kardo)\\s*" +
                "(?:ki|ke|that|jo)?\\s*(.+)$",
        )
        for (rx in patterns) {
            val m = Regex(rx).find(cmd) ?: continue
            val who = m.groupValues[1].trim().trim('.', '!', ',')
            val body = m.groupValues[2].trim().trim('.', '!', ',')
            if (who.isNotEmpty() && body.isNotEmpty()) return who to body
        }
        return null
    }

    private fun normalize(raw: String): String = raw
        .replace('\u2019', '\'')
        .replace('\u2018', '\'')
        .replace('\u201c', '"')
        .replace('\u201d', '"')
        .trim()
        .trim('"', '\'', ' ')
        .trimEnd('.', '!', '?', ',', ' ')
        .replace(Regex("\\s+"), " ")

    private fun go(i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Finds a contact by the name the user said. "papa" also asks for "dad" / "father", so a
     * contact saved under any spelling of the same relation is still found; the exact
     * spelling always wins, so nothing is guessed when a real match exists.
     */
    /** A saved contact. [dataId] is the row WhatsApp needs to open the *saved* chat. */
    data class Contact(val name: String, val number: String, val dataId: Long? = null)

    /** Which action was waiting on a name the user had to confirm. */
    enum class What { Call, Message }

    private class Pick(
        val what: What,
        val body: String,
        val options: List<Contact>,
        val labels: List<String> = emptyList(),
    )

    /**
     * A name that matched nothing, held only until the very next message so "yes" or the
     * right name finishes the job. One turn only: a stale guess must never fire later.
     */
    private var pick: Pick? = null

    private fun canReadContacts(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** What a spoken name turned out to be. */
    private sealed interface Found {
        data class One(val contact: Contact) : Found
        data class Many(val options: List<Contact>) : Found
        data object None : Found
    }

    private fun wordsOf(s: String): List<String> =
        s.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

    /**
     * A name is matched in the order a person would: the name as saved, then every word in
     * it, then the relation ("papa" -> "dad"), then without "bhai" in front. Being stricter
     * than a plain LIKE is what stops "Brijesh" from answering for "Brijesh Vadhel", and any
     * name that still fits more than one saved contact is handed back for the user to pick.
     */
    private fun resolve(query: String): Found {
        val q = query.trim()
        if (Regex("^[+0-9 ()\\-]{5,}$").matches(q)) {
            return Found.One(Contact(q, q.filter { it.isDigit() || it == '+' }))
        }
        if (q.isEmpty() || !canReadContacts()) return Found.None
        val saved = allContacts()
        val want = wordsOf(q)
        saved.firstOrNull { wordsOf(it.name) == want }?.let { return Found.One(it) }
        val byWords = expand(saved.filter { c -> want.all { it in wordsOf(c.name) } })
        if (byWords != null) return byWords
        for (variant in Relations.variants(q)) {
            if (variant.equals(q, ignoreCase = true)) continue
            val v = wordsOf(variant)
            saved.firstOrNull { wordsOf(it.name) == v }?.let { return Found.One(it) }
            expand(saved.filter { c -> v.all { it in wordsOf(c.name) } })?.let { return it }
        }
        // "bhai shubham ko message karde" - the address word is not part of the name.
        val bare = q.replace(Regex("^(?:bhaiya|bhai|bro|yaar)\\s+", RegexOption.IGNORE_CASE), "").trim()
        if (bare.isNotEmpty() && !bare.equals(q, ignoreCase = true)) {
            val w = wordsOf(bare)
            saved.firstOrNull { wordsOf(it.name) == w }?.let { return Found.One(it) }
            expand(saved.filter { c -> w.all { it in wordsOf(c.name) } })?.let { return it }
        }
        return Found.None
    }

    private fun expand(hits: List<Contact>): Found? = when {
        hits.isEmpty() -> null
        hits.size == 1 -> Found.One(hits.first())
        else -> Found.Many(hits.take(5))
    }

    /** A number with only its last four digits visible, so the list never spills numbers. */
    private fun mask(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length <= 4) "+" + digits else "••••" + digits.takeLast(4)
    }

    private fun label(contact: Contact): String = contact.name + " · " + mask(contact.number)

    /**
     * More than one saved name fits. Nothing is guessed: the question is asked and the
     * chooser on screen offers the same names, each with a masked number so the right person
     * is clear without showing whole numbers.
     */
    private fun askWhich(
        options: List<Contact>,
        what: What,
        body: String,
        query: String,
        verb: String,
    ): String {
        val labels = options.map { label(it) }
        pick = Pick(what, body, options, labels)
        onChoices?.invoke(labels)
        val list = labels.mapIndexed { i, l -> "${i + 1}. $l" }.joinToString(", ")
        return "More than one contact matches \"$query\": $list. Which one should I $verb?"
    }

    /**
     * Rows for one name, preferring a mobile - a landline is the usual reason a WhatsApp
     * chat refuses to open.
     */
    private fun lookupContact(name: String): Contact? = try {
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone._ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE),
            "${Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null,
        )?.use { c ->
            var best: Contact? = null
            var bestRank = Int.MAX_VALUE
            while (c.moveToNext()) {
                val number = c.getString(2)?.trim().orEmpty()
                if (number.isBlank()) continue
                val type = if (c.isNull(3)) 0 else c.getInt(3)
                val rank = when (type) {
                    Phone.TYPE_MOBILE -> 0
                    else -> 1
                }
                if (rank < bestRank) {
                    bestRank = rank
                    best = Contact(c.getString(1) ?: name, number, c.getLong(0))
                }
            }
            best
        }
    } catch (_: Throwable) {
        null
    }

    /** Every saved contact, best number first. Used only to suggest, never to act on its own. */
    private fun allContacts(): List<Contact> = try {
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone._ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE),
            null,
            null,
            null,
        )?.use { c ->
            val byName = LinkedHashMap<String, Contact>()
            val rank = HashMap<String, Int>()
            while (c.moveToNext()) {
                val name = c.getString(1)?.trim().orEmpty()
                val number = c.getString(2)?.trim().orEmpty()
                if (name.isBlank() || number.isBlank()) continue
                val type = if (c.isNull(3)) 0 else c.getInt(3)
                val r = if (type == Phone.TYPE_MOBILE) 0 else 1
                val key = name.lowercase()
                if (rank[key] ?: Int.MAX_VALUE > r) {
                    rank[key] = r
                    byName[key] = Contact(name, number, c.getLong(0))
                }
            }
            byName.values.toList()
        } ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    /**
     * Nothing matched. Before saying no, offer the names that are close - a typed or spoken
     * name is often one letter off, and "did you mean Papa?" is a shorter conversation than
     * "I couldn't find that".
     */
    private fun contactMiss(who: String, what: What, body: String): String {
        if (!canReadContacts()) {
            onNeedPermission?.invoke(Manifest.permission.READ_CONTACTS)
            return "I need to look at your contacts to find $who. Allow contacts above and ask me again."
        }
        val saved = allContacts()
        if (saved.isEmpty()) {
            return "I can see the contacts permission but the phone gave me an empty list, " +
                "so I can't look $who up. Check that the number is saved in Contacts, then ask again."
        }
        val options = Suggest.closest(who, saved.map { it.name })
            .mapNotNull { name -> saved.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        if (options.isEmpty()) {
            return "I couldn't find \"$who\" in your contacts. Check the name, or tell me the number."
        }
        pick = Pick(what, body, options, options.map { label(it) })
        if (options.size > 1) onChoices?.invoke(options.map { label(it) })
        return Suggest.question(who, options.map { it.name })
            ?: "I couldn't find \"$who\" in your contacts. Check the name, or tell me the number."
    }

    /**
     * The follow-up to a suggestion. Returns null when the message is not about the
     * suggestion at all, so ordinary conversation carries on untouched.
     */
    private fun answerPick(clean: String): String? {
        val held = pick ?: return null
        val t = clean.lowercase().trim().trim('?', '!', '.')
        val names = held.options.joinToString(", ") { it.name }

        if (t in YES) {
            if (held.options.size == 1) {
                pick = null
                return runPick(held, held.options.first())
            }
            return "Which one - $names? Tell me the name or its number."
        }
        t.toIntOrNull()?.let { n ->
            if (n in 1..held.options.size) {
                pick = null
                return runPick(held, held.options[n - 1])
            }
        }
        held.options.firstOrNull { t.contains(it.name.lowercase()) }?.let {
            pick = null
            return runPick(held, it)
        }
        // Anything else: drop the guess rather than surprise the user with it later.
        pick = null
        return null
    }

    /** A name tapped in the chooser. Returns Sadu's answer, or null when nothing was waiting. */
    fun chooseName(name: String): String? {
        val held = pick ?: return null
        val asked = name.trim()
        val at = held.labels.indexOfFirst { it.equals(asked, ignoreCase = true) }
        val hit = if (at >= 0) held.options[at]
        else held.options.firstOrNull { it.name.equals(asked, ignoreCase = true) }
            ?: return null
        pick = null
        onChoices?.invoke(emptyList())
        return runPick(held, hit)
    }

    /** The chooser was closed without a choice: forget the guess rather than fire it later. */
    fun clearChoices() {
        pick = null
        onChoices?.invoke(emptyList())
    }

    private fun runPick(held: Pick, contact: Contact): String = when (held.what) {
        What.Call -> callContact(contact)
        What.Message -> message(contact, held.body).display
    }
}

/** "yes" in the shapes people actually type after a suggestion. */
private val YES = setOf("yes", "yeah", "yep", "haan", "han", "ha", "haa", "ok", "okay", "theek hai", "sahi")
private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

/** Said when a look-up is asked for while the Internet switch is off. */
private const val INTERNET_OFF =
    "Internet is off, so I can't search for that. Turn the switch on in Settings and ask me again."
