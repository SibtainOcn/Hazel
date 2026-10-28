package com.hazel.android.ui.screens.more

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.BackupCategory
import com.hazel.android.data.BackupFile
import com.hazel.android.data.BackupRepository
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.keepFlingInSheet
import com.hazel.android.ui.screens.history.CookieAction
import com.hazel.android.ui.screens.history.FactRow
import com.hazel.android.ui.screens.history.Group
import com.hazel.android.ui.screens.history.SplitButton
import com.hazel.android.ui.screens.history.StackedRow
import com.hazel.android.ui.screens.history.rowShape
import com.hazel.android.ui.screens.history.splitShape
import com.hazel.android.util.FolderUtil

/**
 * One saved backup, laid out like a download's properties: what it is, what it holds, and
 * what can be done with it. The file is read once per opening for its contents.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BackupFileSheet(
    file: BackupFile,
    folderUri: String,
    folderLabel: String,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val contents by produceState<BackupRepository.Contents?>(null, file.uri) {
        value = runCatching { BackupRepository.read(context, file.uri) }.getOrNull()
    }
    val readable = contents != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .keepFlingInSheet()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (file.automatic) Icons.Filled.Schedule else Icons.Filled.Backup, null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        backupDate(context, file.madeAt),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(
                            if (file.automatic) R.string.backup_kind_auto else R.string.backup_kind_manual,
                            Formatter.formatShortFileSize(context, file.size)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Tiles(file, contents)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CookieAction(Icons.Filled.Restore, R.string.backup_restore, readable,
                    MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, onRestore)
                val neutral = MaterialTheme.colorScheme.surfaceContainerHighest
                val ink = MaterialTheme.colorScheme.onSurface
                CookieAction(Icons.Filled.Visibility, R.string.backup_view, true, neutral, ink) { view(context, file.uri) }
                CookieAction(Icons.Filled.FolderOpen, R.string.backup_show_folder, true, neutral, ink) {
                    openFolder(context, folderUri)
                }
                CookieAction(Icons.Filled.Share, R.string.backup_share, true, neutral, ink, onShare)
            }

            contents?.let { backup ->
                Group(
                    R.string.backup_section_contains,
                    BackupCategory.entries.filter { it in backup.categories }.map { category ->
                        @Composable {
                            FactRow(
                                stringResource(category.label),
                                if (category == BackupCategory.SETTINGS) stringResource(R.string.backup_included)
                                else stringResource(R.string.backup_entries, backup.counts[category] ?: 0)
                            )
                        }
                    }
                )
            }

            Group(
                R.string.properties_file,
                listOf(
                    {
                        StackedRow(stringResource(R.string.properties_name), file.name, Icons.Filled.ContentCopy, R.string.sheet_link_copy) {
                            copy(context, file.name)
                        }
                    },
                    {
                        StackedRow(
                            stringResource(R.string.properties_saved_to),
                            folderLabel.ifBlank { BackupRepository.DEFAULT_FOLDER_LABEL },
                            Icons.Filled.FolderOpen,
                            R.string.backup_show_folder
                        ) { openFolder(context, folderUri) }
                    }
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SplitButton(R.string.save_location_close, splitShape(start = true), MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), onDismiss)
                SplitButton(R.string.backup_delete, splitShape(start = false), MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer, Modifier.weight(1f), onDelete)
            }
        }
    }
}

/** Size, the version it came from, how many entries and categories, as joined tiles. */
@Composable
private fun Tiles(file: BackupFile, contents: BackupRepository.Contents?) {
    val context = LocalContext.current
    val tiles = buildList {
        add(Formatter.formatShortFileSize(context, file.size) to stringResource(R.string.properties_size))
        contents?.let { backup ->
            backup.appVersion.ifBlank { null }?.let { add(it to stringResource(R.string.backup_version)) }
            add(backup.counts.filterKeys { it != BackupCategory.SETTINGS }.values.sum().toString() to stringResource(R.string.backup_entries_label))
            add(backup.categories.size.toString() to stringResource(R.string.backup_categories_label))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.forEachIndexed { index, (value, label) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(rowShape(index, tiles.size, horizontal = true))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Opens the backup folder in the user's file manager, or the default folder when none was picked. */
internal fun openFolder(context: Context, folderUri: String) {
    runCatching {
        if (folderUri.isBlank()) FolderUtil.open(context, BackupRepository.defaultFolder())
        else FolderUtil.openTree(context, Uri.parse(folderUri))
    }.onFailure { Toast.makeText(context, R.string.backup_view_failed, Toast.LENGTH_SHORT).show() }
}

/** Shows the file in a text or JSON viewer the user picks. */
private fun view(context: Context, uri: Uri) {
    val opened = listOf("application/json", "text/plain").any { type ->
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(intent) }.isSuccess
    }
    if (!opened) Toast.makeText(context, R.string.backup_view_failed, Toast.LENGTH_SHORT).show()
}

private fun copy(context: Context, text: String) {
    val copied = runCatching {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text, text)) != null
    }.getOrDefault(false)
    if (copied) Toast.makeText(context, R.string.properties_copied, Toast.LENGTH_SHORT).show()
}
