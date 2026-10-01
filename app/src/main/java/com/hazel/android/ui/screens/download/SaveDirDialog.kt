package com.hazel.android.ui.screens.download

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SaveDir
import com.hazel.android.util.SdCard
import com.hazel.android.util.SdCards
import com.hazel.android.util.StoragePaths

/**
 * Where one kind of download is saved, and the places it can be moved to: internal storage,
 * each SD card that is in, or any other folder. Opening the current folder stays one tap.
 *
 * The choices are flat tinted surfaces, the same as the batch sheet's rows, with the one in
 * use filled in the accent colour; a card also shows how full it is.
 */
@Composable
internal fun SaveDirDialog(
    isVideo: Boolean,
    saveDir: SaveDir,
    label: String,
    onOpen: () -> Unit,
    /** Given the card to save to, or null to pick any folder. */
    onPick: (card: SdCard?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cards = remember { SdCards.list(context) }
    val onCard = remember(saveDir.uri) { SdCards.cardOf(saveDir.uri, context) }
    val kindFolder = "Hazel/" + SdCards.kindFolderName(isVideo)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Folder, null) },
        title = { Text(stringResource(R.string.format_sheet_save_location_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.format_sheet_save_location_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                SaveDirOption(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.save_location_internal),
                    subtitle = StoragePaths.downloadsDisplay(isAudio = !isVideo),
                    selected = !saveDir.isCustom,
                    onClick = onReset
                )
                cards.forEach { card ->
                    val selected = onCard?.uuid == card.uuid
                    val free = card.freeBytes
                    val total = card.totalBytes
                    SaveDirOption(
                        icon = Icons.Filled.SdCard,
                        title = card.name,
                        subtitle = if (selected) label
                        else stringResource(R.string.save_location_card_hint, kindFolder),
                        detail = free?.let {
                            stringResource(R.string.save_location_free, Formatter.formatShortFileSize(context, it))
                        },
                        usedFraction = if (free != null && total != null && total > 0) {
                            (1f - free.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else null,
                        selected = selected,
                        onClick = { onPick(card) }
                    )
                }
                SaveDirOption(
                    icon = Icons.Filled.CreateNewFolder,
                    title = stringResource(R.string.save_location_other),
                    subtitle = if (saveDir.isCustom && onCard == null) label
                    else stringResource(R.string.save_location_other_hint),
                    selected = saveDir.isCustom && onCard == null,
                    onClick = { onPick(null) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.format_sheet_open_folder)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.save_location_close)) } }
    )
}

/** One place to save to, as a flat surface; the one in use is tinted and checked. */
@Composable
private fun SaveDirOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
    usedFraction: Float? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (usedFraction != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
                if (detail != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = accent)
            }
        }
    }
}
