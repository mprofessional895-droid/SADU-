package com.jarvis.assistant

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Looks a question up on the web in the background, so Jarvis can answer from what it
 * found instead of only opening a browser tab.
 *
 * Three sources, all keyless, tried in order and stopped as soon as there are enough hits:
 * DuckDuckGo Lite (ordinary web results), DuckDuckGo's instant answer (a fact box) and
 * Wikipedia (an encyclopaedia extract). Sponsored rows are skipped on purpose - answering
 * out of an advert is worse than answering with nothing.
 *
 * Fetching is injected so the parsers can be tested without a connection.
 */
object WebSearch {

    const val MaxResults = 3
    private const val MaxSnippet = 320
    private const val UserAgent = "Jarvis/0.9 (personal assistant on Android)"

    data class Hit(val title: String, val url: String, val text: String) {
        /** A search engine's own topic page: fine as a filler, not as the answer. */
        internal val isThin: Boolean get() = url.startsWith("https://duckduckgo.com")
    }

    fun urls(query: String): List<String> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return listOf(
            "https://lite.duckduckgo.com/lite/?q=$q",
            "https://api.duckduckgo.com/?q=$q&format=json&no_html=1&skip_disambig=1",
            "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$q" +
                "&gsrlimit=$MaxResults&prop=extracts&exintro=1&explaintext=1&format=json&formatversion=2",
        )
    }

    /**
     * The whole lookup, in order of how much the answer can be trusted, and stopping as
     * soon as there is enough: real sites from the Lite page first, then the fact box,
     * then the encyclopaedia, and last the search engine's own topic pages - which are
     * only used to fill a gap, never to push out a real result.
     */
    fun search(query: String, fetch: (String) -> String): List<Hit> {
        val addresses = urls(query)
        fun body(index: Int): String =
            runCatching { fetch(addresses[index]) }.getOrNull().orEmpty()

        val hits = ArrayList<Hit>()
        hits += fromLite(body(0))
        if (hits.size < MaxResults) {
            val instant = fromInstant(body(1))
            hits += instant.filterNot { it.isThin }
            if (hits.size < MaxResults) hits += fromWiki(body(2))
            hits += instant.filter { it.isThin }
        }
        return hits.filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .distinctBy { (it.title + it.text).take(60).lowercase() }
            .take(MaxResults)
    }

    /** DuckDuckGo Lite: one link row and one snippet row per organic result. */
    fun fromLite(html: String): List<Hit> {
        val titles = ArrayList<Pair<String, String>>()
        val texts = ArrayList<String>()
        Regex("<tr[^>]*>[\\s\\S]*?</tr>").findAll(html).forEach { row ->
            val markup = row.value
            if (markup.contains("result-sponsored")) return@forEach
            val link = Regex("<a\\s[^>]*>[\\s\\S]*?</a>").findAll(markup)
                .firstOrNull { it.value.contains("result-link") }
            if (link != null) {
                val title = plain(link.value)
                val href = Regex("href=\"([^\"]+)\"").find(link.value)?.groupValues?.get(1).orEmpty()
                if (title.isNotEmpty() && !title.equals("more info", ignoreCase = true)) {
                    titles += title to realUrl(href)
                }
                return@forEach
            }
            val snippet = Regex("<td[^>]*class=['\"]result-snippet['\"][^>]*>([\\s\\S]*?)</td>")
                .find(markup)?.groupValues?.get(1)?.let { plain(it) }.orEmpty()
            if (snippet.isNotEmpty()) texts += snippet
        }
        return titles.mapIndexed { index, (title, url) ->
            Hit(title, url, texts.getOrNull(index).orEmpty())
        }
    }

    /** DuckDuckGo's instant answer: a heading, an abstract and related topics. */
    fun fromInstant(body: String): List<Hit> {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val hits = ArrayList<Hit>()
        val abstract = json.optString("AbstractText").orEmpty()
        if (abstract.isNotBlank()) {
            hits += Hit(json.optString("Heading"), json.optString("AbstractURL"), abstract)
        }
        fun walk(topics: JSONArray?) {
            if (topics == null) return
            for (i in 0 until topics.length()) {
                val item = topics.optJSONObject(i) ?: continue
                val about = item.optString("Text").orEmpty()
                if (about.isNotBlank()) {
                    hits += Hit(about.substringBefore(" - "), item.optString("FirstURL"), about)
                }
                walk(item.optJSONArray("Topics"))
            }
        }
        walk(json.optJSONArray("RelatedTopics"))
        return hits
    }

    /** Wikipedia: the search API with intro extracts in one call. */
    fun fromWiki(body: String): List<Hit> {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val pages = json.optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        val hits = ArrayList<Hit>()
        for (i in 0 until pages.length()) {
            val page = pages.optJSONObject(i) ?: continue
            val title = page.optString("title")
            val extract = page.optString("extract")
            if (title.isBlank() || extract.isBlank()) continue
            hits += Hit(title, "https://en.wikipedia.org/wiki/" + title.replace(' ', '_'), extract)
        }
        return hits
    }

    /** What the model is given along with the question. */
    fun digest(query: String, hits: List<Hit>): String {
        if (hits.isEmpty()) return ""
        val sb = StringBuilder("[web results for \"").append(query.trim()).append("\"]\n")
        hits.take(MaxResults).forEachIndexed { index, hit ->
            sb.append(index + 1).append(". ").append(hit.title.trim().take(90))
            if (hit.text.isNotBlank()) {
                sb.append(" - ").append(hit.text.trim().take(MaxSnippet).replace('\n', ' '))
            }
            if (hit.url.isNotBlank()) sb.append(" (").append(hit.url.take(120)).append(')')
            sb.append('\n')
        }
        sb.append("Answer from these results when they help, and say the answer comes from a web search.")
        return sb.toString()
    }

    /** What the user is told when no model is loaded to summarise with. */
    fun readable(query: String, hits: List<Hit>): String {
        if (hits.isEmpty()) {
            return "I looked but could not find anything about \"" + query.trim() + "\"."
        }
        val sb = StringBuilder("Here is what I found about \"").append(query.trim()).append("\":\n")
        hits.take(MaxResults).forEachIndexed { index, hit ->
            sb.append(index + 1).append(". ").append(hit.title.trim().take(90))
            if (hit.text.isNotBlank()) {
                sb.append(" - ").append(hit.text.trim().take(MaxSnippet).replace('\n', ' '))
            }
            sb.append('\n')
        }
        return sb.append("Sources: ").append(hits.joinToString(", ") { it.url.take(70) })
            .toString().trim()
    }

    /** The real fetcher: plain HTTP with a browser-ish agent and short timeouts. */
    fun live(): (String) -> String = { address ->
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UserAgent)
            conn.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    /** A DuckDuckGo redirect needs unwrapping; the rest is already the real address. */
    private fun realUrl(href: String): String {
        if (href.isBlank()) return ""
        val wrapped = Regex("[?&]uddg=([^&]+)").find(href)?.groupValues?.get(1)
        if (wrapped != null) {
            return runCatching { URLDecoder.decode(wrapped, "UTF-8") }.getOrDefault("")
        }
        return when {
            href.startsWith("//") -> "https:$href"
            else -> href
        }
    }

    /** Tags, entities and runs of whitespace out of a fragment of markup. */
    private fun plain(html: String): String {
        var s = html.replace(Regex("<[^>]*>"), " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'").replace("&#x27;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
        s = Regex("&#(\\d+);").replace(s) { match ->
            runCatching { match.groupValues[1].toInt().toChar() }.getOrDefault(' ').toString()
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }
}
