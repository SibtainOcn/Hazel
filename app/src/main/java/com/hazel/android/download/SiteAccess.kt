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
 */
fun YoutubeDLRequest.applySiteAccess(access: SiteAccess, url: String) {
    val cookies = access.cookieFile ?: return

    addOption("--cookies", cookies.absolutePath)

    if (access.userAgent.isNotBlank()) {
        addOption("--add-header", "User-Agent:${access.userAgent}")
    }

    if (cookiesNarrowTheFormats(url)) {
        addOption("--extractor-args", "youtube:player_client=$SIGNED_IN_PLAYER_CLIENTS")
    }
}

/**
 * The clients to ask for the media when the request carries a sign-in.
 *
 * The site serves each of its player clients a different list, and most of them hold back
 * everything above 360p unless the request carries a proof-of-origin token the app has no
 * way to produce. These two are the ones that answer a signed-in request with anything
 * worth having: the first needs no token when account cookies are sent, and the second
 * answers with streams that need none at all. Naming them also drops the clients that
 * would be asked and would answer with nothing, which is most of the waiting a signed-in
 * read used to do.
 */
private const val SIGNED_IN_PLAYER_CLIENTS = "tv,web_safari"

/**
 * Whether sending cookies to [url] costs formats.
 *
 * True for the one site that treats a signed-in request as something to be careful with.
 * Everywhere else a sign-in only ever adds to what is on offer, so it is sent with every
 * request; here it is held back until the media will not open without it.
 */
fun cookiesNarrowTheFormats(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host.orEmpty() }
        .getOrDefault("")
        .removePrefix("www.")
        .lowercase()

    return host.endsWith("youtube.com") ||
            host.endsWith("youtu.be") ||
            host.endsWith("youtube-nocookie.com")
}
