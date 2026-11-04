package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGuardTest {

    private val mb = 1024L * 1024L

    @Test
    fun need_isModelPlusReserve() {
        assertEquals(1212, MemoryGuard.needMb(700 * mb))
    }

    @Test
    fun fits_needsModelAndRoom() {
        assertTrue(MemoryGuard.fits(700 * mb, 1500))
        assertFalse(MemoryGuard.fits(700 * mb, 1000))
    }

    @Test
    fun unknownMemoryNeverRefuses() {
        assertTrue(MemoryGuard.fits(700 * mb, 0))
    }

    @Test
    fun cap_onlyUnderPressure() {
        assertEquals(1200, MemoryGuard.tokenCap(3000, 8000, 1200))
        assertEquals(384, MemoryGuard.tokenCap(400, 8000, 1200))
        assertEquals(1200, MemoryGuard.tokenCap(0, 0, 1200))
    }
}
