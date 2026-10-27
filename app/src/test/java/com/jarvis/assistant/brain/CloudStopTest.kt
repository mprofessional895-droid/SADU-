package com.jarvis.assistant.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every provider words "I stopped because I ran out of room" differently, and the app has
 * to tell that apart from "I finished my sentence" - otherwise a cut-off answer looks
 * complete.
 */
class CloudStopTest {

    @Test
    fun anOpenAiCompatibleReplyThatHitTheLimitIsRecognised() {
        assertTrue(
            CloudReply.hitTokenLimit(
                CloudProvider.Compatible,
                """{"choices":[{"delta":{},"finish_reason":"length"}]}""",
            ),
        )
    }

    @Test
    fun aFinishedOpenAiReplyIsNotFlagged() {
        assertFalse(
            CloudReply.hitTokenLimit(
                CloudProvider.Compatible,
                """{"choices":[{"message":{"content":"hi"},"finish_reason":"stop"}]}""",
            ),
        )
    }

    @Test
    fun anthropicSaysItInItsOwnWords() {
        assertTrue(
            CloudReply.hitTokenLimit(
                CloudProvider.Anthropic,
                """{"type":"message_delta","delta":{"stop_reason":"max_tokens"}}""",
            ),
        )
        assertFalse(
            CloudReply.hitTokenLimit(
                CloudProvider.Anthropic,
                """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""",
            ),
        )
    }

    @Test
    fun geminiSaysItInCapitals() {
        assertTrue(
            CloudReply.hitTokenLimit(
                CloudProvider.Gemini,
                """{"candidates":[{"content":{"parts":[{"text":"a"}]},"finishReason":"MAX_TOKENS"}]}""",
            ),
        )
        assertFalse(
            CloudReply.hitTokenLimit(
                CloudProvider.Gemini,
                """{"candidates":[{"content":{"parts":[{"text":"a"}]},"finishReason":"STOP"}]}""",
            ),
        )
    }

    @Test
    fun ordinaryChunksAndJunkAreNeverStopReasons() {
        assertFalse(
            CloudReply.hitTokenLimit(
                CloudProvider.Compatible,
                """{"choices":[{"delta":{"content":"hi"}}]}""",
            ),
        )
        assertFalse(CloudReply.hitTokenLimit(CloudProvider.Compatible, "not json at all"))
        assertFalse(
            CloudReply.hitTokenLimit(
                CloudProvider.Gemini,
                """{"candidates":[{"content":{"parts":[{"text":"MAX_TOKENS appears here as a word"}]}}]}""",
            ),
        )
    }
}
