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
    val thumbnail: String?
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
                thumbnail = thumbnailOf(url, extension, info)
            )
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
