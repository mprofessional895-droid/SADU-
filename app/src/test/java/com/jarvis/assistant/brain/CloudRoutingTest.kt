package com.jarvis.assistant.brain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cloud model used to be skipped in silence when internet access was off, so the
 * reply came from the phone with no explanation. Every way of being skipped now says so.
 */
class CloudRoutingTest {

    @Test
    fun nothingIsSaidWhenTheCloudWasNeverWanted() {
        assertNull(cloudSkipNote(false, internetAllowed = false, problem = null, usedCloud = false))
        assertNull(cloudSkipNote(false, internetAllowed = true, problem = "add a model name", usedCloud = false))
    }

    @Test
    fun nothingIsSaidWhenTheCloudAnswered() {
        assertNull(cloudSkipNote(true, internetAllowed = true, problem = null, usedCloud = true))
    }

    @Test
    fun blockedInternetIsNamedAsTheReason() {
        val why = cloudSkipNote(true, internetAllowed = false, problem = null, usedCloud = false)
        assertNotNull(why)
        assertTrue(why!!, why.contains("internet access is blocked"))
        assertTrue(why, why.contains("Settings"))
    }

    @Test
    fun anIncompleteSetupQuotesTheFieldItself() {
        val why = cloudSkipNote(
            true,
            internetAllowed = true,
            problem = "Add the model name, for example gpt-4o-mini",
            usedCloud = false,
        )
        assertNotNull(why)
        assertTrue(why!!, why.contains("Add the model name"))
    }

    @Test
    fun anyOtherSkipIsStillAdmitted() {
        val why = cloudSkipNote(true, internetAllowed = true, problem = null, usedCloud = false)
        assertNotNull(why)
        assertTrue(why!!, why.contains("not used"))
    }
}
