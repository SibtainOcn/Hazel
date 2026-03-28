package com.hazel.android.data

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases API for newer versions.
 * Parses release assets to find the correct APK download URL.
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/SibtainOcn/Hazel/releases/latest"

    data class UpdateInfo(
        val version: String,
        val notes: String,
        val htmlUrl: String,          // GitHub release page (fallback)
        val apkDownloadUrl: String?,  // Direct APK download URL from assets
        val apkSize: Long             // APK size in bytes (0 if unknown)
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Returns [UpdateInfo] if a newer version exists on GitHub, null otherwise.
     * Safe to call from any coroutine context — switches to IO internally.
     */
    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").trimStart('v', 'V')
            val notes = json.optString("body", "").take(500)
            val htmlUrl = json.optString("html_url", "")

            if (tagName.isBlank() || htmlUrl.isBlank()) return@withContext null
            if (!isNewer(tagName, currentVersion)) return@withContext null

            // Parse release assets to find the APK download URL
            val (apkUrl, apkSize) = findApkAsset(json)

            UpdateInfo(
                version = tagName,
                notes = notes,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkSize = apkSize
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Searches release assets for the best APK to download.
     * Priority: device-specific ABI -> universal -> any .apk
     */
    private fun findApkAsset(releaseJson: JSONObject): Pair<String?, Long> {
        val assets = releaseJson.optJSONArray("assets") ?: return null to 0L
        val deviceAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        var bestUrl: String? = null
        var bestSize: Long = 0
        var universalUrl: String? = null
        var universalSize: Long = 0
        var anyApkUrl: String? = null
        var anyApkSize: Long = 0

        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "").lowercase()
            val url = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0)

            if (!name.endsWith(".apk") || url.isBlank()) continue

            // Track any APK as last resort
            if (anyApkUrl == null) {
                anyApkUrl = url
                anyApkSize = size
            }

            // Check for device-specific ABI match
            if (name.contains(deviceAbi)) {
                bestUrl = url
                bestSize = size
            }

            // Check for universal APK
            if (name.contains("universal")) {
                universalUrl = url
                universalSize = size
            }
        }

        // Priority: device ABI -> universal -> any APK
        return when {
            bestUrl != null -> bestUrl to bestSize
            universalUrl != null -> universalUrl to universalSize
            anyApkUrl != null -> anyApkUrl to anyApkSize
            else -> null to 0L
        }
    }

    /**
     * Simple semantic version comparison: "1.1.0" > "1.0.0"
     */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(r.size, c.size)
        for (i in 0 until maxLen) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }
}
