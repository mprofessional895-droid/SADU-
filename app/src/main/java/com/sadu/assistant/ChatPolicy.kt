package com.sadu.assistant

/**
 * The two safety nets that let Chat mode's prompt stay tiny.
 *
 * Chat mode cannot touch the phone - that is enforced in code, and a model cannot argue with
 * it. What a model *can* do is claim it did the thing anyway ("Done, I sent it"), which is
 * worse than refusing, because the user believes it. So instead of spending the prompt on
 * warnings, the app watches in two places:
 *
 *  - [phoneIntent], before the model is asked: a clear request to act on the phone is
 *    answered here, and the model is not called at all;
 *  - [claimsPhoneAction], after the model has answered: a reply that claims an action gets a
 *    one-line note under it.
 *
 * Both are pure text checks, so they are unit tested without a model or a phone.
 */
object ChatPolicy {

    /** Said when the user clearly asks Chat mode to do something on the phone. */
    const val NOT_HERE =
        "This is Chat mode, so I can't touch the phone. Switch to Control mode and I'll do it."

    /** Shown under a reply that claims a phone action it cannot have performed. */
    const val CLAIM_NOTE =
        "Chat mode can't use the phone - switch to Control mode if you want that done."

    /**
     * Question wording, English and Hinglish. A question about the phone is conversation, so it
     * must not be caught by the action patterns below - "WhatsApp kaise kaam karta hai?" is a
     * question, "WhatsApp kholo" is a command, and only the second one reaches the phone.
     */
    private val QUESTION = Regex(
        "\\b(how|what|why|when|where|which|who|does|do i|can i|should i|is it|are there|" +
            "explain|meaning|difference|tell me about|kaise|kya|kyu|kyun|kab|kahan|kidhar|" +
            "kaun|kitna|kitni|batao|bata do|samjhao|samjha)\\b" +
            "|\\?"
    )

    /**
     * The verbs that make a message a request. Kept separate from [QUESTION] so "bhej do ki kya
     * haal hai" stays a command even though it carries a question word.
     */
    private val COMMAND_START = Regex(
        "^(?:send|text|message|sms|dm|reply|forward|call|dial|ring|set|turn|switch|" +
            "toggle|open|launch|start|install|download|uninstall|take|read|play|remind|wake|" +
            "bhej|bhejo|bhej do|bhejde|khol|kholo|khol de|kar|kardo|kar de|lagao|bajao|chalao|" +
            "band|chalu|chalu kar)\\b"
    )

    private val MESSAGE = Regex(
        "^(?:send|text|message|sms|whatsapp|dm|reply|forward|" +
            "bhej do|bhejdo|bhej de|bhejde|bhejo|bhej|likh do|likho)\\b(?=\\s+\\S)"
    )

    private val CALL = Regex("^(?:call|dial|ring|phone)\\b(?=\\s+\\S)")

    private val SCHEDULE = Regex(
        "^(?:set (?:an? )?(?:alarm|timer|reminder|event|meeting)|wake me|remind me|" +
            "create (?:an? )?(?:alarm|reminder|event)|add (?:an? )?(?:event|reminder)|" +
            "start (?:a )?timer)\\b"
    )

    private val SWITCH = Regex(
        "^(?:turn|switch|toggle|put|keep|enable|disable|open|show|connect|disconnect|change)\\b" +
            "[^.]*\\b(flashlight|torch|wifi|wi-?fi|bluetooth|volume|silent|mute|airplane|" +
            "do not disturb|dnd|brightness|mobile data|hotspot)\\b"
    )

    /** "flashlight on karo" puts the device first, so the verb pattern above cannot see it. */
    private val DEVICE_THEN_VERB = Regex(
        "^(?:flashlight|torch|wifi|wi-?fi|bluetooth|volume|silent|airplane|brightness|data)\\b" +
            "[^.]*\\b(?:on|off|band|chalu|chalu kar|karo|kar do|kardo|kar de|up|down|mute|unmute|" +
            "badha|kam)\\b"
    )

    private val DEVICE_ONLY = Regex(
        "^(flashlight|torch|wifi|wi-?fi|bluetooth)(?: (on|off))?$"
    )

    private val APPS = Regex(
        "^(?:open|launch|start|install|download|uninstall|update|close|force stop)\\b[^.]*" +
            "\\b(app|play store|store|browser|chrome|settings|camera|gallery|url|website|link)\\b" +
            "|^(?:install|download|uninstall)\\s+\\S" +
            "|^(?:open|go to)\\s+(?:https?://|www\\.)\\S*"
    )

    private val SCREEN = Regex(
        "^(?:read|describe|look at|check|see|take a screenshot of|screenshot)\\b[^.]*\\bscreen\\b" +
            "|^screenshot\\b"
    )

    private val CAPTURE = Regex(
        "^(?:take|click|snap|record|shoot)\\b[^.]*\\b(photo|picture|selfie|video|screenshot|screen recording)\\b"
    )

    private val MAPS = Regex("^(?:navigate|take me|give me directions|directions|route)\\b[^.]*\\b(to|for)\\b")

    /**
     * True when the message is clearly a request to do something on the phone. Questions
     * about the phone ("how do I send a message?") are conversation and are left alone.
     */
    fun phoneIntent(text: String): Boolean {
        val clean = normalize(text)
        if (clean.isEmpty()) return false
        val command = COMMAND_START.containsMatchIn(clean)
        if (!command && QUESTION.containsMatchIn(clean)) return false
        return MESSAGE.containsMatchIn(clean) ||
            CALL.containsMatchIn(clean) ||
            SCHEDULE.containsMatchIn(clean) ||
            SWITCH.containsMatchIn(clean) ||
            DEVICE_ONLY.matches(clean) ||
            DEVICE_THEN_VERB.containsMatchIn(clean) ||
            APPS.containsMatchIn(clean) ||
            SCREEN.containsMatchIn(clean) ||
            CAPTURE.containsMatchIn(clean) ||
            MAPS.containsMatchIn(clean)
    }

    private val CLAIM = Regex(
        "\\b(?:i|i've|i have|i just|i already|i am|i'm)\\b[^.!?]{0,40}" +
            "\\b(?:sent|messaged|texted|called|dialled|dialed|rang|opened|launched|installed|" +
            "downloaded|set|created|added|started|booked|turned|switched|enabled|disabled|" +
            "changed|silenced|muted|screenshot|took a|read your)\\b"
    )

    private val PHONE_NOUN = Regex(
        "\\b(messages?|texts?|sms|whatsapp|mom|dad|mummy|papa|call(?:ed|s|ing)?|alarm|timer|reminder|" +
            "event|meeting|app|play store|flashlight|torch|wifi|wi-?fi|bluetooth|volume|silent|" +
            "airplane|do not disturb|dnd|brightness|mobile data|hotspot|settings|camera|photo|" +
            "picture|selfie|video|screenshot|notification)\\b"
    )

    /**
     * True when a reply claims a phone action. A claim needs both the wording and something
     * on the phone for it to be about, so "I sent you the details earlier" is not one.
     */
    fun claimsPhoneAction(reply: String): Boolean =
        reply.split('.', '!', '?', '\n').any { sentence ->
            val line = sentence.lowercase()
            line.length in 3..400 && CLAIM.containsMatchIn(line) && PHONE_NOUN.containsMatchIn(line)
        }

    private fun normalize(text: String): String =
        text.trim()
            .lowercase()
            .removePrefix("hey sadu")
            .removePrefix("sadu")
            .trim()
            .removePrefix("ok ")
            .removePrefix("okay ")
            .removePrefix("please ")
            .trim()
}
