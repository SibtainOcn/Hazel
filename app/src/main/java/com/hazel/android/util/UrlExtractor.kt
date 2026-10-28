package com.hazel.android.util

/**
 * Extracts clean, downloadable media URLs from shared intent text and raw user input.
 *
 * When external applications (like YouTube, Twitter/X, Instagram, or mobile browsers) share
 * media to Hazel, they often include supplemental text (such as post titles, descriptions,
 * tracking tags, or punctuation) around the URL. This utility extracts the standalone URL
 * so that extractors and probes can process it cleanly.
 */
object UrlExtractor {

    private val HTTP_URL_REGEX = Regex("""https?://[^\s<>"'{}|\\^`]+""", RegexOption.IGNORE_CASE)

    /**
     * Finds and extracts the first valid web address within [text].
     *
     * Trims enclosing trailing punctuation (dots, commas, parentheses, quotes) often attached
     * when copying links within complete sentences.
     */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()

        // 1. Direct regex match for http/https URLs
        val match = HTTP_URL_REGEX.find(trimmed)
        if (match != null) {
            val candidate = match.value.trimEnd('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')
            if (candidate.isNotBlank()) return candidate
        }

        // 2. Domain prefix without explicit scheme (e.g. "www.youtube.com/watch?v=..." or "youtu.be/...")
        if (trimmed.startsWith("www.", ignoreCase = true) ||
            (trimmed.contains(".") && !trimmed.contains(" ") && !trimmed.contains("\n"))
        ) {
            val candidate = trimmed.trimEnd('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')
            return "https://$candidate"
        }

        return trimmed.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts the 11-character YouTube video ID from any watch, short, embed, or youtu.be link.
     */
    fun extractYouTubeId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val pattern = Regex("""(?:youtu\.be/|youtube\.com/(?:embed/|v/|watch\?v=|watch\?.+&v=|shorts/))([\w-]{11})""", RegexOption.IGNORE_CASE)
        return pattern.find(url.trim())?.groupValues?.get(1)
    }

    /**
     * [url] as the one video it names, when it is a YouTube video opened from inside a
     * playlist or a Mix. Those carry `list=` beside the video, which the engine reads as the
     * whole list; a Mix has no end. Any other link comes back as it was.
     */
    fun youTubeVideoOnly(url: String): String {
        val trimmed = url.trim()
        if (extractYouTubeId(trimmed) == null) return url
        val queryStart = trimmed.indexOf('?').takeIf { it >= 0 } ?: return url
        val fragmentStart = trimmed.indexOf('#', queryStart).takeIf { it >= 0 } ?: trimmed.length
        val params = trimmed.substring(queryStart + 1, fragmentStart).split('&').filter { it.isNotEmpty() }
        if (params.none { it.startsWith("list=") }) return url
        val kept = params.filterNot { param -> PLAYLIST_PARAMS.any { param == it || param.startsWith("$it=") } }
        return trimmed.substring(0, queryStart) +
            (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) +
            trimmed.substring(fragmentStart)
    }

    private val PLAYLIST_PARAMS = listOf("list", "index", "start_radio", "pp")
}
