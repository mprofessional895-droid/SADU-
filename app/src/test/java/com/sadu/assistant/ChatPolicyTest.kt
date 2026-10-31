package com.sadu.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chat mode's prompt is deliberately tiny, so the app - not the model - is what knows Chat
 * mode cannot touch the phone. These are the two checks that make that safe: a clear request
 * is answered before the model is asked, and a reply that claims the deed gets a note.
 */
class ChatPolicyTest {

    private val phoneRequests = listOf(
        "text mom I'm on my way",
        "send a message to Rahul saying I'll be late",
        "message dad",
        "call mom",
        "dial 112",
        "set an alarm for 7 am",
        "wake me at 6",
        "remind me to take my medicine",
        "start a timer for 10 minutes",
        "turn on the flashlight",
        "turn off wifi",
        "open bluetooth settings",
        "check my screen",
        "read my screen",
        "take a screenshot",
        "take a photo",
        "install whatsapp",
        "open the camera app",
        "navigate to Connaught Place",
    )

    @Test
    fun clearPhoneRequestsAreCaught() {
        for (line in phoneRequests) {
            assertTrue("not caught: $line", ChatPolicy.phoneIntent(line))
        }
    }

    private val conversation = listOf(
        "how do I send a message on Android?",
        "what is bluetooth",
        "why is my alarm not working",
        "can I call someone without a network?",
        "tell me about alarms",
        "I called mom yesterday and we talked for an hour",
        "what should I say when I text her?",
        "explain how wifi works",
        "I am feeling tired today",
        "who are you?",
        "tell me a joke",
        "she sent me a gift last year",
        "what app should I use to learn Spanish?",
    )

    @Test
    fun talkingAboutThePhoneStaysConversation() {
        for (line in conversation) {
            assertFalse("should have stayed chat: $line", ChatPolicy.phoneIntent(line))
        }
    }

    @Test
    fun emptyInputIsNotAPhoneRequest() {
        assertFalse(ChatPolicy.phoneIntent(""))
        assertFalse(ChatPolicy.phoneIntent("   "))
    }

    private val claims = listOf(
        "Done! I've sent the message to mom.",
        "I have set an alarm for 7 am.",
        "I just called him for you.",
        "I opened the camera app and took a photo.",
        "I turned on the flashlight.",
    )

    @Test
    fun aClaimedPhoneActionIsNoticed() {
        for (line in claims) {
            assertTrue("claim missed: $line", ChatPolicy.claimsPhoneAction(line))
        }
    }

    @Test
    fun aClaimNeedsSomethingOnThePhoneToBeAbout() {
        assertFalse(ChatPolicy.claimsPhoneAction("I sent you the details earlier."))
        assertFalse(ChatPolicy.claimsPhoneAction("I have a message for you."))
        assertFalse(ChatPolicy.claimsPhoneAction("I opened a book last night."))
        assertFalse(ChatPolicy.claimsPhoneAction("Sounds good to me."))
    }

    @Test
    fun theNotesAreShortAndSayWhatToDo() {
        assertTrue(ChatPolicy.NOT_HERE.contains("Control mode"))
        assertTrue(ChatPolicy.CLAIM_NOTE.contains("Control mode"))
    }
}
