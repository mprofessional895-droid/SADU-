package com.jarvis.assistant

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
import com.jarvis.assistant.control.JarvisAccessibilityService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Everything Jarvis can DO on the phone. Two entry points:
 *  - [tryHandle]: fast, reliable rule matching for common commands (no AI needed).
 *  - the public helpers below: called by the AI tool layer when it decides to act.
 */
class Actions(
    private val ctx: Context,
    private val memory: Memory,
    private val prefs: Prefs,
    private val accessibility: () -> JarvisAccessibilityService?,
) {

    /** Returns a reply if the text matched a rule, or null if it is normal chat. */
    fun tryHandle(raw: String): String? = handle(raw, chatOnly = false)

    /**
     * The rules Chat mode may still run: Jarvis's own memory, the clock, and the network.
     * Everything that reaches into the phone returns null, so it stays a conversation.
     */
    fun tryHandleChatMode(raw: String): String? = handle(raw, chatOnly = true)

    private fun handle(raw: String, chatOnly: Boolean): String? {
        val clean = normalize(raw)
        if (clean.isEmpty()) return null
        val t = clean.lowercase()
        val ic = RegexOption.IGNORE_CASE

        // ---- memory ----
        Regex(
            "^(?:(?:hey |ok |okay )?jarvis[ ,]+)?(?:please )?" +
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

        // ---- questions Jarvis can always answer, command or not ----
        if (Regex("^(?:jarvis[ ,]+)?(?:what(?:'s| is)? the time|what time is it|current time|time)(?: now| please)?$").matches(t)) {
            return "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
        }
        if (Regex("^(?:jarvis[ ,]+)?(?:what(?:'s| is)? the date|what day is it|today'?s date|what(?:'s| is)? today)(?: today)?$").matches(t)) {
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

        // Everything past here actually touches the phone, so only run it when the
        // message reads as a command. Otherwise hand it to the brain as conversation.
        val cmd = CommandParser.commandText(clean) ?: return null

        // ---- the network: the one thing both modes may use ----
        Regex("^(?:install|download)\\s+(?:the\\s+)?(?:app\\s+)?(.+)$").find(cmd)?.let {
            return installApp(it.groupValues[1])
        }
        Regex("^(?:search|google|look up|search the web for|search for)\\s+(.+)$").find(cmd)?.let {
            return webSearch(it.groupValues[1])
        }
        Regex("^(?:open|go to)\\s+((?:https?://|www\\.)\\S+)$").find(cmd)?.let {
            return openUrl(it.groupValues[1])
        }

        // Chat mode stops here. Everything below can change something on the phone.
        if (chatOnly) return null

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

        // ---- call ----
        Regex("^(?:call|phone|dial|ring)\\s+(.+)$").find(cmd)?.let {
            val who = it.groupValues[1].trim()
            if (CommandParser.isRealTarget(who) && findContact(who) != null) return call(who)
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
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
        }
        val label = SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
        return if (go(i)) "Alarm set for $label." else "I couldn't open the clock app to set the alarm."
    }

    fun call(who: String): String {
        val hit = findContact(who) ?: return contactMiss(who)
        val direct = prefs.directCall &&
            ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (direct) {
            val i = Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(hit.first)))
            return if (go(i)) "Calling ${hit.second}." else "I couldn't start the call."
        }
        val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(hit.first)))
        return if (go(i)) {
            "Opening the dialer for ${hit.second}. Tap the call button to confirm."
        } else "I couldn't open the dialer."
    }

    fun sendSms(who: String, body: String): String {
        if (body.isBlank()) return "What should the message to $who say?"
        val hit = findContact(who.trim()) ?: return contactMiss(who.trim())
        val canSend = ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (canSend && !prefs.confirmSms) {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val sm = ctx.getSystemService(SmsManager::class.java)
                    sm.sendTextMessage(hit.first, null, body, null, null)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault().sendTextMessage(hit.first, null, body, null, null)
                }
                "Message sent to ${hit.second}."
            } catch (e: Exception) {
                "I couldn't send the message: ${e.message}"
            }
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(hit.first)))
            .putExtra("sms_body", body)
        return if (go(i)) {
            "I've written the message to ${hit.second}. Tap send in the Messages app to confirm."
        } else "I couldn't open the messages app."
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
            ?: return "Screen control is off. Turn on Jarvis in Settings > Accessibility first."
        return svc.performAction(action, text)
    }

    fun readScreen(): String {
        val svc = accessibility() ?: return "Screen control is off. Turn on Jarvis in Settings > Accessibility first."
        return svc.readScreen()
    }

    // ------------------------------------------------------------ internals

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

    private fun findContact(query: String): Pair<String, String>? {
        val q = query.trim()
        if (Regex("^[+0-9 ()\\-]{5,}$").matches(q)) {
            return q.filter { it.isDigit() || it == '+' } to q
        }
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            "${Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$q%"),
            null
        )?.use { c ->
            if (c.moveToFirst()) return c.getString(1) to c.getString(0)
        }
        return null
    }

    private fun contactMiss(who: String): String =
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            "I need permission to read your contacts. Please allow it in the app's permission settings."
        } else {
            "I couldn't find \"$who\" in your contacts."
        }
}
