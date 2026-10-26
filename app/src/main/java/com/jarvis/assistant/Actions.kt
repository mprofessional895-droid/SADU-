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
    fun tryHandle(raw: String): String? {
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

        // ---- accessibility / screen control ----
        screenCommand(t)?.let { return it }

        // ---- flashlight ----
        if (t.contains("flashlight") || t.contains("torch")) {
            val on = !(t.contains(" off") || t.contains("disable") || t.startsWith("off"))
            return setTorch(on)
        }

        // ---- battery ----
        if (Regex("b[ae]t+[a-z]{0,2}ry").containsMatchIn(t)) return battery()

        // ---- volume ----
        if (t.contains("volume") || t.contains("louder") || t.contains("quieter")) {
            val dir = when {
                t.contains("unmute") -> "unmute"
                t.contains("mute") -> "mute"
                t.contains("down") || t.contains("lower") || t.contains("decrease") ||
                    t.contains("reduce") || t.contains("quieter") -> "down"
                else -> "up"
            }
            return volume(dir)
        }

        // ---- wifi / bluetooth ----
        if (t.contains("wifi") || t.contains("wi-fi") || t.contains("wi fi")) {
            return if (go(Intent(Settings.ACTION_WIFI_SETTINGS))) {
                "Opening Wi-Fi settings. Android doesn't let apps flip the switch, so tap it yourself."
            } else "I couldn't open Wi-Fi settings."
        }
        if (t.contains("bluetooth")) {
            return if (go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) {
                "Opening Bluetooth settings. Android doesn't let apps flip the switch, so tap it yourself."
            } else "I couldn't open Bluetooth settings."
        }

        // ---- time / date ----
        if (t.contains("what time") || t == "time" || t.contains("current time")) {
            return "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
        }
        if (t.contains("the date") || t.contains("what day") || t.contains("today's date")) {
            return "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
        }

        // ---- alarm ----
        if (t.contains("alarm") || t.startsWith("wake me")) {
            Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?").find(t)?.let {
                return setAlarm(it.value)
            }
            return "What time should I set it for? For example: set an alarm for 6:30 am."
        }

        // ---- install app (internet) ----
        Regex("^(?:please )?(?:install|download)\\s+(?:the\\s+)?(?:app\\s+)?(.+)$", ic).find(clean)?.let {
            return installApp(it.groupValues[1])
        }

        // ---- web search / open url ----
        Regex("^(?:please )?(?:search|google|look up|search the web for|search for)\\s+(.+)$", ic)
            .find(clean)?.let { return webSearch(it.groupValues[1]) }
        Regex("^(?:please )?(?:open|go to)\\s+((?:https?://|www\\.).+)$", ic).find(clean)?.let {
            return openUrl(it.groupValues[1])
        }

        // ---- send a message ----
        val sms = Regex(
            "^(?:please )?(?:send|write)\\s+(?:a\\s+)?(?:message|text|sms)\\s+to\\s+(.+?)(?:\\s+(?:saying|that says|say|with message)\\s+(.+))?$",
            ic
        ).find(clean) ?: Regex(
            "^(?:please )?(?:text|message|sms)\\s+(.+?)(?:\\s+(?:saying|that says|say)\\s+(.+))?$",
            ic
        ).find(clean)
        if (sms != null) return sendSms(sms.groupValues[1], sms.groupValues[2])

        // ---- call ----
        Regex("^(?:please )?(?:call|phone|dial|ring)\\s+(.+)$", ic).find(clean)?.let {
            return call(it.groupValues[1])
        }

        // ---- open an app ----
        Regex("^(?:please )?(?:open|launch|start)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$", ic).find(clean)?.let {
            return openApp(it.groupValues[1])
        }

        // ---- play something ----
        Regex("^(?:please )?play\\s+(.+)$", ic).find(clean)?.let { return play(it.groupValues[1]) }

        return null
    }

    // ------------------------------------------------------------ screen control

    private fun screenCommand(t: String): String? {
        if (Regex("\\b(go back|press back|navigate back)\\b").containsMatchIn(t)) {
            return accessibilityAction("back", null)
        }
        if (Regex("\\b(go home|press home|home screen)\\b").containsMatchIn(t)) {
            return accessibilityAction("home", null)
        }
        if (Regex("\\b(recent apps|show recents|app switcher)\\b").containsMatchIn(t)) {
            return accessibilityAction("recents", null)
        }
        if (Regex("\\b(read (?:the )?screen|what'?s on (?:the )?screen|describe (?:the )?screen)\\b")
                .containsMatchIn(t)
        ) {
            return accessibilityAction("read_screen", null)
        }
        if (Regex("\\bscroll (?:down|up)\\b").containsMatchIn(t)) {
            return accessibilityAction(if (t.contains("down")) "scroll_down" else "scroll_up", null)
        }
        Regex("^\\s*(?:please )?tap (?:on )?(.+)$", RegexOption.IGNORE_CASE).find(t)?.let {
            return accessibilityAction("tap", it.groupValues[1].trim())
        }
        Regex("^\\s*(?:please )?type (?:the text |this )?(.+)$", RegexOption.IGNORE_CASE).find(t)?.let {
            return accessibilityAction("type", it.groupValues[1].trim())
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
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val q = name.lowercase().trim().removeSuffix(" app").trim()
        val hit = apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.firstOrNull { it.first.lowercase().contains(q) }
            ?: apps.firstOrNull { it.first.length > 2 && q.contains(it.first.lowercase()) }
            ?: return "I couldn't find an app called \"$name\"."
        val li = pm.getLaunchIntentForPackage(hit.second)
            ?: return "I found ${hit.first} but couldn't open it."
        return if (go(li)) "Opening ${hit.first}." else "I couldn't open ${hit.first}."
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
