package com.hazel.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * One download attempt that failed unexpectedly.
 *
 * Excludes user-initiated cancellations. Kept persistently in DataStore
 * so failures and their error logs can be inspected and retried across sessions.
 */
data class FailedDownload(
    val id: Long = System.currentTimeMillis(),
    val url: String,
    val title: String,
    val author: String,
    val thumbnail: String?,
    val isVideo: Boolean,
    val errorLog: String,
    val failedAt: Long = System.currentTimeMillis(),
    val queuedPayload: String? = null
)

object FailedDownloadRepository {

    private const val LIMIT = 200

    private val FAILED_KEY = stringPreferencesKey("failed_downloads")

    fun getFailed(context: Context): Flow<List<FailedDownload>> =
        context.dataStore.data.map { prefs -> decode(prefs[FAILED_KEY]) }

    suspend fun record(context: Context, entry: FailedDownload) {
        context.dataStore.edit { prefs ->
            val existing = decode(prefs[FAILED_KEY]).filterNot { it.url == entry.url }
            prefs[FAILED_KEY] = encode((listOf(entry) + existing).take(LIMIT))
        }
    }

    suspend fun remove(context: Context, id: Long) {
        context.dataStore.edit { prefs ->
            val remaining = decode(prefs[FAILED_KEY]).filterNot { it.id == id }
            if (remaining.isEmpty()) prefs.remove(FAILED_KEY)
            else prefs[FAILED_KEY] = encode(remaining)
        }
    }

    suspend fun removeByUrl(context: Context, url: String) {
        context.dataStore.edit { prefs ->
            val remaining = decode(prefs[FAILED_KEY]).filterNot { it.url == url }
            if (remaining.isEmpty()) prefs.remove(FAILED_KEY)
            else prefs[FAILED_KEY] = encode(remaining)
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs -> prefs.remove(FAILED_KEY) }
    }

    private fun encode(entries: List<FailedDownload>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("url", entry.url)
                    put("title", entry.title)
                    put("author", entry.author)
                    put("thumbnail", entry.thumbnail ?: "")
                    put("isVideo", entry.isVideo)
                    put("errorLog", entry.errorLog)
                    put("failedAt", entry.failedAt)
                    put("queuedPayload", entry.queuedPayload ?: "")
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<FailedDownload> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        FailedDownload(
                            id = obj.optLong("id", System.currentTimeMillis()),
                            url = obj.optString("url"),
                            title = obj.optString("title"),
                            author = obj.optString("author"),
                            thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() },
                            isVideo = obj.optBoolean("isVideo", true),
                            errorLog = obj.optString("errorLog"),
                            failedAt = obj.optLong("failedAt", System.currentTimeMillis()),
                            queuedPayload = obj.optString("queuedPayload").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
