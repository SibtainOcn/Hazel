package com.hazel.android.download

import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Which browsers the engine on this device can make its requests look like.
 *
 * Imitating a browser takes a network library yt-dlp does not carry itself, so whether any
 * are on offer depends on the device's engine, not on the site. yt-dlp stops a download
 * outright when asked for one it cannot do, which is why the app asks first and only offers
 * what came back.
 */
object ImpersonateTargets {

    /** Runs `--list-impersonate-targets`. Null when the engine could not be asked. */
    suspend fun check(): List<String>? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(emptyList<String>())
                .addOption("--list-impersonate-targets")
            parse(YtDlpEngine.execute(request).out)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The browser families in a target listing that can be used, in the order listed.
     *
     * yt-dlp 2026.08 prints a table of client, OS and source, one target per row
     * ("Chrome-131      Android-14   curl_cffi"), and marks a row it cannot use with
     * "(unavailable)". A family is taken from the client name without its version, which is
     * the form `--impersonate` accepts for any version of that browser.
     */
    fun parse(output: String): List<String> = output.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.contains("(unavailable)") }
        .mapNotNull { line ->
            val columns = line.split(Regex("\\s+"))
            if (columns.size < 3) return@mapNotNull null
            ROW_CLIENT.matchEntire(columns[0])?.groupValues?.get(1)?.lowercase()
        }
        // The table's own heading reads as a row of three words too.
        .filter { it != "client" }
        .distinct()
        .toList()

    /** A client cell: a browser name with an optional version, such as "Chrome-131". */
    private val ROW_CLIENT = Regex("""^([A-Za-z]+)(?:-[0-9][0-9.]*)?$""")
}
