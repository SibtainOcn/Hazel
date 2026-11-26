package com.hazel.android.ui.screens.gallery

import com.hazel.android.ui.components.CaptionSheet
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.ui.screens.download.SheetDownloadPill
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.ui.components.ShapeIconButton
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val MAX_ZOOM = 5f

/** The files of a post edge to edge: swipe between them, pinch to zoom, save the one in view. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GalleryViewer(
    items: List<GalleryItem>,
    startIndex: Int,
    /** Opens the download sheet for the file in view. */
    onOpenSheet: (GalleryItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    val strip = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { strip.animateScrollToItem((pager.currentPage - 2).coerceAtLeast(0)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.85f }
                ) {
                    items.getOrNull(page)?.let { ViewerPage(item = it, active = page == pager.currentPage) }
                }
            }

            // The results can shrink under an open viewer (a new search), so the page is clamped.
            val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
            val hasCaption = current.caption.isNotBlank() && current.caption != current.title
            var captionOpen by remember { mutableStateOf(false) }
            val openCaption = stringResource(R.string.caption_view_full)

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeIconButton(onClick = onDismiss, size = 44.dp) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.gallery_close))
                    }
                    Text(
                        "${pager.currentPage + 1} / ${items.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // Keeps the count centred now that the close button has nothing opposite it.
                    Spacer(modifier = Modifier.size(44.dp))
                }
                if (hasCaption) {
                    Text(
                        stringResource(R.string.gallery_caption_tap_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 12.dp)
            ) {
                // The single sheet's own Download pill: it opens that sheet for the file in view.
                // It moves under the details only where they would not fit beside it.
                SheetHeaderLayout(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // The title and its details open the caption too, a larger target than its two lines.
                    Column(
                        modifier = Modifier.clickable(enabled = hasCaption, onClickLabel = openCaption) { captionOpen = true }
                    ) {
                        if (current.title.isNotBlank()) {
                            Text(
                                current.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            listOfNotNull(
                                current.author.takeIf { it.isNotBlank() },
                                current.extension.uppercase().takeIf { it.isNotBlank() },
                                current.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width} × ${it.height}" }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SheetDownloadPill(
                        label = stringResource(R.string.format_sheet_download),
                        icon = Icons.Filled.Download,
                        enabled = true,
                        onClick = { onOpenSheet(current) },
                        iconOnly = true
                    )
                }
                // The caption's start; a tap opens all of it.
                if (hasCaption) {
                    Text(
                        current.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = openCaption) { captionOpen = true }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                if (captionOpen) CaptionSheet(current.caption, current.title, onDismiss = { captionOpen = false })
                if (items.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        state = strip,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(items) { index, item ->
                            val selected = index == pager.currentPage
                            val scale by animateFloatAsState(if (selected) 1.1f else 1f, label = "thumb")
                            val fade by animateFloatAsState(if (selected) 1f else 0.5f, label = "thumbAlpha")
                            AsyncImage(
                                model = item.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable { scope.launch { pager.animateScrollToPage(index) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerPage(item: GalleryItem, active: Boolean) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(active) {
        if (!active) {
            zoom = 1f
            pan = Offset.Zero
        }
    }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        pan = if (zoom > 1f) pan + panChange else Offset.Zero
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    zoom = if (zoom > 1f) 1f else 2.5f
                    pan = Offset.Zero
                })
            }
            .transformable(transform, canPan = { zoom > 1f }),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = if (item.isVideo) item.thumbnail else item.url,
            contentDescription = item.title.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }
        )
        if (item.isVideo) {
            val stream = item.playUrl ?: item.url.takeIf { it.startsWith("http") }
            if (stream != null) GalleryVideo(url = stream, active = active)
        }
    }
}
