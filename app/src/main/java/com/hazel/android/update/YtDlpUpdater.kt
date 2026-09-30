package com.hazel.android.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.YtDlpEngine
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

/**
 * Updates the yt-dlp binary from the yt-dlp GitHub releases, independently of the
 * app's own release cycle.
 *
 * A new binary is never written over the live one. It is downloaded next to it, checked,
 * and then renamed into place in a single step, and only while no yt-dlp run is in flight
 * (see [YtDlpEngine]). A binary fetched while something is downloading waits on disk and
 * goes live the moment the engine is free, or at the next launch.
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

    /** A real yt-dlp build is a few megabytes; anything far smaller is an error page. */
    private const val MIN_BINARY_BYTES = 500_000L

    /** Keys youtubedl-android records the installed release under. */
    private const val LIBRARY_PREFS = "youtubedl-android"
    private const val VERSION_KEY = "dlpVersion"
    private const val VERSION_NAME_KEY = "dlpVersionName"

    private val isUpdating = AtomicBoolean(false)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun engineDir(context: Context) = File(context.noBackupFilesDir, YoutubeDL.baseName)
    private fun liveDir(context: Context) = File(engineDir(context), YoutubeDL.ytdlpDirName)
    private fun liveBinary(context: Context) = File(liveDir(context), YoutubeDL.ytdlpBin)

    /** A downloaded build waiting to go live, and the release tag it came from. */
    private fun stagedBinary(context: Context) = File(engineDir(context), "yt-dlp.staged")
    private fun stagedTag(context: Context) = File(engineDir(context), "yt-dlp.staged.tag")

    /** Whether [file] is a complete yt-dlp zip rather than a truncated or foreign file. */
    private fun isValidBinary(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_BINARY_BYTES) return false
        return try {
            ZipFile(file).use { zip ->
                zip.getEntry("__main__.py") != null || zip.getEntry("yt_dlp/__init__.py") != null
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Makes sure the live yt-dlp binary is a complete, readable zip.
     *
     * A binary cut short by an older build's in-place update fails every run with a zip
     * import error. When that is what is on disk it is replaced with the copy bundled in
     * the app, so the engine works again even if it is a release behind.
     *
     * Returns true when a valid binary is in place.
     */
    @Synchronized
    fun ensureValidBinary(context: Context): Boolean {
        val binary = liveBinary(context)
        if (isValidBinary(binary)) return true

        Log.w("Hazel", "yt-dlp binary is missing or damaged, restoring the bundled copy")
        return try {
            binary.delete()
            YoutubeDL.getInstance().init_ytdlp(context, liveDir(context))
            // The recorded release no longer describes the file on disk.
            context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).edit()
                .remove(VERSION_KEY)
                .remove(VERSION_NAME_KEY)
                .apply()
            isValidBinary(binary)
        } catch (e: Exception) {
            Log.e("Hazel", "Restoring the bundled yt-dlp failed", e)
            false
        }
    }

    /**
     * Replaces the live binary with the copy bundled in the app, whatever is on disk.
     *
     * For a binary that is intact but too old to run with the options the engine library
     * passes: every run fails on it, so there is nothing to wait for. The copy is written
     * beside the live one and renamed over it, so the swap itself is atomic.
     */
    @Synchronized
    fun restoreBundledBinary(context: Context): Boolean {
        val partial = File(engineDir(context), "yt-dlp.bundled")
        return try {
            context.resources.openRawResource(com.yausername.youtubedl_android.R.raw.ytdlp).use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            if (!isValidBinary(partial) || !partial.renameTo(liveBinary(context))) return false
            liveBinary(context).setExecutable(true, false)
            context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).edit()
                .remove(VERSION_KEY)
                .remove(VERSION_NAME_KEY)
                .apply()
            true
        } catch (e: Exception) {
            Log.e("Hazel", "Restoring the bundled yt-dlp failed", e)
            false
        } finally {
            partial.delete()
        }
    }

    /**
     * Puts a downloaded build live if one is waiting and nothing is running.
     *
     * The rename is atomic on the same filesystem, so a run either sees the old file or the
     * new one, never half of each. Returns true when a staged build went live.
     */
    fun applyStagedUpdate(context: Context): Boolean {
        val staged = stagedBinary(context)
        if (!staged.exists()) return false

        return YtDlpEngine.whenIdle {
            synchronized(this) {
                if (!staged.exists()) return@synchronized false
                if (!isValidBinary(staged)) {
                    staged.delete()
                    stagedTag(context).delete()
                    return@synchronized false
                }
                val tag = runCatching { stagedTag(context).readText().trim() }.getOrDefault("")
                liveDir(context).mkdirs()
                val live = liveBinary(context)
                if (!staged.renameTo(live)) return@synchronized false

                live.setExecutable(true, false)
                stagedTag(context).delete()
                context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).edit().apply {
                    if (tag.isNotBlank()) putString(VERSION_KEY, tag).putString(VERSION_NAME_KEY, tag)
                    else remove(VERSION_KEY).remove(VERSION_NAME_KEY)
                }.apply()
                Log.i("Hazel", "yt-dlp ${tag.ifBlank { "update" }} is now live")
                true
            }
        } ?: run {
            Log.i("Hazel", "yt-dlp update staged; it goes live once the running jobs finish")
            false
        }
    }

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
            YtDlpEngine.execute(request).out.trim().ifBlank { null }
        } catch (_: Exception) {
            // Library not initialized, or the binary is unusable: fall back to the
            // version recorded by the last successful update.
            cachedVersion(context)
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
     * Downloads the `yt-dlp` asset of [release] next to the live binary, without touching it.
     *
     * The asset is fetched by its release tag from github.com rather than through the API,
     * which rate limits unauthenticated callers. [onProgress] receives bytes done and the
     * total, the total being 0 when the server does not say.
     */
    private suspend fun stage(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val staged = stagedBinary(context)
        val partial = File(engineDir(context), "yt-dlp.part")
        val request = Request.Builder()
            .url("https://github.com/${release.channel.repo}/releases/download/${release.version}/$BINARY_ASSET")
            .header("User-Agent", "Hazel-App-Updater")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val total = response.body.contentLength().coerceAtLeast(0L)
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
            }
            if (!isValidBinary(partial)) throw IOException("Downloaded yt-dlp is not a valid build")

            synchronized(this@YtDlpUpdater) {
                staged.delete()
                if (!partial.renameTo(staged)) throw IOException("Could not stage yt-dlp")
                stagedTag(context).writeText(release.version)
            }
        } finally {
            partial.delete()
        }
    }

    /**
     * Downloads and installs the latest yt-dlp binary of [channel].
     *
     * When a download is running the new build waits for it to finish before going live,
     * so this can take as long as that download does. Throws when the download fails.
     */
    suspend fun install(
        context: Context,
        channel: Channel,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): YoutubeDL.UpdateStatus = withContext(Dispatchers.IO) {
        val release = latestRelease(channel) ?: throw IOException("Couldn't reach GitHub")
        stage(context, release, onProgress)
        while (!applyStagedUpdate(context)) {
            if (!stagedBinary(context).exists()) throw IOException("Staged yt-dlp was rejected")
            delay(APPLY_RETRY_MS)
        }
        YoutubeDL.UpdateStatus.DONE
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

    /**
     * Keeps yt-dlp current without the user opening the update screen.
     *
     * Follows the update screen's own settings: nothing happens with automatic download
     * off, and nothing on a metered connection with Wi-Fi only on. The new build is staged
     * in the background and goes live as soon as no run needs the old one. Returns true
     * when a newer build was fetched.
     */
    suspend fun autoUpdateIfAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isUpdating.compareAndSet(false, true)) return@withContext false
        try {
            if (!SettingsRepository.getUpdateAutoDownload(context).first()) return@withContext false
            if (SettingsRepository.getUpdateWifiOnly(context).first() && !onUnmeteredNetwork(context)) {
                return@withContext false
            }

            val channel = Channel.fromLabel(SettingsRepository.getYtDlpChannel(context).first())
            val remote = latestRelease(channel) ?: return@withContext false
            val current = installedVersion(context)
            if (!isNewer(remote.version, current)) return@withContext false

            val alreadyStaged = runCatching { stagedTag(context).readText().trim() }.getOrNull()
            if (alreadyStaged != remote.version || !stagedBinary(context).exists()) {
                Log.i("Hazel", "Fetching yt-dlp ${remote.version} (${channel.label})")
                stage(context, remote)
            }
            if (applyStagedUpdate(context)) SettingsRepository.setYtDlpUpdateAvailable(context, false)
            true
        } catch (e: Exception) {
            Log.w("Hazel", "yt-dlp auto-update failed: ${e.message}")
            false
        } finally {
            isUpdating.set(false)
        }
    }

    private fun onUnmeteredNetwork(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** How often a manual install checks whether the engine has come free. */
    private const val APPLY_RETRY_MS = 1_000L
}
