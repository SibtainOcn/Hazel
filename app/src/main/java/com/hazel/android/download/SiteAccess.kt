package com.hazel.android.download

import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * What a request needs in order to reach a site as the signed-in user rather than as a
 * stranger.
 *
 * Cookies on their own are only half of a session. A site hands them out to a particular
 * browser and expects them back from that same browser, so the identity they were collected
 * under travels with them; sending them under a different one is what makes a site treat a
 * valid sign-in as suspicious and serve the stripped-down media it serves to anyone.
 *
 * One of these is built per fetch and used by both the metadata read and the download that
 * follows it, so the two always ask the site the same question. A download that asked
 * differently would be offered a different set of formats, and the id the sheet showed
 * would not exist in it.
 */
data class SiteAccess(
    /** Netscape cookie file to send, or null when there are no sign-ins to use. */
    val cookieFile: File? = null,
    /** The browser identity the cookies were collected under, or blank if unknown. */
    val userAgent: String = ""
) {
    val hasCookies: Boolean get() = cookieFile != null

    companion object {
        /** No sign-ins: every request the app makes without them looks like this. */
        val NONE = SiteAccess()
    }
}

/**
 * Applies the sign-in to a request.
 *
 * Called on the metadata read and on the download alike. Nothing is added when there are no
 * cookies, so an ordinary fetch carries no extra options.
 *
 * No YouTube player client is forced. Which clients still answer with the full format ladder
 * changes with YouTube, and yt-dlp's own default set follows those changes release by
 * release; a fixed list goes stale and leaves only the single 360p stream every client serves.
 */
fun YoutubeDLRequest.applySiteAccess(access: SiteAccess, url: String) {
    val cookies = access.cookieFile ?: return

    addOption("--cookies", cookies.absolutePath)

    // Do not override User-Agent for YouTube because yt-dlp manages extractor client-specific
    // user agents internally. Passing mobile WebView user agents forces YouTube to return
    // stripped-down mobile markup which breaks playlist and tab page extraction.
    if (access.userAgent.isNotBlank() && !isYouTube(url)) {
        addOption("--add-header", "User-Agent:${access.userAgent}")
    }
}

/**
 * Whether the URL targets YouTube.
 */
fun isYouTube(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host.orEmpty() }
        .getOrDefault("")
        .removePrefix("www.")
        .lowercase()

    return host.endsWith("youtube.com") ||
            host.endsWith("youtu.be") ||
            host.endsWith("youtube-nocookie.com")
}
