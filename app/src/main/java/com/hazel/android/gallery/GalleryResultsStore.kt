package com.hazel.android.gallery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The Gallery tab's last results, kept until cleared, as Home keeps its cards. Never written
 * in incognito; turning incognito on removes the file.
 */
object GalleryResultsStore {

    private const val FILE_NAME = "gallery_results.json"
    private const val MAX_POSTS = 200

    data class Saved(val query: String, val fromSearch: Boolean, val posts: List<GalleryPost>)

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun save(context: Context, saved: Saved) {
        runCatching {
            if (saved.posts.isEmpty()) {
                clear(context)
                return
            }
            val target = file(context)
            val temp = File(target.parentFile, "$FILE_NAME.tmp")
            temp.writeText(encode(saved))
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    fun load(context: Context): Saved? = runCatching {
        file(context).takeIf { it.exists() }?.readText()?.let(::decode)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    internal fun encode(saved: Saved): String {
        val posts = JSONArray()
        saved.posts.take(MAX_POSTS).forEach { post ->
            val items = JSONArray()
            post.items.forEach { item ->
                items.put(
                    JSONObject()
                        .put("index", item.index).put("url", item.url).put("extension", item.extension)
                        .put("title", item.title).put("author", item.author).put("site", item.site)
                        .put("width", item.width).put("height", item.height)
                        .put("thumbnail", item.thumbnail.orEmpty()).put("postKey", item.postKey)
                        .put("postUrl", item.postUrl.orEmpty()).put("caption", item.caption)
                        .put("playUrl", item.playUrl.orEmpty())
                )
            }
            posts.put(
                JSONObject().put("key", post.key).put("source", post.source)
                    .put("direct", post.direct).put("items", items)
                    .put("section", post.section?.name.orEmpty())
            )
        }
        return JSONObject().put("query", saved.query).put("fromSearch", saved.fromSearch)
            .put("posts", posts).toString()
    }

    internal fun decode(text: String): Saved? {
        val root = JSONObject(text)
        val array = root.optJSONArray("posts") ?: return null
        val posts = (0 until array.length()).mapNotNull { i ->
            val post = array.optJSONObject(i) ?: return@mapNotNull null
            val list = post.optJSONArray("items") ?: return@mapNotNull null
            val items = (0 until list.length()).mapNotNull { j ->
                val o = list.optJSONObject(j) ?: return@mapNotNull null
                val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                GalleryItem(
                    index = o.optInt("index"), url = url, extension = o.optString("extension"),
                    title = o.optString("title"), author = o.optString("author"), site = o.optString("site"),
                    width = o.optInt("width"), height = o.optInt("height"),
                    thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
                    postKey = o.optString("postKey"),
                    postUrl = o.optString("postUrl").takeIf { it.isNotBlank() },
                    caption = o.optString("caption"),
                    playUrl = o.optString("playUrl").takeIf { it.startsWith("http") }
                )
            }
            if (items.isEmpty()) null
            else GalleryPost(
                post.optString("key"), post.optString("source"), items, post.optBoolean("direct"),
                section = ProfileSection.entries.firstOrNull { it.name == post.optString("section") }
            )
        }
        if (posts.isEmpty()) return null
        return Saved(root.optString("query"), root.optBoolean("fromSearch"), posts)
    }
}
