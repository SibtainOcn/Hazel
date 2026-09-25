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
     * Queries GitHub releases API and finds the latest release matching the channel.
     */
    suspend fun latestRelease(channel: Channel): ReleaseInfo? = withContext(Dispatchers.IO) {
        if (isFdroid()) return@withContext null
        try {
            val url = "https://api.github.com/repos/$REPO_NAME/releases"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Hazel-App-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val releases = JSONArray(bodyStr)

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
                        return@withContext ReleaseInfo(
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
                null
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
            val body = response.body ?: throw IllegalStateException("Empty response body")
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
        context.startActivity(intent)
    }
}
