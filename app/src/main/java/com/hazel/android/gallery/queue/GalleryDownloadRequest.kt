package com.hazel.android.gallery.queue

import com.hazel.android.gallery.GalleryImageSize
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryVideoMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * One gallery download waiting its turn: the post's link and, when only some of its files are
 * wanted, their positions (counted from 1). Title, author and thumbnail are for the queue card
 * and history; blank ones are shown from the link.
 */
data class GalleryDownloadRequest(
    val url: String,
    val indexes: List<Int>? = null,
    val options: GalleryOptions? = null,
    val title: String = "",
    val author: String = "",
    val thumbnail: String? = null,
    val id: Long = nextId()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("title", title)
        put("author", author)
        thumbnail?.let { put("thumbnail", it) }
        put("id", id)
        indexes?.let { put("indexes", JSONArray(it)) }
        options?.let { o ->
            put("options", JSONObject().apply {
                put("imageSize", o.imageSize.name)
                put("videoMode", o.videoMode.name)
                put("filenameTemplate", o.filenameTemplate)
                put("writeMetadata", o.writeMetadata)
                put("keepPostDate", o.keepPostDate)
                put("packZip", o.packZip)
                put("skipDownloaded", o.skipDownloaded)
            })
        }
    }

    /** Stored with a failure, so Retry on the Failed tab sends it back to the gallery queue. */
    fun toPayload(): String = PAYLOAD_PREFIX + toJson()

    companion object {
        private const val PAYLOAD_PREFIX = "gallery:"
        private val lastId = java.util.concurrent.atomic.AtomicLong()

        /** Time-based and strictly increasing, so two requests made at once never share an id. */
        fun nextId(): Long = lastId.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }

        fun isPayload(payload: String?): Boolean = payload?.startsWith(PAYLOAD_PREFIX) == true

        fun fromPayload(payload: String?): GalleryDownloadRequest? =
            payload?.takeIf(::isPayload)?.let { fromJson(it.removePrefix(PAYLOAD_PREFIX)) }

        fun fromJson(raw: String): GalleryDownloadRequest? = runCatching {
            val json = JSONObject(raw)
            val url = json.getString("url").takeIf { it.startsWith("http") } ?: return null
            GalleryDownloadRequest(
                url = url,
                indexes = json.optJSONArray("indexes")?.let { a -> List(a.length()) { a.getInt(it) } },
                options = json.optJSONObject("options")?.let { o ->
                    GalleryOptions(
                        imageSize = runCatching { GalleryImageSize.valueOf(o.optString("imageSize")) }.getOrDefault(GalleryImageSize.ORIGINAL),
                        videoMode = runCatching { GalleryVideoMode.valueOf(o.optString("videoMode")) }.getOrDefault(GalleryVideoMode.BEST),
                        filenameTemplate = o.optString("filenameTemplate"),
                        writeMetadata = o.optBoolean("writeMetadata"),
                        keepPostDate = o.optBoolean("keepPostDate", true),
                        packZip = o.optBoolean("packZip"),
                        skipDownloaded = o.optBoolean("skipDownloaded")
                    )
                },
                title = json.optString("title"),
                author = json.optString("author"),
                thumbnail = json.optString("thumbnail").takeIf { it.isNotBlank() },
                id = json.optLong("id").takeIf { it > 0 } ?: nextId()
            )
        }.getOrNull()
    }
}
