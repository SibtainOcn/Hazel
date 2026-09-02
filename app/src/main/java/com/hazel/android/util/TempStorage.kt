package com.hazel.android.util

import android.content.Context
import androidx.annotation.StringRes
import com.hazel.android.R
import com.hazel.android.data.CookieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One kind of working file the app leaves behind, and what removing it costs.
 *
 * The name and the explanation are held as resource ids rather than as text. This list is
 * built on a background thread, and resolving the text here would fix it to whichever
 * language was current at that moment. Reading it in the composable instead means the
 * screen follows the device language, including when it changes while the app is open.
 */
data class TempCategory(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val bytes: Long,
    /**
     * True when clearing this makes the app slower rather than losing anything, so the
     * screen can say which entries are free to remove and which have a consequence.
     */
    val safeToClear: Boolean = true
)

/**
 * Finds and removes the working files the app accumulates.
 *
 * Every directory the app writes to outside the user's own downloads is listed here, so
 * nothing can quietly grow without appearing on the cleanup screen. Anything added later
 * that writes to a new location has to be added to [categories] as well, which is the point
 * of keeping the list in one place rather than scattering deletions around the code.
 *
 * The user's downloaded media is never touched: it has been moved into public storage by
 * the time these directories are read.
 */
object TempStorage {

    /**
     * The yt-dlp binary and its Python environment. Clearing this forces the app to unpack
     * them again on next use, so it is offered but marked as having a cost.
     */
    private const val ENGINE_DIR = "youtubedl-android"

    suspend fun categories(context: Context): List<TempCategory> = withContext(Dispatchers.IO) {
        val cache = context.cacheDir
        val files = context.filesDir

        listOf(
            TempCategory(
                id = "ytdlp_cache",
                labelRes = R.string.cleanup_category_link_data_label,
                descriptionRes = R.string.cleanup_category_link_data_description,
                bytes = sizeOf(File(cache, "yt-dlp"))
            ),
            TempCategory(
                id = "partial_downloads",
                labelRes = R.string.cleanup_category_partial_downloads_label,
                descriptionRes = R.string.cleanup_category_partial_downloads_description,
                bytes = sizeOf(StoragePaths.tempDownloads)
            ),
            TempCategory(
                id = "converted",
                labelRes = R.string.cleanup_category_conversions_label,
                descriptionRes = R.string.cleanup_category_conversions_description,
                bytes = sizeOf(StoragePaths.tempConverted)
            ),
            TempCategory(
                id = "engine",
                labelRes = R.string.cleanup_category_engine_label,
                descriptionRes = R.string.cleanup_category_engine_description,
                bytes = sizeOf(File(files, ENGINE_DIR)) + sizeOf(File(cache, ENGINE_DIR)),
                safeToClear = false
            ),
            TempCategory(
                id = "other_cache",
                labelRes = R.string.cleanup_category_other_cache_label,
                descriptionRes = R.string.cleanup_category_other_cache_description,
                bytes = otherCacheSize(context)
            )
        )
    }

    /** Total across every category, for the summary shown on the settings row. */
    suspend fun totalBytes(context: Context): Long =
        categories(context).sumOf { it.bytes }

    /**
     * Removes one category.
     *
     * The cookie file lives in the cache directory but is user data rather than a working
     * file, so it is preserved when the surrounding cache is cleared. Losing a sign-in to
     * a cleanup would be a surprise, and it is not what takes up the space.
     */
    suspend fun clear(context: Context, id: String) = withContext(Dispatchers.IO) {
        val cache = context.cacheDir
        val files = context.filesDir

        when (id) {
            "ytdlp_cache" -> wipe(File(cache, "yt-dlp"))
            "partial_downloads" -> wipe(StoragePaths.tempDownloads)
            "converted" -> wipe(StoragePaths.tempConverted)
            "engine" -> {
                wipe(File(files, ENGINE_DIR))
                wipe(File(cache, ENGINE_DIR))
            }
            "other_cache" -> {
                val preserved = setOf("yt-dlp", ENGINE_DIR)
                cache.listFiles()?.forEach { entry ->
                    // Saved sign-ins live here too, one file per site, and clearing the
                    // cache is not a request to sign out of everything.
                    if (entry.name !in preserved && !entry.isCookieFile()) {
                        entry.deleteRecursively()
                    }
                }
            }
        }
        Unit
    }

    /** The whole cookie file and the per-site ones written beside it. */
    private fun java.io.File.isCookieFile(): Boolean = name.startsWith("cookies")

    /** Everything in the cache that is not already counted under its own category. */
    private fun otherCacheSize(context: Context): Long {
        val counted = setOf("yt-dlp", ENGINE_DIR)
        return context.cacheDir.listFiles()
            ?.filterNot { it.name in counted || it.isCookieFile() }
            ?.sumOf { sizeOf(it) }
            ?: 0L
    }

    /** Deletes a directory's contents but keeps the directory itself. */
    private fun wipe(dir: File) {
        runCatching {
            if (dir.isDirectory) dir.listFiles()?.forEach { it.deleteRecursively() }
            else dir.delete()
        }
    }

    private fun sizeOf(file: File): Long = runCatching {
        when {
            !file.exists() -> 0L
            file.isFile -> file.length()
            else -> file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        }
    }.getOrDefault(0L)
}
