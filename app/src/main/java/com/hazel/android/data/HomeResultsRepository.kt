package com.hazel.android.data

import android.content.Context
import com.hazel.android.download.InfoCache
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.MediaProbe
import com.hazel.android.download.extractor.LinkEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The home screen's cards, kept across restarts until a new read replaces them or they are
 * cleared. Only what a card shows is stored: formats expire, so a card is rebuilt from
 * [InfoCache] when possible and otherwise reads its link again when opened.
 */
object HomeResultsRepository {

    private const val FILE_NAME = "home_results.json"

    private const val MAX_CARDS = 500

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** The cards and, when they came from a keyword search, its words. */
    data class Saved(val results: List<MediaInfo>, val searchQuery: String)

    /**
     * Keeps [results], with the [searchQuery] that found them, so after a restart they are
     * still known as a search: a search's cards get no Download all, a set's do.
     */
    fun save(context: Context, results: List<MediaInfo>, searchQuery: String = "") {
        runCatching {
            if (results.isEmpty()) {
                file(context).delete()
                return
            }
            val array = JSONArray()
            results.take(MAX_CARDS).forEach { info ->
                array.put(JSONObject().apply {
                    put("url", info.url)
                    put("title", info.title)
                    put("uploader", info.uploader)
                    put("thumbnail", info.thumbnail ?: "")
                    put("duration", info.durationSeconds)
                })
            }
            val text = JSONObject()
                .put(KEY_QUERY, searchQuery)
                .put(KEY_RESULTS, array)
                .toString()
            val target = file(context)
            val temp = File(target.parentFile, "$FILE_NAME.tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.writeText(text)
                temp.delete()
            }
        }
    }

    fun load(context: Context): Saved = runCatching {
        val target = file(context)
        if (!target.exists()) return Saved(emptyList(), "")
        parse(target.readText())
    }.getOrDefault(Saved(emptyList(), ""))

    /** Reads either form: an object with the search words, or the bare list kept before them. */
    internal fun parse(text: String): Saved {
        val trimmed = text.trimStart()
        val root = if (trimmed.startsWith("{")) JSONObject(trimmed) else null
        val array = root?.optJSONArray(KEY_RESULTS) ?: if (root == null) JSONArray(trimmed) else JSONArray()
        val results = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            InfoCache.metadataFor(url) ?: MediaProbe.pendingFor(
                LinkEntry(
                    url = url,
                    title = item.optString("title").ifBlank { url },
                    uploader = item.optString("uploader"),
                    thumbnail = item.optString("thumbnail").takeIf { it.isNotBlank() },
                    durationSeconds = item.optInt("duration")
                )
            )
        }
        // Words with no cards to go with them mean nothing.
        val query = if (results.isEmpty()) "" else root?.optString(KEY_QUERY).orEmpty()
        return Saved(results, query)
    }

    private const val KEY_QUERY = "searchQuery"
    private const val KEY_RESULTS = "results"

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** The file itself, for a backup. */
    fun backupFile(context: Context): File = file(context)
}
