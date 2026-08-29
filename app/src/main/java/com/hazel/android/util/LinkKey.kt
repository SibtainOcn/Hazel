package com.hazel.android.util

/**
 * Reduces a link to the media it points at, so two spellings of the same thing compare equal.
 *
 * The same video is handed around in many forms: a share sheet produces `youtu.be/ID?si=...`,
 * the address bar produces `youtube.com/watch?v=ID`, a link out of a playlist carries
 * `&list=` and `&index=`, and any of them may arrive with campaign parameters bolted on.
 * Comparing those as plain strings says they are four different videos, which is why a link
 * already downloaded could be downloaded again without a word.
 *
 * Two rules do nearly all of the work: keep only the part of the address that identifies the
 * media, and drop the parameters that describe how someone arrived at it.
 */
object LinkKey {

    /**
     * A comparable form of [url]. Not a valid address and never used as one: it exists to be
     * equal to the key of the same media written another way.
     */
    fun canonical(url: String): String {
        val trimmed = url.trim()
        val parsed = runCatching { java.net.URI(trimmed) }.getOrNull()
            ?: return trimmed.lowercase()

        val host = parsed.host?.removePrefix("www.")?.lowercase().orEmpty()
        val path = parsed.path.orEmpty().trimEnd('/')
        val query = parseQuery(parsed.rawQuery)

        // Sites that mint a stable id for a piece of media are keyed on that id alone, which
        // is what collapses the share form and the address-bar form onto one key. The key
        // names the service rather than the host it was written with, because those differ
        // for the same media: a share sheet writes youtu.be and the address bar writes
        // youtube.com.
        mediaId(host, path, query)?.let { (service, id) -> return "$service/$id" }

        val kept = query
            .filterKeys { it.lowercase() !in NOISE_PARAMS }
            .toSortedMap()
            .map { (key, value) -> "$key=$value" }
            .joinToString("&")

        return buildString {
            append(host)
            append(path.lowercase())
            if (kept.isNotEmpty()) {
                append('?')
                append(kept)
            }
        }
    }

    /**
     * Whether two links point at the same media, which is the question a repeat download
     * turns on.
     */
    fun sameMedia(first: String, second: String): Boolean =
        canonical(first) == canonical(second) && canonical(first).isNotBlank()

    /** Filename-safe digest of the canonical form, for anything keyed on disk. */
    fun digest(url: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical(url).toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * The service and media id, where the site has one and it can be read off the address.
     *
     * Only the forms that genuinely carry an id are matched. Guessing wrong here would make
     * two different videos compare equal, which is worse than not matching at all, so
     * anything unrecognised falls through to the general path above.
     */
    private fun mediaId(
        host: String,
        path: String,
        query: Map<String, String>
    ): Pair<String, String>? {
        val segments = path.split('/').filter { it.isNotBlank() }

        val id = when {
            host == "youtu.be" -> segments.firstOrNull()

            host.endsWith("youtube.com") -> when {
                path.startsWith("/watch") -> query["v"]
                segments.size >= 2 && segments[0] in YOUTUBE_ID_SEGMENTS -> segments[1]
                else -> null
            }

            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null

        return "youtube" to id
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null
                else pair.substring(0, index) to pair.substring(index + 1)
            }
            .toMap()
    }

    private val YOUTUBE_ID_SEGMENTS = setOf("shorts", "embed", "live", "v")

    /**
     * Parameters that say how the link was arrived at rather than what it points to. A
     * playlist reference is in here too: opening a video from inside a playlist and opening
     * it on its own are the same video.
     */
    private val NOISE_PARAMS = setOf(
        "si", "feature", "app", "pp", "ab_channel", "index", "list", "start_radio",
        "t", "time_continue", "utm_source", "utm_medium", "utm_campaign", "utm_term",
        "utm_content", "fbclid", "gclid", "igshid", "ref", "ref_src", "ref_url", "s"
    )
}
