package com.jarvis.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyClassifierTest {

    @Test
    fun plainAnswersAreAnswers() {
        assertEquals(ReplyKind.Answer, ReplyClassifier.of("I'm doing well, thanks for asking!"))
        assertEquals(ReplyKind.Answer, ReplyClassifier.of("Done - the flashlight is on."))
        assertEquals(ReplyKind.Answer, ReplyClassifier.of("Alarm set for 6:30 am."))
    }

    @Test
    fun routerFailuresAreFailures() {
        assertEquals(ReplyKind.Failure, ReplyClassifier.of("Sorry, something went wrong: null"))
        assertEquals(ReplyKind.Failure, ReplyClassifier.of("Model failed to load: no space"))
        assertEquals(ReplyKind.Failure, ReplyClassifier.of("Copy failed: permission denied"))
        assertEquals(ReplyKind.Failure, ReplyClassifier.of("I couldn't find that app on your phone."))
        assertEquals(ReplyKind.Failure, ReplyClassifier.of("I can't do that without screen control."))
    }

    @Test
    fun missingSetupIsANotice() {
        assertEquals(
            ReplyKind.Notice,
            ReplyClassifier.of("My AI model isn't loaded yet. Tap \"Choose model file\" in Settings."),
        )
        assertEquals(ReplyKind.Notice, ReplyClassifier.of("Screen control is off. Turn it on in Settings."))
        assertEquals(ReplyKind.Notice, ReplyClassifier.of("I need microphone permission to listen."))
    }

    @Test
    fun surroundingSpaceDoesNotMatter() {
        assertEquals(ReplyKind.Failure, ReplyClassifier.of("   Sorry, I hit a problem.  "))
    }

    @Test
    fun emptyTextIsAnAnswer() {
        assertEquals(ReplyKind.Answer, ReplyClassifier.of(""))
    }
}
