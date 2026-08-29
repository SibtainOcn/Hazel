package com.hazel.android.download

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Reads metadata and the format list for a single URL.
 *
 * Runs `yt-dlp --dump-single-json` with the same fixed option set the downloader uses, and
 * nothing else. Extractor-specific arguments are deliberately avoided: they are what make
 * some clients fail on sources such as Instagram, while the plain dump works across every
 * extractor yt-dlp supports.
 *
 * The JSON is parsed here rather than through the library's own mapper, whose model exposes
 * immutable fields that the default Jackson configuration cannot populate.
 *
 * Only the URL itself is guaranteed to come back. YouTube fills in every field, but many
 * extractors return far less: Instagram commonly omits the title, uploader, duration, codec
 * names, and per-format sizes, and may describe the media as a single direct stream with no
 * `formats` array at all. Every field below therefore degrades instead of failing, and a
 * format is only discarded when it is provably not downloadable.
 */
object MediaProbe {

    /**
     * @param cacheDir persistent yt-dlp cache, shared with the download request so the
     *   player data fetched here is reused instead of being resolved a second time.
     * @param cookieFile saved sign-ins to send with the request, or null for none. Without
     *   it a source that requires an account reports the media as unavailable.
     */
    suspend fun probe(
        url: String,
        cacheDir: File,
        cookieFile: File? = null,
        fetchMode: FetchMode = FetchMode.DEFAULT,
        forceIpv4: Boolean = false,
        processId: String = PROBE_PROCESS_ID
    ): MediaInfo = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            applySharedOptions(cacheDir, cookieFile, fetchMode, forceIpv4)
            addOption("--dump-single-json")
        }

        activeProcessIds.add(processId)
        try {
            val response = YoutubeDL.getInstance().execute(request, processId, null)
            val payload = response.out.trim().takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No metadata returned")

            parse(url, JSONObject(payload))
        } finally {
            activeProcessIds.remove(processId)
        }
    }

    /**
     * Reads several links, grouping them by the site they come from.
     *
     * One yt-dlp run works through its URL list one entry at a time, so handing every link
     * to a single run would read them in sequence no matter how many there are. Links are
     * therefore grouped by host and each group gets its own run:
     *
     *  - links from the same site share a run, so the extractor is warmed up once and one
     *    set of cookies and cache entries serves all of them;
     *  - different sites run at the same time, so a slow site cannot hold up a fast one.
     *
     * This adapts on its own to whatever was pasted. A set of links from one site behaves
     * like a single efficient batch; a mixed set fans out across sites. [MAX_PARALLEL_HOSTS]
     * caps how many run at once so a long, varied list cannot start a process per link.
     *
     * A link that cannot be read produces no output line and is simply absent from the
     * result, leaving the rest of the set intact. Results come back in the order the links
     * were given.
     */
    suspend fun probeAll(
        urls: List<String>,
        cacheDir: File,
        cookieFile: File? = null,
        fetchMode: FetchMode = FetchMode.DEFAULT,
        forceIpv4: Boolean = false
    ): List<MediaInfo> = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) return@withContext emptyList()
        if (urls.size == 1) {
            return@withContext listOf(
                probe(urls.first(), cacheDir, cookieFile, fetchMode, forceIpv4)
            )
        }

        // Links are grouped by site, then each group is split into small runs. One run
        // extracts its links one after another, so a group left whole would read in
        // sequence however many links it held; splitting it lets a site's links overlap
        // while still sharing an extractor warm-up within each run.
        val runs = urls
            .groupBy { hostOf(it) }
            .flatMap { (host, groupUrls) ->
                groupUrls.chunked(URLS_PER_RUN).mapIndexed { index, chunk ->
                    "${host}_$index" to chunk
                }
            }

        val gate = Semaphore(MAX_PARALLEL_RUNS)

        val resolved = coroutineScope {
            runs.map { (key, runUrls) ->
                async {
                    gate.withPermit {
                        runCatching {
                            probeGroup(
                                key, runUrls, cacheDir, cookieFile, fetchMode, forceIpv4
                            )
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }

        // Back into the order the links were given, which is the order the cards appear in.
        val byUrl = resolved.associateBy { it.url }
        val ordered = urls.mapNotNull { requested ->
            byUrl[requested] ?: resolved.firstOrNull { it.url.contains(requested) }
        }
        ordered.ifEmpty { resolved }
    }

    /** One yt-dlp run covering a handful of links from the same site. */
    private fun probeGroup(
        key: String,
        urls: List<String>,
        cacheDir: File,
        cookieFile: File?,
        fetchMode: FetchMode,
        forceIpv4: Boolean
    ): List<MediaInfo> {
        val request = YoutubeDLRequest(urls).apply {
            applySharedOptions(cacheDir, cookieFile, fetchMode, forceIpv4)
            // One JSON object per link, rather than one object wrapping them all.
            addOption("--dump-json")
            // One unreadable link in the group must not abandon the others.
            addOption("--ignore-errors")
        }

        val processId = BATCH_PROCESS_ID + "_" + key
        activeProcessIds.add(processId)
        val response = try {
            YoutubeDL.getInstance().execute(request, processId, null)
        } finally {
            activeProcessIds.remove(processId)
        }

        // Results are matched back to the requested links by address, because a redirect
        // can change what the payload reports.
        return response.out
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("{") }
            .mapNotNull { line ->
                runCatching {
                    val json = JSONObject(line)
                    val reported = firstNonBlank(
                        json.optString("webpage_url"),
                        json.optString("original_url")
                    )
                    val requested = urls.firstOrNull { it == reported }
                        ?: urls.firstOrNull { reported.isNotBlank() && reported.contains(it) }
                        ?: reported.ifBlank { urls.first() }
                    parse(requested, json)
                }.getOrNull()
            }
            .toList()
    }

    /**
     * The site a link belongs to, used only to decide what shares a run. An address that
     * cannot be parsed becomes its own group, so a malformed entry cannot drag a working
     * group down with it.
     */
    private fun hostOf(url: String): String = runCatching {
        java.net.URL(url).host.removePrefix("www.").lowercase()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

    /**
     * Kills every in-flight probe.
     *
     * Several links are read at once, each in its own yt-dlp process, so the ids in flight
     * are tracked rather than assumed. Killing by a single fixed id would leave the others
     * running after the user had moved on.
     */
    fun cancel() {
        activeProcessIds.toList().forEach { id ->
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (_: Exception) { /* nothing running under that id */ }
        }
        activeProcessIds.clear()
    }

    /** Options every metadata read uses, whether it covers one link or many. */
    private fun YoutubeDLRequest.applySharedOptions(
        cacheDir: File,
        cookieFile: File?,
        fetchMode: FetchMode,
        forceIpv4: Boolean
    ) {
        addOption("--no-playlist")
        addOption("--no-warnings")
        addOption("--no-check-certificates")
        addOption("--cache-dir", cacheDir.absolutePath)

        // Metadata is almost entirely network waiting. yt-dlp defaults to a twenty second
        // socket timeout and ten retries, so a single stalled connection can add tens of
        // seconds before anything is shown. These bounds keep a slow source from holding
        // up the whole read.
        addOption("--socket-timeout", fetchMode.socketTimeoutSeconds.toString())
        addOption("-R", fetchMode.retries.toString())

        if (forceIpv4) addOption("--force-ipv4")
        cookieFile?.let { addOption("--cookies", it.absolutePath) }
    }

    const val PROBE_PROCESS_ID = "hazel_probe"

    private const val BATCH_PROCESS_ID = "hazel_probe_batch"

    /** How many reads run at the same time. */
    private const val MAX_PARALLEL_RUNS = 4

    /**
     * Links per run. Small enough that a set overlaps rather than queueing behind itself,
     * large enough that neighbouring links still share one extractor warm-up.
     */
    private const val URLS_PER_RUN = 2

    private val activeProcessIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    /**
     * Rows that do not name a format id and let yt-dlp resolve the stream itself. They head
     * both tabs, so "best" is always the first option, and they are the reason a download can
     * still go ahead on a source that reports no usable format list.
     */
    private val BEST_VIDEO = MediaFormat(
        formatId = "best",
        selector = "bv*+ba/b",
        label = "Best available",
        ext = "",
        vcodec = null,
        acodec = null,
        height = 0,
        fps = 0,
        bitrateKbps = 0.0,
        fileSizeBytes = 0L,
        hasVideo = true,
        hasAudio = true,
        isGeneric = true
    )

    private val BEST_AUDIO = MediaFormat(
        formatId = "bestaudio",
        selector = "ba/b",
        label = "BEST AUDIO",
        ext = "",
        vcodec = null,
        acodec = null,
        height = 0,
        fps = 0,
        bitrateKbps = 0.0,
        fileSizeBytes = 0L,
        hasVideo = false,
        hasAudio = true,
        isGeneric = true
    )

    /**
     * Stand-in used when metadata could not be read at all. yt-dlp can often still download a
     * URL whose metadata dump fails, so the sheet is offered with the generic rows only.
     */
    fun fallbackFor(url: String) = MediaInfo(
        url = url,
        title = url.substringAfter("://").substringBefore("?").trimEnd('/').ifBlank { url },
        uploader = "",
        thumbnail = null,
        durationSeconds = 0,
        videoFormats = listOf(BEST_VIDEO),
        audioFormats = listOf(BEST_AUDIO)
    )

    private fun parse(url: String, root: JSONObject): MediaInfo {
        // Carousels and multi-part posts come back as a playlist wrapper even with
        // --no-playlist. Fall through to the first entry that actually carries media.
        val media = root.optJSONArray("entries")
            ?.let { entries ->
                (0 until entries.length())
                    .mapNotNull { entries.optJSONObject(it) }
                    .firstOrNull { it.has("formats") || it.has("url") }
            }
            ?: root

        // Resolved before the formats, because a format with no reported size can only
        // be estimated from its bitrate and the running time.
        val duration = resolveDuration(media).takeIf { it > 0 } ?: resolveDuration(root)

        val parsed = readFormats(media, duration).distinctBy { it.formatId }

        // Video entries lead with the highest resolution, then the smoothest frame rate,
        // then the richest bitrate, so the best option is always the first row.
        val video = parsed
            .filter { it.hasVideo }
            .sortedWith(
                compareByDescending<MediaFormat> { it.height }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.bitrateKbps }
                    .thenByDescending { it.fileSizeBytes }
            )

        val audio = parsed
            .filter { it.hasAudio && !it.hasVideo }
            .sortedWith(
                compareByDescending<MediaFormat> { it.bitrateKbps }
                    .thenByDescending { it.fileSizeBytes }
            )

        return MediaInfo(
            url = firstNonBlank(media.optString("webpage_url"), root.optString("webpage_url"), url),
            title = resolveTitle(media, root),
            uploader = firstNonBlank(
                media.optString("uploader"),
                media.optString("channel"),
                media.optString("creator"),
                media.optString("uploader_id"),
                root.optString("uploader"),
                root.optString("channel")
            ),
            thumbnail = resolveThumbnail(media) ?: resolveThumbnail(root),
            durationSeconds = duration,
            // "Best" always heads each tab, whatever the source did or did not report.
            videoFormats = listOf(BEST_VIDEO) + video,
            audioFormats = listOf(BEST_AUDIO) + audio
        )
    }

    /**
     * Reads the `formats` array, or synthesises a single entry from the top-level fields
     * when the extractor describes the media as one direct stream (common on Instagram).
     */
    private fun readFormats(media: JSONObject, durationSeconds: Int): List<MediaFormat> {
        val array = media.optJSONArray("formats") ?: JSONArray()
        val formats = buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { toFormat(it, durationSeconds) }?.let { add(it) }
            }
        }
        if (formats.isNotEmpty()) return formats

        // No usable format list. If the payload still points at a stream, offer that.
        return listOfNotNull(toFormat(media, durationSeconds, fallbackId = "0"))
    }

    private fun toFormat(
        json: JSONObject,
        durationSeconds: Int,
        fallbackId: String? = null
    ): MediaFormat? {
        val id = json.optString("format_id").takeIf { it.isNotBlank() && it != "null" }
            ?: fallbackId
            ?: return null

        val note = json.optString("format_note").takeIf { it.isNotBlank() && it != "null" }

        // Image strips are never downloadable media.
        val ext = json.optString("ext").takeIf { it.isNotBlank() && it != "null" }
        if (ext == "mhtml") return null
        if (note?.contains("storyboard", ignoreCase = true) == true) return null

        // A format with no stream behind it cannot be handed to yt-dlp.
        if (fallbackId != null && json.optString("url").isBlank()) return null

        // "none" means the stream is absent; a missing key means the extractor simply did
        // not report a codec, which is not the same thing.
        val vcodec = json.readCodec("vcodec")
        val acodec = json.readCodec("acodec")
        val hasVideoKey = json.has("vcodec")
        val hasAudioKey = json.has("acodec")

        val height = json.optInt("height", 0)
        val fps = json.optInt("fps", 0)

        // When neither codec was reported, assume a combined stream rather than dropping a
        // format the source can very likely download.
        val codecsUnknown = !hasVideoKey && !hasAudioKey
        val hasVideo = when {
            vcodec != null -> true
            hasVideoKey -> false
            codecsUnknown -> height > 0 || fps > 0 || ext.isVideoContainer() || fallbackId != null
            else -> false
        }
        val hasAudio = when {
            acodec != null -> true
            hasAudioKey -> false
            codecsUnknown -> !hasVideo || ext.isVideoContainer() || fallbackId != null
            else -> false
        }

        if (!hasVideo && !hasAudio) return null

        val bitrate = json.optPositiveDouble("tbr")
            ?: json.optPositiveDouble("abr")
            ?: json.optPositiveDouble("vbr")
            ?: 0.0

        // An exact size when the source reports one. Streaming sites usually do not for
        // adaptive formats, so the fallback is the bitrate over the running time. That is
        // an upper bound rather than a measurement: the bitrate a site advertises is what
        // the stream peaks at, and content that compresses well finishes well under it.
        // The distinction is carried through so the sheet can mark it as approximate
        // rather than quoting a figure the finished file will not match.
        val exactSize = json.optPositiveLong("filesize")
        val reportedApprox = json.optPositiveLong("filesize_approx")
        val estimatedSize = if (bitrate > 0 && durationSeconds > 0) {
            (bitrate * 1000.0 / 8.0 * durationSeconds).toLong()
        } else {
            0L
        }

        return MediaFormat(
            formatId = id,
            selector = id,
            label = buildLabel(json, note, height, fps, hasVideo, id),
            ext = ext ?: "",
            vcodec = vcodec,
            acodec = acodec,
            height = height,
            fps = fps,
            bitrateKbps = bitrate,
            fileSizeBytes = exactSize ?: reportedApprox ?: estimatedSize,
            isEstimatedSize = exactSize == null,
            hasVideo = hasVideo,
            hasAudio = hasAudio
        )
    }

    /**
     * Builds the row's headline. Falls back through resolution, the extractor's note, and
     * finally the format id, so a row is never blank no matter how sparse the payload is.
     */
    private fun buildLabel(
        json: JSONObject,
        note: String?,
        height: Int,
        fps: Int,
        hasVideo: Boolean,
        id: String
    ): String {
        if (!hasVideo) {
            return (note ?: json.optPositiveDouble("abr")?.let { "%.0f kbps".format(it) } ?: "Audio")
                .uppercase()
        }
        val resolution = json.optString("resolution").takeIf { it.isNotBlank() && it != "null" }
        val base = when {
            height > 0 -> "${height}p"
            !note.isNullOrBlank() -> note
            resolution != null -> resolution
            else -> "Format $id"
        }
        return if (height > 0 && fps > 30) "$base$fps" else base
    }

    /** Instagram posts have no title; the caption or the post id stands in for one. */
    private fun resolveTitle(media: JSONObject, root: JSONObject): String {
        val direct = firstNonBlank(media.optString("title"), root.optString("title"))
        if (direct.isNotBlank()) return direct

        val caption = firstNonBlank(media.optString("description"), root.optString("description"))
        if (caption.isNotBlank()) {
            return caption.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120)
                ?: "Untitled"
        }

        val id = firstNonBlank(media.optString("id"), root.optString("id"))
        return if (id.isNotBlank()) id else "Untitled"
    }

    /** Uses the top-level thumbnail, else the largest entry in the thumbnails array. */
    private fun resolveThumbnail(json: JSONObject): String? {
        json.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" }?.let { return it }

        val thumbs = json.optJSONArray("thumbnails") ?: return null
        return (0 until thumbs.length())
            .mapNotNull { thumbs.optJSONObject(it) }
            .maxByOrNull { it.optInt("preference", 0) * 100_000 + it.optInt("width", 0) }
            ?.optString("url")
            ?.takeIf { it.isNotBlank() }
    }

    /** Duration arrives as a number, a string, or not at all. */
    private fun resolveDuration(json: JSONObject): Int {
        json.optPositiveDouble("duration")?.let { return it.toInt() }
        return json.optString("duration_string")
            .split(":")
            .mapNotNull { it.trim().toIntOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.fold(0) { acc, part -> acc * 60 + part }
            ?: 0
    }

    private fun JSONObject.readCodec(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "none" && it != "null" }

    private fun JSONObject.optPositiveDouble(key: String): Double? =
        optDouble(key, 0.0).takeIf { !it.isNaN() && it > 0.0 }

    private fun JSONObject.optPositiveLong(key: String): Long? =
        optLong(key, 0L).takeIf { it > 0L }

    private fun String?.isVideoContainer(): Boolean =
        this != null && this in setOf("mp4", "webm", "mkv", "mov", "flv", "avi", "3gp", "ts")

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() && it != "null" } ?: ""
}
