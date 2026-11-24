package com.hazel.android.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.hazel.android.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import com.hazel.android.download.NetworkProxy.throughProxy
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles fetching, verifying, and downloading Hazel application releases directly
 * from the GitHub repository, compliant with F-Droid and open-source standards.
 */
object HazelUpdater {

    const val REPO_NAME = "SibtainOcn/Hazel"
    const val GITHUB_REPO_URL = "https://github.com/SibtainOcn/Hazel"
    const val FDROID_PACKAGE_URL = "https://f-droid.org/packages/com.hazel.android/"

    @Volatile
    private var currentDownloadCall: Call? = null

    /** Set when the person stops a download, so a stopped background download is not announced. */
    private val stoppedByUser = AtomicBoolean(false)

    fun cancelActiveDownload() {
        stoppedByUser.set(true)
        try {
            currentDownloadCall?.cancel()
        } catch (_: Exception) {}
        currentDownloadCall = null
    }

    /** One APK download at a time: the screen's and the background's share the same file. */
    private val downloadLock = Mutex()

    /** A background download in progress, for the update screen to show instead of starting its own. */
    data class BackgroundDownload(
        val version: String,
        val bytes: Long = 0L,
        val total: Long = 0L,
        val speedBps: Long = 0L,
        val etaSeconds: Long = 0L
    )

    private val _background = MutableStateFlow<BackgroundDownload?>(null)
    val background: StateFlow<BackgroundDownload?> = _background.asStateFlow()

    /** Outlives the screen that started the check, so a background download is not cut short. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class Channel(val label: String) {
        STABLE("Stable"),
        BETA("Beta"),
        NIGHTLY("Nightly");

        companion object {
            fun fromLabel(label: String?): Channel =
                entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: STABLE
        }
    }

    data class ReleaseInfo(
        val version: String,
        val channel: Channel,
        val binarySize: Long,
        val downloadUrl: String,
        val changelog: String,
        val publishedAt: String,
        val assetName: String
    )

    sealed interface CheckResult {
        data class Success(val info: ReleaseInfo) : CheckResult
        data object NoReleaseFound : CheckResult
        data class NetworkError(val message: String) : CheckResult
    }

    private val client = OkHttpClient.Builder()
        .throughProxy()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isFdroid(): Boolean = BuildConfig.IS_FDROID

    fun distributionFlavor(): String = BuildConfig.DISTRIBUTION_FLAVOR

    fun installedVersion(): String = BuildConfig.VERSION_NAME

    /**
     * Parses semver into numeric components (e.g., "1.0.8" -> (1, 0, 8)).
     */
    fun parseSemver(version: String): Triple<Int, Int, Int> {
        val clean = version.trim().removePrefix("v").removePrefix("V").split("-")[0]
        val parts = clean.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    /**
     * Compares remote release version to current installed version.
     */
    /** Nightly < beta < stable for the same numbers, then the build number: the order of the version codes. */
    fun isNewer(remote: String, installed: String = installedVersion()): Boolean {
        if (installed.isBlank()) return true
        val r = HazelVersion.parse(remote) ?: return false
        val i = HazelVersion.parse(installed) ?: return true
        return r > i
    }

    /** The channel this build was published on. */
    fun buildChannel(): Channel = Channel.fromLabel(BuildConfig.HAZEL_CHANNEL)

    /** The installed build is ahead of [channel]: Android cannot go back, so it waits for that channel's next release. */
    fun waitsForChannel(channel: Channel, installed: String = installedVersion()): Boolean {
        val stage = HazelVersion.parse(installed)?.stage ?: return false
        return !channel.accepts(stage)
    }

    /**
     * A channel takes its own releases and every steadier one, so Beta and Nightly move on to a
     * stable that is newer than their last build instead of waiting for the next one of their own.
     */
    internal fun Channel.accepts(stage: HazelVersion.Stage): Boolean = when (this) {
        Channel.STABLE -> stage == HazelVersion.Stage.STABLE
        Channel.BETA -> stage != HazelVersion.Stage.NIGHTLY
        Channel.NIGHTLY -> true
    }

    private fun HazelVersion.Stage.channel(): Channel = when (this) {
        HazelVersion.Stage.NIGHTLY -> Channel.NIGHTLY
        HazelVersion.Stage.BETA -> Channel.BETA
        HazelVersion.Stage.STABLE -> Channel.STABLE
    }

    /**
     * What a release is, from its tag, or from its title for the rolling "nightly" tag that
     * carries no version. A pre-release with a plain version tag counts as a beta.
     */
    private fun releaseVersion(tag: String, title: String, prerelease: Boolean): Pair<HazelVersion, String>? {
        val fromTag = HazelVersion.parse(tag)
        val version = fromTag ?: HazelVersion.find(title) ?: return null
        val settled = if (prerelease && version.stage == HazelVersion.Stage.STABLE) {
            version.copy(stage = HazelVersion.Stage.BETA)
        } else version
        val text = if (fromTag != null) tag.trim().removePrefix("v").removePrefix("V") else version.toString()
        return settled to text
    }

    /**
     * Resolves the optimal APK filename and direct download URL for the target device architecture.
     */
    fun resolveBestApkForDevice(
        version: String,
        channel: Channel,
        supportedAbis: List<String> = try { Build.SUPPORTED_ABIS?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() },
        tag: String = "v$version"
    ): Pair<String, String> {
        val channelSuffix = channel.name.lowercase()
        val abiAliases = mapOf(
            "arm64-v8a" to listOf("arm64-v8a", "arm64", "v8a"),
            "armeabi-v7a" to listOf("armeabi-v7a", "armv7", "v7a"),
            "x86_64" to listOf("x86_64", "x64"),
            "x86" to listOf("x86")
        )

        var selectedAbi: String? = null
        for (abi in supportedAbis) {
            val matched = abiAliases.entries.firstOrNull { (_, aliases) ->
                aliases.any { it.equals(abi, ignoreCase = true) }
            }
            if (matched != null) {
                selectedAbi = matched.key
                break
            }
        }

        val targetAbi = selectedAbi ?: "universal"
        val assetName = "Hazel-v${version}-${targetAbi}-${channelSuffix}.apk"
        val downloadUrl = "https://github.com/$REPO_NAME/releases/download/$tag/$assetName"
        return Pair(assetName, downloadUrl)
    }

    /**
     * Parses the public GitHub releases Atom feed, completely immune to GitHub API 403 rate limits.
     */
    fun parseReleasesAtom(
        xml: String,
        channel: Channel,
        supportedAbis: List<String> = try { Build.SUPPORTED_ABIS?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
    ): ReleaseInfo? {
        val entryRegex = Regex("<entry[\\s\\S]*?</entry>", RegexOption.MULTILINE)
        var best: Pair<HazelVersion, ReleaseInfo>? = null

        for (match in entryRegex.findAll(xml)) {
            val entryStr = match.value

            val tagFromLink = Regex("""href=["'][^"']*/releases/tag/([^"']+)["']""").find(entryStr)?.groupValues?.get(1)
            val tagFromId = Regex("""<id>[^<]*/([^/<]+)</id>""").find(entryStr)?.groupValues?.get(1)
            val tag = (tagFromLink ?: tagFromId ?: continue).trim()

            val title = Regex("""<title[^>]*>([\s\S]*?)</title>""").find(entryStr)?.groupValues?.get(1)?.trim() ?: ""
            // The feed does not mark pre-releases; the version word in the tag or title does.
            val (version, text) = releaseVersion(tag, title, prerelease = false) ?: continue
            if (!channel.accepts(version.stage)) continue
            if (best != null && version <= best.first) continue

            val updated = Regex("""<updated[^>]*>([\s\S]*?)</updated>""").find(entryStr)?.groupValues?.get(1)?.trim() ?: ""
            val content = Regex("""<content[^>]*>([\s\S]*?)</content>""").find(entryStr)?.groupValues?.get(1)?.trim() ?: ""
            val (assetName, assetUrl) = resolveBestApkForDevice(text, version.stage.channel(), supportedAbis, tag)

            val changelog = content
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
                .replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()

            best = version to ReleaseInfo(
                version = text,
                channel = version.stage.channel(),
                binarySize = 0L,
                downloadUrl = assetUrl,
                changelog = if (changelog.isNotBlank()) changelog else title,
                publishedAt = updated,
                assetName = assetName
            )
        }
        return best?.second
    }

    suspend fun queryReleasesAtom(channel: Channel): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://github.com/$REPO_NAME/releases.atom"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val xml = response.body.string()
                parseReleasesAtom(xml, channel)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks for updates from the official F-Droid package repository with a fallback
     * to GitHub release metadata, strictly without triggering executable downloads.
     */
    suspend fun checkFdroidRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        // First try official F-Droid package metadata API
        try {
            val url = "https://f-droid.org/api/v1/packages/com.hazel.android"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Hazel-App-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body.string()
                    val json = JSONObject(bodyStr)
                    var suggested = json.optString("suggestedVersionName", "").trim()
                    val packages = json.optJSONArray("packages")
                    var publishedAt = ""
                    if (suggested.isBlank() && packages != null && packages.length() > 0) {
                        val firstPkg = packages.getJSONObject(0)
                        suggested = firstPkg.optString("versionName", "").trim()
                        publishedAt = firstPkg.optString("added", "")
                    }
                    if (suggested.isNotBlank()) {
                        return@withContext ReleaseInfo(
                            version = suggested.removePrefix("v").removePrefix("V"),
                            channel = Channel.STABLE,
                            binarySize = 0L,
                            downloadUrl = FDROID_PACKAGE_URL,
                            changelog = "Official F-Droid repository release",
                            publishedAt = publishedAt,
                            assetName = ""
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Network or parsing failure, try repo fallback
        }

        // Secondary fallback: query repo latest release tag via Atom feed (never hits 403)
        try {
            val atomRelease = queryReleasesAtom(Channel.STABLE)
            if (atomRelease != null) {
                return@withContext atomRelease.copy(
                    downloadUrl = FDROID_PACKAGE_URL,
                    assetName = "",
                    binarySize = 0L
                )
            }
        } catch (_: Exception) {
            // Ignore
        }

        null
    }

    /**
     * Finds the latest release object in the JSON array matching the specified channel.
     */
    fun findReleaseMatchingChannel(
        releases: JSONArray,
        channel: Channel,
        supportedAbis: List<String> = try { Build.SUPPORTED_ABIS?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
    ): ReleaseInfo? {
        var best: Pair<HazelVersion, ReleaseInfo>? = null
        for (i in 0 until releases.length()) {
            val rel = releases.optJSONObject(i) ?: continue
            if (rel.optBoolean("draft", false)) continue

            val (version, text) = releaseVersion(
                rel.optString("tag_name", ""),
                rel.optString("name", ""),
                rel.optBoolean("prerelease", false)
            ) ?: continue
            if (!channel.accepts(version.stage)) continue
            if (best != null && version <= best.first) continue

            val (assetName, assetUrl, assetSize) = findBestApkAsset(rel, supportedAbis)
            if (assetUrl.isBlank()) continue
            best = version to ReleaseInfo(
                version = text,
                channel = version.stage.channel(),
                binarySize = assetSize,
                downloadUrl = assetUrl,
                changelog = rel.optString("body", "").trim(),
                publishedAt = rel.optString("published_at", ""),
                assetName = assetName
            )
        }
        return best?.second
    }

    /**
     * Queries releases API and finds the latest release matching the channel.
     * Uses resilient Atom feed fallback so it is immune to GitHub API 403 rate limits.
     */
    suspend fun latestReleaseResult(channel: Channel): CheckResult = withContext(Dispatchers.IO) {
        if (isFdroid()) {
            if (channel != Channel.STABLE) {
                return@withContext CheckResult.NoReleaseFound
            }
            val rel = checkFdroidRelease()
            return@withContext if (rel != null) CheckResult.Success(rel) else CheckResult.NoReleaseFound
        }

        // 1. Try GitHub Releases API first
        try {
            // Up to 100, so a run of pre-releases never hides the stable behind the first page.
            val url = "https://api.github.com/repos/$REPO_NAME/releases?per_page=100"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Hazel-App-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body.string()
                    val releases = JSONArray(bodyStr)
                    val release = findReleaseMatchingChannel(releases, channel)
                    if (release != null) return@withContext CheckResult.Success(release)
                    return@withContext CheckResult.NoReleaseFound
                }
            }
        } catch (_: Exception) {
            // Fall back to Atom feed
        }

        // 2. Resilient fallback: GitHub Releases Atom feed (zero API rate limits, 403-proof)
        try {
            val atomRelease = queryReleasesAtom(channel)
            if (atomRelease != null) {
                return@withContext CheckResult.Success(atomRelease)
            }

            // Verify if Atom feed is reachable
            val feedCheck = Request.Builder()
                .url("https://github.com/$REPO_NAME/releases.atom")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val feedReachable = try {
                client.newCall(feedCheck).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }
            if (feedReachable) {
                return@withContext CheckResult.NoReleaseFound
            }
        } catch (_: Exception) {
            // Network failure
        }

        CheckResult.NetworkError("Couldn't reach GitHub. Check your connection.")
    }

    suspend fun latestRelease(channel: Channel): ReleaseInfo? =
        (latestReleaseResult(channel) as? CheckResult.Success)?.info

    /**
     * Identifies the optimal APK asset for the current device architecture.
     */
    fun findBestApkAsset(
        releaseJson: JSONObject,
        supportedAbis: List<String> = try { Build.SUPPORTED_ABIS?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
    ): Triple<String, String, Long> {
        val assets = releaseJson.optJSONArray("assets") ?: return Triple("", "", 0L)
        // F-Droid APKs have no updater, so a GitHub install never moves onto one.
        val apks = (0 until assets.length()).mapNotNull { i ->
            val asset = assets.optJSONObject(i) ?: return@mapNotNull null
            val name = asset.optString("name", "")
            if (!name.endsWith(".apk", ignoreCase = true) || name.contains("-fdroid-", ignoreCase = true)) {
                return@mapNotNull null
            }
            Triple(name, asset.optString("browser_download_url", ""), asset.optLong("size", 0L))
        }
        fun abiOf(name: String) = APK_ABI.find(name)?.groupValues?.get(1)?.lowercase()

        // The device's own order: its main architecture first, never one it only lists second.
        for (abi in supportedAbis) {
            val wanted = canonicalAbi(abi) ?: continue
            apks.firstOrNull { abiOf(it.first) == wanted }?.let { return it }
        }
        return apks.firstOrNull { abiOf(it.first) == "universal" }
            ?: apks.firstOrNull { abiOf(it.first) == null }
            ?: Triple("", "", 0L)
    }

    /** The architecture word in an APK name, matched whole, so x86 never takes the x86_64 APK. */
    private val APK_ABI = Regex("""-(arm64-v8a|armeabi-v7a|x86_64|x86|universal)(?=[-.])""", RegexOption.IGNORE_CASE)

    private fun canonicalAbi(abi: String): String? = when (abi.lowercase()) {
        "arm64-v8a", "arm64", "v8a" -> "arm64-v8a"
        "armeabi-v7a", "armv7", "v7a" -> "armeabi-v7a"
        "x86_64", "x64" -> "x86_64"
        "x86" -> "x86"
        else -> null
    }

    /**
     * Cleans up cached update APK files to reclaim storage and prevent installer file bloat.
     * When preserveVersion is specified, keeps the downloaded APK matching that version.
     */
    fun cleanStaleApks(context: Context, preserveVersion: String? = null) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (updatesDir.exists() && updatesDir.isDirectory) {
                updatesDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val isPart = file.extension.equals("part", ignoreCase = true) || file.name.endsWith(".part")
                        val isApk = file.extension.equals("apk", ignoreCase = true)
                        if (isPart) {
                            file.delete()
                        } else if (isApk) {
                            if (preserveVersion != null && file.name.contains(preserveVersion)) {
                                return@forEach
                            }
                            file.delete()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore filesystem cleanup exceptions
        }
    }

    /**
     * Removes downloaded APKs that are no longer ahead of the app: the one just installed,
     * and anything older. Needs no network, unlike the check in [checkUpdatesSilently],
     * which only tidied up once it had reached the release feed; an app updated and then
     * opened offline kept its installer, tens of megabytes, in the cache. An APK for a
     * version still newer than the app is kept, so an update downloaded but not yet
     * installed is not fetched again.
     */
    fun cleanInstalledApks(context: Context) {
        runCatching {
            File(context.cacheDir, "updates").listFiles()?.forEach { file ->
                if (!file.isFile || !file.name.endsWith(".apk", ignoreCase = true)) return@forEach
                val version = APK_VERSION.find(file.name)?.groupValues?.get(1)
                if (version == null || !isNewer(version)) file.delete()
            }
        }
    }

    /** The version in an update's file name, as in Hazel-v1.0.11-arm64-v8a-stable.apk. */
    private val APK_VERSION = Regex("""^Hazel-v(\d+(?:\.\d+)*)""", RegexOption.IGNORE_CASE)

    /**
     * Retrieves an already downloaded and verified APK from cache, if available.
     */
    fun getCachedApk(context: Context, info: ReleaseInfo): File? {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) return null
        val safeName = info.assetName.ifBlank { "Hazel-v${info.version}.apk" }
        val targetFile = File(updatesDir, safeName)
        if (targetFile.exists() && targetFile.isFile && targetFile.length() > 0) {
            if (info.binarySize <= 0L || targetFile.length() == info.binarySize) {
                return targetFile
            }
        }
        return null
    }

    /**
     * Asynchronously checks for Hazel and engine updates in the background on app start,
     * reactively syncing flags so Home top bar Update pill and More screen red dots
     * appear immediately in real-time without requiring manual user navigation.
     */
    suspend fun checkUpdatesSilently(context: Context) = withContext(Dispatchers.IO) {
        cleanInstalledApks(context)
        // An update installed since the last check is no longer one, whether or not the
        // network is there to say so.
        val offered = com.hazel.android.data.SettingsRepository.getHazelUpdateVersion(context).first()
        if (offered != null && !isNewer(offered)) {
            com.hazel.android.data.SettingsRepository.setHazelUpdateAvailable(context, false)
        }
        try {
            val channelLabel = com.hazel.android.data.SettingsRepository.getHazelChannel(context).first()
            val channel = Channel.fromLabel(channelLabel)
            val release = latestRelease(channel)
            val hasHazelUpdate = release != null && isNewer(release.version)
            com.hazel.android.data.SettingsRepository.setHazelUpdateAvailable(context, hasHazelUpdate, release?.version)
            // A download in progress owns its part file; tidying now would delete it under it.
            if (!downloadLock.isLocked) {
                cleanStaleApks(context, preserveVersion = if (hasHazelUpdate) release.version else null)
            }
            if (hasHazelUpdate) onNewRelease(context, release)
        } catch (_: Exception) {
            // Ignore silent network check failure
        }

        try {
            val installedEngine = YtDlpUpdater.installedVersion(context)
            if (installedEngine != null) {
                val channel = YtDlpUpdater.Channel.fromLabel(
                    com.hazel.android.data.SettingsRepository.getYtDlpChannel(context).first()
                )
                val releaseInfo = YtDlpUpdater.latestRelease(channel)
                val hasYtDlpUpdate = releaseInfo != null && YtDlpUpdater.isNewer(releaseInfo.version, installedEngine)
                com.hazel.android.data.SettingsRepository.setYtDlpUpdateAvailable(context, hasYtDlpUpdate)
            }
        } catch (_: Exception) {
            // Ignore silent engine check failure
        }
    }

    /**
     * A newer version was found. With auto-download on (and Wi-Fi only off, or on a network that
     * is not metered) it is fetched in the background so the update screen opens on Install;
     * otherwise, or if that fails, it is only announced. F-Droid builds are updated by F-Droid.
     */
    private suspend fun onNewRelease(context: Context, release: ReleaseInfo) {
        val app = context.applicationContext
        if (getCachedApk(app, release) != null) {
            HazelUpdateNotifier.post(app, HazelUpdateNotifier.Event.READY, release.version)
            return
        }
        val wifiOnly = com.hazel.android.data.SettingsRepository.getHazelUpdateWifiOnly(app).first()
        val auto = !isFdroid() && release.downloadUrl.isNotBlank() &&
            com.hazel.android.data.SettingsRepository.getHazelUpdateAutoDownload(app).first() &&
            (!wifiOnly || onUnmeteredNetwork(app))
        if (!auto || !_background.compareAndSet(null, BackgroundDownload(release.version, total = release.binarySize))) {
            if (!auto) HazelUpdateNotifier.post(app, HazelUpdateNotifier.Event.AVAILABLE, release.version)
            return
        }
        scope.launch {
            try {
                var lastNetworkCheck = System.currentTimeMillis()
                downloadApk(app, release) { bytes, total, speed, eta ->
                    // Wi-Fi only also holds when Wi-Fi drops mid-download and mobile data takes over.
                    val now = System.currentTimeMillis()
                    if (wifiOnly && now - lastNetworkCheck >= 3_000) {
                        lastNetworkCheck = now
                        if (!onUnmeteredNetwork(app)) throw java.io.IOException("Network became metered")
                    }
                    _background.value = BackgroundDownload(release.version, bytes, total, speed, eta)
                }
                HazelUpdateNotifier.post(app, HazelUpdateNotifier.Event.READY, release.version)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("Hazel", "Hazel update download in the background failed: ${e.message}")
                if (!stoppedByUser.get()) HazelUpdateNotifier.post(app, HazelUpdateNotifier.Event.AVAILABLE, release.version)
            } finally {
                _background.value = null
            }
        }
    }

    /** Metered or unknown counts as metered: an APK is tens of megabytes. */
    private fun onUnmeteredNetwork(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        manager?.activeNetwork != null && !manager.isActiveNetworkMetered
    }.getOrDefault(false)

    /**
     * Downloads the APK file to cache directory with progress reporting and immediate cancellation.
     * Waits for a download already running and reuses its file when it is the same version.
     */
    suspend fun downloadApk(
        context: Context,
        info: ReleaseInfo,
        onProgress: (bytesRead: Long, totalBytes: Long, speedBps: Long, etaSeconds: Long) -> Unit
    ): File = downloadLock.withLock {
        getCachedApk(context, info)?.let { return@withLock it }
        stoppedByUser.set(false)
        fetchApk(context, info, onProgress)
    }

    private suspend fun fetchApk(
        context: Context,
        info: ReleaseInfo,
        onProgress: (bytesRead: Long, totalBytes: Long, speedBps: Long, etaSeconds: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        cleanStaleApks(context, preserveVersion = info.version)

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()

        val safeName = info.assetName.ifBlank { "Hazel-v${info.version}.apk" }
        val targetFile = File(updatesDir, safeName)
        val tempFile = File(updatesDir, "$safeName.part")

        if (tempFile.exists()) tempFile.delete()

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "Hazel-App-Updater")
            .build()

        val call = client.newCall(request)
        currentDownloadCall = call

        val completionHandle = coroutineContext.job.invokeOnCompletion {
            call.cancel()
        }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw UpdateFailureException(UpdateFailure.http(response.code))
                val body = response.body
                val totalBytes = if (info.binarySize > 0) info.binarySize else body.contentLength()

                val buffer = ByteArray(8192)
                var bytesReadTotal = 0L
                var lastSampleTime = System.currentTimeMillis()
                var lastSampleBytes = 0L
                var currentSpeed = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, read)
                            bytesReadTotal += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastSampleTime
                            if (elapsed >= 300) {
                                val bytesDelta = bytesReadTotal - lastSampleBytes
                                currentSpeed = if (elapsed > 0) (bytesDelta * 1000) / elapsed else 0L
                                val remainingBytes = (totalBytes - bytesReadTotal).coerceAtLeast(0L)
                                val eta = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                                onProgress(bytesReadTotal, totalBytes, currentSpeed, eta)
                                lastSampleTime = now
                                lastSampleBytes = bytesReadTotal
                            }
                        }
                        output.flush()
                    }
                }
                coroutineContext.ensureActive()

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                onProgress(bytesReadTotal, totalBytes, currentSpeed, 0L)
            }
            targetFile
        } catch (e: Throwable) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        } finally {
            completionHandle.dispose()
            if (currentDownloadCall === call) {
                currentDownloadCall = null
            }
        }
    }

    fun canInstallApks(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Prompts the system package installer to install the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolveList = context.packageManager.queryIntentActivities(
            intent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resolveList) {
            val pkg = resolveInfo.activityInfo.packageName
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

