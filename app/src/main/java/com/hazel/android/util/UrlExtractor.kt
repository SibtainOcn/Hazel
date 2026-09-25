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
}
