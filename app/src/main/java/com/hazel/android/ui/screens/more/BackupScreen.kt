package com.hazel.android.ui.screens.more

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hazel.android.R
import com.hazel.android.data.BackupCategory
import com.hazel.android.data.BackupRepository
import com.hazel.android.util.MediaStoreHelper
import kotlinx.coroutines.launch

/** Backs up the app's data to a file, restores it, and backs up on its own before updates. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoBackup by BackupRepository.getAutoBackup(context).collectAsState(initial = true)
    val folderLabel by BackupRepository.getFolderLabel(context).collectAsState(initial = "")

    var choosingBackup by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf<BackupRepository.Contents?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    val backupFailed = stringResource(R.string.backup_failed)
    val notABackup = stringResource(R.string.backup_not_a_backup)
    val restored = stringResource(R.string.backup_restored)

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { BackupRepository.setFolder(context, uri.toString(), MediaStoreHelper.describeTree(uri)) }
        } catch (_: SecurityException) {
            toast(backupFailed)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            restoring = runCatching { BackupRepository.read(context, uri) }.getOrNull()
            if (restoring == null) toast(notABackup)
        }
    }

    SettingsScreen(
        title = stringResource(R.string.backup_title),
        description = stringResource(R.string.backup_description),
        onBack = onBack
    ) {
        SettingsSection(
            title = stringResource(R.string.backup_section_now),
            rows = listOf<@Composable () -> Unit>(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Backup,
                        title = stringResource(R.string.backup_backup),
                        value = stringResource(R.string.backup_backup_summary),
                        enabled = !busy,
                        onClick = { choosingBackup = true }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Restore,
                        title = stringResource(R.string.backup_restore),
                        value = stringResource(R.string.backup_restore_summary),
                        enabled = !busy,
                        onClick = { filePicker.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                    )
                }
            )
        )

        SettingsSection(
            title = stringResource(R.string.backup_section_where),
            rows = buildList<@Composable () -> Unit> {
                add {
                    SwitchSettingRow(
                        icon = Icons.Filled.Schedule,
                        title = stringResource(R.string.backup_auto),
                        summary = stringResource(R.string.backup_auto_summary),
                        checked = autoBackup,
                        onCheckedChange = { on -> scope.launch { BackupRepository.setAutoBackup(context, on) } }
                    )
                }
                add {
                    ValueSettingRow(
                        icon = Icons.Filled.Folder,
                        title = stringResource(R.string.backup_folder),
                        value = folderLabel.ifBlank { BackupRepository.DEFAULT_FOLDER_LABEL },
                        onClick = { folderPicker.launch(null) }
                    )
                }
                if (folderLabel.isNotBlank()) add {
                    ValueSettingRow(
                        icon = Icons.Filled.FolderOff,
                        title = stringResource(R.string.backup_folder_reset),
                        value = BackupRepository.DEFAULT_FOLDER_LABEL,
                        onClick = { scope.launch { BackupRepository.setFolder(context, "", "") } }
                    )
                }
            }
        )
    }

    val allCategories = BackupCategory.entries.map { it to stringResource(it.label) }

    if (choosingBackup) {
        MultiChoiceDialog(
            title = stringResource(R.string.backup_choose),
            choices = allCategories,
            selected = BackupCategory.entries.toSet(),
            onConfirm = { chosen ->
                choosingBackup = false
                if (chosen.isEmpty()) return@MultiChoiceDialog
                busy = true
                scope.launch {
                    val name = BackupRepository.backup(context, chosen)
                    busy = false
                    toast(name?.let { context.getString(R.string.backup_saved, it) } ?: backupFailed)
                }
            },
            onDismiss = { choosingBackup = false }
        )
    }

    restoring?.let { contents ->
        MultiChoiceDialog(
            title = stringResource(R.string.backup_restore_choose),
            choices = allCategories.filter { it.first in contents.categories },
            selected = contents.categories,
            onConfirm = { chosen ->
                restoring = null
                if (chosen.isEmpty()) return@MultiChoiceDialog
                busy = true
                scope.launch {
                    val ok = runCatching { BackupRepository.restore(context, contents, chosen) }.isSuccess
                    busy = false
                    toast(if (ok) restored else backupFailed)
                }
            },
            onDismiss = { restoring = null }
        )
    }
}
