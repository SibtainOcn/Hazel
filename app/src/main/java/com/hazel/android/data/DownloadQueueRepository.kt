package com.hazel.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadPlan
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * One link waiting to be downloaded, written down so it outlives the app.
 *
 * This is not the whole of what the sheet knew about the link. A read reports every format
 * a source offers, and keeping all of them for every queued item would write a great deal
 * to disk to answer a question nobody will ask again: by the time an item is queued the
 * choice has been made. What is kept is what the download itself needs, which is the chosen
 * format, the audio track it will be muxed with, and the naming.
 */
data class QueuedDownload(
    val url: String,
    val title: String,
    val author: String,
    val thumbnail: String?,
    val durationSeconds: Int,
    val formatId: String,
    val selector: String,
    val formatLabel: String,
    val ext: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val isGeneric: Boolean,
    val fileSizeBytes: Long,
    /** The audio track a video-only stream is muxed with, when there is one. */
    val mergeAudioSelector: String?,
    val mergeAudioSizeBytes: Long,
    val treeUri: String,
    /** Whether reading this link needed the saved sign-in, which the download needs too. */
    val requiresSignIn: Boolean = false,
    /** The soundtrack chosen for it, for a source that published more than one. */
    val audioLanguage: String? = null,
    /** The settings this link was asked for under, so a later change does not rewrite it. */
    val options: DownloadOptions,
    /**
     * True for a link the user stopped on purpose.
     *
     * Kept on the record, because the part file it already fetched is worth coming back to,
     * but not started again on its own: a pause is a decision, and a launch undoing it
     * would make the button mean nothing.
     */
    val paused: Boolean = false
)

/**
 * The download queue, kept on disk.
 *
 * A queue that lives only in memory is a queue that a swipe off the recents list, a crash or
 * a low-memory kill throws away silently: the user asked for ten downloads, got three, and
 * nothing anywhere says what happened to the other seven. Written down, the run picks up
 * where it left off the next time the app opens.
 *
 * Stored as JSON in the same preferences the rest of the app uses rather than in a database
 * of its own. It is a short list, read once at startup and rewritten when an item finishes,
 * with no queries to answer and nothing to migrate, which is not what a database is for.
 */
object DownloadQueueRepository {

    private val QUEUE_KEY = stringPreferencesKey("download_queue")

    /** Everything still waiting, oldest first. */
    suspend fun load(context: Context): List<QueuedDownload> =
        decode(context.dataStore.data.first()[QUEUE_KEY])

    /** Replaces the stored queue with [items]. */
    suspend fun save(context: Context, items: List<QueuedDownload>) {
        context.dataStore.edit { prefs ->
            if (items.isEmpty()) prefs.remove(QUEUE_KEY)
            else prefs[QUEUE_KEY] = encode(items)
        }
    }

    /** Adds to the end of the queue, ignoring a link already waiting there. */
    suspend fun add(context: Context, items: List<QueuedDownload>) {
        if (items.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = decode(prefs[QUEUE_KEY])
            val known = existing.mapTo(mutableSetOf()) { it.url }
            val merged = existing + items.filterNot { it.url in known }
            prefs[QUEUE_KEY] = encode(merged)
        }
    }

    /** Takes one link out, whether it finished, failed for good, or was cancelled. */
    suspend fun remove(context: Context, url: String) {
        context.dataStore.edit { prefs ->
            val remaining = decode(prefs[QUEUE_KEY]).filterNot { it.url == url }
            if (remaining.isEmpty()) prefs.remove(QUEUE_KEY)
            else prefs[QUEUE_KEY] = encode(remaining)
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs -> prefs.remove(QUEUE_KEY) }
    }

    /** Marks one link as stopped on purpose, or as owed again. */
    suspend fun setPaused(context: Context, url: String, paused: Boolean) {
        context.dataStore.edit { prefs ->
            val updated = decode(prefs[QUEUE_KEY]).map {
                if (it.url == url) it.copy(paused = paused) else it
            }
            if (updated.isEmpty()) prefs.remove(QUEUE_KEY)
            else prefs[QUEUE_KEY] = encode(updated)
        }
    }

    /** Clears the paused mark from everything, for a run being started again. */
    suspend fun clearPaused(context: Context) {
        context.dataStore.edit { prefs ->
            val updated = decode(prefs[QUEUE_KEY]).map { it.copy(paused = false) }
            if (updated.isEmpty()) prefs.remove(QUEUE_KEY)
            else prefs[QUEUE_KEY] = encode(updated)
        }
    }

    private fun encode(items: List<QueuedDownload>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("url", item.url)
                    put("title", item.title)
                    put("author", item.author)
                    put("thumbnail", item.thumbnail ?: JSONObject.NULL)
                    put("duration", item.durationSeconds)
                    put("formatId", item.formatId)
                    put("selector", item.selector)
                    put("formatLabel", item.formatLabel)
                    put("ext", item.ext)
                    put("hasVideo", item.hasVideo)
                    put("hasAudio", item.hasAudio)
                    put("isGeneric", item.isGeneric)
                    put("size", item.fileSizeBytes)
                    put("mergeAudio", item.mergeAudioSelector ?: JSONObject.NULL)
                    put("mergeAudioSize", item.mergeAudioSizeBytes)
                    put("treeUri", item.treeUri)
                    put("requiresSignIn", item.requiresSignIn)
                    put("audioLanguage", item.audioLanguage ?: JSONObject.NULL)
                    put("options", encodeOptions(item.options))
                    put("paused", item.paused)
                }
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<QueuedDownload> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                QueuedDownload(
                    url = url,
                    title = item.optString("title"),
                    author = item.optString("author"),
                    thumbnail = item.optString("thumbnail").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    durationSeconds = item.optInt("duration"),
                    formatId = item.optString("formatId"),
                    selector = item.optString("selector"),
                    formatLabel = item.optString("formatLabel"),
                    ext = item.optString("ext"),
                    hasVideo = item.optBoolean("hasVideo"),
                    hasAudio = item.optBoolean("hasAudio"),
                    isGeneric = item.optBoolean("isGeneric"),
                    fileSizeBytes = item.optLong("size"),
                    mergeAudioSelector = item.optString("mergeAudio").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    mergeAudioSizeBytes = item.optLong("mergeAudioSize"),
                    treeUri = item.optString("treeUri"),
                    requiresSignIn = item.optBoolean("requiresSignIn"),
                    audioLanguage = item.optString("audioLanguage").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    options = decodeOptions(item.optJSONObject("options")),
                    paused = item.optBoolean("paused")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeOptions(options: DownloadOptions) = JSONObject().apply {
        put("videoContainer", options.videoContainer)
        put("audioContainer", options.audioContainer)
        put("embedThumbnail", options.embedThumbnail)
        put("filenameTemplate", options.filenameTemplate)
        put("sponsorBlock", JSONArray(options.sponsorBlockFilters.toList()))
        put("addChapters", options.addChapters)
        put("splitByChapters", options.splitByChapters)
        put("embedSubs", options.embedSubs)
        put("writeSubs", options.writeSubs)
        put("writeAutoSubs", options.writeAutoSubs)
        put("subLanguages", options.subLanguages)
    }

    private fun decodeOptions(json: JSONObject?): DownloadOptions {
        if (json == null) return DownloadOptions()
        val filters = json.optJSONArray("sponsorBlock")
        return DownloadOptions(
            videoContainer = json.optString("videoContainer"),
            audioContainer = json.optString("audioContainer"),
            embedThumbnail = json.optBoolean("embedThumbnail"),
            filenameTemplate = json.optString("filenameTemplate")
                .ifBlank { DownloadOptions.DEFAULT_FILENAME_TEMPLATE },
            sponsorBlockFilters = buildSet {
                if (filters != null) {
                    for (i in 0 until filters.length()) {
                        filters.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            },
            addChapters = json.optBoolean("addChapters", true),
            splitByChapters = json.optBoolean("splitByChapters"),
            embedSubs = json.optBoolean("embedSubs", true),
            writeSubs = json.optBoolean("writeSubs"),
            writeAutoSubs = json.optBoolean("writeAutoSubs"),
            subLanguages = json.optString("subLanguages")
                .ifBlank { DownloadOptions.DEFAULT_SUB_LANGUAGES }
        )
    }
}

/** What the engine needs, rebuilt from what was written down. */
fun QueuedDownload.toPlan(): DownloadPlan {
    val format = MediaFormat(
        formatId = formatId,
        selector = selector,
        label = formatLabel,
        ext = ext,
        vcodec = null,
        acodec = null,
        height = 0,
        fps = 0,
        bitrateKbps = 0.0,
        fileSizeBytes = fileSizeBytes,
        hasVideo = hasVideo,
        hasAudio = hasAudio,
        isGeneric = isGeneric
    )

    // The muxed audio track is put back where the engine looks for it, which is the info's
    // own audio list, so a restored download builds the same command as a fresh one.
    val mergeAudio = mergeAudioSelector?.let {
        MediaFormat(
            formatId = it,
            selector = it,
            label = "",
            // Carries the soundtrack it was chosen for, so looking the track up by
            // language finds this one rather than falling back to the source's default.
            language = audioLanguage,
            ext = "",
            vcodec = null,
            acodec = null,
            height = 0,
            fps = 0,
            bitrateKbps = 0.0,
            fileSizeBytes = mergeAudioSizeBytes,
            hasVideo = false,
            hasAudio = true
        )
    }

    val info = MediaInfo(
        url = url,
        title = title,
        uploader = author,
        thumbnail = thumbnail,
        durationSeconds = durationSeconds,
        videoFormats = if (format.hasVideo) listOf(format) else emptyList(),
        audioFormats = listOfNotNull(
            format.takeIf { !it.hasVideo },
            mergeAudio
        ),
        requiresSignIn = requiresSignIn
    )

    return DownloadPlan(info, format, title, author, audioLanguage)
}

/** What to write down for a link about to be queued. */
fun DownloadPlan.toQueued(options: DownloadOptions, treeUri: String): QueuedDownload {
    // The track for the soundtrack that was chosen, not whichever the source listed
    // first. A queued item is rebuilt from these fields alone, so a language recorded
    // here and a track recorded from somewhere else would disagree at download time.
    val mergeAudio =
        if (format.hasVideo && !format.hasAudio) info.mergeAudioFor(audioLanguage) else null
    return QueuedDownload(
        url = info.url,
        title = title,
        author = author,
        thumbnail = info.thumbnail,
        durationSeconds = info.durationSeconds,
        formatId = format.formatId,
        selector = format.selector,
        formatLabel = format.label,
        ext = format.ext,
        hasVideo = format.hasVideo,
        hasAudio = format.hasAudio,
        isGeneric = format.isGeneric,
        fileSizeBytes = format.fileSizeBytes,
        mergeAudioSelector = mergeAudio?.selector,
        mergeAudioSizeBytes = mergeAudio?.fileSizeBytes ?: 0L,
        treeUri = treeUri,
        requiresSignIn = info.requiresSignIn,
        audioLanguage = audioLanguage,
        options = options
    )
}
