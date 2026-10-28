package com.sadu.assistant

import com.sadu.assistant.brain.Gguf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The check that runs when someone picks a model file: is this a `.gguf` at all, and what does it
 * say it is? A wrong pick used to cost a long copy and a load that failed on the phone.
 */
class GgufTest {

    /** A real header plus whatever metadata the test needs. */
    private fun headerOf(
        version: Int = 3,
        tensors: Long = 200,
        keys: List<Pair<String, Any>> = emptyList(),
    ): ByteArray {
        val buffer = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(Gguf.MAGIC)
        buffer.putInt(version)
        buffer.putLong(tensors)
        buffer.putLong(keys.size.toLong())
        keys.forEach { (key, value) ->
            putString(buffer, key)
            when (value) {
                is String -> {
                    buffer.putInt(8)
                    putString(buffer, value)
                }
                is Int -> {
                    buffer.putInt(4)
                    buffer.putInt(value)
                }
                is Float -> {
                    buffer.putInt(6)
                    buffer.putFloat(value)
                }
                is Boolean -> {
                    buffer.putInt(7)
                    buffer.put(if (value) 1 else 0)
                }
                is List<*> -> {
                    buffer.putInt(9)
                    buffer.putInt(8)
                    buffer.putLong(value.size.toLong())
                    value.forEach { putString(buffer, it as String) }
                }
                else -> error("no helper for $value")
            }
        }
        val written = buffer.position()
        val bytes = ByteArray(written)
        buffer.flip()
        buffer.get(bytes)
        return bytes
    }

    private fun putString(buffer: ByteBuffer, text: String) {
        val bytes = text.toByteArray()
        buffer.putLong(bytes.size.toLong())
        buffer.put(bytes)
    }

    @Test
    fun aRealGgufIsAcceptedAndDescribesItself() {
        val bytes = headerOf(
            keys = listOf(
                "general.architecture" to "gemma3",
                "general.name" to "gemma-3-1b-it-Q4_K_M",
                "general.file_type" to 15,
            ),
        )
        val check = Gguf.inspect(bytes)
        assertTrue(check.toString(), check is Gguf.Check.Ok)
        val info = (check as Gguf.Check.Ok).info
        assertEquals(3, info.version)
        assertEquals(200L, info.tensors)
        assertEquals("gemma3", info.architecture)
        assertEquals("gemma-3-1b-it-Q4_K_M", info.name)
        assertEquals("gemma-3-1b-it-Q4_K_M", info.label)
    }

    @Test
    fun everyKindOfValueIsSteppedOver() {
        // A boolean, a float, an array of strings and an integer all sit in front of the key we
        // want, in the order a real file puts them. Reading past them wrong loses the name.
        val bytes = headerOf(
            keys = listOf(
                "general.alignment" to 32,
                "some.flag" to true,
                "some.ratio" to 1.5f,
                "tokenizer.ggml.tokens" to listOf("a", "bb", "ccc"),
                "general.architecture" to "llama",
            ),
        )
        val info = (Gguf.inspect(bytes) as Gguf.Check.Ok).info
        assertEquals("llama", info.architecture)
    }

    @Test
    fun aTaskFileIsRefusedByMagic() {
        val notGguf = ByteArray(64) { 7 }
        val check = Gguf.inspect(notGguf)
        assertTrue(check is Gguf.Check.Bad)
        val reason = (check as Gguf.Check.Bad).reason
        assertTrue(reason, reason.contains("not a .gguf file"))
        assertTrue(reason, reason.contains("MediaPipe"))
    }

    @Test
    fun anOldVersionIsRefusedByName() {
        val check = Gguf.inspect(headerOf(version = 1))
        assertTrue(check is Gguf.Check.Bad)
        val reason = (check as Gguf.Check.Bad).reason
        assertTrue(reason, reason.contains("version 1"))
        assertTrue(reason, reason.contains("versions 2 and 3"))
    }

    @Test
    fun aFileThatIsTooSmallIsRefused() {
        assertTrue(Gguf.inspect(ByteArray(8)) is Gguf.Check.Bad)
    }

    @Test
    fun aCutOffFileStillReportsWhatTheHeaderSaid() {
        // Only a prefix of a file is read, so the metadata can run out. The header is what decides
        // whether it is usable; a missing name is a missing name, not a rejection.
        val full = headerOf(keys = listOf("general.architecture" to "gemma3"))
        val info = (Gguf.inspect(full.copyOf(full.size)) as Gguf.Check.Ok).info
        assertEquals("gemma3", info.architecture)

        val cut = (Gguf.inspect(full.copyOf(28)) as Gguf.Check.Ok).info
        assertEquals("", cut.architecture)
    }

    @Test
    fun labelFallsBackToTheArchitectureThenToAWord() {
        assertEquals("gemma3", Gguf.Info(3, 1, 1, architecture = "gemma3").label)
        assertEquals("gguf model", Gguf.Info(3, 1, 1).label)
    }

    @Test
    fun aWrittenFileIsReadFromDisk() {
        val file = java.io.File.createTempFile("model", ".gguf")
        try {
            file.writeBytes(headerOf(keys = listOf("general.name" to "tiny")))
            val info = (Gguf.inspect(file) as Gguf.Check.Ok).info
            assertEquals("tiny", info.name)
        } finally {
            file.delete()
        }
    }
}
