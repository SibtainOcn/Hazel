package com.hazel.android.ui.screens.download.batch

import com.hazel.android.ui.theme.sheetButtonColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import com.hazel.android.R
import com.hazel.android.ui.screens.download.rememberIncognitoSwitch

/**
 * The bottom of the batch sheet as one connected button group: download type, quality, save
 * folder, the set's links and incognito. The group spans the width, its two ends fully round
 * and the joins between buttons tight, so the five read as one control rather than as icons
 * scattered along a row.
 *
 * The buttons act on the ticked links, or on all of them when nothing is ticked.
 */
@Composable
fun BatchActionBar(
    isVideo: Boolean,
    qualityLabel: String,
    /** The quality button's own text: the chosen quality alone, such as "720p" or "AUTO". */
    hqLabel: String,
    onDownloadType: () -> Unit,
    onQuality: () -> Unit,
    onSaveDir: () -> Unit,
    linksEnabled: Boolean,
    onLinks: () -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val incognito = rememberIncognitoSwitch(onFeedback)
    val accent = MaterialTheme.colorScheme.primary

    // layout-safe: icons and a short quality label, each segment sharing the width
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GROUP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(GROUP_GAP),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Segment(
            position = 0, count = 5, weight = 1f,
            description = stringResource(R.string.batch_bar_download_type),
            onClick = onDownloadType,
            icon = rememberVectorPainter(if (isVideo) Icons.Filled.Videocam else Icons.Filled.MusicNote)
        )
        Segment(
            position = 1, count = 5, weight = 1.6f,
            description = stringResource(R.string.batch_bar_quality, qualityLabel),
            onClick = onQuality,
            text = hqLabel
        )
        Segment(
            position = 2, count = 5, weight = 1f,
            description = stringResource(R.string.batch_bar_save_location),
            onClick = onSaveDir,
            icon = rememberVectorPainter(Icons.Filled.Folder)
        )
        Segment(
            position = 3, count = 5, weight = 1f,
            description = stringResource(R.string.sheet_link_options),
            onClick = onLinks,
            enabled = linksEnabled,
            icon = rememberVectorPainter(Icons.Filled.Link)
        )
        Segment(
            position = 4, count = 5, weight = 1f,
            description = if (incognito.on) "Incognito on, this download is not recorded" else "Incognito off",
            onClick = incognito.toggle,
            icon = painterResource(R.drawable.incognito),
            fill = if (incognito.on) accent.copy(alpha = 0.22f) else null,
            tint = if (incognito.on) accent else null
        )
    }
}

/** One button of the group: round on the group's outer ends, tight where it meets the next. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(
    position: Int,
    count: Int,
    weight: Float,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: Painter? = null,
    text: String? = null,
    fill: Color? = null,
    tint: Color? = null
) {
    val outer = GROUP_HEIGHT / 2
    val start: Dp = if (position == 0) outer else INNER_CORNER
    val end: Dp = if (position == count - 1) outer else INNER_CORNER
    val ink = (tint ?: MaterialTheme.colorScheme.onSurface).let { if (enabled) it else it.copy(alpha = 0.38f) }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
        color = fill ?: sheetButtonColor,
        modifier = Modifier
            .weight(weight)
            .height(GROUP_HEIGHT)
            .semantics { contentDescription = description }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = ink)
            } else if (text != null) {
                FittedLabel(text, ink)
            }
        }
    }
}

/**
 * A one-line label that steps its size down until it fits, so a large system font shrinks
 * the quality text rather than cutting its value off.
 */
@Composable
private fun FittedLabel(text: String, color: Color) {
    val base = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val room = constraints.maxWidth
        // The floor is what shows on screen, whatever the system font scale.
        val floor = MIN_LABEL_SP / LocalDensity.current.fontScale
        val size = remember(text, room, base, floor) {
            generateSequence(base.fontSize.value) { it - 0.5f }
                .takeWhile { it >= floor }
                .firstOrNull { sp ->
                    measurer.measure(text, base.copy(fontSize = sp.sp), maxLines = 1, softWrap = false)
                        .size.width <= room
                } ?: floor
        }
        Text(
            text,
            style = base.copy(fontSize = size.sp),
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val MIN_LABEL_SP = 9f

private val GROUP_HEIGHT = 52.dp
private val GROUP_GAP = 4.dp
private val INNER_CORNER = 10.dp
