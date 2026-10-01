package com.hazel.android.ui.screens.download.batch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R

/**
 * The row of buttons along the bottom of the batch sheet: what kind of download the set is,
 * how high its quality goes, and where it is saved.
 *
 * These are the three choices a set is most often changed by, so they stay in reach under
 * the list rather than inside a section. The adjust-download options and the container live
 * in the sheet's Adjust section above, the way the single download sheet groups them.
 *
 * Each button opens a sheet of its own and carries what it stands for: the kind as an icon,
 * the quality as its value. The buttons act on the ticked links, or on all of them when
 * nothing is ticked, which is why nothing here says how many it covers: the count above the
 * list already does.
 */
@Composable
fun BatchActionBar(
    isVideo: Boolean,
    qualityLabel: String,
    /** The quality button's own text, such as "HQ: 720p". */
    hqLabel: String,
    onDownloadType: () -> Unit,
    onQuality: () -> Unit,
    onSaveDir: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set at the far end of the row, such as the sheet's link and incognito buttons. */
    trailing: @Composable RowScope.() -> Unit = {}
) {
    // Ranged along the start rather than spread across the width: spreading them put wide
    // gaps between buttons that belong together and left the row reading as a set of
    // unrelated controls.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BarButton(
            icon = if (isVideo) Icons.Filled.Videocam else Icons.Filled.MusicNote,
            description = stringResource(R.string.batch_bar_download_type),
            onClick = onDownloadType
        )
        BarButton(
            text = hqLabel,
            description = stringResource(R.string.batch_bar_quality, qualityLabel),
            onClick = onQuality
        )
        BarButton(
            icon = Icons.Filled.Folder,
            description = stringResource(R.string.batch_bar_save_location),
            onClick = onSaveDir
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * One of the bar's own buttons, on a surface one step up from the sheet so the row reads as
 * three controls rather than as an icon and some text printed on it.
 */
@Composable
private fun BarButton(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    text: String? = null
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = batchButtonColor,
        modifier = Modifier
            .height(44.dp)
            .semantics { contentDescription = description }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = tint
                )
            } else if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
