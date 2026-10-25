package com.jarvis.assistant

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Things Jarvis can DO on the phone. Commands are matched with simple rules
 * (fast and reliable). Anything that is not a command goes to the AI to chat.
 * Risky actions (call, SMS) only open the phone app; you tap the final button.
 */
class Actions(private val ctx: Context, private val memory: Memory) {

    /** Returns the reply if the text was a command, or null if it is normal chat. */
    fun tryHandle(raw: String): String? {
        val clean = raw.trim().trimEnd('.', '!', '?', ' ')
        if (clean.isEmpty()) return null
        val t = clean.lowercase()
        val ic = RegexOption.IGNORE_CASE

        // ---- memory ----
        Regex("^(?:please )?remember (?:that )?(.+)$", ic).find(clean)?.let {
            memory.add(it.groupValues[1])
            return "Got it, I'll remember that."
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

        // ---- flashlight ----
        if (t.contains("flashlight") || t.contains("torch")) {
            val on = !(t.contains(" off") || t.contains("disable") || t.startsWith("off"))
            return setTorch(on)
        }

        // ---- time and date ----
        if (t.contains("what time") || t == "time" || t.contains("current time")) {
            return "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
        }
        if (t.contains("the date") || t.contains("what day") || t.contains("today's date")) {
            return "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
        }

        // ---- alarm ----
        if (t.contains("alarm") || t.startsWith("wake me")) {
            return setAlarm(t)
        }

        // ---- send a message ----
        val sms = Regex(
            "^(?:please )?(?:send|write)\\s+(?:a\\s+)?(?:message|text|sms)\\s+to\\s+(.+?)(?:\\s+(?:saying|that says|say|with message)\\s+(.+))?$",
            ic
        ).find(clean) ?: Regex(
            "^(?:please )?(?:text|message|sms)\\s+(.+?)(?:\\s+(?:saying|that says|say)\\s+(.+))?$",
            ic
        ).find(clean)
        if (sms != null) {
            return sendSms(sms.groupValues[1], sms.groupValues[2])
        }

        // ---- call ----
        Regex("^(?:please )?(?:call|phone|dial|ring)\\s+(.+)$", ic).find(clean)?.let {
            return call(it.groupValues[1])
        }

        // ---- open an app ----
        Regex("^(?:please )?(?:open|launch|start)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$", ic).find(clean)?.let {
            return openApp(it.groupValues[1])
        }

        // ---- play something ----
        Regex("^(?:please )?play\\s+(.+)$", ic).find(clean)?.let {
            return play(it.groupValues[1])
        }

        return null
    }

    // ------------------------------------------------------------------

    private fun go(i: Intent): Boolean = try {
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    private fun setTorch(on: Boolean): String = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) {
            "This phone doesn't seem to have a flashlight."
        } else {
            cm.setTorchMode(id, on)
            if (on) "Flashlight on." else "Flashlight off."
        }
    } catch (e: Exception) {
        "I couldn't control the flashlight: ${e.message}"
    }

    private fun setAlarm(t: String): String {
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

    /** Returns (number, display name) or null. */
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
            if (c.moveToFirst()) {
                return c.getString(1) to c.getString(0)
            }
        }
        return null
    }

    private fun contactMiss(who: String): String =
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            "I need permission to read your contacts. Please allow it in the app's permission settings."
        } else {
            "I couldn't find \"$who\" in your contacts."
        }

    private fun call(who: String): String {
        val hit = findContact(who) ?: return contactMiss(who)
        val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(hit.first)))
        return if (go(i)) {
            "Opening the dialer for ${hit.second}. Tap the call button to confirm."
        } else {
            "I couldn't open the dialer."
        }
    }

    private fun sendSms(who: String, body: String): String {
        val hit = findContact(who.trim()) ?: return contactMiss(who.trim())
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(hit.first)))
            .putExtra("sms_body", body)
        return if (go(i)) {
            "I've written the message to ${hit.second}. Tap send in the Messages app to confirm."
        } else {
            "I couldn't open the messages app."
        }
    }

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val q = name.lowercase().trim()
        val hit = apps.firstOrNull { it.first.lowercase() == q }
            ?: apps.firstOrNull { it.first.lowercase().contains(q) }
            ?: apps.firstOrNull { it.first.length > 2 && q.contains(it.first.lowercase()) }
            ?: return "I couldn't find an app called \"$name\"."
        val li = pm.getLaunchIntentForPackage(hit.second)
            ?: return "I found ${hit.first} but couldn't open it."
        return if (go(li)) "Opening ${hit.first}." else "I couldn't open ${hit.first}."
    }

    private fun play(query: String): String {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, query)
        if (go(i)) return "Looking for $query in your music app."
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        )
        return if (go(web)) "Searching YouTube for $query." else "I couldn't find an app to play that."
    }
}
