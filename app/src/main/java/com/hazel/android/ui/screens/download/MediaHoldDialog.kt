package com.hazel.android.ui.screens.download

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.components.ArtworkPlaceholder
import com.hazel.android.ui.theme.glassBackdrop
import com.hazel.android.ui.theme.tonalFill

/**
 * What a long press on a home card offers for that link: play it in the large player,
 * open the download sheet, and, for a copy still on the device, open it in another app or
 * show its properties. An action left null is not shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaHoldDialog(
    info: MediaInfo,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)?,
    onDownload: () -> Unit,
    onOpen: (() -> Unit)?,
    onProperties: (() -> Unit)?
) {
    val shape = RoundedCornerShape(28.dp)
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.glassBackdrop(shape)
        ) {
            Column(Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    ArtworkPlaceholder(Modifier.matchParentSize())
                    if (info.thumbnail != null) {
                        AsyncImage(
                            model = info.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    info.title.ifBlank { info.url },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                if (info.uploader.isNotBlank()) {
                    Text(
                        info.uploader,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val accent = MaterialTheme.colorScheme.primary
                    val onAccent = MaterialTheme.colorScheme.onPrimary
                    val tonal = tonalFill(0.55f)
                    val onTonal = MaterialTheme.colorScheme.onSurface
                    onPlay?.let {
                        HoldAction(MaterialShapes.Pentagon, Icons.Filled.PlayArrow, R.string.player_play, accent, onAccent, it)
                    }
                    HoldAction(MaterialShapes.Cookie7Sided, Icons.Filled.Download, R.string.format_sheet_download, tonal, onTonal, onDownload)
                    onOpen?.let {
                        HoldAction(MaterialShapes.Clover4Leaf, Icons.AutoMirrored.Filled.OpenInNew, R.string.sheet_link_open, tonal, onTonal, it)
                    }
                    onProperties?.let {
                        HoldAction(MaterialShapes.Cookie4Sided, Icons.Filled.Info, R.string.history_card_properties, tonal, onTonal, it)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HoldAction(
    polygon: RoundedPolygon,
    icon: ImageVector,
    @StringRes label: Int,
    container: Color,
    content: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = polygon.toShape(),
            color = container,
            contentColor = content,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
