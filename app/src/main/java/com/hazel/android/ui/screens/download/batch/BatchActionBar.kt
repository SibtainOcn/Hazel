package com.hazel.android.ui.screens.download.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.hazel.android.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The bar along the bottom of the batch sheet.
 *
 * Everything here changes a whole set of links at once, and each button opens a sheet of
 * its own rather than expanding in place. Laying the settings out inline is what made the
 * sheet long enough to bury the list it is about, so they are kept one tap away and the
 * list keeps the height.
 *
 * The buttons act on the ticked links, or on all of them when nothing is ticked, which is
 * why nothing here says how many it covers: the count above the list already does.
 */
@Composable
fun BatchActionBar(
    isVideo: Boolean,
    qualityLabel: String,
    containerLabel: String,
    incognito: Boolean,
    onDownloadType: () -> Unit,
    onQuality: () -> Unit,
    onSaveDir: () -> Unit,
    onContainer: () -> Unit,
    onIncognito: () -> Unit,
    onMore: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarButton(
                icon = if (isVideo) Icons.Filled.Videocam else Icons.Filled.MusicNote,
                description = "Preferred download type",
                onClick = onDownloadType
            )
            BarButton(
                icon = Icons.Filled.HighQuality,
                description = "Quality: $qualityLabel",
                onClick = onQuality
            )
            BarButton(
                icon = Icons.Filled.Folder,
                description = "Save location",
                onClick = onSaveDir
            )
            // Carries its value rather than an icon: a container is a name, and a picture
            // of one would have to be learned before it said anything.
            BarButton(
                text = containerLabel,
                description = "Container",
                onClick = onContainer
            )
            // Dimmed when it is off, so the bar says which way the setting is standing
            // without needing a label under it.
            BarButton(
                painter = painterResource(R.drawable.incognito),
                description = if (incognito) "Incognito is on" else "Incognito is off",
                highlighted = incognito,
                onClick = onIncognito
            )
            BarButton(
                icon = Icons.Filled.MoreHoriz,
                description = "More options",
                onClick = onMore
            )
        }
    }
}

@Composable
private fun BarButton(
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Painter? = null,
    text: String? = null,
    highlighted: Boolean = false
) {
    val tint = if (highlighted) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .background(androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = description,
                modifier = Modifier.size(22.dp),
                tint = tint
            )
        } else if (painter != null) {
            Icon(
                painter,
                contentDescription = description,
                modifier = Modifier.size(22.dp),
                tint = tint
            )
        } else if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}
