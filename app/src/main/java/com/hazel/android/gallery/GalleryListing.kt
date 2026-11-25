package com.hazel.android.gallery

import org.json.JSONArray
import org.json.JSONObject

/** One file of a post, in the order gallery-dl's `--range` counts them (from 1). */
data class GalleryItem(
    val index: Int,
    val url: String,
    val extension: String,
    val title: String,
    val author: String,
    val site: String,
    val width: Int,
    val height: Int,
    val thumbnail: String?,
    /** Which post the file belongs to; files of one post share it. */
    val postKey: String = "",
    /** The post's own link, when the site gives one. */
    val postUrl: String? = null
) {
    val isVideo: Boolean get() = extension.lowercase() in VIDEO_EXTENSIONS

    companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v", "mkv", "m3u8")
    }
}

/**
 * What one link holds: the files of a post, and for a profile, search or tag page the
 * posts it leads to, which are read one by one later.
 */
data class GalleryListing(val items: List<GalleryItem>, val posts: List<String>) {
    val isEmpty: Boolean get() = items.isEmpty() && posts.isEmpty()

    companion object {
        private const val MESSAGE_URL = 3
        private const val MESSAGE_QUEUE = 6
        private const val TITLE_MAX = 140
        private val POST_KEYS = listOf("post_url", "post_id", "tweet_id", "post_shortcode", "shortcode")

        /** Reads the output of `gallery-dl -j`. */
        fun parse(output: String): GalleryListing {
            val messages = runCatching { JSONArray(output.trim()) }.getOrElse {
                throw GalleryDlException(GalleryDlException.Kind.OTHER, "unreadable listing", it)
            }
            val items = mutableListOf<GalleryItem>()
            val posts = mutableListOf<String>()
            for (i in 0 until messages.length()) {
                val message = messages.optJSONArray(i) ?: continue
                when (message.optInt(0)) {
                    MESSAGE_URL -> items += item(items.size + 1, message.optString(1), message.optJSONObject(2) ?: JSONObject())
                    MESSAGE_QUEUE -> message.optString(1).takeIf { it.startsWith("http") }?.let(posts::add)
                }
            }
            return GalleryListing(items, posts)
        }

        private fun item(index: Int, url: String, info: JSONObject): GalleryItem {
            val extension = info.optString("extension").lowercase()
            return GalleryItem(
                index = index,
                url = url,
                extension = extension,
                title = titleOf(info),
                author = authorOf(info),
                site = info.optString("category"),
                width = info.optInt("width"),
                height = info.optInt("height"),
                thumbnail = thumbnailOf(url, extension, info),
                postKey = postKeyOf(info),
                postUrl = info.optString("post_url").takeIf { it.startsWith("http") }
            )
        }

        private fun postKeyOf(info: JSONObject): String {
            val site = info.optString("category")
            POST_KEYS.forEach { key ->
                info.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { return "$site:$key:$it" }
            }
            return "$site:id:${info.optString("id")}:${authorOf(info)}"
        }

        private fun titleOf(info: JSONObject): String =
            listOf("title", "description", "content", "caption")
                .firstNotNullOfOrNull { key -> info.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(TITLE_MAX)
                .orEmpty()

        private fun authorOf(info: JSONObject): String {
            listOf("fullname", "username", "uploader").forEach { key ->
                info.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { return it }
            }
            listOf("author", "user").forEach { key ->
                when (val value = info.opt(key)) {
                    is JSONObject -> listOf("name", "nick", "username").firstNotNullOfOrNull { field ->
                        value.optString(field).takeIf { it.isNotBlank() }
                    }?.let { return it }
                    is String -> if (value.isNotBlank()) return value
                }
            }
            return ""
        }

        private fun thumbnailOf(url: String, extension: String, info: JSONObject): String? {
            if (extension !in GalleryItem.VIDEO_EXTENSIONS) return url.takeIf { it.startsWith("http") }
            return listOf("display_url", "thumbnail", "preview_url", "poster").firstNotNullOfOrNull { key ->
                info.optString(key).takeIf { it.startsWith("http") }
            }
        }
    }
}

/** One post of a result: its files, and the link whose `--range` their indexes count in. */
data class GalleryPost(val key: String, val source: String, val items: List<GalleryItem>) {
    val author: String get() = items.firstOrNull { it.author.isNotBlank() }?.author.orEmpty()
    val site: String get() = items.firstOrNull()?.site.orEmpty()

    companion object {
        /**
         * Splits the files read from [source] into posts, in the order they came. A post with
         * its own link is downloaded from it, counted from 1; otherwise from [source].
         */
        fun group(source: String, items: List<GalleryItem>): List<GalleryPost> {
            if (items.isEmpty()) return emptyList()
            val groups = items.groupBy { it.postKey }
            if (groups.size == 1) return listOf(GalleryPost(items.first().postKey, source, items))
            return groups.map { (key, files) ->
                val link = files.first().postUrl
                if (link == null) GalleryPost(key, source, files)
                else GalleryPost(key, link, files.mapIndexed { i, file -> file.copy(index = i + 1) })
            }
        }
    }
}
