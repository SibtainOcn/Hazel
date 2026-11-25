package com.hazel.android.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.HistoryEntry
import com.hazel.android.download.DownloadViewModelHolder
import com.hazel.android.gallery.queue.GalleryQueue
import com.hazel.android.ui.components.CardTag
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize

/** What is downloading now, from either engine: its title and share done, null while unknown. */
internal data class ActiveDownload(val title: String, val progress: Float?)

/**
 * The download in progress, at the top of the Downloads screen; a tap opens the Queue's
 * Running tab. Reads the run's state here only, so progress ticks redraw this card alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActiveDownloadCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state by DownloadViewModelHolder.get().state.collectAsState()
    val jobs by GalleryQueue.jobs.collectAsState()
    val active = state.active?.takeIf { state.isDownloading }?.let {
        ActiveDownload(it.title.ifBlank { it.url }, state.progress.takeUnless { state.unmeasured })
    } ?: jobs.firstOrNull { it.state == GalleryQueue.State.RUNNING }?.let { job ->
        val total = job.request.files?.size ?: job.request.indexes?.size ?: 0
        ActiveDownload(
            job.request.title.ifBlank { job.request.url },
            if (total > 0) job.fetched.toFloat() / total else null
        )
    } ?: return
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = scheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(scheme.onPrimaryContainer.copy(alpha = 0.12f), MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, tint = scheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        active.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    active.progress?.let {
                        Text(
                            "${(it.coerceIn(0f, 1f) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onPrimaryContainer,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val track = scheme.onPrimaryContainer.copy(alpha = 0.15f)
                val bar = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                val progress = active.progress
                if (progress == null) {
                    LinearProgressIndicator(modifier = bar, color = scheme.primary, trackColor = track)
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = bar,
                        color = scheme.primary,
                        trackColor = track,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

/**
 * The newest finished downloads as a row of cards to swipe through. The list's own order,
 * filters and picking apply, so it is the start of what the list shows.
 */
@Composable
internal fun RecentCarousel(
    entries: List<HistoryEntry>,
    presence: (HistoryEntry) -> Boolean,
    marks: (HistoryEntry) -> Boolean?,
    actionsFor: (HistoryEntry) -> EntryActions,
    sidePadding: Dp
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.history_recent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        // Edge to edge: the list's side padding becomes the row's, so cards scroll off the screen edge.
        LazyRow(
            modifier = Modifier.layout { measurable, constraints ->
                val extra = (sidePadding * 2).roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra)
                )
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
            },
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                RecentCard(entry, presence(entry), marks(entry), actionsFor(entry))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecentCard(entry: HistoryEntry, present: Boolean, mark: Boolean?, actions: EntryActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(236.dp)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = { if (mark != null || present) actions.onOpen() },
                onLongClick = actions.onHold
            )
    ) {
        Artwork(entry, present, Modifier.matchParentSize())
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
        )
        if (mark == true) {
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)))
        }
        // At least the card's height, growing with large text instead of drawing over itself.
        Column(
            modifier = Modifier.heightIn(min = 148.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.22f), MaterialShapes.Cookie6Sided.toShape()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(kindIcon(entry), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                entry.fileName.substringAfterLast('.', "").takeIf { it.length in 1..5 }?.let {
                    CardTag(it.uppercase(), modifier = Modifier.padding(end = 6.dp))
                }
                if (mark != null) SelectMark(mark, onImage = true)
                else Box {
                    CookieMenuButton(onClick = { menuOpen = true })
                    EntryMenu(menuOpen, present, actions) { menuOpen = false }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = listOfNotNull(
                    entry.author.takeIf { it.isNotBlank() },
                    formatDuration(entry.durationSeconds).takeIf { it.isNotBlank() }
                        ?: entry.sizeBytes.takeIf { it > 0 }?.let(::formatFileSize)
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!present) DeletedTag()
            }
        }
    }
}

/** An entry's options button: three dots on a tonal six-sided cookie. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CookieMenuButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(MaterialShapes.Cookie6Sided.toShape())
            .background(scheme.primaryContainer)
            .clickable(onClick = onClick, onClickLabel = stringResource(R.string.history_card_options)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.history_card_options),
            modifier = Modifier.size(20.dp),
            tint = scheme.onPrimaryContainer
        )
    }
}
