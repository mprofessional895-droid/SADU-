package com.sadu.assistant

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadu.assistant.brain.LlamaEngine
import com.sadu.assistant.brain.LocalEngine
import com.sadu.assistant.brain.OfflineBrain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The `.gguf` engine without a phone: what it does when the native library is not there, when no
 * model has been imported, and when the file someone picked is not a model at all.
 *
 * Real inference cannot run here - the library is built for arm64 Android and there is no device
 * or emulator for this project - so what is proved here is every path that leads up to it.
 */
@RunWith(AndroidJUnit4::class)
class LlamaEngineTest {

    private lateinit var ctx: Context
    private lateinit var engine: LlamaEngine

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        engine = LlamaEngine(ctx)
        File(ctx.filesDir, LlamaEngine.ModelName).delete()
    }

    @Test
    fun aUnitTestBuildHasNoNativeLibrary() {
        assertFalse(LlamaEngine.isAvailable)
    }

    @Test
    fun loadingWithoutTheLibraryExplainsItself() {
        val problem = assertThrows(IllegalStateException::class.java) { engine.load(4096) }
        assertTrue(problem.message.orEmpty(), problem.message.orEmpty().contains("NDK"))
    }

    @Test
    fun nothingIsReadyBeforeAModelIsImported() {
        assertFalse(engine.isReady)
        assertFalse(engine.isModelPresent())
        assertEquals(0L, engine.modelBytes())
        assertEquals(0, engine.contextTokens)
        assertFalse(engine.isWriting)
    }

    @Test
    fun thereIsNoTemplateWithoutAModel() {
        assertEquals("", engine.applyTemplate("INSTRUCTIONS", emptyList(), "hello"))
    }

    @Test
    fun writingWithoutAModelIsRefusedRatherThanCrashing() {
        val problem = assertThrows(IllegalStateException::class.java) {
            engine.generate("prompt", Sampling.chat(), 512)
        }
        assertTrue(problem.message.orEmpty().contains("No .gguf model"))
        assertFalse(engine.cancel())
    }

    @Test
    fun theWrongFileIsNeverSavedAsTheModel() {
        val picked = File.createTempFile("not-a-model", ".task")
        picked.writeBytes(ByteArray(4096) { 3 })
        try {
            assertThrows(IllegalStateException::class.java) {
                engine.importModel(Uri.fromFile(picked)) { }
            }
            assertFalse(File(ctx.filesDir, LlamaEngine.ModelName).exists())
            assertFalse(engine.isModelPresent())
        } finally {
            picked.delete()
        }
    }

    @Test
    fun aModelFileBelowTheFloorDoesNotCount() {
        // A real quantised 1B model is hundreds of megabytes; a few kilobytes is a mistake.
        val tooSmall = File(ctx.filesDir, LlamaEngine.ModelName)
        tooSmall.writeBytes(ByteArray(1024))
        try {
            assertFalse(engine.isModelPresent())
        } finally {
            tooSmall.delete()
        }
    }

    @Test
    fun switchingEnginesIsRememberedAndCanGoBack() {
        val prefs = Prefs(ctx)
        assertEquals(LocalEngine.MediaPipe, prefs.localEngine)

        prefs.localEngine = LocalEngine.Llama
        assertEquals(LocalEngine.Llama, Prefs(ctx).localEngine)

        prefs.localEngine = LocalEngine.MediaPipe
        assertEquals(LocalEngine.MediaPipe, Prefs(ctx).localEngine)
    }

    @Test
    fun anUnknownStoredEngineMeansMediaPipe() {
        assertEquals(LocalEngine.MediaPipe, LocalEngine.of(null))
        assertEquals(LocalEngine.MediaPipe, LocalEngine.of("nonsense"))
        assertEquals(LocalEngine.MediaPipe, LocalEngine.of(""))
        assertEquals(LocalEngine.Llama, LocalEngine.of("llama"))
    }

    @Test
    fun eachEngineHasAStableKeyALabelAndABlurb() {
        // The keys are written to disk, so they must never change.
        assertEquals("mediapipe", LocalEngine.MediaPipe.key)
        assertEquals("llama", LocalEngine.Llama.key)
        LocalEngine.entries.forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.blurb.isNotBlank())
        }
    }

    @Test
    fun aBrainKnowsWhichEngineIsBehindIt() {
        // What the reply line and the Settings status are built from, and the difference between
        // an engine that is running and one that cannot be: neither of these has a model loaded.
        assertEquals("On-device llama.cpp", OfflineBrain(LlamaEngine(ctx)).name)
        assertEquals("On-device Gemma", OfflineBrain(LlmEngine(ctx)).name)
        assertFalse(OfflineBrain(LlamaEngine(ctx)).isReady)
    }

    @Test
    fun theLlamaSamplingCarriesThePenaltiesMediaPipeIgnores() {
        val sampling = Sampling.chat(0.7f)
        assertEquals(1.1f, sampling.repeatPenalty, 0.001f)
        assertEquals(0.05f, sampling.minP, 0.001f)
        assertEquals(Sampling.DefaultRepeatPenalty, Sampling.Command.repeatPenalty, 0.001f)
    }
}
