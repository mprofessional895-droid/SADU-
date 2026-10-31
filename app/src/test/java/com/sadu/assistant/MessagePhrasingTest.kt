package com.sadu.assistant

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadu.assistant.brain.Brain
import com.sadu.assistant.brain.BrainRouter
import com.sadu.assistant.brain.CloudConfig
import com.sadu.assistant.brain.LlmRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowContentResolver

/**
 * The sentences that used to fall through the rules and reach the model - and the message
 * shown when no model can answer.
 */
@RunWith(AndroidJUnit4::class)
class MessagePhrasingTest {

    private lateinit var ctx: Context
    private lateinit var prefs: Prefs
    private lateinit var actions: Actions

    private class Fake(
        private val label: String,
        private val replies: List<String>,
        private val ready: Boolean,
    ) : Brain {
        override val isReady: Boolean get() = ready
        override val name: String get() = label
        var calls = 0
        override fun generate(request: LlmRequest): String = replies.getOrElse(calls++) { "" }
    }

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
            val needle = selectionArgs?.firstOrNull()
                ?.removePrefix("%")?.removeSuffix("%")?.lowercase()
            return MatrixCursor(cols).apply {
                rows.withIndex()
                    .filter { needle == null || it.value.first.lowercase().contains(needle) }
                    .forEach { (i, r) -> addRow(arrayOf<Any>(100L + i, r.first, r.second, r.third)) }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        prefs = Prefs(ctx)
        prefs.mode = Mode.Control
        prefs.riskyControls = true
        prefs.directCall = false
        org.robolectric.Shadows
            .shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.READ_CONTACTS)
        ShadowContentResolver.registerProviderInternal(
            ContactsContract.AUTHORITY,
            FakeContacts(
                listOf(
                    Triple(
                        "Shubham Pande",
                        "+919812345678",
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                    ),
                ),
            ),
        )
        actions = Actions(ctx, Memory(ctx), prefs, { null })
    }

    /** Every way the user might ask, and none of them may fall through to the model. */
    @Test
    fun everyWhatsAppPhrasingReachesTheRules() {
        val sentences = listOf(
            "whatsapp shubham pando that i am late",
            "whatsapp to shubham pando saying i am late",
            "message shubham pando on whatsapp that i am late",
            "send shubham pando a whatsapp saying i am late",
            "shubham pando ko whatsapp par message bhejo ki main late hoon",
            "shubham pando ko whatsapp me message kar ki main late hoon",
            "shubham pando ko call kar",
            "call shubham pando",
        )
        for (s in sentences) {
            val reply = actions.tryHandle(s)
            assertNotNull("no rule answered: $s", reply)
            assertTrue("the name should be guessed, got: $reply", reply!!.contains("Shubham Pande"))
        }
    }

    @Test
    fun aRealNameIsUsedNotGuessed() {
        val reply = actions.tryHandle("whatsapp shubham pande that i am late")
        assertNotNull(reply)
        assertTrue("should not ask, got: $reply", !reply!!.contains("Did you mean"))
        assertTrue("should open WhatsApp, got: $reply", reply.contains("WhatsApp"))
    }

    @Test
    fun aCloudBrainThatCannotAnswerSaysWhy() {
        prefs.cloudEnabled = true
        prefs.cloudApiKey = ""
        val assistant = assistantFor(
            Fake("local", listOf("x"), ready = false),
            cloudOn = true,
            cloud = Fake("cloud", listOf("x"), ready = false),
        )
        val reply = assistant.respond("say hello")
        assertTrue(reply, reply.contains("cloud brain is on but cannot answer"))
    }

    @Test
    fun withNothingLoadedItSaysThatPlainly() {
        prefs.cloudEnabled = false
        val assistant = assistantFor(Fake("local", listOf("x"), ready = false), cloudOn = false)
        val reply = assistant.respond("say hello")
        assertTrue(reply, reply.contains("No brain is loaded"))
    }

    @Test
    fun aCloudBrainThatIsSetUpAnswersInsteadOfComplaining() {
        prefs.cloudEnabled = true
        prefs.cloudModel = "some-model"
        prefs.cloudBaseUrl = "https://api.example.com/v1"
        prefs.cloudApiKey = "sk-test"
        val cloud = Fake("cloud", listOf("Cloud answer."), ready = true)
        val assistant = assistantFor(Fake("local", listOf("x"), ready = false), cloudOn = true, cloud = cloud)
        assertEquals("Cloud answer.", assistant.respond("say hello"))
        assertEquals(1, cloud.calls)
    }

    private fun assistantFor(
        local: Brain,
        cloudOn: Boolean,
        cloud: Brain = Fake("cloud", listOf("unused"), ready = true),
    ): Assistant {
        val router = BrainRouter(local, cloud) { cloudOn }
        val memory = Memory(ctx)
        val a = Actions(ctx, memory, prefs, { null })
        return Assistant(
            brain = router,
            actions = a,
            memory = memory,
            tools = Tools(a, memory, { prefs.mode }, { prefs.riskyControls }),
            language = { prefs.language },
            mode = { prefs.mode },
            contextTokens = { prefs.contextTokens },
            replyLength = { prefs.replyLength },
            chatStyle = { prefs.chatStyle },
            extraInstructions = { prefs.chatInstructions },
            webAnswers = { false },
            internet = { false },
            fetch = { "" },
            riskyAllowed = { prefs.riskyControls },
            brainProblem = {
                if (!cloudOn) null
                else CloudConfig(
                    provider = prefs.cloudProvider,
                    baseUrl = prefs.cloudBaseUrl,
                    model = prefs.cloudModel,
                    apiKey = prefs.cloudApiKey,
                    maxTokens = 512,
                ).problem()
            },
        )
    }
}
