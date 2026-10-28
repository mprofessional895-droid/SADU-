package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class AssistantTextTest {

    @Test
    fun greetingFollowsTheClock() {
        assertEquals("Good morning.", AssistantText.greeting(7, null))
        assertEquals("Good afternoon.", AssistantText.greeting(14, null))
        assertEquals("Good evening.", AssistantText.greeting(19, null))
        assertEquals("Still up.", AssistantText.greeting(2, null))
    }

    @Test
    fun greetingUsesTheRememberedName() {
        assertEquals("Good morning, Ada.", AssistantText.greeting(8, "Ada"))
        assertEquals("Good evening.", AssistantText.greeting(20, "   "))
        assertEquals("Good evening.", AssistantText.greeting(20, null))
    }

    @Test
    fun everyStateHasAWord() {
        AiState.values().forEach { state ->
            assertEquals(true, AssistantText.stateLabel(state).isNotBlank())
        }
        assertEquals("Ready", AssistantText.stateLabel(AiState.Idle))
        assertEquals("Listening", AssistantText.stateLabel(AiState.Listening))
        assertEquals("Thinking", AssistantText.stateLabel(AiState.Processing))
        assertEquals("Speaking", AssistantText.stateLabel(AiState.Speaking))
        assertEquals("Something went wrong", AssistantText.stateLabel(AiState.Error))
    }

    @Test
    fun nameComesFromTheStoredFact() {
        val facts = listOf("Likes black coffee", "The user's name is Ada")
        assertEquals("Ada", AssistantText.nameFrom(facts))
        assertNull(AssistantText.nameFrom(listOf("Likes black coffee")))
        assertNull(AssistantText.nameFrom(emptyList()))
    }

    @Test
    fun clockIsTwentyFourHour() {
        val midday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 5)
        }.timeInMillis
        assertEquals("13:05", AssistantText.clock(midday))
    }

    @Test
    fun todayAndYesterdayAreNamed() {
        val now = System.currentTimeMillis()
        assertEquals("Today", AssistantText.dayLabel(now, now))
        assertEquals("Yesterday", AssistantText.dayLabel(now - 24 * 60 * 60 * 1000L, now))
    }

    @Test
    fun modesAreNamedForPeople() {
        assertEquals("Chat", AssistantText.modeLabel(Mode.Chat))
        assertEquals("Control", AssistantText.modeLabel(Mode.Control))
        assertTrue(AssistantText.modeLine(Mode.Chat).contains("will not touch your phone"))
        assertTrue(AssistantText.modeLine(Mode.Control).contains("messages, calls"))
    }

    @Test
    fun sizesReadLikeASettingsScreen() {
        assertEquals("none", AssistantText.bytes(0))
        assertEquals("512 B", AssistantText.bytes(512))
        assertEquals("3 KB", AssistantText.bytes(3 * 1024))
        assertEquals("1.5 MB", AssistantText.bytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", AssistantText.bytes(2L * 1024 * 1024 * 1024))
    }
}
