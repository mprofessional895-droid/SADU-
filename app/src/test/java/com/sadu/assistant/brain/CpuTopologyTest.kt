package com.sadu.assistant.brain

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuTopologyTest {

    private fun freqs(vararg values: Long?): (Int) -> Long? = { i -> values.getOrNull(i) }

    @Test
    fun fourBigAndFourLittleGivesFour() {
        // Snapdragon 7 Gen 3 shaped: 1 x 2.6, 3 x 2.4, 4 x 1.9 GHz.
        val f = freqs(2600000, 2400000, 2400000, 2400000, 1900000, 1900000, 1900000, 1900000)
        assertEquals(4, CpuTopology.threads(8, f))
    }

    @Test
    fun twoBigCoresAreNotInflatedToFour() {
        val f = freqs(2800000, 2800000, 1800000, 1800000, 1800000, 1800000)
        assertEquals(2, CpuTopology.threads(6, f))
    }

    @Test
    fun aUniformReportIsCappedAtFour() {
        val f = freqs(2000000, 2000000, 2000000, 2000000, 2000000, 2000000, 2000000, 2000000)
        assertEquals(4, CpuTopology.threads(8, f))
    }

    @Test
    fun anUnreadableKernelGivesThePlainFour() {
        val f: (Int) -> Long? = { null }
        assertEquals(4, CpuTopology.threads(8, f))
    }

    @Test
    fun aSmallPhoneNeverGoesBelowTwo() {
        assertEquals(2, CpuTopology.threads(1, freqs(1500000)))
        assertEquals(2, CpuTopology.threads(2, freqs(1500000, 1500000)))
    }

    @Test
    fun noCoresAtAllIsStillTwo() {
        assertEquals(2, CpuTopology.threads(0, freqs()))
    }

    @Test
    fun aPartiallyReadableReportStillWorks() {
        // Some kernels hide the small cores' files; the readable ones still say four big.
        val f = freqs(2600000, 2400000, 2400000, 2400000, null, null, null, null)
        assertEquals(4, CpuTopology.threads(8, f))
    }
}
