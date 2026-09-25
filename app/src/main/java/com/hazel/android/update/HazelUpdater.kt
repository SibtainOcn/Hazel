package com.hazel.android.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.hazel.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

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

    fun cancelActiveDownload() {
        try {
            currentDownloadCall?.cancel()
        } catch (_: Exception) {}
        currentDownloadCall = null
    }

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
    fun isNewer(remote: String, installed: String = installedVersion()): Boolean {
        if (installed.isBlank()) return true
        val (rMaj, rMin, rPatch) = parseSemver(remote)
        val (iMaj, iMin, iPatch) = parseSemver(installed)

        if (rMaj != iMaj) return rMaj > iMaj
        if (rMin != iMin) return rMin > iMin
        if (rPatch != iPatch) return rPatch > iPatch

        // If semver numbers match, check pre-release tags (e.g. 1.0.9-beta vs 1.0.9)
        val rHasPre = remote.contains("-")
        val iHasPre = installed.contains("-")
        return !rHasPre && iHasPre
    }

    /**
     * Resolves the optimal APK filename and direct download URL for the target device architecture.
     */
    fun resolveBestApkForDevice(
        version: String,
        channel: Channel,
        supportedAbis: List<String> = try { Build.SUPPORTED_ABIS?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
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
        val downloadUrl = "https://github.com/$REPO_NAME/releases/download/v${version}/$assetName"
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
        val entries = entryRegex.findAll(xml)

        for (match in entries) {
            val entryStr = match.value

            val tagFromLink = Regex("""href=["'][^"']*/releases/tag/([^"']+)["']""").find(entryStr)?.groupValues?.get(1)
            val tagFromId = Regex("""<id>[^<]*/([^/<]+)</id>""").find(entryStr)?.groupValues?.get(1)
            val rawTag = tagFromLink ?: tagFromId ?: continue
            val tag = rawTag.trim()

            val title = Regex("""<title[^>]*>([\s\S]*?)</title>""").find(entryStr)?.groupValues?.get(1)?.trim() ?: ""
            val updated = Regex("""<updated[^>]*>([\s\S]*?)</updated>""").find(entryStr)?.groupValues?.get(1)?.trim() ?: ""
            val content = Regex("""<content[^>]*>([\s\S]*?)</content>""").find(entryStr)?.groupValues?.get(1)?.trim() ?: ""

            val isBetaTag = tag.contains("beta", ignoreCase = true) || title.contains("beta", ignoreCase = true)
            val isNightlyTag = tag.contains("nightly", ignoreCase = true) || title.contains("nightly", ignoreCase = true)

            val matchesChannel = when (channel) {
                Channel.STABLE -> !isBetaTag && !isNightlyTag
                Channel.BETA -> isBetaTag && !isNightlyTag
                Channel.NIGHTLY -> isNightlyTag
            }

            if (!matchesChannel) continue

            val version = tag.removePrefix("v").removePrefix("V")
            val (assetName, assetUrl) = resolveBestApkForDevice(version, channel, supportedAbis)

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

            return ReleaseInfo(
                version = version,
                channel = channel,
                binarySize = 0L,
                downloadUrl = assetUrl,
                changelog = if (changelog.isNotBlank()) changelog else title,
                publishedAt = updated,
                assetName = assetName
            )
        }
        return null
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
        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            if (rel.optBoolean("draft", false)) continue

            val tagName = rel.optString("tag_name", "").trim()
            val isPrerelease = rel.optBoolean("prerelease", false)
            val isBetaTag = tagName.contains("beta", ignoreCase = true)
            val isNightlyTag = tagName.contains("nightly", ignoreCase = true)

            val matchesChannel = when (channel) {
                Channel.STABLE -> !isPrerelease && !isBetaTag && !isNightlyTag
                Channel.BETA -> (isPrerelease || isBetaTag) && !isNightlyTag
                Channel.NIGHTLY -> isNightlyTag
            }

            if (!matchesChannel) {
                continue
            }

            val version = tagName.removePrefix("v").removePrefix("V")
            val changelog = rel.optString("body", "").trim()
            val publishedAt = rel.optString("published_at", "")

            val (assetName, assetUrl, assetSize) = findBestApkAsset(rel, supportedAbis)
            if (assetUrl.isNotBlank()) {
                return ReleaseInfo(
                    version = version,
                    channel = channel,
                    binarySize = assetSize,
                    downloadUrl = assetUrl,
                    changelog = changelog,
                    publishedAt = publishedAt,
                    assetName = assetName
                )
            }
        }
        return null
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
            val url = "https://api.github.com/repos/$REPO_NAME/releases"
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
        val abiAliases = mapOf(
            "arm64-v8a" to listOf("arm64-v8a", "arm64", "v8a"),
            "armeabi-v7a" to listOf("armeabi-v7a", "armv7", "v7a"),
            "x86_64" to listOf("x86_64", "x64"),
            "x86" to listOf("x86")
        )

        var bestMatch: Triple<String, String, Long>? = null
        var fallbackUniversal: Triple<String, String, Long>? = null
        var firstApk: Triple<String, String, Long>? = null

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (!name.endsWith(".apk", ignoreCase = true)) continue

            val downloadUrl = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0L)
            val current = Triple(name, downloadUrl, size)

            if (firstApk == null) firstApk = current

            if (name.contains("universal", ignoreCase = true)) {
                fallbackUniversal = current
            }

            for (abi in supportedAbis) {
                val aliases = abiAliases[abi] ?: listOf(abi)
                if (aliases.any { name.contains(it, ignoreCase = true) }) {
                    bestMatch = current
                    break
                }
            }
            if (bestMatch != null) break
        }

        return bestMatch ?: fallbackUniversal ?: firstApk ?: Triple("", "", 0L)
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
        try {
            val channelLabel = com.hazel.android.data.SettingsRepository.getHazelChannel(context).first()
            val channel = Channel.fromLabel(channelLabel)
            val release = latestRelease(channel)
            val hasHazelUpdate = release != null && isNewer(release.version)
            com.hazel.android.data.SettingsRepository.setHazelUpdateAvailable(context, hasHazelUpdate)
            cleanStaleApks(context, preserveVersion = if (hasHazelUpdate) release.version else null)
        } catch (_: Exception) {
            // Ignore silent network check failure
        }

        try {
            val installedEngine = YtDlpUpdater.installedVersion(context)
            if (installedEngine != null) {
                val channel = YtDlpUpdater.Channel.STABLE
                val releaseInfo = YtDlpUpdater.latestRelease(channel)
                val hasYtDlpUpdate = releaseInfo != null && YtDlpUpdater.isNewer(releaseInfo.version, installedEngine)
                com.hazel.android.data.SettingsRepository.setYtDlpUpdateAvailable(context, hasYtDlpUpdate)
            }
        } catch (_: Exception) {
            // Ignore silent engine check failure
        }
    }

    /**
     * Downloads the APK file to cache directory with progress reporting and immediate cancellation.
     */
    suspend fun downloadApk(
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
                if (!response.isSuccessful) throw IllegalStateException("Download failed with HTTP ${response.code}")
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

