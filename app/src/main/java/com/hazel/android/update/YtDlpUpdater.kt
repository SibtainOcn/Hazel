package com.hazel.android.update

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Updates the yt-dlp binary from the yt-dlp GitHub releases, independently of the
 * app's own release cycle.
 *
 * The download and binary swap are performed by youtubedl-android's
 * [YoutubeDL.updateYoutubeDL]. This object adds what the library does not expose:
 * reading the installed version and resolving the latest release of a channel
 * without installing it.
 */
object YtDlpUpdater {

    /** Release channels published by the yt-dlp project. */
    enum class Channel(
        val label: String,
        val ytdlChannel: YoutubeDL.UpdateChannel,
        val repo: String
    ) {
        STABLE("Stable", YoutubeDL.UpdateChannel.STABLE, "yt-dlp/yt-dlp"),
        NIGHTLY("Nightly", YoutubeDL.UpdateChannel.NIGHTLY, "yt-dlp/yt-dlp-nightly-builds"),
        MASTER("Master", YoutubeDL.UpdateChannel.MASTER, "yt-dlp/yt-dlp-master-builds");

        val releasesUrl: String get() = "https://github.com/$repo/releases"

        companion object {
            fun fromLabel(label: String?): Channel =
                entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: STABLE
        }
    }

    /** Latest release of a [Channel], as reported by the GitHub API. */
    data class ReleaseInfo(
        val version: String,
        val channel: Channel,
        val binarySize: Long
    )

    private const val BINARY_ASSET = "yt-dlp"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Version of the yt-dlp binary currently installed on the device.
     *
     * Queries the binary directly (`yt-dlp --version`) so the bundled binary also
     * reports a version; [YoutubeDL.version] is only populated after the first
     * successful in-app update. Returns null when yt-dlp is not initialized.
     */
    suspend fun installedVersion(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(emptyList<String>()).addOption("--version")
            val output = YoutubeDL.getInstance().execute(request, null, null).out.trim()
            output.ifBlank { null }
        } catch (_: Exception) {
            // Library not initialized, or the binary is unusable: fall back to the
            // version recorded by the last successful update.
            try {
                YoutubeDL.getInstance().version(context)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Version recorded by the last successful in-app update, read from shared prefs.
     * Synchronous and allocation-free; null when yt-dlp was never updated in-app.
     */
    fun cachedVersion(context: Context): String? = try {
        YoutubeDL.getInstance().version(context)
    } catch (_: Exception) {
        null
    }

    sealed interface CheckResult {
        data class Success(val info: ReleaseInfo) : CheckResult
        data object NoReleaseFound : CheckResult
        data class NetworkError(val message: String) : CheckResult
    }

    /**
     * Extracts the first release tag from a GitHub Atom feed.
     */
    fun parseFirstTagFromAtom(xml: String): String? {
        val match = Regex("""href=["'][^"']*/releases/tag/([^"']+)["']""").find(xml)
            ?: Regex("""<id>[^<]*/([^/<]+)</id>""").find(xml)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * Fetches the latest release of [channel] from GitHub without installing anything.
     * Uses resilient redirect and Atom feed resolution to be immune to GitHub API 403 rate limits.
     */
    suspend fun latestReleaseResult(channel: Channel): CheckResult = withContext(Dispatchers.IO) {
        // 1. Direct release redirect check (instantaneous, zero API rate limits, 403-proof)
        try {
            val noRedirectClient = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val request = Request.Builder()
                .url("https://github.com/${channel.repo}/releases/latest")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            noRedirectClient.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (location != null && location.contains("/releases/tag/")) {
                    val tag = location.substringAfterLast("/releases/tag/").trim()
                    if (tag.isNotBlank()) {
                        return@withContext CheckResult.Success(
                            ReleaseInfo(
                                version = tag,
                                channel = channel,
                                binarySize = 0L
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to Atom feed
        }

        // 2. Releases Atom feed check (CDN cached, zero rate limits)
        try {
            val atomRequest = Request.Builder()
                .url("https://github.com/${channel.repo}/releases.atom")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(atomRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val xml = response.body.string()
                    val tag = parseFirstTagFromAtom(xml)
                    if (tag != null && tag.isNotBlank()) {
                        return@withContext CheckResult.Success(
                            ReleaseInfo(
                                version = tag,
                                channel = channel,
                                binarySize = 0L
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fall back to API
        }

        // 3. Fallback to API if available (when not rate limited)
        try {
            val request = Request.Builder()
                .url(channel.ytdlChannel.apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Hazel-App-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body.string())
                    val tag = json.optString("tag_name", "").trim()
                    if (tag.isNotBlank()) {
                        return@withContext CheckResult.Success(
                            ReleaseInfo(
                                version = tag,
                                channel = channel,
                                binarySize = findBinarySize(json)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Network failure
        }

        CheckResult.NetworkError("Couldn't reach GitHub. Check your connection.")
    }

    suspend fun latestRelease(channel: Channel): ReleaseInfo? =
        (latestReleaseResult(channel) as? CheckResult.Success)?.info

    /**
     * Downloads and installs the latest yt-dlp binary of [channel].
     * Throws when the download or the binary swap fails.
     */
    suspend fun install(context: Context, channel: Channel): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().updateYoutubeDL(context, channel.ytdlChannel)
        }

    /**
     * yt-dlp publishes a fresh date-based tag for every build, so tag equality is the
     * only meaningful comparison; there is no numeric ordering to evaluate.
     */
    fun isNewer(remote: String, installed: String?): Boolean {
        if (installed.isNullOrBlank()) return true
        return remote.trimStart('v', 'V') != installed.trimStart('v', 'V')
    }

    /** Size of the `yt-dlp` asset in a release payload, 0 when absent. */
    private fun findBinarySize(releaseJson: JSONObject): Long {
        val assets = releaseJson.optJSONArray("assets") ?: return 0L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == BINARY_ASSET) {
                return asset.optLong("size", 0L)
            }
        }
        return 0L
    }
}
