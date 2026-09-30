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

    fun save(context: Context, results: List<MediaInfo>) {
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
            val target = file(context)
            val temp = File(target.parentFile, "$FILE_NAME.tmp")
            temp.writeText(array.toString())
            if (!temp.renameTo(target)) {
                target.writeText(array.toString())
                temp.delete()
            }
        }
    }

    fun load(context: Context): List<MediaInfo> = runCatching {
        val target = file(context)
        if (!target.exists()) return emptyList()
        val array = JSONArray(target.readText())
        (0 until array.length()).mapNotNull { index ->
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
    }.getOrDefault(emptyList())

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** The file itself, for a backup. */
    fun backupFile(context: Context): File = file(context)
}
