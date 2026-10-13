package com.hazel.android.download.extractor

import android.content.Context
import android.util.Log
import java.util.regex.Pattern

/**
 * Tells whether yt-dlp has a site of its own for a link, without asking the network.
 *
 * The rules are yt-dlp's: each extractor's link pattern, copied into the app by
 * tools/sites/generate.py and read here once, the first time a link is checked. A link
 * passes when it matches one of them from its start, as yt-dlp's own check does. yt-dlp's
 * generic extractor, which would take any page, is not among them, so a pass means a site
 * yt-dlp knows by name.
 *
 * A rule that fails to compile is left out rather than stopping the rest, and a missing
 * file means nothing passes: Web search then shows nothing, never a link Hazel cannot read.
 */
object SupportedSites {

    private const val TAG = "SupportedSites"
    private const val ASSET = "ytdlp_sites.txt"

    @Volatile private var rules: List<Pattern>? = null

    /** Whether [url] belongs to a site yt-dlp has an extractor for. */
    fun supports(context: Context, url: String): Boolean = matches(load(context), url)

    private fun load(context: Context): List<Pattern> {
        rules?.let { return it }
        synchronized(this) {
            rules?.let { return it }
            val text = runCatching {
                context.applicationContext.assets.open(ASSET).bufferedReader().use { it.readText() }
            }.onFailure { Log.w(TAG, "No site rules", it) }.getOrDefault("")
            val parsed = parse(text)
            if (parsed.failed > 0) Log.w(TAG, "${parsed.failed} site rules did not compile")
            return parsed.rules.also { rules = it }
        }
    }

    internal class Parsed(val rules: List<Pattern>, val failed: Int)

    /** The rules in [text], one `<extractor>\t<pattern>` per line, compiled. */
    internal fun parse(text: String): Parsed {
        var failed = 0
        val compiled = text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val pattern = line.substringAfter('\t', "")
                if (pattern.isEmpty()) return@mapNotNull null
                runCatching { Pattern.compile(pattern) }.getOrElse { failed++; null }
            }
            .toList()
        return Parsed(compiled, failed)
    }

    /** Whether [url] matches one of [rules] from its start. */
    internal fun matches(rules: List<Pattern>, url: String): Boolean =
        rules.any { it.matcher(url).lookingAt() }
}
