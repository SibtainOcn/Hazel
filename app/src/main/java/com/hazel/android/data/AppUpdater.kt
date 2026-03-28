package com.hazel.android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Handles downloading APK updates from GitHub and triggering the system installer.
 * Industry-grade: streaming download with progress, FileProvider-based install,
 * cache cleanup on app start.
 */
object AppUpdater {

    private const val UPDATE_DIR = "updates"
    private const val APK_FILENAME = "hazel-update.apk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Downloads the APK from [url] to the app's cache directory.
     * Calls [onProgress] with (bytesDownloaded, totalBytes) for UI updates.
     * Returns the downloaded [File] on success, null on failure.
     */
    suspend fun downloadUpdate(
        context: Context,
        url: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
            val apkFile = File(updateDir, APK_FILENAME)

            // Delete any previous partial download
            if (apkFile.exists()) apkFile.delete()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body ?: return@withContext null
            val totalBytes = body.contentLength().coerceAtLeast(0)

            body.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        // Check for coroutine cancellation
                        ensureActive()
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, totalBytes)
                    }
                }
            }

            apkFile
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Clean up partial download on cancel
            cleanupUpdates(context)
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Triggers the Android system package installer for the given APK file.
     * Uses FileProvider for API 24+ compatibility.
     */
    fun installUpdate(context: Context, apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
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

    /**
     * Returns the cached update APK if it exists (for "Install Now" after app restart).
     */
    fun getCachedUpdate(context: Context): File? {
        val apkFile = File(File(context.cacheDir, UPDATE_DIR), APK_FILENAME)
        return if (apkFile.exists() && apkFile.length() > 0) apkFile else null
    }

    /**
     * Deletes all cached update APKs. Call on app launch to clean stale updates.
     */
    fun cleanupUpdates(context: Context) {
        val updateDir = File(context.cacheDir, UPDATE_DIR)
        if (updateDir.exists()) {
            updateDir.listFiles()?.forEach { it.delete() }
        }
    }
}
