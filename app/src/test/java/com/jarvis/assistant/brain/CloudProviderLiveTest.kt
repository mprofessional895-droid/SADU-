package com.jarvis.assistant.brain

import com.jarvis.assistant.Mode
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The provider-specific half of the HTTP contract. A stub server on the loopback address
 * writes down exactly what it was sent, so the path, the headers and the body of each
 * provider are checked as they really go out.
 */
class CloudProviderLiveTest {

    private var server: ServerSocket? = null
    private val seen = Collections.synchronizedList(ArrayList<String>())

    @After
    fun stop() {
        server?.close()
        server = null
    }

    /** Serves [reply] to every request and remembers the request itself. */
    private fun stub(reply: String): String {
        val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        server = socket
        val body = reply.toByteArray(Charsets.UTF_8)
        Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        val request = StringBuilder()
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.lowercase().startsWith("content-length:")) {
                                length = line.substringAfter(":").trim().toIntOrNull() ?: 0
                            }
                            if (line.isEmpty()) break
                            request.append(line).append("\r\n")
                        }
                        if (length > 0) {
                            val chars = CharArray(length)
                            var read = 0
                            while (read < length) {
                                val got = reader.read(chars, read, length - read)
                                if (got < 0) break
                                read += got
                            }
                            request.append(chars, 0, read)
                        }
                        seen += request.toString()
                        val head = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n" +
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                        val out = client.getOutputStream()
                        out.write(head.toByteArray(Charsets.UTF_8))
                        out.write(body)
                        out.flush()
                    }
                } catch (_: IOException) {
                    break
                }
            }
        }.apply { isDaemon = true }.start()
        return "http://127.0.0.1:" + socket.localPort
    }

    private fun question() = LlmRequest("You are Jarvis.", emptyList(), "hello", Mode.Chat)

    private val blank = "\n\n"
    private val quote = "\""

    @Test
    fun anthropicGetsItsOwnPathHeadersAndBody() {
        val base = stub(
            "event: content_block_delta" + blank +
                "data: {" + quote + "type" + quote + ":" + quote + "content_block_delta" + quote + "," +
                quote + "delta" + quote + ":{" + quote + "type" + quote + ":" + quote + "text_delta" + quote +
                "," + quote + "text" + quote + ":" + quote + "Hel" + quote + "}}" + blank +
                "data: {" + quote + "type" + quote + ":" + quote + "content_block_delta" + quote + "," +
                quote + "delta" + quote + ":{" + quote + "text" + quote + ":" + quote + "lo" + quote + "}}" + blank,
        )
        val cfg = CloudConfig(CloudProvider.Anthropic, base, "claude-3-5-sonnet-latest", "sk-ant-test")
        val text = CloudBrain { cfg }.generateStream(question()) { }

        assertEquals("Hello", text)
        val request = seen.last()
        assertTrue(request, request.startsWith("POST /v1/messages "))
        assertTrue(request, request.contains("x-api-key: sk-ant-test"))
        assertTrue(request, request.contains("anthropic-version: 2023-06-01"))
        assertTrue(request, request.contains("\"model\":\"claude-3-5-sonnet-latest\""))
        assertTrue("Anthropic wants max_tokens", request.contains("\"max_tokens\""))
        assertTrue("the instructions go in their own field", request.contains("\"system\""))
        assertTrue("no system role inside messages", !request.contains("\"role\":\"system\""))
        assertTrue(request, request.contains("text/event-stream"))
    }

    @Test
    fun geminiGetsItsOwnPathHeadersAndBody() {
        val base = stub(
            "data: {" + quote + "candidates" + quote + ":[" + "{" + quote + "content" + quote + ":{" +
                quote + "parts" + quote + ":[" + "{" + quote + "text" + quote + ":" + quote + "Hel" + quote +
                "}]}}]}" + blank +
                "data: {" + quote + "candidates" + quote + ":[" + "{" + quote + "content" + quote + ":{" +
                quote + "parts" + quote + ":[" + "{" + quote + "text" + quote + ":" + quote + "lo" + quote +
                "}]}}]}" + blank,
        )
        val cfg = CloudConfig(
            CloudProvider.Gemini,
            "$base/v1beta",
            "gemini-2.0-flash",
            "AIza-test",
        )
        val text = CloudBrain { cfg }.generateStream(question()) { }

        assertEquals("Hello", text)
        val request = seen.last()
        assertTrue(request, request.startsWith("POST /v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse "))
        assertTrue(request, request.contains("x-goog-api-key: AIza-test"))
        assertTrue(request, request.contains("\"systemInstruction\""))
        assertTrue(request, request.contains("\"generationConfig\""))
        assertTrue("Gemini calls the assistant a model", request.contains("\"role\":\"user\""))
    }

    @Test
    fun theSameQuestionReachesEveryProvider() {
        val base = stub("data: {" + quote + "choices" + quote + ":[" + "{" + quote + "delta" + quote + ":{" +
            quote + "content" + quote + ":" + quote + "Hi there" + quote + "}}]}" + blank)
        val cfg = CloudConfig(CloudProvider.Compatible, "$base/v1", "gpt-4o-mini", "sk-test")
        assertEquals("Hi there", CloudBrain { cfg }.generateStream(question()) { })
        val request = seen.last()
        assertTrue(request, request.startsWith("POST /v1/chat/completions "))
        assertTrue(request.contains("Authorization: Bearer sk-test"))
        assertTrue("the conversation carries the question", request.contains("hello"))
        assertTrue("and the instructions", request.contains("You are Jarvis."))
    }
}
