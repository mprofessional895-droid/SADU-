package com.sadu.assistant

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Looks a question up on the web in the background, so Sadu can answer from what it
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

    /**
     * A normal Android browser's user agent, not one that announces the app.
     *
     * This matters: search engines serve a JavaScript "are you a robot" page to clients whose
     * agent looks like a script, and the old "Sadu/0.9 (personal assistant on Android)" did
     * exactly that. The page has no results in it, so the lookup came back empty and Sadu fell
     * back to opening a browser tab - which is the complaint this fixes.
     */
    const val UserAgent =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Mobile Safari/537.36"

    data class Hit(val title: String, val url: String, val text: String) {
        /** A search engine's own topic page: fine as a filler, not as the answer. */
        internal val isThin: Boolean get() = url.startsWith("https://duckduckgo.com")
    }

    /**
     * What a lookup produced. [blocked] is true when a source answered with a robot check
     * instead of results, so the app can say why it has nothing rather than pretending the
     * web had no answer.
     */
    data class Lookup(val hits: List<Hit>, val blocked: Boolean, val sources: List<String>)

    fun urls(query: String): List<String> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return listOf(
            "https://lite.duckduckgo.com/lite/?q=$q",
            "https://www.bing.com/search?q=$q&format=rss",
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
    fun search(query: String, fetch: (String) -> String): List<Hit> = lookup(query, fetch).hits

    /**
     * The whole lookup, and enough about it to explain an empty result: real sites first, then
     * Bing's RSS feed (which answers even when DuckDuckGo is showing a robot check), then the
     * fact box and the encyclopaedia.
     */
    fun lookup(query: String, fetch: (String) -> String): Lookup {
        val addresses = urls(query)
        fun body(index: Int): String =
            runCatching { fetch(addresses[index]) }.getOrNull().orEmpty()

        val hits = ArrayList<Hit>()
        val sources = ArrayList<String>()
        var blocked = false

        val lite = body(0)
        blocked = blocked || looksBlocked(lite)
        val liteHits = fromLite(lite)
        if (liteHits.isNotEmpty()) {
            hits += liteHits
            sources += "DuckDuckGo"
        }

        if (hits.size < MaxResults) {
            val feed = body(1)
            blocked = blocked || looksBlocked(feed)
            val bing = fromBingRss(feed)
            if (bing.isNotEmpty()) {
                hits += bing
                sources += "Bing"
            }
        }

        if (hits.size < MaxResults) {
            val instant = fromInstant(body(2))
            hits += instant.filterNot { it.isThin }
            val wiki = fromWiki(body(3))
            hits += wiki
            if (wiki.isNotEmpty()) sources += "Wikipedia"
            if (instant.isNotEmpty()) sources += "DuckDuckGo"
            hits += instant.filter { it.isThin }
        }

        val kept = hits.filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .distinctBy { (it.title + it.text).take(60).lowercase() }
            .take(MaxResults)
        return Lookup(kept, blocked, sources.distinct())
    }

    /**
     * True when a body is a robot check rather than a results page. DuckDuckGo answers with a
     * small "anomaly" page, Bing with an "unusual traffic" notice.
     */
    fun looksBlocked(body: String): Boolean =
        body.isNotBlank() && body.length < 60_000 && Regex(
            "anomaly-modal|/anomaly|js-challenge|challenge-form|unusual traffic|" +
                "automated queries|are you a robot|verify you are human",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(body)

    /** Bing's RSS output: a plain `<item>` per result, which is far easier to trust than its HTML. */
    fun fromBingRss(xml: String): List<Hit> {
        if (xml.isBlank()) return emptyList()
        val hits = ArrayList<Hit>()
        Regex("<item[\\s\\S]*?</item>").findAll(xml).forEach { item ->
            val body = item.value
            fun tag(name: String): String =
                Regex("<$name[^>]*>([\\s\\S]*?)</$name>").find(body)
                    ?.groupValues?.get(1)?.let { plain(it) }.orEmpty()
            val title = tag("title")
            val link = tag("link")
            val text = tag("description")
            if (title.isNotEmpty()) hits += Hit(title, link, text)
        }
        return hits
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
