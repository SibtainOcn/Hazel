package com.hazel.android.ui.share

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.gallery.GallerySpare
import com.hazel.android.gallery.queue.GalleryDownloadRequest
import com.hazel.android.ui.components.ArtworkPlaceholder
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.ui.components.ShimmerLabel
import com.hazel.android.ui.screens.download.NoResultsDialog
import com.hazel.android.ui.screens.download.SheetDownloadPill
import com.hazel.android.ui.screens.gallery.GalleryItemSheet
import com.hazel.android.ui.screens.gallery.GalleryPostSheet
import com.hazel.android.ui.screens.gallery.dialogKind
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.theme.sheetBlackColor
import com.hazel.android.util.copyToClipboard
import com.hazel.android.util.siteRootOf
import kotlinx.coroutines.CancellationException

private sealed interface GalleryRead {
    data object Reading : GalleryRead
    data class Ready(val post: GalleryPost) : GalleryRead
    data class Failed(val error: GalleryDlException) : GalleryRead
}

/**
 * Hazel Images over the app a link was shared from: a reading sheet while gallery-dl reads
 * the link, then the gallery sheet for the first post, or the failure with its log.
 */
@Composable
internal fun GalleryShareContent(
    url: String,
    onDownload: (GalleryDownloadRequest) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
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

    when (val state = read) {
        GalleryRead.Reading -> GalleryReadingSheet(onDismiss = onClose)
        is GalleryRead.Failed -> {
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
        is GalleryRead.Ready -> {
            val post = state.post
            val single = post.items.singleOrNull()
            if (single != null) {
                GalleryItemSheet(
                    item = single,
                    total = 1,
                    postUrl = post.source,
                    onDownload = { options -> onDownload(requestFor(post, listOf(single.index), options)) },
                    onDismiss = onClose
                )
            } else {
                GalleryPostSheet(
                    items = post.items,
                    postUrl = post.source,
                    onDownload = { indexes, options -> onDownload(requestFor(post, indexes, options)) },
                    onDismiss = onClose
                )
            }
        }
    }
}

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

private fun requestFor(post: GalleryPost, indexes: List<Int>?, options: com.hazel.android.gallery.GalleryOptions): GalleryDownloadRequest {
    val first = indexes?.firstOrNull()?.let { i -> post.items.firstOrNull { it.index == i } } ?: post.items.firstOrNull()
    return GalleryDownloadRequest(
        url = post.source,
        indexes = indexes,
        options = options,
        title = first?.title.orEmpty(),
        author = post.author,
        thumbnail = first?.thumbnail ?: first?.url?.takeUnless { first.isVideo }
    )
}

/** The gallery sheet's header and preview while the link is read, as the video overlay shows it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryReadingSheet(onDismiss: () -> Unit) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBlackColor) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SheetHeaderLayout {
                Column {
                    ShimmerLabel(
                        stringResource(R.string.format_sheet_fetching),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.format_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                SheetDownloadPill(
                    label = stringResource(R.string.format_sheet_download),
                    icon = Icons.Filled.Download,
                    enabled = false,
                    onClick = {}
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ArtworkPlaceholder(Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
