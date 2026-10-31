package com.sadu.assistant.control

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Gives Sadu the ability to see and touch the screen: back / home / recents,
 * tap by text, type, scroll and read what is displayed.
 * The user must enable it in Settings > Accessibility. It acts only when asked.
 */
class SaduAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun performAction(action: String, text: String?): String = when (action) {
        "back" -> global(GLOBAL_ACTION_BACK, "Went back.")
        "home" -> global(GLOBAL_ACTION_HOME, "Went home.")
        "recents" -> global(GLOBAL_ACTION_RECENTS, "Showing recent apps.")
        "notifications" -> global(GLOBAL_ACTION_NOTIFICATIONS, "Opening notifications.")
        "scroll_down" -> scroll(true)
        "scroll_up" -> scroll(false)
        "tap" -> tap(text)
        "tap_result" -> tapResult(text)
        "type" -> typeText(text)
        "read_screen" -> readScreen()
        else -> "I don't know how to do that on the screen."
    }

    private fun global(action: Int, okMessage: String): String =
        if (performGlobalAction(action)) okMessage else "I couldn't do that right now."

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 5000) {
            val n = queue.removeFirst()
            if (predicate(n)) return n
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    /** Like [tap], but never the text field itself: the name you searched for is also in it. */
    private fun tapResult(label: String?): String {
        if (label.isNullOrBlank()) return "What should I open?"
        val q = label.lowercase().trim()
        val node = findNode { n ->
            if (n.isEditable) return@findNode false
            val t = n.text?.toString()?.lowercase()
            val d = n.contentDescription?.toString()?.lowercase()
            (t != null && t.contains(q)) || (d != null && d.contains(q))
        } ?: return "I couldn't find \"$label\" in the list."
        val target = clickableAncestor(node) ?: node
        return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            "Opened \"$label\"."
        } else {
            "I found \"$label\" but couldn't open it."
        }
    }

    private fun tap(label: String?): String {
        if (label.isNullOrBlank()) return "What should I tap?"
        val q = label.lowercase().trim()
        val node = findNode { n ->
            val t = n.text?.toString()?.lowercase()
            val d = n.contentDescription?.toString()?.lowercase()
            (t != null && t.contains(q)) || (d != null && d.contains(q))
        } ?: return "I couldn't find \"$label\" on the screen."
        val target = clickableAncestor(node) ?: node
        return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            "Tapped \"$label\"."
        } else {
            "I found \"$label\" but couldn't tap it."
        }
    }

    private fun typeText(text: String?): String {
        if (text.isNullOrBlank()) return "What should I type?"
        val field = findNode { it.isEditable }
            ?: return "I couldn't find a text field on the screen."
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            "Typed it."
        } else {
            "I couldn't type into that field."
        }
    }

    private fun scroll(down: Boolean): String {
        val node = findNode { it.isScrollable } ?: return "There's nothing to scroll here."
        val action = if (down) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return if (node.performAction(action)) {
            if (down) "Scrolled down." else "Scrolled up."
        } else {
            "I couldn't scroll."
        }
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "I can't see the screen right now."
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var guard = 0
        while (queue.isNotEmpty() && sb.length < 1500 && guard++ < 5000) {
            val n = queue.removeFirst()
            val t = n.text?.toString()
            val d = n.contentDescription?.toString()
            when {
                !t.isNullOrBlank() -> sb.append(t.trim()).append(". ")
                !d.isNullOrBlank() -> sb.append(d.trim()).append(". ")
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return if (sb.isEmpty()) "The screen looks empty." else "On screen: " + sb.toString().take(1500)
    }

    companion object {
        @Volatile
        var instance: SaduAccessibilityService? = null
        val isConnected: Boolean get() = instance != null
    }
}
