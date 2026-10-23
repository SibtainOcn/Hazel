package com.hazel.android.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hazel.android.util.LinkKey
import com.hazel.android.util.MediaDeleter
import com.hazel.android.util.MediaPresence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
    val completedAt: Long,
    /**
     * The quality that was asked for, as the sheet named it, with the stream's own codec
     * and bitrate behind it. Read off the download rather than off the file, because a
     * container reports what it holds and not what was chosen.
     */
    val formatLabel: String = "",
    val codec: String = "",
    val bitrateKbps: Double = 0.0,
    /**
     * What was written into the file besides the media: subtitles, chapters, cover art. A
     * comma separated list, empty for a download that carried none of it.
     *
     * Recorded at the time, because none of it can be recovered afterwards without opening
     * the file and reading its streams, and a record that was never written cannot be
     * filled in later. Entries made before this existed simply have nothing to say.
     */
    val embedded: String = ""
) {
    val site: String
        get() = runCatching {
            java.net.URL(url).host.removePrefix("www.")
        }.getOrNull().orEmpty()

    /** The output container, taken off the name it was saved under. */
    val container: String
        get() = fileName.substringAfterLast('.', "").uppercase()
}

/** What a delete did, entry by entry. */
data class DeleteOutcome(
    val deleted: Int,
    val failed: Int,
    val removedIds: Set<Long>,
    val consentIds: Set<Long>,
    val consentUris: List<Uri> = emptyList()
) {
    companion object {
        fun of(results: Map<Long, MediaDeleter.Result>): DeleteOutcome = DeleteOutcome(
            deleted = results.count { it.value == MediaDeleter.Result.DELETED },
            failed = results.count { it.value == MediaDeleter.Result.FAILED },
            removedIds = results.filterValues {
                it == MediaDeleter.Result.DELETED || it == MediaDeleter.Result.GONE
            }.keys,
            consentIds = results.filterValues { it == MediaDeleter.Result.NEEDS_CONSENT }.keys
        )
    }
}

/** How the history list is ordered. */
enum class HistorySort(@param:androidx.annotation.StringRes val labelRes: Int) {
    NEWEST(com.hazel.android.R.string.history_sort_date),
    TITLE(com.hazel.android.R.string.history_sort_title),
    SIZE(com.hazel.android.R.string.history_sort_size)
}

/** Which of the downloads list's files it shows, by whether they are still on the device. */
enum class HistoryStatus(@param:androidx.annotation.StringRes val labelRes: Int) {
    ALL(com.hazel.android.R.string.history_status_all),
    PRESENT(com.hazel.android.R.string.history_status_present),
    DELETED(com.hazel.android.R.string.history_status_deleted)
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
        context.dataStore.data
            .map { prefs -> prefs[HISTORY_KEY] }
            .distinctUntilChanged()
            .map { raw ->
                withContext(Dispatchers.Default) {
                    decode(raw)
                }
            }
            .flowOn(Dispatchers.Default)

    suspend fun record(context: Context, entry: HistoryEntry) = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            val existing = decode(prefs[HISTORY_KEY])
            prefs[HISTORY_KEY] = encode((listOf(withFreeId(entry, existing)) + existing).take(LIMIT))
        }
    }

    suspend fun remove(context: Context, id: Long) = removeAll(context, setOf(id))

    /** Drops several entries in one write, leaving their files where they are. */
    suspend fun removeAll(context: Context, ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        context.dataStore.edit { prefs ->
            val remaining = decode(prefs[HISTORY_KEY]).filterNot { it.id in ids }
            if (remaining.isEmpty()) prefs.remove(HISTORY_KEY)
            else prefs[HISTORY_KEY] = encode(remaining)
        }
    }

    /**
     * The entries that repeat an earlier download: the same media, saved as the same kind.
     * The newest record of each is the one kept.
     */
    fun duplicatesIn(entries: List<HistoryEntry>): Set<Long> =
        entries.sortedByDescending { it.completedAt }
            .groupBy { LinkKey.canonical(it.url) to it.isVideo }
            .values
            .flatMap { group -> group.drop(1) }
            .mapTo(mutableSetOf()) { it.id }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs -> prefs.remove(HISTORY_KEY) }
    }

    /**
     * Whether the file behind an entry is still on the device.
     *
     * A download can be deleted from a file manager or a gallery app long after it was
     * made, so the record on its own says nothing about what is there now. The reading
     * itself lives in [MediaPresence], which is also what the screens ask, so a row on the
     * downloads list and the marker on a link that was fetched again cannot disagree.
     */
    suspend fun fileExists(context: Context, entry: HistoryEntry): Boolean =
        MediaPresence.refresh(context, entry.fileUri)

    /**
     * Deletes the files behind [entries] and drops the records of those that are now gone.
     * A file that could not be deleted keeps its record, so nothing disappears silently.
     * Entries in [knownGone] are already off the device and only lose their record.
     */
    suspend fun deleteFiles(
        context: Context,
        entries: List<HistoryEntry>,
        knownGone: Set<Long> = emptySet()
    ): DeleteOutcome =
        withContext(Dispatchers.IO + NonCancellable) {
            val unique = entries.distinctBy { it.id }
            val outcome = DeleteOutcome.of(unique.associate {
                it.id to if (it.id in knownGone) MediaDeleter.Result.GONE else MediaDeleter.delete(context, it.fileUri)
            })
            removeAll(context, outcome.removedIds)
            MediaPresence.forget()
            outcome.copy(consentUris = unique.filter { it.id in outcome.consentIds }.map { Uri.parse(it.fileUri) })
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
                    put("formatLabel", entry.formatLabel)
                    put("codec", entry.codec)
                    put("bitrate", entry.bitrateKbps)
                    put("embedded", entry.embedded)
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
                        completedAt = json.optLong("completedAt"),
                        formatLabel = json.optString("formatLabel"),
                        codec = json.optString("codec"),
                        bitrateKbps = json.optDouble("bitrate", 0.0),
                        embedded = json.optString("embedded")
                    )
                }
            }.let(::uniqueIds)
        }.getOrDefault(emptyList())
    }

    /** Two downloads can finish in the same millisecond; ids must stay unique so acting on one never touches another. */
    internal fun withFreeId(entry: HistoryEntry, existing: List<HistoryEntry>): HistoryEntry {
        if (existing.none { it.id == entry.id }) return entry
        return entry.copy(id = maxOf(entry.id, existing.maxOf { it.id }) + 1)
    }

    /** Gives a repeated id in older records the next free one, the same way on every read. */
    internal fun uniqueIds(entries: List<HistoryEntry>): List<HistoryEntry> {
        val seen = HashSet<Long>(entries.size)
        if (entries.all { seen.add(it.id) }) return entries
        seen.clear()
        var next = entries.maxOf { it.id } + 1
        return entries.map { entry ->
            if (seen.add(entry.id)) entry
            else entry.copy(id = next++).also { seen.add(it.id) }
        }
    }
}
