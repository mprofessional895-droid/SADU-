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

    private fun contacts(vararg rows: Triple<String, String, Int>) {
        val provider = FakeContacts(rows.map { Triple(it.first, it.second, it.third) })
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY, provider)
    }

    private fun mobile(name: String, number: String) =
        Triple(name, number, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)

    /** A real provider behind the contacts authority, so the query in Actions actually runs. */
    private class FakeContacts(private val rows: List<Triple<String, String, Int>>) : ContentProvider() {
        override fun onCreate(): Boolean = true
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
            )
            // Honour the LIKE in the app's query, so "ravi" cannot match "Papa".
            val needle = selectionArgs?.firstOrNull()
                ?.removePrefix("%")?.removeSuffix("%")?.lowercase()
            return MatrixCursor(cols).apply {
                rows.withIndex()
                    .filter { needle == null || it.value.first.lowercase().contains(needle) }
                    .forEach { (i, row) ->
                        addRow(arrayOf<Any>(100L + i, row.first, row.second, row.third))
                    }
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
        contacts(mobile("Papa", "+919876543210"))

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
        contacts(mobile("Papa", "+919876543210"))

        val answer = actions.tryHandle("papa ko call kar")

        assertTrue(answer, answer != null && answer.contains("Papa"))
        assertEquals(Intent.ACTION_DIAL, startedIntent()?.action)
    }

    @Test
    fun dadFindsAContactSavedAsPapa() {
        grantContacts()
        contacts(mobile("Papa", "+919876543210"))

        val answer = actions.tryHandle("call dad")

        assertTrue(answer, answer != null && answer.contains("Papa"))
    }

    @Test
    fun aMissingContactSaysSoInsteadOfSlidingIntoChat() {
        grantContacts()
        contacts(mobile("Papa", "+919876543210"))

        val answer = actions.tryHandle("call ravi")

        assertTrue("a rule must answer, not return null", answer != null)
        assertTrue(answer!!, answer.contains("couldn't find"))
        assertTrue("nothing was opened", startedIntent() == null)
    }

    @Test
    fun withoutContactsPermissionItAsksForItAndSaysWhy() {
        contacts(mobile("Papa", "+919876543210"))

        val answer = actions.tryHandle("call papa")

        assertTrue("a rule must answer, not return null", answer != null)
        assertEquals(Manifest.permission.READ_CONTACTS, askedFor)
        assertTrue(answer!!, answer.contains("contacts"))
        assertTrue("nothing was opened", startedIntent() == null)
    }

    @Test
    fun aNumberDialledStraightNeedsNoContacts() {
        contacts(mobile("Nobody", "1"))

        val answer = actions.tryHandle("call 9876543210")

        assertTrue(answer, answer != null && answer.contains("9876543210"))
        assertEquals(Intent.ACTION_DIAL, startedIntent()?.action)
    }

    @Test
    fun riskyControlsOffMeansNoDialerAtAll() {
        grantContacts()
        contacts(mobile("Papa", "+919876543210"))
        prefs.riskyControls = false

        val answer = actions.tryHandle("call papa")

        assertTrue(answer!!, answer.contains("held back"))
        assertTrue("nothing may open", startedIntent() == null)
    }

    @Test
    fun directCallUsesTheRealCallActionWhenAllowed() {
        grantContacts()
        contacts(mobile("Papa", "+919876543210"))
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(Manifest.permission.CALL_PHONE)
        prefs.directCall = true

        val answer = actions.tryHandle("call papa")

        assertTrue(answer!!, answer.contains("Calling"))
        assertEquals(Intent.ACTION_CALL, startedIntent()?.action)
    }

    @Test
    fun aLandlineIsNotChosenOverTheMobile() {
        grantContacts()
        contacts(
            Triple("Chacha", "+911123456789", ContactsContract.CommonDataKinds.Phone.TYPE_HOME),
            Triple("Chacha", "+919876500000", ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE),
        )

        val answer = actions.tryHandle("call chacha")

        assertTrue(answer, answer != null)
        assertEquals("tel:+919876500000", startedIntent()?.data.toString())
    }

    @Test
    fun aTypedNameOneLetterOffGetsASuggestionInsteadOfANo() {
        grantContacts()
        contacts(mobile("Papa", "+919876543210"))

        val answer = actions.tryHandle("call popa")

        assertTrue(answer, answer != null)
        assertTrue("must offer the near name", answer!!.contains("Papa"))
        assertTrue("must ask, not act", answer.contains("Did you mean"))
        assertTrue("nothing may open before the answer", startedIntent() == null)
    }

    @Test
    fun yesOnASuggestionFinishesTheCall() {
        grantContacts()
        contacts(mobile("Papa", "+919876543210"))

        actions.tryHandle("call popa")
        val answer = actions.tryHandle("haan")

        assertTrue(answer, answer != null)
        assertEquals("the call the user meant", "tel:+919876543210", startedIntent()?.data.toString())
    }

    @Test
    fun aNumberAnswerPicksFromTheSuggestion() {
        grantContacts()
        // Neither name contains "sandeep", so both have to come from the fuzzy pass.
        contacts(
            mobile("Sundeep", "+919876543210"),
            mobile("Sandip", "+919876543299"),
        )

        val asked = actions.tryHandle("call sandeep")
        assertTrue(asked!!, asked.contains("Sundeep"))
        assertTrue("both are offered", asked.contains("Sandip"))

        val answer = actions.tryHandle("2")
        assertTrue(answer, answer != null)
        assertEquals("the second name", "tel:+919876543299", startedIntent()?.data.toString())
    }

    @Test
    fun aSuggestionIsForgottenWhenTheNextMessageIsUnrelated() {
        grantContacts()
        contacts(mobile("Papa", "+919876543210"))

        actions.tryHandle("call popa")
        val answer = actions.tryHandle("tell me a joke")

        assertTrue("the guess must not answer it", answer == null || !answer.contains("Papa"))
        assertTrue("no call may fire", startedIntent() == null)
        // and the guess is gone: a later "yes" must not call anyone
        actions.tryHandle("yes")
        assertTrue("the stale guess is dropped", startedIntent() == null)
    }

    @Test
    fun aSuggestionForAMessageSendsNothingUntilConfirmed() {
        grantContacts()
        contacts(mobile("Mummy", "+919876543211"))

        val asked = actions.tryHandle("mummy ko whatsapp me messge kar ki late hoon")
        assertTrue("a suggestion or a refusal, never a send", asked == null || !asked.contains("Tap Send"))
        assertTrue("nothing opened", startedIntent() == null)
    }

    @Test
    fun whatsappOpensTheChatWithTheTextFilledIn() {
        grantContacts()
        contacts(mobile("Mummy", "+919876543211"))

        val answer = actions.tryHandle("mummy ko whatsapp me message kar ki main late aa raha hoon")

        assertTrue(answer, answer != null)
        val intent = startedIntent()
        assertTrue("WhatsApp must be opened", intent != null)
        // The contact's own row, not a number WhatsApp has to look up on its servers. That
        // lookup is what failed for a number the account does not use.
        assertEquals("content://com.android.contacts/data/100", intent!!.data.toString())
        assertEquals("com.whatsapp", intent.`package`)
        assertTrue("never claims a send", !answer!!.contains("Message sent"))
    }
}
