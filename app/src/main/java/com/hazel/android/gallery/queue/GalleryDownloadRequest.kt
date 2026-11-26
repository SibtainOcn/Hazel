package com.hazel.android.gallery.queue

import com.hazel.android.gallery.GalleryImageSize
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.gallery.GallerySave
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
    val id: Long = nextId(),
    /** File addresses fetched one by one instead of [url], for a post without a link of its own. */
    val files: List<String>? = null,
    /** The post's caption, saved as a text file or written into the images as [save] and the options ask. */
    val caption: String = "",
    val save: GallerySave = GallerySave.MEDIA
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("title", title)
        put("author", author)
        thumbnail?.let { put("thumbnail", it) }
        put("id", id)
        indexes?.let { put("indexes", JSONArray(it)) }
        files?.let { put("files", JSONArray(it)) }
        if (caption.isNotEmpty()) put("caption", caption)
        put("save", save.name)
        options?.let { o ->
            put("options", JSONObject().apply {
                put("imageSize", o.imageSize.name)
                put("videoMode", o.videoMode.name)
                put("filenameTemplate", o.filenameTemplate)
                put("writeMetadata", o.writeMetadata)
                put("keepPostDate", o.keepPostDate)
                put("packZip", o.packZip)
                put("skipDownloaded", o.skipDownloaded)
                put("embedCaption", o.embedCaption)
                put("retries", o.retries)
            })
        }
    }

    /** Whether this request downloads [item] of [post]. */
    fun covers(post: GalleryPost, item: com.hazel.android.gallery.GalleryItem): Boolean =
        files?.contains(item.url) ?: (url == post.source && (indexes == null || item.index in indexes))

    /** Stored with a failure, so Retry on the Failed tab sends it back to the gallery queue. */
    fun toPayload(): String = PAYLOAD_PREFIX + toJson()

    companion object {
        private const val PAYLOAD_PREFIX = "gallery:"
        private val lastId = java.util.concurrent.atomic.AtomicLong()

        /** Time-based and strictly increasing, so two requests made at once never share an id. */
        fun nextId(): Long = lastId.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }

        fun isPayload(payload: String?): Boolean = payload?.startsWith(PAYLOAD_PREFIX) == true

        /**
         * Marks a failure that happened while reading a link for the gallery sheet: there is no
         * download to replay, so retrying opens the sheet on the link again.
         */
        const val READ_MARK = "gallery-read"

        fun isRead(payload: String?): Boolean = payload == READ_MARK

        /** True for a failure the gallery engine had, whose link the gallery sheet reopens. */
        fun isGallery(payload: String?): Boolean = isPayload(payload) || isRead(payload)

        fun fromPayload(payload: String?): GalleryDownloadRequest? =
            payload?.takeIf(::isPayload)?.let { fromJson(it.removePrefix(PAYLOAD_PREFIX)) }

        fun fromJson(raw: String): GalleryDownloadRequest? = runCatching {
            val json = JSONObject(raw)
            val url = json.getString("url").takeIf { it.startsWith("http") } ?: return null
            GalleryDownloadRequest(
                url = url,
                indexes = json.optJSONArray("indexes")?.let { a -> List(a.length()) { a.getInt(it) } },
                files = json.optJSONArray("files")?.let { a -> List(a.length()) { a.getString(it) } }
                    ?.filter { it.startsWith("http") || it.startsWith("ytdl:") }?.takeIf { it.isNotEmpty() },
                caption = json.optString("caption"),
                save = GallerySave.fromName(json.optString("save")),
                options = json.optJSONObject("options")?.let { o ->
                    GalleryOptions(
                        imageSize = runCatching { GalleryImageSize.valueOf(o.optString("imageSize")) }.getOrDefault(GalleryImageSize.ORIGINAL),
                        videoMode = runCatching { GalleryVideoMode.valueOf(o.optString("videoMode")) }.getOrDefault(GalleryVideoMode.BEST),
                        filenameTemplate = o.optString("filenameTemplate"),
                        writeMetadata = o.optBoolean("writeMetadata"),
                        keepPostDate = o.optBoolean("keepPostDate", true),
                        packZip = o.optBoolean("packZip"),
                        skipDownloaded = o.optBoolean("skipDownloaded"),
                        embedCaption = o.optBoolean("embedCaption"),
                        retries = o.optInt("retries", GalleryOptions.DEFAULT_RETRIES)
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

/** The request saving the files of this post at [indexes] (counted from 1), or all of them when null. */
fun GalleryPost.request(
    indexes: List<Int>?,
    options: GalleryOptions?,
    title: String? = null,
    author: String? = null,
    save: GallerySave = GallerySave.MEDIA
): GalleryDownloadRequest {
    val chosen = indexes?.toSet()?.let { wanted -> items.filter { it.index in wanted } } ?: items
    val first = chosen.firstOrNull()
    return GalleryDownloadRequest(
        url = source,
        indexes = indexes.takeUnless { direct },
        options = options,
        title = title ?: first?.title.orEmpty(),
        author = author ?: this.author,
        thumbnail = first?.thumbnail ?: first?.url?.takeUnless { first.isVideo },
        files = chosen.map { it.url }.takeIf { direct },
        caption = first?.caption.orEmpty(),
        save = save
    )
}

/** What a link shared to Instant Gallery saves: one post with its details, else the link with the author they share. */
fun instantRequest(url: String, posts: List<GalleryPost>, options: GalleryOptions?): GalleryDownloadRequest =
    posts.singleOrNull()?.request(null, options)
        ?: GalleryDownloadRequest(url, options = options, author = posts.map { it.author }.distinct().singleOrNull().orEmpty())
