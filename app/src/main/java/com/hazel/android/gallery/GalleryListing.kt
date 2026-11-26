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
    val postUrl: String? = null,
    /** The post's full text as its author wrote it (caption, description); blank when there is none. */
    val caption: String = "",
    /** A web address a player can stream; null when the site gives none. */
    val playUrl: String? = null
) {
    val isVideo: Boolean get() = extension.lowercase() in VIDEO_EXTENSIONS

    companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v", "mkv", "m3u8")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "bmp")
    }
}

/**
 * What one link holds: the files of a post, and for a profile, search or tag page the
 * posts it leads to, which are read one by one later.
 */
data class GalleryListing(
    val items: List<GalleryItem>,
    val posts: List<String>,
    /** gallery-dl's error (class name, message) when reading stopped on one; it still exits 0 then. */
    val error: Pair<String, String>? = null
) {
    val isEmpty: Boolean get() = items.isEmpty() && posts.isEmpty()

    companion object {
        private const val MESSAGE_URL = 3
        private const val MESSAGE_QUEUE = 6
        private const val MESSAGE_ERROR = -1
        private const val TITLE_MAX = 140
        private const val CAPTION_MAX = 20_000
        private val CAPTION_KEYS = listOf("description", "content", "caption", "title")
        private val POST_KEYS = listOf("post_url", "post_id", "tweet_id", "post_shortcode", "shortcode")

        /** Reads the output of `gallery-dl -j`. */
        fun parse(output: String): GalleryListing {
            val messages = runCatching { JSONArray(output.trim()) }.getOrElse {
                throw GalleryDlException(GalleryDlException.Kind.OTHER, "unreadable listing", it)
            }
            val items = mutableListOf<GalleryItem>()
            val posts = mutableListOf<String>()
            var error: Pair<String, String>? = null
            for (i in 0 until messages.length()) {
                val message = messages.optJSONArray(i) ?: continue
                when (message.optInt(0)) {
                    MESSAGE_URL -> items += item(items.size + 1, message.optString(1), message.optJSONObject(2) ?: JSONObject())
                    MESSAGE_QUEUE -> message.optString(1).takeIf { it.startsWith("http") }?.let(posts::add)
                    MESSAGE_ERROR -> message.optJSONObject(1)?.let { error = it.optString("error") to it.optString("message") }
                }
            }
            return GalleryListing(items, posts, error)
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
                postUrl = postLinkOf(info),
                caption = CAPTION_KEYS
                    .firstNotNullOfOrNull { key -> info.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" } }
                    ?.take(CAPTION_MAX).orEmpty(),
                playUrl = playUrlOf(url, info)
            )
        }

        /**
         * A file gallery-dl hands to yt-dlp is named `ytdl:<page>`, which no player reads; the
         * site's own progressive file (Instagram's `video_url`) streams instead.
         */
        private fun playUrlOf(url: String, info: JSONObject): String? =
            url.takeIf { it.startsWith("http") }
                ?: info.optString("video_url").takeIf { it.startsWith("http") }

        /** The post's own page, from the site's fields; null when the site gives none (Pinterest search). */
        internal fun postLinkOf(info: JSONObject): String? {
            fun field(key: String) = info.optString(key).takeIf { it.isNotBlank() && it != "null" }
            field("post_url")?.takeIf { it.startsWith("http") }?.let { return it }
            return when (info.optString("category")) {
                "twitter" -> field("tweet_id")?.let { "https://x.com/i/status/$it" }
                "pixiv" -> field("id")?.let { "https://www.pixiv.net/artworks/$it" }
                "reddit" -> field("permalink")?.takeIf { it.startsWith("/") }?.let { "https://www.reddit.com$it" }
                "deviantart" -> field("url")?.takeIf { it.startsWith("https://www.deviantart.com/") }
                "bluesky" -> {
                    val handle = info.optJSONObject("author")?.optString("handle")?.takeIf { it.isNotBlank() }
                    field("post_id")?.let { id -> handle?.let { "https://bsky.app/profile/$it/post/$id" } }
                }
                else -> null
            }
        }

        private fun postKeyOf(info: JSONObject): String {
            val site = info.optString("category")
            POST_KEYS.forEach { key ->
                info.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { return "$site:$key:$it" }
            }
            return "$site:id:${info.optString("id")}:${authorOf(info)}"
        }

        // Pinterest search pins mostly leave title and description empty; their alt text reads as one.
        private val TITLE_KEYS = listOf("title", "grid_title", "description", "content", "caption", "seo_alt_text", "auto_alt_text")

        private fun titleOf(info: JSONObject): String =
            TITLE_KEYS
                .firstNotNullOfOrNull { key -> info.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                ?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(TITLE_MAX)
                .orEmpty()

        private fun authorOf(info: JSONObject): String {
            listOf("fullname", "username", "uploader").forEach { key ->
                info.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { return it }
            }
            listOf("author", "user", "pinner").forEach { key ->
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

/**
 * One post of a result: its files, and the link whose `--range` their indexes count in. A [direct]
 * post has no link of its own, so its files are fetched from their own addresses.
 */
data class GalleryPost(val key: String, val source: String, val items: List<GalleryItem>, val direct: Boolean = false) {
    val author: String get() = items.firstOrNull { it.author.isNotBlank() }?.author.orEmpty()
    val site: String get() = items.firstOrNull()?.site.orEmpty()

    companion object {
        /**
         * Single-file posts by one author gathered into one post, where the author first
         * appears. Posts with several files stay as they are. The gathered files come from
         * different posts, so they are fetched by their own addresses.
         */
        fun byAuthor(posts: List<GalleryPost>): List<GalleryPost> {
            val shared = posts.filter { it.items.size == 1 && it.author.isNotBlank() }
                .groupBy { it.site to it.author }
                .filterValues { it.size > 1 }
            if (shared.isEmpty()) return posts
            val placed = HashSet<Pair<String, String>>()
            return posts.mapNotNull { post ->
                if (post.items.size != 1) return@mapNotNull post
                val key = post.site to post.author
                val group = shared[key] ?: return@mapNotNull post
                if (!placed.add(key)) return@mapNotNull null
                GalleryPost(
                    key = "author:${key.first}:${key.second}",
                    source = group.first().source,
                    items = group.flatMap { it.items }.mapIndexed { i, file -> file.copy(index = i + 1) },
                    direct = true
                )
            }
        }

        /**
         * Splits the files read from [source] into posts, in the order they came, each counted
         * from 1. A post with its own link is downloaded from it; one read from a [feed] (search,
         * profile, several posts) without a link is fetched file by file, since the feed moves.
         */
        fun group(source: String, items: List<GalleryItem>, feed: Boolean = false): List<GalleryPost> {
            if (items.isEmpty()) return emptyList()
            val groups = items.groupBy { it.postKey }
            if (groups.size == 1 && !feed && items.first().postUrl == null) return listOf(GalleryPost(items.first().postKey, source, items))
            return groups.map { (key, files) ->
                val numbered = files.mapIndexed { i, file -> file.copy(index = i + 1) }
                val link = files.first().postUrl
                if (link == null) GalleryPost(key, source, numbered, direct = true)
                else GalleryPost(key, link, numbered)
            }
        }
    }
}
