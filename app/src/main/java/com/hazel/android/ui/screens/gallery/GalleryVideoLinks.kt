package com.hazel.android.ui.screens.gallery

import android.content.Context
import android.util.Log
import com.hazel.android.HazelApp
import com.hazel.android.data.SaveDirs
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadPlan
import com.hazel.android.download.DownloadViewModelHolder
import com.hazel.android.download.MediaInfo
import com.hazel.android.gallery.FileLinks
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One video for yt-dlp: its read (the file or page it downloads), what was picked, and the settings. */
internal class VideoDownload(val read: Deferred<MediaInfo?>, val plan: DownloadPlan, val options: DownloadOptions)

/**
 * Links for gallery videos, shared by the Gallery tab and the share sheet: one that plays, and
 * what the yt-dlp sheet downloads. [scope] must run on the main thread, where the maps live.
 */
internal class GalleryVideoLinks(private val scope: CoroutineScope, context: Context) {

    private val app = context.applicationContext

    /** Fresh links by the file's listed url, kept while the app runs; saved results keep theirs. */
    private val freshLinks = mutableMapOf<String, String>()
    private val linkReads = mutableMapOf<String, Deferred<Map<String, String>>>()

    /**
     * A playable link for [item] that has not expired: the one it was listed with, or one from
     * reading [post] again (saved results can predate the link, and the sites' links expire).
     * [failed] is a link that just stopped working. A post is read once for all its files, and
     * once at a time; the read finishes in [scope] even when its caller leaves.
     */
    suspend fun playableUrl(post: GalleryPost, item: GalleryItem, failed: String? = null): String? {
        (freshLinks[item.url] ?: item.playUrl)?.takeUnless { FileLinks.isStale(it) || it == failed }?.let { return it }
        val read = linkReads[post.source]?.takeIf { it.isActive } ?: scope.async {
            val fresh = try {
                withContext(Dispatchers.IO) {
                    FileLinks.freshLinks(post.items, GalleryDlEngine.list(app, post.source).items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Hazel", "Gallery link refresh failed: ${e.javaClass.simpleName}")
                emptyMap()
            }
            freshLinks.putAll(fresh)
            linkReads.remove(post.source)
            fresh
        }.also { linkReads[post.source] = it }
        return read.await()[item.url]
    }

    /**
     * What the yt-dlp sheet downloads for [item]: its own page, read in full, else its file
     * link with the post kept for history. Null when neither can be had.
     */
    fun videoInfo(post: GalleryPost, item: GalleryItem, labels: VideoLabels): Deferred<MediaInfo?> = scope.async {
        ownPage(post, item)?.let { page ->
            DownloadViewModelHolder.get().formatsFor(page)?.let { read ->
                return@async read.copy(
                    url = page,
                    title = read.title.ifBlank { videoTitle(item) },
                    thumbnail = read.thumbnail ?: item.thumbnail
                )
            }
        }
        playableUrl(post, item)?.let { fileInfo(item, it, labels) }
    }

    /**
     * Hands each video to the yt-dlp queue once its read lands, in the order picked, each with
     * its own settings, and says how many could not be had. Reads already in hand start at
     * once, before this returns.
     */
    fun download(picks: List<VideoDownload>, saveDirs: SaveDirs, onMissed: (Int) -> Unit): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val ready = picks.mapNotNull { pick ->
                val info = try {
                    pick.read.await()
                } catch (e: CancellationException) {
                    ensureActive()
                    null
                }
                info?.let { pick.plan.copy(info = it) to pick.options }
            }
            if (ready.size < picks.size) onMissed(picks.size - ready.size)
            // One run of the queue takes one set of settings, so picks are grouped by theirs.
            ready.groupBy({ it.second }, { it.first }).forEach { (options, plans) ->
                DownloadViewModelHolder.get().startBatch(app, plans, options, saveDirs = saveDirs)
            }
        }

    companion object {
        /** For sheets opened over other apps, whose reads and downloads outlive the sheet. */
        val forShares by lazy {
            GalleryVideoLinks(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), HazelApp.instance)
        }
    }
}
