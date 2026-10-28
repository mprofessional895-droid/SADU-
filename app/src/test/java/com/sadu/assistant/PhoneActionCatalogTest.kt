package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionCatalogTest {

    private val action = { id: String -> PhoneActionCatalog.all.first { it.id == id } }

    @Test
    fun blockersExplainEveryReason() {
        val screen = action("screen")
        assertEquals(
            "Screen control is off",
            PhoneActionCatalog.blocker(screen, granted = emptySet(), accessibilityOn = false, sdk = 34),
        )
        assertNull(
            PhoneActionCatalog.blocker(screen, granted = emptySet(), accessibilityOn = true, sdk = 34),
        )

        val sms = action("sms")
        assertEquals(
            "Permission needed",
            PhoneActionCatalog.blocker(sms, granted = emptySet(), accessibilityOn = true, sdk = 34),
        )
        assertNull(
            PhoneActionCatalog.blocker(
                sms,
                granted = setOf(PhoneActionCatalog.PERM_SMS),
                accessibilityOn = true,
                sdk = 34,
            ),
        )
    }

    @Test
    fun tooOldAndroidIsReportedAsUnavailable() {
        val newest = PhoneActionCatalog.all.maxBy { it.minSdk }
        assertEquals(
            "Not available on this Android version",
            PhoneActionCatalog.blocker(newest, granted = emptySet(), accessibilityOn = true, sdk = 1),
        )
        assertFalse(PhoneActionCatalog.forSdk(1).any { it.id == "notifications" })
        assertTrue(PhoneActionCatalog.forSdk(1).size < PhoneActionCatalog.all.size)
    }

    @Test
    fun theTorchDoesNotAskForTheCameraPermission() {
        // setTorchMode needs no permission; asking for CAMERA would block the flashlight.
        val torch = action("flashlight")
        assertNull(torch.permission)
        assertNull(PhoneActionCatalog.blocker(torch, emptySet(), accessibilityOn = false, sdk = 34))
    }

    @Test
    fun permissionsAskedForAreRuntimeOnes() {
        val perms = PhoneActionCatalog.runtimePermissions(34)
        assertTrue(perms.contains(PhoneActionCatalog.PERM_MIC))
        assertTrue(perms.contains(PhoneActionCatalog.PERM_SMS))
        assertTrue(perms.contains(PhoneActionCatalog.PERM_NOTIFICATIONS))
        assertEquals(perms.size, perms.distinct().size)
        assertFalse(perms.contains("android.permission.CAMERA"))
    }

    @Test
    fun notificationsAreNotRequestedBelowAndroid13() {
        assertFalse(PhoneActionCatalog.runtimePermissions(32).contains(PhoneActionCatalog.PERM_NOTIFICATIONS))
        assertTrue(PhoneActionCatalog.runtimePermissions(33).contains(PhoneActionCatalog.PERM_NOTIFICATIONS))
    }

    @Test
    fun everyActionIsDescribed() {
        PhoneActionCatalog.all.forEach { a ->
            assertTrue("id", a.id.isNotBlank())
            assertTrue("title", a.title.isNotBlank())
            assertTrue("detail", a.detail.isNotBlank())
            if (a.permission != null) {
                assertEquals(
                    "Permission needed",
                    PhoneActionCatalog.blocker(a, emptySet(), false, 34),
                )
            }
            if (a.accessibility) {
                assertEquals(
                    "Screen control is off",
                    PhoneActionCatalog.blocker(a, emptySet(), false, 34),
                )
            }
        }
        assertEquals(
            PhoneActionCatalog.all.size,
            PhoneActionCatalog.all.map { it.id }.distinct().size,
        )
    }

    @Test
    fun groupsKeepTheCatalogOrder() {
        val groups = PhoneActionCatalog.groups()
        assertEquals(listOf("Voice", "Messages and calls", "Device control", "System"), groups)
    }
}
