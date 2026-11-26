package com.hazel.android.util

import android.content.Context
import androidx.annotation.StringRes
import com.hazel.android.R
import com.hazel.android.data.CookieRepository
import com.hazel.android.download.InfoCache
import com.hazel.android.download.YtDlpEngine
import com.hazel.android.gallery.GalleryLogs
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
    @param:StringRes val labelRes: Int,
    @param:StringRes val descriptionRes: Int,
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
 *
 * The engine (Python, yt-dlp, ffmpeg) is not listed: it is unpacked once into no-backup
 * storage and marked as done, so removing it would leave the app unable to download.
 */
object TempStorage {

    /** The formats of links read lately, kept so opening one again needs no read. */
    const val LINK_READS = "link_reads"

    const val HISTORY_THUMBS = "history_thumbs"

    /** gallery-dl's work folders, its cache and its run logs. */
    const val GALLERY = "gallery"

    private const val GALLERY_WORK_PREFIX = "gallery-"
    private const val GALLERY_CACHE = "gallery-dl.sqlite3"

    /** Folders in the cache that have a category of their own, not counted as "other". */
    private val OWN_CATEGORY = setOf(
        "yt-dlp", InfoCache.DIRECTORY_NAME, HistoryThumbnails.DIRECTORY_NAME,
        GalleryLogs.DIRECTORY_NAME, GALLERY_CACHE, "$GALLERY_CACHE-journal"
    )

    suspend fun categories(context: Context): List<TempCategory> = withContext(Dispatchers.IO) {
        val cache = context.cacheDir

        listOf(
            TempCategory(
                id = "ytdlp_cache",
                labelRes = R.string.cleanup_category_link_data_label,
                descriptionRes = R.string.cleanup_category_link_data_description,
                bytes = sizeOf(File(cache, "yt-dlp"))
            ),
            TempCategory(
                id = LINK_READS,
                labelRes = R.string.cleanup_category_link_reads_label,
                descriptionRes = R.string.cleanup_category_link_reads_description,
                bytes = InfoCache.diskBytes()
            ),
            TempCategory(
                id = HISTORY_THUMBS,
                labelRes = R.string.cleanup_category_history_thumbs_label,
                descriptionRes = R.string.cleanup_category_history_thumbs_description,
                bytes = HistoryThumbnails.sizeBytes(context)
            ),
            TempCategory(
                id = "partial_downloads",
                labelRes = R.string.cleanup_category_partial_downloads_label,
                descriptionRes = R.string.cleanup_category_partial_downloads_description,
                // Counted on the SD card as well: a download saved to a card works there.
                bytes = partialSize(SdCards.workRoots(context))
            ),
            TempCategory(
                id = GALLERY,
                labelRes = R.string.cleanup_category_gallery_label,
                descriptionRes = R.string.cleanup_category_gallery_description,
                bytes = galleryFiles(SdCards.workRoots(context), cache).sumOf { sizeOf(it) }
            ),
            TempCategory(
                id = "converted",
                labelRes = R.string.cleanup_category_conversions_label,
                descriptionRes = R.string.cleanup_category_conversions_description,
                bytes = sizeOf(StoragePaths.tempConverted)
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
     * Removes one category. False, with nothing removed, when the category is in use by a
     * running download.
     *
     * The cookie file lives in the cache directory but is user data rather than a working
     * file, so it is preserved when the surrounding cache is cleared. Losing a sign-in to
     * a cleanup would be a surprise, and it is not what takes up the space.
     */
    suspend fun clear(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val cache = context.cacheDir

        when (id) {
            // gallery-dl runs hold the engine's shared lock while they use these files.
            GALLERY -> return@withContext YtDlpEngine.whenIdle {
                com.hazel.android.gallery.GallerySpare.discard()
                galleryFiles(SdCards.workRoots(context), cache).forEach { runCatching { it.deleteRecursively() } }
            } != null
            "ytdlp_cache" -> wipe(File(cache, "yt-dlp"))
            // Through the cache itself, so what it holds in memory goes with the files.
            LINK_READS -> InfoCache.clear()
            HISTORY_THUMBS -> HistoryThumbnails.clearAll(context)
            "partial_downloads" -> SdCards.workRoots(context).forEach { root ->
                root.listFiles()?.filterNot { it.isGalleryWork() }?.forEach { runCatching { it.deleteRecursively() } }
            }
            "converted" -> wipe(StoragePaths.tempConverted)
            "other_cache" -> {
                cache.listFiles()?.forEach { entry ->
                    // Saved sign-ins live here too, one file per site, and clearing the
                    // cache is not a request to sign out of everything.
                    if (entry.name !in OWN_CATEGORY && !entry.isCookieFile()) {
                        entry.deleteRecursively()
                    }
                }
            }
        }
        true
    }

    /** Unfinished downloads in [roots], without gallery work folders, which count under [GALLERY]. */
    internal fun partialSize(roots: List<File>): Long =
        roots.sumOf { root -> root.listFiles()?.filterNot { it.isGalleryWork() }?.sumOf { sizeOf(it) } ?: 0L }

    /** Every file of the [GALLERY] category. The payload and the download record are not here. */
    internal fun galleryFiles(workRoots: List<File>, cache: File): List<File> =
        workRoots.flatMap { root -> root.listFiles()?.filter { it.isGalleryWork() }.orEmpty() } +
            listOf(File(cache, GALLERY_CACHE), File(cache, "$GALLERY_CACHE-journal"), File(cache, GalleryLogs.DIRECTORY_NAME))
                .filter { it.exists() }

    private fun File.isGalleryWork(): Boolean = isDirectory && name.startsWith(GALLERY_WORK_PREFIX)

    /** The whole cookie file and the per-site ones written beside it. */
    private fun java.io.File.isCookieFile(): Boolean = name.startsWith("cookies")

    /** Everything in the cache that is not already counted under its own category. */
    private fun otherCacheSize(context: Context): Long {
        return context.cacheDir.listFiles()
            ?.filterNot { it.name in OWN_CATEGORY || it.isCookieFile() }
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
