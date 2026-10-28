package com.sadu.assistant.brain

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tells a real `.gguf` file from the other things a phone's file picker will happily hand over.
 *
 * This is not a model parser - llama.cpp is the only authority on whether a file runs. It answers
 * one earlier question: did the user pick the right file? A wrong pick costs a 700 MB copy and a
 * load that ends in a failed reply, and the answer costs a 64 KB read.
 *
 * The header is checked strictly. The metadata (the model's own name and architecture) is read
 * best effort from the first [PREFIX_BYTES] bytes, because a model with a very large metadata
 * block would run past any prefix we chose.
 */
object Gguf {

    /** "GGUF" as little endian bytes. */
    const val MAGIC = 0x46554747

    private val AcceptedVersions = 2..3

    const val PREFIX_BYTES = 64 * 1024

    data class Info(
        val version: Int,
        val tensors: Long,
        val keys: Long,
        val architecture: String = "",
        val name: String = "",
    ) {
        /** The friendliest thing to show: the model's own name, or its architecture. */
        val label: String get() = name.ifEmpty { architecture.ifEmpty { "gguf model" } }
    }

    sealed interface Check {

        data class Ok(val info: Info) : Check

        /** [reason] is written for the person holding the phone. */
        data class Bad(val reason: String) : Check
    }

    fun inspect(file: File): Check = runCatching {
        file.inputStream().use { stream ->
            val prefix = ByteArray(PREFIX_BYTES)
            var read = 0
            while (read < prefix.size) {
                val n = stream.read(prefix, read, prefix.size - read)
                if (n <= 0) break
                read += n
            }
            inspect(prefix.copyOf(read))
        }
    }.getOrElse { Check.Bad("That file could not be read. Try picking it again.") }

    fun inspect(prefix: ByteArray): Check {
        if (prefix.size < HEADER_BYTES) return Check.Bad("That file is too small to be a model.")

        val buffer = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != MAGIC) {
            return Check.Bad(
                "That is not a .gguf file. The llama.cpp engine needs a .gguf model - a .task file " +
                    "belongs to the MediaPipe engine.",
            )
        }
        val version = buffer.int
        if (version !in AcceptedVersions) {
            return Check.Bad("That .gguf file is version $version, and llama.cpp reads versions 2 and 3.")
        }
        val tensors = buffer.long
        val keys = buffer.long

        var architecture = ""
        var name = ""
        var left = keys.coerceAtMost(MaxKeys.toLong())
        while (left-- > 0) {
            val key = Reader.string(buffer) ?: break
            val type = Reader.int(buffer) ?: break
            val text = when (key) {
                "general.architecture", "general.name" -> Reader.stringValue(buffer, type) ?: break
                else -> {
                    if (!Reader.skip(buffer, type)) break
                    null
                }
            }
            if (key == "general.architecture") architecture = text.orEmpty()
            if (key == "general.name") name = text.orEmpty()
        }

        return Check.Ok(Info(version, tensors, keys, architecture, name))
    }

    private const val HEADER_BYTES = 24
    private const val MaxKeys = 4096

    /** Reading only a prefix means every read can run off the end; a false means "stop here". */
    private object Reader {

        fun int(buffer: ByteBuffer): Int? = if (buffer.remaining() >= 4) buffer.int else null

        fun long(buffer: ByteBuffer): Long? = if (buffer.remaining() >= 8) buffer.long else null

        fun string(buffer: ByteBuffer): String? {
            val length = long(buffer) ?: return null
            if (length < 0 || length > buffer.remaining()) return null
            val bytes = ByteArray(length.toInt())
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        /** A value we want, when it is a string; otherwise it is stepped over and reported empty. */
        fun stringValue(buffer: ByteBuffer, type: Int): String? =
            if (type == TypeString) string(buffer) else if (skip(buffer, type)) "" else null

        fun skip(buffer: ByteBuffer, type: Int): Boolean {
            when (type) {
                TypeUInt8, TypeInt8, TypeBool -> {
                    if (buffer.remaining() < 1) return false
                    buffer.get()
                }
                TypeUInt16, TypeInt16 -> {
                    if (buffer.remaining() < 2) return false
                    buffer.short
                }
                TypeUInt32, TypeInt32, TypeFloat32 -> {
                    if (buffer.remaining() < 4) return false
                    buffer.int
                }
                TypeUInt64, TypeInt64, TypeFloat64 -> {
                    if (buffer.remaining() < 8) return false
                    buffer.long
                }
                TypeString -> if (string(buffer) == null) return false
                TypeArray -> {
                    val element = int(buffer) ?: return false
                    val count = long(buffer) ?: return false
                    if (count < 0) return false
                    var left = count
                    while (left-- > 0) if (!skip(buffer, element)) return false
                }
                else -> return false
            }
            return true
        }
    }

    private const val TypeUInt8 = 0
    private const val TypeInt8 = 1
    private const val TypeUInt16 = 2
    private const val TypeInt16 = 3
    private const val TypeUInt32 = 4
    private const val TypeInt32 = 5
    private const val TypeFloat32 = 6
    private const val TypeBool = 7
    private const val TypeString = 8
    private const val TypeArray = 9
    private const val TypeUInt64 = 10
    private const val TypeInt64 = 11
    private const val TypeFloat64 = 12
}
