package com.hazel.android.download.playback

import com.hazel.android.HazelApp
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.InfoCache
import com.hazel.android.download.MediaProbe
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.newpipe.NewPipeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Where to play a piece of media from. [url] carries the picture, or everything; [audioUrl]
 * is a separate sound stream to play alongside it, when the site splits the two.
 */
data class PlayableStream(
    val url: String,
    val audioUrl: String? = null,
    val hasVideo: Boolean = true,
    val isHls: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val audioHeaders: Map<String, String> = emptyMap(),
    val engine: ListingSource,
    /** Every picture quality this media can be played at, tallest first. */
    val heights: List<Int> = emptyList()
)

/**
 * Finds a stream to play for a link, on any site either engine can read.
 *
 * NewPipe answers in about a second on the sites it knows, so it is asked first unless the
 * settings say otherwise or the link needs a sign-in. yt-dlp answers for everything else,
 * reusing a recent read of the same link when there is one. Nothing found here is saved:
 * stream addresses expire within hours.
 */
object StreamResolver {

    /** Tries after the first; the last one reads the link again from scratch. */
    const val LAST_ATTEMPT = 2

    /**
     * @param attempt how many tries have already failed to play. Only the first may use
     *   NewPipe, and the last refuses a cached read whose addresses may have expired.
     */
    suspend fun resolve(url: String, attempt: Int = 0): PlayableStream =
        withContext(Dispatchers.IO) {
            val app = HazelApp.instance
            val engine = SettingsRepository.getPlayEngine(app).first()
            val maxHeight = SettingsRepository.getPlayQuality(app).first()
            val access = CookieRepository.accessFor(app, url)

            if (attempt == 0 && engine == ListingSource.NEWPIPE && !access.hasCookies) {
                NewPipeEngine.playable(url, maxHeight)?.let { return@withContext it }
            }
            if (attempt >= LAST_ATTEMPT) InfoCache.invalidate(url)

            val file = InfoCache.infoJsonFor(url) ?: run {
                MediaProbe.probe(
                    url,
                    File(app.cacheDir, "yt-dlp").apply { mkdirs() },
                    access,
                    SettingsRepository.getFetchMode(app).first(),
                    SettingsRepository.getForceIpv4(app).first(),
                    PLAY_PROCESS_ID
                )
                InfoCache.infoJsonFor(url)
            }
            file?.let { pick(JSONObject(it.readText()), maxHeight) } ?: error("Nothing to play")
        }

    /**
     * Chooses from yt-dlp's reading of a link: the tallest file holding both streams within
     * [maxHeight], a taller picture paired with the best sound, or sound alone.
     */
    internal fun pick(root: JSONObject, maxHeight: Int): PlayableStream? {
        // A post holding several items reads as a list; its first item is the one shown.
        val media = root.optJSONArray("entries")?.optJSONObject(0) ?: root
        val formats = media.optJSONArray("formats")
            ?: return media.takeIf { it.isPlayable() }?.let { single(it) }

        val usable = (0 until formats.length()).mapNotNull { formats.optJSONObject(it) }
            .filter { it.isPlayable() }
        val fits = { f: JSONObject -> f.height() <= maxHeight }
        val videoOrder = compareBy<JSONObject>({ it.height() }, { it.optString("ext") == "mp4" }, { it.optDouble("tbr", 0.0) })

        val both = usable.filter { it.hasVideo() && it.hasAudio() && fits(it) }.maxWithOrNull(videoOrder)
        // Nothing within the cap means the smallest picture there is, not sound alone.
        val picture = usable.filter { it.hasVideo() && !it.hasAudio() && fits(it) }.maxWithOrNull(videoOrder)
            ?: usable.filter { it.hasVideo() && !it.hasAudio() && it.height() > 0 }
                .takeIf { both == null }
                ?.minByOrNull { it.height() }
        val smallestBoth = if (both == null) {
            usable.filter { it.hasVideo() && it.hasAudio() && it.height() > 0 }.minByOrNull { it.height() }
        } else null
        val sound = usable.filter { it.hasAudio() && !it.hasVideo() }
            .maxWithOrNull(compareBy({ it.optString("ext") == "m4a" }, { it.optDouble("abr", 0.0) }))

        val heights = usable.filter { it.hasVideo() && it.height() > 0 }
            .map { it.height() }.distinct().sortedDescending()

        val chosen = when {
            picture != null && sound != null && (both == null || picture.height() > both.height()) ->
                PlayableStream(
                    url = picture.optString("url"),
                    audioUrl = sound.optString("url"),
                    isHls = picture.isHls(),
                    headers = picture.headers(),
                    audioHeaders = sound.headers(),
                    engine = ListingSource.YT_DLP
                )
            both != null -> single(both)
            smallestBoth != null -> single(smallestBoth)
            sound != null -> single(sound).copy(hasVideo = false)
            // Nothing within the cap: the smallest picture is still better than none.
            else -> usable.filter { it.hasVideo() }.minByOrNull { it.height() }?.let { single(it) }
                ?: media.takeIf { it.isPlayable() }?.let { single(it) }
        }
        return chosen?.copy(heights = heights)
    }

    private fun single(format: JSONObject) = PlayableStream(
        url = format.optString("url"),
        hasVideo = format.hasVideo(),
        isHls = format.isHls(),
        headers = format.headers(),
        engine = ListingSource.YT_DLP
    )

    /**
     * The picture's quality in lines: its shorter side, so a 1080 by 1920 short counts as
     * 1080p the way the sites label it.
     */
    private fun JSONObject.height(): Int {
        val h = optInt("height", 0)
        val w = optInt("width", 0)
        return if (h > 0 && w > 0) minOf(h, w) else h
    }

    /**
     * Many sites leave codecs out. A stream is taken to hold a picture unless it says it has
     * none, or everything about it says sound.
     */
    private fun JSONObject.hasVideo(): Boolean {
        val codec = optString("vcodec")
        if (codec == "none") return false
        if (codec.isNotBlank() || height() > 0 || optInt("width", 0) > 0) return true
        return optString("ext").lowercase() !in AUDIO_EXTENSIONS
    }

    private fun JSONObject.hasAudio() = optString("acodec") != "none"

    private fun JSONObject.isPlayable(): Boolean {
        val protocol = optString("protocol")
        return optString("url").startsWith("http") && !has("fragments") &&
            (protocol.isBlank() || protocol in PLAYABLE_PROTOCOLS)
    }

    private fun JSONObject.isHls() = optString("protocol").startsWith("m3u8")

    /** The headers the site asked for, plus any cookies the read was given for the stream. */
    private fun JSONObject.headers(): Map<String, String> {
        val headers = optJSONObject("http_headers")?.let { json ->
            json.keys().asSequence().associateWith { json.optString(it) }
        }.orEmpty().toMutableMap()
        cookieHeader(optString("cookies"))?.let { headers["Cookie"] = it }
        return headers
    }

    /** Turns yt-dlp's cookie string into a request header, dropping the attributes. */
    internal fun cookieHeader(raw: String): String? =
        raw.split(";")
            .map { it.trim() }
            .filter { part ->
                val name = part.substringBefore('=', "").trim()
                name.isNotEmpty() && name.lowercase() !in COOKIE_ATTRIBUTES
            }
            .joinToString("; ")
            .takeIf { it.isNotBlank() }

    private val PLAYABLE_PROTOCOLS = setOf("https", "http", "m3u8", "m3u8_native")
    private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "opus", "ogg", "oga", "flac", "wav", "weba")
    private val COOKIE_ATTRIBUTES = setOf("domain", "path", "expires", "max-age", "samesite", "secure", "httponly")
    const val PLAY_PROCESS_ID = "hazel_play"
}
