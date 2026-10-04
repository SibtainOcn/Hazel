package com.hazel.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
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
    val queuedPayload: String? = null,
    /** Where a download that had started was when it failed, as the user reads it. */
    val stoppedAt: String? = null
)

object FailedDownloadRepository {

    /** Failures kept, newest first. Enough to work back through a bad day, no more. */
    internal const val LIMIT = 100

    private val FAILED_KEY = stringPreferencesKey("failed_downloads")

    /**
     * Failures from incognito downloads. Incognito promises that links are not remembered,
     * so these are never written down; they are listed, logged and retried like the rest
     * until the app is closed.
     */
    private val unsaved = MutableStateFlow<List<FailedDownload>>(emptyList())

    fun getFailed(context: Context): Flow<List<FailedDownload>> =
        context.dataStore.data
            .map { prefs -> prefs[FAILED_KEY] }
            .distinctUntilChanged()
            .map { raw ->
                withContext(Dispatchers.Default) {
                    decode(raw)
                }
            }
            .combine(unsaved) { saved, held -> (held + saved).take(LIMIT) }
            .flowOn(Dispatchers.Default)

    suspend fun record(context: Context, entry: FailedDownload) = withContext(Dispatchers.IO) {
        if (SettingsRepository.getIncognito(context).first()) {
            unsaved.update { held -> (listOf(entry) + held.filterNot { same(it.url, entry.url) }).take(LIMIT) }
            return@withContext
        }
        context.dataStore.edit { prefs ->
            val existing = decode(prefs[FAILED_KEY]).filterNot { same(it.url, entry.url) }
            prefs[FAILED_KEY] = encode((listOf(entry) + existing).take(LIMIT))
        }
    }

    suspend fun remove(context: Context, id: Long) = removeAll(context, setOf(id))

    /** Removes every failure in [ids] in one write. */
    suspend fun removeAll(context: Context, ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        unsaved.update { held -> held.filterNot { it.id in ids } }
        context.dataStore.edit { prefs ->
            val remaining = decode(prefs[FAILED_KEY]).filterNot { it.id in ids }
            if (remaining.isEmpty()) prefs.remove(FAILED_KEY)
            else prefs[FAILED_KEY] = encode(remaining)
        }
    }

    suspend fun removeByUrl(context: Context, url: String) = withContext(Dispatchers.IO) {
        unsaved.update { held -> held.filterNot { same(it.url, url) } }
        context.dataStore.edit { prefs ->
            val remaining = decode(prefs[FAILED_KEY]).filterNot { same(it.url, url) }
            if (remaining.isEmpty()) prefs.remove(FAILED_KEY)
            else prefs[FAILED_KEY] = encode(remaining)
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        unsaved.value = emptyList()
        context.dataStore.edit { prefs -> prefs.remove(FAILED_KEY) }
    }

    /**
     * One link written two ways, a shared short link and the address the site reads it as,
     * is one failure: a download that finishes under either clears it.
     */
    private fun same(a: String, b: String) = a == b || com.hazel.android.util.LinkKey.sameMedia(a, b)

    internal fun encode(entries: List<FailedDownload>): String {
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
                    put("stoppedAt", entry.stoppedAt ?: "")
                }
            )
        }
        return array.toString()
    }

    internal fun decode(raw: String?): List<FailedDownload> {
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
                            queuedPayload = obj.optString("queuedPayload").takeIf { it.isNotBlank() },
                            stoppedAt = obj.optString("stoppedAt").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
