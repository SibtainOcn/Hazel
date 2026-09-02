package com.hazel.android.ui.screens.download.batch

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatDuration
import com.hazel.android.ui.screens.download.BadgeTone
import com.hazel.android.ui.screens.download.MetaBadge

/**
 * One link in the set: artwork, title, and what it will download.
 *
 * Tapping the card is how a single link is adjusted, so it opens that link's own format
 * list rather than ticking anything. Ticking is a separate mode, reached by holding a card
 * or from the list menu, and only then does a checkbox appear: outside it every link is
 * already going to be downloaded and a column of empty boxes would say nothing.
 *
 * The trailing button switches just this link between audio and video, which is the one
 * change common enough to be worth a control of its own on the row. Swiping takes the link
 * out of the results entirely, which is a different thing from unticking it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BatchDownloadCard(
    info: MediaInfo,
    formatLabel: String,
    sizeLabel: String,
    isVideo: Boolean,
    isAdjusted: Boolean,
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTypeClick: () -> Unit,
    onRemove: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Fills the row exactly, so nothing of it shows until the row is actually
            // dragged aside.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    "Remove",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            // Opaque: a translucent row would let the swipe background show through it
            // while the row is sitting still.
            color = batchRaisedColor
        ) {
            Row(
                modifier = Modifier
                    .combinedClickable(onLongClick = onLongClick, onClick = onClick)
                    .padding(start = if (selectionMode) 4.dp else 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(checked = checked, onCheckedChange = { onClick() })
                }

                Box(
                    modifier = Modifier
                        .width(88.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (info.thumbnail != null) {
                        AsyncImage(
                            model = info.thumbnail,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    val duration = formatDuration(info.durationSeconds)
                    if (duration.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp),
                            shape = RoundedCornerShape(3.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(
                        info.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (formatLabel.isNotBlank()) {
                            // Marked when the link carries a choice of its own, so a set
                            // where one link was changed does not look uniform.
                            MetaBadge(
                                formatLabel.uppercase(),
                                tone = if (isAdjusted) BadgeTone.SOLID else BadgeTone.NEUTRAL
                            )
                        }
                        if (sizeLabel.isNotBlank()) MetaBadge(sizeLabel, tone = BadgeTone.SIZE)
                    }
                }

                IconButton(onClick = onTypeClick) {
                    Icon(
                        if (isVideo) Icons.Filled.Videocam else Icons.Filled.MusicNote,
                        contentDescription = "Download type for this link",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
