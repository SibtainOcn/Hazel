package com.hazel.android.ui.screens.download

import com.hazel.android.ui.theme.tonalFill
import android.content.Context
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
import com.hazel.android.ui.components.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.hazel.android.util.FolderSize
import com.hazel.android.util.StorageSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
 * use filled in the accent colour. Internal storage and each card show how full they are,
 * refreshed while the dialog is open.
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
    val onCard = remember(saveDir.uri) { SdCards.cardOf(saveDir.uri, context) }
    val kindFolder = "Hazel/" + SdCards.kindFolderName(isVideo)
    val folderBytes by produceState<Long?>(null, saveDir.uri, onCard) {
        if (saveDir.isCustom && onCard == null) {
            value = withContext(Dispatchers.IO) { FolderSize.of(context, saveDir.uri) }
        }
    }
    val firstCards = remember { SdCards.list(context) }
    val volumes by produceState(Volumes(null, firstCards.map { it to null })) {
        while (true) {
            withContext(Dispatchers.IO) { runCatching { Volumes.read(context) }.getOrNull() }?.let { value = it }
            delay(SPACE_REFRESH_MS)
        }
    }

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

                val internal = volumes.internal
                SaveDirOption(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.save_location_internal),
                    subtitle = StoragePaths.downloadsDisplay(isAudio = !isVideo),
                    detail = internal?.let { freeLabel(it) },
                    usedFraction = internal?.usedFraction,
                    selected = !saveDir.isCustom,
                    onClick = onReset
                )
                volumes.cards.forEach { (card, space) ->
                    val selected = onCard?.uuid == card.uuid
                    SaveDirOption(
                        icon = Icons.Filled.SdCard,
                        title = card.name,
                        subtitle = if (selected) label
                        else stringResource(R.string.save_location_card_hint, kindFolder),
                        detail = space?.let { freeLabel(it) },
                        usedFraction = space?.usedFraction,
                        selected = selected,
                        onClick = { onPick(card) }
                    )
                }
                SaveDirOption(
                    icon = Icons.Filled.CreateNewFolder,
                    title = stringResource(R.string.save_location_other),
                    subtitle = if (saveDir.isCustom && onCard == null) label
                    else stringResource(R.string.save_location_other_hint),
                    detail = folderBytes?.let {
                        stringResource(R.string.save_location_folder_uses, Formatter.formatShortFileSize(context, it))
                    },
                    selected = saveDir.isCustom && onCard == null,
                    onClick = { onPick(null) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.format_sheet_open_folder)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.save_location_close)) } }
    )
}

/** Internal storage and the cards that are in right now, each with how much room it has. */
private class Volumes(val internal: StorageSpace?, val cards: List<Pair<SdCard, StorageSpace?>>) {
    companion object {
        fun read(context: Context) = Volumes(
            StorageSpace.internal(context),
            SdCards.list(context).map { it to it.space() }
        )
    }
}

private const val SPACE_REFRESH_MS = 2_000L
private const val NEARLY_FULL = 0.9f

@Composable
private fun freeLabel(space: StorageSpace): String = stringResource(
    R.string.save_location_free,
    Formatter.formatShortFileSize(LocalContext.current, space.freeBytes)
)

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
        else tonalFill(0.45f)
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
                        color = if (usedFraction >= NEARLY_FULL) MaterialTheme.colorScheme.error else accent,
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
