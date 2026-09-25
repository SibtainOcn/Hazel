package com.hazel.android.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.hazel.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    val suggested = json.optString("suggestedVersionName", "").trim()
                    if (suggested.isNotBlank()) {
                        val packages = json.optJSONArray("packages")
                        var changelog = "Official F-Droid build release"
                        var publishedAt = ""
                        if (packages != null && packages.length() > 0) {
                            val firstPkg = packages.getJSONObject(0)
                            publishedAt = firstPkg.optString("added", "")
                        }
                        return@withContext ReleaseInfo(
                            version = suggested.removePrefix("v").removePrefix("V"),
                            channel = Channel.STABLE,
                            binarySize = 0L,
                            downloadUrl = FDROID_PACKAGE_URL,
                            changelog = changelog,
                            publishedAt = publishedAt,
                            assetName = ""
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Network or parsing failure, try repo fallback
        }

        // Secondary fallback: query repo latest release tag to detect if a newer release exists
        try {
            val url = "https://api.github.com/repos/$REPO_NAME/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Hazel-App-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body.string()
                    val json = JSONObject(bodyStr)
                    val tagName = json.optString("tag_name", "").trim()
                    val version = tagName.removePrefix("v").removePrefix("V")
                    val changelog = json.optString("body", "").trim()
                    val publishedAt = json.optString("published_at", "")
                    if (version.isNotBlank()) {
                        return@withContext ReleaseInfo(
                            version = version,
                            channel = Channel.STABLE,
                            binarySize = 0L,
                            downloadUrl = FDROID_PACKAGE_URL,
                            changelog = changelog,
                            publishedAt = publishedAt,
                            assetName = ""
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore
        }

        null
    }

    /**
     * Finds the latest release object in the JSON array matching the specified channel.
     */
    fun findReleaseMatchingChannel(releases: JSONArray, channel: Channel): ReleaseInfo? {
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
                Channel.NIGHTLY -> isNightlyTag || (channel == Channel.NIGHTLY && isPrerelease)
            }

            if (!matchesChannel && releases.length() > 1) {
                continue
            }

            val version = tagName.removePrefix("v").removePrefix("V")
            val changelog = rel.optString("body", "").trim()
            val publishedAt = rel.optString("published_at", "")

            val (assetName, assetUrl, assetSize) = findBestApkAsset(rel)
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
     * In F-Droid builds, queries F-Droid release feed metadata instead of APK downloads.
     */
    suspend fun latestRelease(channel: Channel): ReleaseInfo? = withContext(Dispatchers.IO) {
        if (isFdroid()) {
            return@withContext checkFdroidRelease()
        }

        // Local development/test feed check (e.g. over adb reverse tcp:8998)
        try {
            val localTestUrl = "http://127.0.0.1:8998/releases.json"
            val localReq = Request.Builder()
                .url(localTestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "Hazel-App-Updater")
                .build()

            client.newBuilder()
                .connectTimeout(400, TimeUnit.MILLISECONDS)
                .readTimeout(800, TimeUnit.MILLISECONDS)
                .build()
                .newCall(localReq).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body.string()
                        if (bodyStr.isNotBlank()) {
                            val parsed = findReleaseMatchingChannel(JSONArray(bodyStr), channel)
                            if (parsed != null) return@withContext parsed
                        }
                    }
                }
        } catch (_: Exception) {
            // Local dev server not running; fall through to GitHub endpoint
        }

        try {
            val url = "https://api.github.com/repos/$REPO_NAME/releases"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Hazel-App-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body.string()
                val releases = JSONArray(bodyStr)
                findReleaseMatchingChannel(releases, channel)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Identifies the optimal APK asset for the current device architecture.
     */
    private fun findBestApkAsset(releaseJson: JSONObject): Triple<String, String, Long> {
        val assets = releaseJson.optJSONArray("assets") ?: return Triple("", "", 0L)
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
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
     * Downloads the APK file to cache directory with progress reporting.
     */
    suspend fun downloadApk(
        context: Context,
        info: ReleaseInfo,
        onProgress: (bytesRead: Long, totalBytes: Long, speedBps: Long, etaSeconds: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) updatesDir.mkdirs()

        val safeName = info.assetName.ifBlank { "Hazel-v${info.version}.apk" }
        val targetFile = File(updatesDir, safeName)

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "Hazel-App-Updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Download failed with HTTP ${response.code}")
            val body = response.body
            val totalBytes = if (info.binarySize > 0) info.binarySize else body.contentLength()

            val buffer = ByteArray(8192)
            var bytesReadTotal = 0L
            var lastSampleTime = System.currentTimeMillis()
            var lastSampleBytes = 0L
            var currentSpeed = 0L

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesReadTotal += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastSampleTime
                        if (elapsed >= 400) {
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
            onProgress(bytesReadTotal, totalBytes, currentSpeed, 0L)
        }

        targetFile
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
     * Accurately requests unknown sources permission on API 26+ if not yet granted,
     * ensuring installations never fail silently across all supported Android SDKs.
     */
    fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(settingsIntent)
                    return
                } catch (_: Exception) {
                    // Fallback to direct install intent
                }
            }
        }

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
