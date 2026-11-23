package com.hazel.android.ui.screens.download

import androidx.annotation.StringRes
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.components.ArtworkPlaceholder
import com.hazel.android.ui.theme.glassBackdrop

/**
 * What a long press on a home card offers for that link: play it in the large player,
 * open the download sheet, and, for a copy still on the device, open it in another app or
 * show its properties. An action left null is not shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaHoldDialog(
    info: MediaInfo,
    onDismiss: () -> Unit,
    onPlay: (() -> Unit)?,
    onDownload: () -> Unit,
    onOpen: (() -> Unit)?,
    onProperties: (() -> Unit)?
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        MediaHoldCard(info, onPlay, onDownload, onOpen, onProperties)
    }
}

/** The hold dialog's card on its own, so it can be drawn outside a dialog window. */
@Composable
internal fun MediaHoldCard(
    info: MediaInfo,
    onPlay: (() -> Unit)?,
    onDownload: () -> Unit,
    onOpen: (() -> Unit)?,
    onProperties: (() -> Unit)?
) {
    val shape = RoundedCornerShape(28.dp)
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
            Spacer(Modifier.height(20.dp))
            HoldActions(onPlay, onDownload, onOpen, onProperties)
        }
    }
}

/**
 * The actions as one connected group: the main one full width on top, the rest as tonal
 * tiles under it sharing the width. Corners are large on the group's outside and small
 * where two buttons meet, and a pressed button squeezes its corners in.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HoldActions(
    onPlay: (() -> Unit)?,
    onDownload: () -> Unit,
    onOpen: (() -> Unit)?,
    onProperties: (() -> Unit)?
) {
    // Without a player the download is the main action, so the card always leads with one.
    val download = HoldItem(MaterialShapes.Cookie9Sided, Icons.Rounded.Download, R.string.format_sheet_download, onDownload)
    val main = onPlay?.let { HoldItem(MaterialShapes.Pentagon, Icons.Rounded.PlayArrow, R.string.player_play, it) } ?: download
    val tiles = listOfNotNull(
        download.takeIf { onPlay != null },
        onOpen?.let { HoldItem(MaterialShapes.Sunny, Icons.AutoMirrored.Rounded.OpenInNew, R.string.sheet_link_open, it) },
        onProperties?.let { HoldItem(MaterialShapes.Cookie6Sided, Icons.Rounded.Info, R.string.history_card_properties, it) }
    )
    val scheme = MaterialTheme.colorScheme
    val tone = scheme.primary.copy(alpha = 0.16f).compositeOver(scheme.surfaceContainerHigh)

    Column(verticalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
        HoldButton(
            item = main,
            corners = Corners(OUTER, OUTER, if (tiles.isEmpty()) OUTER else INNER, if (tiles.isEmpty()) OUTER else INNER),
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            wide = true,
            main = true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        )
        if (tiles.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Side by side while every label fits on one line of its tile; a long
                // language at a large font stacks them instead of breaking words apart.
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                val labels = tiles.map { stringResource(it.label) }
                val tileWidth = (maxWidth - GROUP_GAP * (tiles.size - 1)) / tiles.size - TILE_PADDING * 2
                val fits = with(LocalDensity.current) {
                    labels.all { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width.toDp() <= tileWidth }
                }
                if (fits && tiles.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(GROUP_GAP),
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    ) {
                        tiles.forEachIndexed { index, item ->
                            HoldButton(
                                item = item,
                                corners = Corners(
                                    INNER, INNER,
                                    if (index == 0) OUTER else INNER,
                                    if (index == tiles.lastIndex) OUTER else INNER
                                ),
                                color = tone,
                                contentColor = scheme.onSurface,
                                wide = false,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                } else {
                    // A lone second action pairs with the main one at its full size, so the two
                    // read as an even pair rather than a button and a footnote.
                    val pair = tiles.size == 1
                    Column(verticalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
                        tiles.forEachIndexed { index, item ->
                            val bottom = if (index == tiles.lastIndex) OUTER else INNER
                            HoldButton(
                                item = item,
                                corners = Corners(INNER, INNER, bottom, bottom),
                                color = tone,
                                contentColor = scheme.onSurface,
                                wide = true,
                                large = pair,
                                modifier = Modifier.fillMaxWidth().heightIn(min = if (pair) 64.dp else 52.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private class HoldItem(val shape: RoundedPolygon, val icon: ImageVector, @StringRes val label: Int, val onClick: () -> Unit)

private class Corners(val topStart: Dp, val topEnd: Dp, val bottomStart: Dp, val bottomEnd: Dp)

@Composable
private fun HoldButton(
    item: HoldItem,
    corners: Corners,
    color: Color,
    contentColor: Color,
    wide: Boolean,
    modifier: Modifier = Modifier,
    main: Boolean = false,
    large: Boolean = main
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bouncy = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    // Pressed, every corner meets in the middle, the way the expressive buttons squeeze.
    fun corner(rest: Dp) = if (pressed) PRESSED else rest
    val topStart by animateDpAsState(corner(corners.topStart), bouncy, label = "ts")
    val topEnd by animateDpAsState(corner(corners.topEnd), bouncy, label = "te")
    val bottomStart by animateDpAsState(corner(corners.bottomStart), bouncy, label = "bs")
    val bottomEnd by animateDpAsState(corner(corners.bottomEnd), bouncy, label = "be")

    Surface(
        onClick = item.onClick,
        shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart),
        color = color,
        contentColor = contentColor,
        interactionSource = interaction,
        modifier = modifier
    ) {
        val label = stringResource(item.label)
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                ShapedIcon(item, if (large) 38.dp else 32.dp, main, pressed)
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = if (large) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = TILE_PADDING, vertical = 12.dp)
            ) {
                ShapedIcon(item, 36.dp, main, pressed)
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The action's icon on its own expressive shape. Pressed, the shape gives a bouncy part turn while the icon stays upright.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapedIcon(item: HoldItem, size: Dp, onAccent: Boolean, pressed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Solid accent on the tonal tiles, and the reverse on the accent button, so the shape
    // reads on every theme, glass included, where the tiles let the backdrop through.
    val shapeColor = if (onAccent) scheme.onPrimary else scheme.primary
    val iconColor = if (onAccent) scheme.primary else scheme.onPrimary
    val turn by animateFloatAsState(
        if (pressed) PRESS_TURN else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "iconTurn"
    )
    val shape = item.shape.toShape()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = turn }
                .clip(shape)
                .background(shapeColor)
        )
        Icon(item.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(size * 0.56f))
    }
}

private const val PRESS_TURN = 36f

private val OUTER = 26.dp
private val INNER = 8.dp
private val PRESSED = 16.dp
private val GROUP_GAP = 4.dp
private val TILE_PADDING = 6.dp
