package com.hazel.android.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.ui.components.ArtworkPlaceholder
import com.hazel.android.ui.screens.download.CornerTag
import com.hazel.android.ui.theme.tonalFill

/** One file of a post, drawn like a video card on Home: title, author, a corner tag and one action. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GalleryCard(
    item: GalleryItem,
    total: Int,
    onOpen: () -> Unit,
    onView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = tonalFill(0.35f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpen)
        ) {
            ArtworkPlaceholder(Modifier.matchParentSize())
            item.thumbnail?.let { thumbnail ->
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            0.4f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 12.dp, end = 14.dp)
            ) {
                Text(
                    item.title.ifBlank { stringResource(if (item.isVideo) R.string.gallery_video else R.string.gallery_image) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        item.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                CornerTag(text = if (total > 1) "${item.extension.uppercase()} · ${item.index} / $total" else item.extension.uppercase())
            }
            val action = stringResource(if (item.isVideo) R.string.player_play else R.string.gallery_view)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(44.dp)
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClickLabel = action, onClick = onView),
                contentAlignment = Alignment.Center
            ) {
                if (item.isVideo) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = action, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_fullscreen_item), contentDescription = action, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
