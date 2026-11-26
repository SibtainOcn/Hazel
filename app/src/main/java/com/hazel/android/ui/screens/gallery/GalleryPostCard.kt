package com.hazel.android.ui.screens.gallery

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.ui.components.ArtworkPlaceholder
import com.hazel.android.ui.screens.download.CornerTag
import com.hazel.android.ui.theme.tonalFill

/**
 * One post of a result holding several: who posted it, how many files, and its files in an
 * M3 multi-browse carousel. Tapping the card or a file opens the post as cards.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GalleryPostCard(
    post: GalleryPost,
    /** Opens the viewer on the file at this place in the post. */
    onOpen: (position: Int) -> Unit,
    onDownloadAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val count = post.items.size
    // A highlight is named after itself, not its author.
    val highlight = post.section == com.hazel.android.gallery.ProfileSection.IG_HIGHLIGHTS
    val heading = post.items.firstOrNull()?.title?.takeIf { highlight && it.isNotBlank() } ?: post.author.ifBlank { post.site }
    Surface(
        onClick = { onOpen(0) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = tonalFill(0.35f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(colors.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        heading.firstOrNull()?.uppercase() ?: "#",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        heading,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (post.site.isNotBlank()) {
                        Text(
                            if (highlight) stringResource(R.string.profile_highlight_of, post.site.replaceFirstChar { it.uppercase() })
                            else post.site.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                post.section?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    SectionTag(stringResource(it.label))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // The count sits on the carousel, so a long name and a large font keep the header readable.
            val state = rememberCarouselState { count }
            Box {
                HorizontalMultiBrowseCarousel(
                    state = state,
                    preferredItemWidth = 186.dp,
                    itemSpacing = 8.dp,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                ) { i ->
                    val item = post.items[i]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .maskClip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant)
                            .clickable { onOpen(i) }
                    ) {
                        ArtworkPlaceholder(Modifier.fillMaxSize())
                        item.thumbnail?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (i == 0) {
                            Box(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                CornerTag(text = "1 / $count")
                            }
                        }
                        if (item.isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(44.dp)
                                    .clip(MaterialShapes.Cookie6Sided.toShape())
                                    .background(colors.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.gallery_video),
                                    tint = colors.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostDownloadAllChip(onClick = onDownloadAll, modifier = Modifier.weight(1f, fill = false))
                Spacer(modifier = Modifier.width(12.dp))
                PageDots(count = count, current = state.currentItem)
            }
        }
    }
}

/** The post card's Download all: a compact tonal chip, so it fits beside the dots in any language. */
@Composable
private fun PostDownloadAllChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // The accent's own tint, as the sheets' link button has, rather than the stock tonal colour.
    val accent = MaterialTheme.colorScheme.primary
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = accent.copy(alpha = 0.18f),
            contentColor = accent
        ),
        contentPadding = PaddingValues(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            stringResource(R.string.download_download_all),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize)
        )
    }
}

/** Where the carousel is: one dot per file up to [MAX_DOTS], the current one drawn long. */
@Composable
private fun PageDots(count: Int, current: Int) {
    if (count < 2) return
    val shown = minOf(count, MAX_DOTS)
    val active = if (count <= MAX_DOTS) current else (current * (shown - 1)) / (count - 1)
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(shown) { i ->
            val width by animateDpAsState(if (i == active) 16.dp else 6.dp, label = "dot")
            Box(
                modifier = Modifier
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (i == active) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

private const val MAX_DOTS = 6
