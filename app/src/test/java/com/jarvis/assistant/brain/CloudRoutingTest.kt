package com.jarvis.assistant.brain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cloud model that is switched on but does not answer must say why, in words the user
 * can act on. The trap this guards: a saved API key with the model name still empty, and
 * every reply coming quietly from the phone.
 */
class CloudRoutingTest {

    @Test
    fun nothingIsSaidWhenTheCloudWasNeverWanted() {
        assertNull(cloudSkipNote(cloudEnabled = false, problem = null, usedCloud = false))
        assertNull(cloudSkipNote(false, problem = "Add the model name", usedCloud = false))
    }

    @Test
    fun nothingIsSaidWhenTheCloudAnswered() {
        assertNull(cloudSkipNote(true, problem = null, usedCloud = true))
        assertNull(cloudSkipNote(true, problem = null, usedCloud = true))
    }

    @Test
    fun anIncompleteSetupQuotesTheFieldItself() {
        val why = cloudSkipNote(
            cloudEnabled = true,
            problem = "Add the model name, for example gpt-4o-mini",
            usedCloud = false,
        )
        assertNotNull(why)
        assertTrue(why!!, why.contains("Add the model name"))
        assertTrue(why, why.contains("not ready"))
    }

    @Test
    fun anyOtherSkipIsStillAdmitted() {
        val why = cloudSkipNote(cloudEnabled = true, problem = null, usedCloud = false)
        assertNotNull(why)
        assertTrue(why!!, why.contains("came from the phone"))
    }

    @Test
    fun theInternetSwitchIsNotPartOfThisDecision() {
        // Anyone reading the note must not be sent to the wrong switch: the cloud model
        // has its own toggle, and "allow internet access" is only about the phone tools.
        val why = cloudSkipNote(cloudEnabled = true, problem = null, usedCloud = false)
        assertNotNull(why)
        assertTrue(why!!, !why.contains("internet"))
    }
}
