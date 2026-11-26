package com.hazel.android.gallery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

/** A site gallery-dl can search without a sign-in, as the search page address it reads. */
enum class GallerySearchSite(val label: String, private val template: String) {
    PINTEREST("Pinterest", "https://www.pinterest.com/search/pins/?q=%s"),
    BLUESKY("Bluesky", "https://bsky.app/search?q=%s"),
    TUMBLR("Tumblr", "https://www.tumblr.com/search/%s"),
    DEVIANTART("DeviantArt", "https://www.deviantart.com/search?q=%s");

    fun urlFor(query: String): String =
        template.format(URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20"))

    companion object {
        val DEFAULT = PINTEREST
        fun fromName(name: String?): GallerySearchSite = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Which files of a post are kept. */
enum class GalleryShow {
    ALL, IMAGES, VIDEOS;

    companion object {
        fun fromName(name: String?): GalleryShow = entries.firstOrNull { it.name == name } ?: ALL
    }
}

/** Work handed to the Gallery tab from another screen. */
sealed interface GalleryRequest {
    data class Links(val urls: List<String>) : GalleryRequest
    data class Search(val query: String, val site: GallerySearchSite) : GalleryRequest
}

/** Holds a [GalleryRequest] until the Gallery tab takes it. */
object GalleryRequests {
    private val pending = MutableStateFlow<GalleryRequest?>(null)
    val next: StateFlow<GalleryRequest?> = pending.asStateFlow()

    fun send(request: GalleryRequest) {
        pending.value = request
    }

    fun take(): GalleryRequest? = pending.getAndUpdateNull()

    private fun MutableStateFlow<GalleryRequest?>.getAndUpdateNull(): GalleryRequest? {
        while (true) {
            val current = value ?: return null
            if (compareAndSet(current, null)) return current
        }
    }
}
