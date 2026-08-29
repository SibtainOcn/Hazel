package com.hazel.android.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What was downloaded, as it was at the moment it finished. */
data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val author: String,
    val thumbnail: String?,
    val durationSeconds: Int,
    val fileName: String,
    /** Address the file was saved to, used to open it. Blank when it could not be resolved. */
    val fileUri: String,
    /** Where it landed, for display only. */
    val savedPath: String,
    val isVideo: Boolean,
    val sizeBytes: Long,
    val completedAt: Long
) {
    val site: String
        get() = runCatching {
            java.net.URL(url).host.removePrefix("www.")
        }.getOrNull().orEmpty()
}

/** How the history list is ordered. */
enum class HistorySort(val label: String) {
    NEWEST("Date added"),
    TITLE("Title"),
    SIZE("File size")
}

/** Which kinds of download the list shows. */
enum class HistoryFilter(val label: String) {
    ALL("All"),
    AUDIO("Audio"),
    VIDEO("Video")
}

/**
 * Everything that has finished downloading.
 *
 * Exposed as a flow, so the list updates the moment a download completes rather than when
 * the screen is next opened. Entries are records of what happened and are kept even if the
 * file is later moved or deleted elsewhere on the device; the screen checks whether each
 * file is still there and says so, which is more useful than silently dropping the row.
 */
object DownloadHistoryRepository {

    private const val LIMIT = 500

    private val HISTORY_KEY = stringPreferencesKey("download_history")

    fun getHistory(context: Context): Flow<List<HistoryEntry>> =
        context.dataStore.data.map { prefs -> decode(prefs[HISTORY_KEY]) }

    suspend fun record(context: Context, entry: HistoryEntry) {
        context.dataStore.edit { prefs ->
            val existing = decode(prefs[HISTORY_KEY])
            prefs[HISTORY_KEY] = encode((listOf(entry) + existing).take(LIMIT))
        }
    }

    suspend fun remove(context: Context, id: Long) {
        context.dataStore.edit { prefs ->
            prefs[HISTORY_KEY] = encode(decode(prefs[HISTORY_KEY]).filterNot { it.id == id })
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs -> prefs.remove(HISTORY_KEY) }
    }

    /**
     * Whether the file behind an entry is still on the device.
     *
     * A download can be deleted from a file manager or a gallery app long after it was
     * made, so the record on its own says nothing about what is there now. The address is
     * opened rather than merely inspected, because a MediaStore row can outlive the file
     * it points at.
     */
    suspend fun fileExists(context: Context, entry: HistoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            if (entry.fileUri.isBlank()) return@withContext false
            runCatching {
                context.contentResolver.openFileDescriptor(Uri.parse(entry.fileUri), "r")
                    ?.use { true } ?: false
            }.getOrDefault(false)
        }

    /** Deletes the file an entry points at, and the entry with it. */
    suspend fun deleteFile(context: Context, entry: HistoryEntry): Boolean =
        withContext(Dispatchers.IO) {
            val deleted = runCatching {
                entry.fileUri.isNotBlank() &&
                        context.contentResolver.delete(Uri.parse(entry.fileUri), null, null) > 0
            }.getOrDefault(false)
            remove(context, entry.id)
            deleted
        }

    private fun encode(entries: List<HistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("url", entry.url)
                    put("title", entry.title)
                    put("author", entry.author)
                    put("thumbnail", entry.thumbnail ?: "")
                    put("duration", entry.durationSeconds)
                    put("fileName", entry.fileName)
                    put("fileUri", entry.fileUri)
                    put("savedPath", entry.savedPath)
                    put("isVideo", entry.isVideo)
                    put("size", entry.sizeBytes)
                    put("completedAt", entry.completedAt)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { json ->
                    HistoryEntry(
                        id = json.optLong("id"),
                        url = json.optString("url"),
                        title = json.optString("title"),
                        author = json.optString("author"),
                        thumbnail = json.optString("thumbnail").takeIf { it.isNotBlank() },
                        durationSeconds = json.optInt("duration"),
                        fileName = json.optString("fileName"),
                        fileUri = json.optString("fileUri"),
                        savedPath = json.optString("savedPath"),
                        isVideo = json.optBoolean("isVideo", true),
                        sizeBytes = json.optLong("size"),
                        completedAt = json.optLong("completedAt")
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
