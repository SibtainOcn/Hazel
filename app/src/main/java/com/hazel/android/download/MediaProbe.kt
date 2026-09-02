package com.hazel.android.download

import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.download.extractor.LinkContents
import com.hazel.android.download.extractor.LinkEntry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
     * @param access saved sign-ins to read as, or [SiteAccess.NONE]. Without them a source
     *   that requires an account reports the media as unavailable.
     */
    suspend fun probe(
        url: String,
        cacheDir: File,
        access: SiteAccess = SiteAccess.NONE,
        fetchMode: FetchMode = FetchMode.DEFAULT,
        forceIpv4: Boolean = false,
        processId: String = PROBE_PROCESS_ID
    ): MediaInfo = withContext(Dispatchers.IO) {
        // Some sites answer a signed-in request with far less than they answer an
        // anonymous one: the largest of them now serves a signed-in session a single
        // 360p stream unless the request carries a token the app cannot produce. So the
        // sign-in is held back until the media turns out to need it, and the ordinary
        // link keeps the full ladder it has always had.
        val attempts = signInAttempts(url, access)

        attempts.forEachIndexed { index, attempt ->
            try {
                return@withContext readOne(
                    url, cacheDir, attempt, fetchMode, forceIpv4, processId,
                    // True when this is the fallback, which only runs because the
                    // anonymous read was refused.
                    signedIn = index > 0
                )
            } catch (e: Exception) {
                val last = index == attempts.lastIndex
                if (last || e is CancellationException || !isSignInRefusal(e.message)) throw e
            }
        }

        error("No metadata returned")
    }

    /** One read, at one level of access. */
    private suspend fun readOne(
        url: String,
        cacheDir: File,
        access: SiteAccess,
        fetchMode: FetchMode,
        forceIpv4: Boolean,
        processId: String,
        signedIn: Boolean
    ): MediaInfo {
        val request = YoutubeDLRequest(url).apply {
            applySharedOptions(url, cacheDir, access, fetchMode, forceIpv4)
            addOption("--dump-single-json")
        }

        activeProcessIds.add(processId)
        try {
            val response = YoutubeDL.getInstance().execute(request, processId, null)
            val payload = response.out.trim().takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No metadata returned")

            val parsed = parse(url, JSONObject(payload)).copy(requiresSignIn = signedIn)

            // Kept so a repeat of this link needs no read, and so the download can replay
            // the payload instead of extracting the same thing over again.
            InfoCache.put(url, parsed, payload)
            return parsed
        } finally {
            activeProcessIds.remove(processId)
        }
    }

    /**
     * The reads to try, in order.
     *
     * One for almost everything: a link with no sign-in to offer, and a site that answers
     * a signed-in request as fully as an anonymous one, both have a single way of being
     * read. The two-step only exists for the sites that hold formats back from a signed-in
     * request, and there the second step is what still reaches private, members-only and
     * age-restricted media.
     */
    private fun signInAttempts(url: String, access: SiteAccess): List<SiteAccess> = when {
        !access.hasCookies -> listOf(SiteAccess.NONE)
        cookiesNarrowTheFormats(url) -> listOf(SiteAccess.NONE, access)
        else -> listOf(access)
    }

    /** Whether a failure reads as the site asking who is calling. */
    private fun isSignInRefusal(message: String?): Boolean {
        val text = message?.lowercase() ?: return false
        return SIGN_IN_REFUSALS.any { it in text }
    }

    private val SIGN_IN_REFUSALS = listOf(
        "sign in", "log in", "login", "private video", "members-only", "members only",
        "age-restricted", "age restricted", "confirm your age", "not a bot",
        "this video is unavailable", "video unavailable", "account"
    )

    /**
     * Finds out what a link actually holds: one item, or a collection of them.
     *
     * The question is put to yt-dlp rather than answered by matching the address, because
     * the address is not reliable evidence. A playlist, a channel, an album, a watch-later
     * page and a multi-part post look nothing like each other, and a source nobody thought
     * about looks like none of them. `_type` in the reply is the extractor's own answer, so
     * anything it learns to handle is handled here without a change.
     *
     * `--flat-playlist` stops it opening each entry of a collection, which is what keeps the
     * cost of listing three hundred videos the same as listing three. It does not affect a
     * link that turns out to hold one item, so that case comes back fully resolved from this
     * single read and needs no second one.
     */
    suspend fun listContents(
        url: String,
        cacheDir: File,
        access: SiteAccess = SiteAccess.NONE,
        fetchMode: FetchMode = FetchMode.DEFAULT,
        forceIpv4: Boolean = false,
        processId: String = PROBE_PROCESS_ID
    ): LinkContents = withContext(Dispatchers.IO) {
        // The same two-step as a single read, and for the same reason: a public list is
        // read anonymously, and the sign-in is kept for the one that will not open
        // without it.
        val attempts = signInAttempts(url, access)

        attempts.forEachIndexed { index, attempt ->
            try {
                return@withContext listOne(
                    url, cacheDir, attempt, fetchMode, forceIpv4, processId,
                    signedIn = index > 0
                )
            } catch (e: Exception) {
                val last = index == attempts.lastIndex
                if (last || e is CancellationException || !isSignInRefusal(e.message)) throw e
            }
        }

        error("No metadata returned")
    }

    /** One listing, at one level of access. */
    private suspend fun listOne(
        url: String,
        cacheDir: File,
        access: SiteAccess,
        fetchMode: FetchMode,
        forceIpv4: Boolean,
        processId: String,
        signedIn: Boolean
    ): LinkContents {
        val request = YoutubeDLRequest(url).apply {
            applySharedOptions(url, cacheDir, access, fetchMode, forceIpv4, singleItem = false)
            addOption("--flat-playlist")
            // Entries are emitted as they are found rather than after the whole collection
            // has been walked, which is what stops a long playlist stalling on its own tail.
            addOption("--lazy-playlist")
            addOption("--dump-single-json")
        }

        activeProcessIds.add(processId)
        val payload = try {
            YoutubeDL.getInstance().execute(request, processId, null).out.trim()
                .takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("No metadata returned")
        } finally {
            activeProcessIds.remove(processId)
        }

        val root = JSONObject(payload)
        val entries = root.optJSONArray("entries")
        val isCollection = root.optString("_type") == "playlist" && entries != null

        if (!isCollection) {
            val info = parse(url, root).copy(requiresSignIn = signedIn)
            InfoCache.put(url, info, payload)
            return LinkContents.Single(info)
        }

        val listed = buildList {
            for (index in 0 until entries!!.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                toEntry(entry)?.let { add(it) }
            }
        }

        // A wrapper holding exactly one entry is a single item wearing a collection's
        // clothes, which is how several sites describe an ordinary post. Reading it as a
        // list would put one card behind a set of links it does not belong to.
        if (listed.size == 1) {
            val only = listed.first()
            val resolved = runCatching {
                probe(only.url, cacheDir, access, fetchMode, forceIpv4, processId)
            }.getOrNull()
            if (resolved != null) return LinkContents.Single(resolved)
        }

        val many = LinkContents.Many(
            title = firstNonBlank(root.optString("title"), root.optString("playlist_title")),
            entries = listed
        )
        // Kept so the same collection pasted again opens as the set of cards it opened as
        // last time, without walking it a second time.
        InfoCache.putListing(url, many)
        return many
    }

    /** Reads one entry of a flat listing, which carries no formats by design. */
    private fun toEntry(json: JSONObject): LinkEntry? {
        val address = firstNonBlank(
            json.optString("webpage_url"),
            json.optString("url"),
            json.optString("original_url")
        ).takeIf { it.isNotBlank() && it.startsWith("http") } ?: return null

        val title = firstNonBlank(json.optString("title"), json.optString("alt_title"))
        // Entries a source has withdrawn still occupy a slot in the listing. They cannot be
        // downloaded, so they are left out rather than shown as cards that will fail.
        if (title in UNAVAILABLE_TITLES) return null

        return LinkEntry(
            url = address,
            title = title.ifBlank { address },
            uploader = firstNonBlank(
                json.optString("uploader"),
                json.optString("channel"),
                json.optString("uploader_id")
            ),
            thumbnail = resolveThumbnail(json),
            durationSeconds = resolveDuration(json)
        )
    }

    /**
     * A card for an entry whose formats have not been read yet.
     *
     * It carries only the generic rows, so [MediaInfo.hasResolvedFormats] reports false and
     * the sheet knows to resolve this item before showing a quality to choose.
     */
    fun pendingFor(entry: LinkEntry) = MediaInfo(
        url = entry.url,
        title = entry.title,
        uploader = entry.uploader,
        thumbnail = entry.thumbnail,
        durationSeconds = entry.durationSeconds,
        videoFormats = listOf(BEST_VIDEO),
        audioFormats = listOf(BEST_AUDIO)
    )

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
        url: String,
        cacheDir: File,
        access: SiteAccess,
        fetchMode: FetchMode,
        forceIpv4: Boolean,
        singleItem: Boolean = true
    ) {
        // Left off when the point of the read is to find out whether the link holds one
        // item or several, because that is exactly the question this option answers in
        // advance. Everywhere else the caller already knows it wants one item.
        if (singleItem) addOption("--no-playlist")
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

        // The sign-in, and everything that has to travel with it for the site to honour it.
        applySiteAccess(access, url)
    }

    const val PROBE_PROCESS_ID = "hazel_probe"

    /** Titles a source gives an entry that is no longer there. */
    private val UNAVAILABLE_TITLES = setOf(
        "[Private video]", "[Deleted video]", "[Unavailable video]", "[Removed video]"
    )




    private val activeProcessIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )

    /**
     * Rows that do not name a format id and let yt-dlp resolve the stream itself. They are
     * what a tab holds when the source reported no usable format list, and the reason a
     * download can still go ahead there.
     */
    private val BEST_VIDEO get() = MediaFormat(
        formatId = "best",
        selector = "bv*+ba/b",
        label = HazelApp.instance.getString(R.string.format_best_quality),
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

    private val BEST_AUDIO get() = MediaFormat(
        formatId = "bestaudio",
        selector = "ba/b",
        label = HazelApp.instance.getString(R.string.format_best_audio),
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

    /**
     * Internal rather than private so the unit tests can feed it saved engine payloads.
     * Nothing outside this module calls it.
     */
    internal fun parse(url: String, root: JSONObject): MediaInfo {
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
            // The generic row stands in only where the source named nothing concrete. A
            // list that already holds real formats does not need it: it says nothing the
            // first real entry does not, and it cannot show the size, codec and
            // resolution that entry can.
            videoFormats = video.ifEmpty { listOf(BEST_VIDEO) },
            audioFormats = audio.ifEmpty { listOf(BEST_AUDIO) }
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
            // Which dubbed track this is. Reported per format by sources that carry more
            // than one, and absent everywhere else, which is the ordinary case.
            language = json.optString("language")
                .takeIf { it.isNotBlank() && it != "null" && it != "none" },
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
        val resolution = json.optString("resolution")
            .takeIf { it.isNotBlank() && it != "null" && it != "audio only" }

        if (!hasVideo) {
            // A note of "medium" says nothing on its own about what it describes, so the
            // kind of stream is spelled out unless the source already did.
            val base = note
                ?: json.optPositiveDouble("abr")?.let { "%.0f kbps".format(it) }
                ?: "Audio"
            return if (base.endsWith("audio", ignoreCase = true)) base else "$base audio"
        }

        val base = when {
            // The source's own note leads, because it names the step on the ladder the way
            // the site does. The height stands in where there is no note.
            !note.isNullOrBlank() -> note
            height > 0 -> if (fps > 30) "${height}p$fps" else "${height}p"
            resolution != null -> resolution
            else -> "Format $id"
        }

        // The measured resolution goes next to it, since a note like "premium" says which
        // step it is and nothing at all about how big the picture is.
        return if (resolution != null && !base.contains(resolution)) "$base ($resolution)"
        else base
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
