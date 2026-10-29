package com.sadu.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

/**
 * "call papa" all the way to the phone app: the rule, the contact lookup, the intent.
 *
 * The contact list is stubbed, so the lookup itself is exercised - not bypassed.
 */
@RunWith(AndroidJUnit4::class)
class CallChainTest {

    private lateinit var ctx: Context
    private lateinit var prefs: Prefs
    private lateinit var actions: Actions
    private var askedFor: String? = null

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = Prefs(ctx)
        prefs.mode = Mode.Control
        prefs.riskyControls = true
        prefs.directCall = false
        actions = Actions(ctx, Memory(ctx), prefs) { null }
        actions.onNeedPermission = { askedFor = it }
    }

    private fun contacts(vararg rows: Pair<String, String>) {
        val provider = FakeContacts(rows.toList())
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
    }

    /** A real provider behind the contacts authority, so the query in Actions actually runs. */
    private class FakeContacts(private val rows: List<Pair<String, String>>) : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            // Honour the LIKE in the app's query, so "ravi" cannot match "Papa".
            val needle = selectionArgs?.firstOrNull()
                ?.removePrefix("%")?.removeSuffix("%")?.lowercase()
            return MatrixCursor(cols).apply {
                rows.filter { needle == null || it.first.lowercase().contains(needle) }
                    .forEach { (name, number) -> addRow(arrayOf<Any>(name, number)) }
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private fun grantContacts() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    private fun startedIntent(): Intent? = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity

    @Test
    fun aSavedContactIsFoundAndTheDialerOpens() {
        grantContacts()
        contacts("Papa" to "+919876543210")

        val answer = actions.tryHandle("call papa")

        assertTrue(answer, answer!!.contains("Papa"))
        val intent = startedIntent()
        assertTrue("the phone app must be asked", intent != null)
        assertEquals(Intent.ACTION_DIAL, intent!!.action)
        assertEquals("tel:+919876543210", intent.data.toString())
    }

    @Test
    fun theHindiWordOrderReachesTheSamePlace() {
        grantContacts()
        contacts("Papa" to "+919876543210")

        val answer = actions.tryHandle("papa ko call kar")

        assertTrue(answer, answer != null && answer.contains("Papa"))
        assertEquals(Intent.ACTION_DIAL, startedIntent()?.action)
    }

    @Test
    fun dadFindsAContactSavedAsPapa() {
        grantContacts()
        contacts("Papa" to "+919876543210")

        val answer = actions.tryHandle("call dad")

        assertTrue(answer, answer != null && answer.contains("Papa"))
    }

    @Test
    fun aMissingContactSaysSoInsteadOfSlidingIntoChat() {
        grantContacts()
        contacts("Papa" to "+919876543210")

        val answer = actions.tryHandle("call ravi")

        assertTrue("a rule must answer, not return null", answer != null)
        assertTrue(answer!!, answer.contains("couldn't find"))
        assertTrue("nothing was opened", startedIntent() == null)
    }

    @Test
    fun withoutContactsPermissionItAsksForItAndSaysWhy() {
        contacts("Papa" to "+919876543210")

        val answer = actions.tryHandle("call papa")

        assertTrue("a rule must answer, not return null", answer != null)
        assertEquals(Manifest.permission.READ_CONTACTS, askedFor)
        assertTrue(answer!!, answer.contains("contacts"))
        assertTrue("nothing was opened", startedIntent() == null)
    }

    @Test
    fun aNumberDialledStraightNeedsNoContacts() {
        contacts()

        val answer = actions.tryHandle("call 9876543210")

        assertTrue(answer, answer != null && answer.contains("9876543210"))
        assertEquals(Intent.ACTION_DIAL, startedIntent()?.action)
    }

    @Test
    fun riskyControlsOffMeansNoDialerAtAll() {
        grantContacts()
        contacts("Papa" to "+919876543210")
        prefs.riskyControls = false

        val answer = actions.tryHandle("call papa")

        assertTrue(answer!!, answer.contains("held back"))
        assertTrue("nothing may open", startedIntent() == null)
    }

    @Test
    fun directCallUsesTheRealCallActionWhenAllowed() {
        grantContacts()
        contacts("Papa" to "+919876543210")
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(Manifest.permission.CALL_PHONE)
        prefs.directCall = true

        val answer = actions.tryHandle("call papa")

        assertTrue(answer!!, answer.contains("Calling"))
        assertEquals(Intent.ACTION_CALL, startedIntent()?.action)
    }

    @Test
    fun whatsappOpensTheChatWithTheTextFilledIn() {
        grantContacts()
        contacts("Mummy" to "+919876543211")

        val answer = actions.tryHandle("mummy ko whatsapp me message kar ki main late aa raha hoon")

        assertTrue(answer, answer != null)
        val intent = startedIntent()
        assertTrue("WhatsApp must be opened", intent != null)
        assertTrue(intent!!.data.toString().startsWith("https://wa.me/919876543211"))
        assertTrue(intent.data.toString().contains("late"))
        assertTrue("never claims a send", !answer!!.contains("Message sent"))
    }
}
