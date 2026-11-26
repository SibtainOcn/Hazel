package com.hazel.android.ui.share

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hazel.android.R
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.gallery.GallerySpare
import com.hazel.android.gallery.queue.GalleryDownloadRequest
import com.hazel.android.gallery.queue.request
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.screens.download.NoResultsDialog
import com.hazel.android.ui.screens.gallery.GalleryItemSheet
import com.hazel.android.ui.screens.gallery.GalleryPostSheet
import com.hazel.android.ui.screens.gallery.WarmGalleryOptions
import com.hazel.android.ui.screens.gallery.dialogKind
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.theme.sheetBlackColor
import com.hazel.android.util.copyToClipboard
import com.hazel.android.util.siteRootOf
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.ui.screens.gallery.GallerySheetSkeleton
import com.hazel.android.ui.screens.gallery.GalleryVideoLinks
import com.hazel.android.ui.screens.gallery.GalleryVideoSheet
import com.hazel.android.ui.screens.gallery.LocalInHostSheet
import com.hazel.android.ui.screens.gallery.PostVideos
import kotlinx.coroutines.Job

private sealed interface GalleryRead {
    data object Reading : GalleryRead
    data class Ready(val post: GalleryPost) : GalleryRead
    data class Failed(val error: GalleryDlException) : GalleryRead
}

/**
 * Hazel Gallery over the app a link was shared from. One sheet shows the image sheet's
 * skeleton while gallery-dl reads the link and then fills in place with the image sheet, or
 * Download all for a post of several files. Videos open the yt-dlp sheets; a failure shows
 * its log.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryShareContent(
    url: String,
    /**
     * Files for gallery-dl's queue, and the job handing videos to yt-dlp's, which ends once
     * each is queued or found missing.
     */
    onQueued: (requests: List<GalleryDownloadRequest>, videos: Job?) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    WarmGalleryOptions()
    var read by remember(url) { mutableStateOf<GalleryRead>(GalleryRead.Reading) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    // Signing in from the failure dialog reads the link again with the new cookies.
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            read = GalleryRead.Reading
            attempt++
        } else {
            onClose()
        }
    }
    LaunchedEffect(url, attempt) {
        read = try {
            GalleryRead.Ready(readFirstPost(context.applicationContext, url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: GalleryDlException) {
            GalleryRead.Failed(e)
        } catch (e: Exception) {
            GalleryRead.Failed(GalleryDlException(GalleryDlException.Kind.OTHER, e.javaClass.simpleName, e))
        }
    }
    val links = GalleryVideoLinks.forShares
    val onMissed: (Int) -> Unit = {
        Toast.makeText(context, R.string.gallery_video_unavailable, Toast.LENGTH_SHORT).show()
    }

    val state = read
    val videos = (state as? GalleryRead.Ready)?.post?.let(::videosOnly)
    when {
        state is GalleryRead.Failed -> {
            val log = state.error.log.ifBlank { stringResource(state.error.kind.message) }
            NoResultsDialog(
                message = log,
                link = url,
                knownKind = dialogKind(state.error.kind),
                canFetchCookies = true,
                canAddCookies = true,
                canContinue = false,
                onCopyLog = {
                    copyToClipboard(context, log)
                    Toast.makeText(context, context.getString(R.string.history_failed_log_copied), Toast.LENGTH_SHORT).show()
                },
                onGetCookies = {
                    signInLauncher.launch(CookieWebViewActivity.intent(context, siteRootOf(url)))
                },
                onContinueAnyway = {},
                onDismiss = onClose
            )
        }
        state is GalleryRead.Ready && videos != null -> GalleryVideoSheet(
            post = state.post,
            items = videos,
            links = links,
            onQueued = { job -> onQueued(emptyList(), job) },
            onMissed = onMissed,
            onDismiss = onClose
        )
        else -> {
            val sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
            ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = sheetBlackColor) {
                CompositionLocalProvider(LocalInHostSheet provides true) {
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it::class },
                        transitionSpec = { fadeIn(tween(FILL_IN_MS)) togetherWith fadeOut(tween(FILL_IN_MS / 2)) },
                        label = "galleryShareSheet"
                    ) { shown ->
                        val post = (shown as? GalleryRead.Ready)?.post
                        val single = post?.items?.singleOrNull()
                        when {
                            post == null -> GallerySheetSkeleton()
                            single != null -> GalleryItemSheet(
                                item = single,
                                postUrl = post.source,
                                onDownload = { options, title, author, save ->
                                    onQueued(listOf(requestFor(post, listOf(single.index), options, title, author, save)), null)
                                },
                                onDismiss = onClose
                            )
                            else -> GalleryPostSheet(
                                items = post.items,
                                postUrl = post.source,
                                postVideos = PostVideos(post, links, onMissed),
                                onDownload = { picks, job ->
                                    onQueued(picks.map { requestFor(post, it.indexes, it.options, it.title, it.author) }, job)
                                },
                                onDismiss = onClose
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val FILL_IN_MS = 240

/** The post's files when they are all videos, which the yt-dlp sheets take; null otherwise. */
internal fun videosOnly(post: GalleryPost): List<GalleryItem>? =
    post.items.takeIf { it.isNotEmpty() && it.all(GalleryItem::isVideo) }

/** The first post the link holds; a profile or tag page is followed to its first post. */
private suspend fun readFirstPost(context: Context, url: String): GalleryPost {
    GallerySpare.warm(context)
    val listing = GalleryDlEngine.list(context, url)
    val post = GalleryPost.group(url, listing.items).firstOrNull()
        ?: listing.posts.firstOrNull()?.let { link ->
            GalleryPost.group(link, GalleryDlEngine.list(context, link).items).firstOrNull()
        }
    return post ?: throw GalleryDlException(GalleryDlException.Kind.NOT_FOUND, "no files")
}

private fun requestFor(
    post: GalleryPost,
    indexes: List<Int>?,
    options: com.hazel.android.gallery.GalleryOptions,
    title: String?,
    author: String?,
    save: com.hazel.android.gallery.GallerySave = com.hazel.android.gallery.GallerySave.MEDIA
): GalleryDownloadRequest = post.request(indexes, options, title, author, save)
