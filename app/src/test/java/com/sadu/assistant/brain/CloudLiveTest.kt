package com.sadu.assistant.brain

import com.sadu.assistant.Mode
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Talks to a stub provider on the loopback address, so no real service is needed. */
class CloudLiveTest {

    private var stub: ServerSocket? = null
    private val quote = "\""
    private val blank = "\n".repeat(2)

    private fun start(reply: String, code: Int = 200): CloudConfig {
        val socket = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
        stub = socket
        val body = reply.toByteArray(Charsets.UTF_8)
        Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val head = "HTTP/1.1 " + code + " Answer\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Content-Length: " + body.size + "\r\n" +
                            "Connection: close\r\n\r\n"
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
        return CloudConfig(
            provider = CloudProvider.Compatible,
            baseUrl = "http://127.0.0.1:" + socket.localPort + "/v1",
            model = "stub",
            maxTokens = 32,
            timeoutMs = 5_000,
        )
    }

    @After
    fun stop() {
        stub?.close()
        stub = null
    }

    private fun question() = LlmRequest("rules", emptyList(), "hello", Mode.Chat)

    private fun sse(text: String): String {
        val head = "{" + quote + "choices" + quote + ":[" + "{" + quote + "delta" + quote + ":{"
        return "data: " + head + quote + "content" + quote + ":" + quote + text + quote + "}}]}" + blank
    }

    private fun json(text: String): String {
        val head = "{" + quote + "choices" + quote + ":[" + "{" + quote + "message" + quote + ":"
        return head + "{" + quote + "content" + quote + ":" + quote + text + quote + "}}]}"
    }

    @Test
    fun chunksAreJoinedAsTheyArrive() {
        val cfg = start(sse("Hel") + sse("lo") + "a line with no data prefix" + blank)
        val whole = CloudBrain { cfg }.generateStream(question()) { }
        assertEquals("Hello", whole)
    }

    @Test
    fun aPlainReplyIsAcceptedWhenStreamingIsIgnored() {
        val cfg = start(json("Straight answer"))
        assertEquals("Straight answer", CloudBrain { cfg }.generateStream(question()) { })
    }

    @Test
    fun aRefusalExplainsItself() {
        val cfg = start(
            "{" + quote + "error" + quote + ":{" + quote + "message" + quote + ":"
                + quote + "bad key" + quote + "}}",
            code = 401,
        )
        try {
            CloudBrain { cfg }.generate(question())
            fail("a 401 must not look like a success")
        } catch (e: IOException) {
            val why = e.message.orEmpty()
            assertTrue(why, why.contains("bad key"))
            assertTrue(why, why.contains("check the API key"))
        }
    }
}
