package com.jarvis.assistant

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Small, testable bits of wording. Kept out of the composables so they can be unit tested. */
object AssistantText {

    const val NAME_PREFIX = "The user's name is"

    fun greeting(hourOfDay: Int, name: String?): String {
        val part = when (hourOfDay) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Still up"
        }
        val who = name?.trim()?.takeIf { it.isNotEmpty() }
        return if (who == null) "$part." else "$part, $who."
    }

    fun stateLabel(state: AiState): String = when (state) {
        AiState.Idle -> "Ready"
        AiState.Listening -> "Listening"
        AiState.Processing -> "Thinking"
        AiState.Speaking -> "Speaking"
        AiState.Error -> "Something went wrong"
    }

    /** The name the user asked Jarvis to remember, if any. */
    fun nameFrom(facts: List<String>): String? =
        facts.firstOrNull { it.startsWith(NAME_PREFIX) }
            ?.removePrefix(NAME_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun clock(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

    /** "Today", "Yesterday" or "12 Mar" - used as a conversation subtitle. */
    fun dayLabel(millis: Long, now: Long): String {
        val a = Calendar.getInstance().apply { timeInMillis = millis }
        val b = Calendar.getInstance().apply { timeInMillis = now }
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "Today"
        b.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        if (yesterday) return "Yesterday"
        return SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
    }

    /** File sizes for the status screen. */
    fun bytes(size: Long): String = when {
        size <= 0L -> "none"
        size < 1024L -> "$size B"
        size < 1024L * 1024L -> "${size / 1024L} KB"
        size < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", size / 1048576.0)
        else -> String.format(Locale.US, "%.1f GB", size / 1073741824.0)
    }

    /** Gap between messages large enough to be worth printing a timestamp. */
    const val TIMESTAMP_GAP_MS = 5 * 60 * 1000L
}
