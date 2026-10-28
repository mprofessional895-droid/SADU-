package com.sadu.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three parsers, checked against trimmed copies of the real replies of the real
 * endpoints, plus the two texts Sadu builds out of them.
 */
class WebSearchTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/web/$name")?.bufferedReader()?.use { it.readText() }
            ?: error("missing fixture $name")

    @Test
    fun liteResultsComeOutInOrder() {
        val hits = WebSearch.fromLite(fixture("ddg-lite.html"))
        assertEquals(2, hits.size)
        assertEquals("Welcome to Python.org", hits[0].title)
        assertEquals("https://www.python.org/", hits[0].url)
        assertTrue(hits[0].text.startsWith("Python is a versatile"))
        assertEquals("Python Tutorial - W3Schools", hits[1].title)
        assertEquals("https://www.w3schools.com/python/", hits[1].url)
        assertTrue(hits[1].text.startsWith("Python is a popular"))
    }

    @Test
    fun anAdvertisementIsNeverAnAnswer() {
        val hits = WebSearch.fromLite(fixture("ddg-lite.html"))
        assertFalse(
            "a sponsored row must be skipped",
            hits.any { it.text.contains("Take your skills to a new level") },
        )
        assertTrue(hits.all { !it.title.equals("more info", ignoreCase = true) })
    }

    @Test
    fun instantAnswersAreRead() {
        val hits = WebSearch.fromInstant(fixture("ddg-instant.json"))
        assertTrue(hits.isNotEmpty())
        assertEquals("Python (programming language)", hits[0].title)
        assertTrue(hits[0].text.startsWith("Python is a high-level"))
        assertTrue(hits[0].url.startsWith("https://"))
    }

    @Test
    fun wikipediaExtractsAreRead() {
        val hits = WebSearch.fromWiki(fixture("wiki.json"))
        assertEquals(2, hits.size)
        assertEquals("Python (programming language)", hits[0].title)
        assertEquals("https://en.wikipedia.org/wiki/Python_(programming_language)", hits[0].url)
    }

    @Test
    fun aBrokenBodyIsNotACrash() {
        assertTrue(WebSearch.fromLite("<html>nothing here</html>").isEmpty())
        assertTrue(WebSearch.fromInstant("not json").isEmpty())
        assertTrue(WebSearch.fromWiki("not json").isEmpty())
        assertTrue(WebSearch.fromLite("").isEmpty())
        assertEquals("", WebSearch.digest("anything", emptyList()))
    }

    @Test
    fun sourcesAreTriedUntilThereIsEnough() {
        val asked = ArrayList<String>()
        val hits = WebSearch.search("python programming") { url ->
            asked += url
            if (url.contains("wikipedia")) fixture("wiki.json") else "<html></html>"
        }
        assertEquals(4, asked.size)
        assertTrue(asked[0].contains("duckduckgo"))
        assertTrue("the RSS feed is the second source", asked[1].contains("bing"))
        assertTrue(asked[3].contains("wikipedia"))
        assertEquals(2, hits.size)
    }

    @Test
    fun aSourceThatFailsIsSkipped() {
        val hits = WebSearch.search("python") { url ->
            if (url.contains("duckduckgo")) throw IllegalStateException("offline")
            fixture("wiki.json")
        }
        assertEquals(2, hits.size)
    }

    @Test
    fun aThinTopicPageNeverPushesOutARealResult() {
        // The fact box below is what the live endpoint really returned for "python
        // programming": the language itself, and then two of DuckDuckGo's own topic pages.
        val asked = ArrayList<String>()
        val hits = WebSearch.search("python programming") { url ->
            asked += url
            when {
                url.contains("lite") -> "<html></html>"
                url.contains("api.duckduckgo.com") -> fixture("ddg-instant.json")
                else -> fixture("wiki.json")
            }
        }
        assertEquals(4, asked.size)
        assertEquals(3, hits.size)
        // The fact box repeats the same Wikipedia intro as the extract, so it is only
        // there once; the topic pages may fill the last slot and never the first ones.
        assertTrue("real pages come first", hits.take(2).all { it.url.contains("wikipedia") })
        assertTrue("a topic page may only fill what is left", hits.drop(2).all { it.isThin })
    }

    @Test
    fun bingRssResultsAreRead() {
        val xml = """<?xml version="1.0"?><rss><channel>
            <item><title>Python (programming language)</title>
            <link>https://en.wikipedia.org/wiki/Python</link>
            <description>A high-level language, &amp; a good one.</description></item>
            <item><title>Second result</title><link>https://example.com/b</link>
            <description>Another one</description></item>
        </channel></rss>"""
        val hits = WebSearch.fromBingRss(xml)
        assertEquals(2, hits.size)
        assertEquals("Python (programming language)", hits[0].title)
        assertEquals("https://en.wikipedia.org/wiki/Python", hits[0].url)
        assertTrue("entities are decoded", hits[0].text.contains("& a good one"))
        assertEquals(emptyList<WebSearch.Hit>(), WebSearch.fromBingRss(""))
    }

    @Test
    fun aRobotCheckIsNoticed() {
        // What DuckDuckGo really answers a datacentre with: a small page, no results in it.
        val page = """<!DOCTYPE html><html><head><title>DuckDuckGo</title></head>
            <body><div class="anomaly-modal__title">Unfortunately, bots use DuckDuckGo too.</div>
            </body></html>"""
        assertTrue(WebSearch.looksBlocked(page))
        assertFalse("a real results page is not a robot check", WebSearch.looksBlocked(fixture("ddg-lite.html")))
        assertFalse(WebSearch.looksBlocked(""))
    }

    @Test
    fun aBlockedLookupSaysItWasBlocked() {
        val page = "<html><body><form class=\"challenge-form\">verify you are human</form></body></html>"
        val found = WebSearch.lookup("24byme") { if (it.contains("bing")) "" else page }
        assertTrue(found.hits.isEmpty())
        assertTrue("the app must be able to explain this", found.blocked)
    }

    @Test
    fun theDigestSaysWhereItCameFrom() {
        val digest = WebSearch.digest("python", WebSearch.fromWiki(fixture("wiki.json")))
        assertTrue(digest.startsWith("[web results for \"python\"]"))
        assertTrue(digest.contains("Python (programming language)"))
        assertTrue(digest.contains("web search"))
        assertTrue("the digest must not eat the context window", digest.length < 1200)
    }

    @Test
    fun thePlainAnswerNamesTheTopic() {
        val text = WebSearch.readable("python", WebSearch.fromWiki(fixture("wiki.json")))
        assertTrue(text.startsWith("Here is what I found about \"python\""))
        assertTrue(text.contains("Sources:"))
        assertTrue(WebSearch.readable("nowhere", emptyList()).contains("could not find"))
    }
}
