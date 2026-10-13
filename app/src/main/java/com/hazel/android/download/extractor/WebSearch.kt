package com.hazel.android.download.extractor

import android.content.Context
import com.hazel.android.R
import com.hazel.android.download.NetworkProxy.throughProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Searches the whole Web for words and keeps only the links Hazel can download.
 *
 * DuckDuckGo answers by default, from its plain results page, which needs no key or
 * account. Someone who runs a SearXNG server, or trusts one, can point search at it
 * instead; it answers in JSON. Either way every link is checked against yt-dlp's own
 * rules ([SupportedSites]) before it is shown, so a result is always a page yt-dlp has a
 * site for, read and downloaded like any pasted link.
 *
 * A search that cannot be answered fails with a message saying which service did not
 * answer; it does not quietly try the other.
 */
object WebSearch {

    private val client by lazy {
        OkHttpClient.Builder()
            .throughProxy()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Raised with a message for the person searching. */
    class Failure(message: String) : IOException(message)

    /**
     * Up to [count] downloadable links for [query]. [server] is a SearXNG address, or blank
     * for DuckDuckGo.
     */
    suspend fun search(context: Context, query: String, count: Int, server: String): List<LinkEntry> =
        withContext(Dispatchers.IO) {
            val kept = LinkedHashMap<String, LinkEntry>()
            val pages = if (server.isBlank()) duckDuckGo(context, query) else searxng(context, server, query)
            for (page in pages) {
                ensureActive()
                page.forEach { entry ->
                    if (entry.url !in kept && SupportedSites.supports(context, entry.url)) kept[entry.url] = entry
                }
                if (kept.size >= count) break
            }
            kept.values.take(count)
        }

    // ── DuckDuckGo ──

    private const val DUCKDUCKGO = "https://html.duckduckgo.com/html/"
    private const val DUCKDUCKGO_PAGES = 3

    /** A real browser's name: the plain page turns away requests that give none. */
    private const val BROWSER =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    /** Pages of results, read one at a time as they are asked for. */
    private fun duckDuckGo(context: Context, query: String): Sequence<List<LinkEntry>> = sequence {
        var form: Map<String, String>? = mapOf("q" to query)
        repeat(DUCKDUCKGO_PAGES) { index ->
            val fields = form ?: return@sequence
            val request = Request.Builder()
                .url(DUCKDUCKGO)
                .header("User-Agent", BROWSER)
                .post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build())
                .build()
            val html = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.string()
                }
            } catch (e: IOException) {
                // A later page failing still leaves the earlier ones to show.
                if (index == 0) throw Failure(context.getString(R.string.search_web_failed_duckduckgo))
                return@sequence
            }
            val page = parseDuckDuckGo(html)
            yield(page.results)
            form = page.next
        }
    }

    internal class DuckDuckGoPage(val results: List<LinkEntry>, val next: Map<String, String>?)

    private val RESULT = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val INPUT = Regex("""<input[^>]*>""")
    private val NAME = Regex("""name="([^"]*)"""")
    private val VALUE = Regex("""value="([^"]*)"""")
    private val TAG = Regex("<[^>]+>")

    /** The links on one page of results, and the form that asks for the next page, if any. */
    internal fun parseDuckDuckGo(html: String): DuckDuckGoPage {
        val results = RESULT.findAll(html).mapNotNull { match ->
            val url = target(unescape(match.groupValues[1])) ?: return@mapNotNull null
            LinkEntry(
                url = url,
                title = unescape(match.groupValues[2].replace(TAG, "")).trim(),
                uploader = host(url),
                thumbnail = null,
                durationSeconds = 0
            )
        }.toList()

        val nextAt = html.indexOf("value=\"Next\"")
        val next = if (nextAt < 0) null else {
            val start = html.lastIndexOf("<form", nextAt)
            val end = html.indexOf("</form>", nextAt)
            if (start < 0 || end < 0) null else INPUT.findAll(html.substring(start, end)).mapNotNull { input ->
                val name = NAME.find(input.value)?.groupValues?.get(1) ?: return@mapNotNull null
                val value = VALUE.find(input.value)?.groupValues?.get(1) ?: return@mapNotNull null
                if (input.value.contains("type=\"submit\"")) null else name to unescape(value)
            }.toMap().takeIf { it.containsKey("q") }
        }
        return DuckDuckGoPage(results, next)
    }

    /**
     * The page a result leads to. DuckDuckGo wraps each link in a redirect that carries it
     * as `uddg`; an ad carries none and is dropped.
     */
    private fun target(href: String): String? {
        val absolute = if (href.startsWith("//")) "https:$href" else href
        val url = absolute.toHttpUrlOrNull() ?: return null
        val wrapped = url.queryParameter("uddg")
        val target = wrapped ?: absolute.takeUnless { url.host.endsWith("duckduckgo.com") } ?: return null
        return target.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    // ── SearXNG ──

    private const val SEARXNG_PAGES = 2

    private fun searxng(context: Context, server: String, query: String): Sequence<List<LinkEntry>> = sequence {
        val base = server.trimEnd('/')
        for (page in 1..SEARXNG_PAGES) {
            val url = "$base/search".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("q", query)
                ?.addQueryParameter("format", "json")
                ?.addQueryParameter("categories", "videos")
                ?.addQueryParameter("pageno", page.toString())
                ?.build()
                ?: throw Failure(context.getString(R.string.search_web_failed_server))
            val body = try {
                client.newCall(Request.Builder().url(url).header("User-Agent", BROWSER).build()).execute().use { response ->
                    // A server that has JSON turned off answers 403.
                    if (response.code == 403) throw Failure(context.getString(R.string.search_web_server_refused))
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.string()
                }
            } catch (e: Failure) {
                throw e
            } catch (e: IOException) {
                if (page == 1) throw Failure(context.getString(R.string.search_web_failed_server))
                return@sequence
            }
            val results = parseSearxng(body)
            if (results.isEmpty()) return@sequence
            yield(results)
        }
    }

    /** The links in one SearXNG JSON answer. */
    internal fun parseSearxng(body: String): List<LinkEntry> {
        val list = runCatching { JSONObject(body).getJSONArray("results") }.getOrNull() ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val item = list.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
            LinkEntry(
                url = url,
                title = item.optString("title").ifBlank { url },
                uploader = item.optString("author").ifBlank { host(url) },
                thumbnail = item.optString("thumbnail").ifBlank { item.optString("img_src") }.takeIf { it.startsWith("http") },
                durationSeconds = duration(item.optString("length"))
            )
        }
    }

    /** "1:02:03" or "4:05" in seconds; anything else is unknown. */
    private fun duration(text: String): Int {
        val parts = text.trim().split(':').map { it.toIntOrNull() ?: return 0 }
        if (parts.size !in 2..3) return 0
        return parts.fold(0) { total, part -> total * 60 + part }
    }

    /**
     * A SearXNG address as typed, made whole, or null when it is not one: an http or https
     * address with a host, kept without a trailing slash.
     */
    fun normalizeServer(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val full = (if ("://" in trimmed) trimmed else "https://$trimmed").trimEnd('/')
        val uri = runCatching { URI(full) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return full
    }

    private fun host(url: String): String =
        runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    private fun unescape(text: String): String {
        var out = text.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'")
            .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        out = Regex("&#(\\d+);").replace(out) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        return out
    }
}
