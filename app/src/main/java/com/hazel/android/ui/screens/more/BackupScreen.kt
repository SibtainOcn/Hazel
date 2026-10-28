package com.hazel.android.ui.screens.more

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.BackupCategory
import com.hazel.android.data.BackupFile
import com.hazel.android.data.BackupRepository
import com.hazel.android.data.BackupSchedule
import com.hazel.android.data.BackupSettings
import com.hazel.android.ui.theme.tonalFill
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.SdCards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import com.hazel.android.data.SaveDir
import com.hazel.android.ui.screens.download.SaveDirDialog

private enum class BackupDialog { BACKUP, SCHEDULE, KEEP, INCLUDE, FOLDER }

private const val BACKUP_CARD_SUBFOLDER = "Backups"
private const val BACKUP_CARD_FOLDER = "Hazel/$BACKUP_CARD_SUBFOLDER"

/** Backs up the app's data, lists what is saved, and backs up by itself before updates. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val settings by remember(context) { BackupRepository.getSettings(context) }
        .collectAsState(initial = BackupSettings(true, BackupSchedule.OFF, 5, BackupCategory.entries.toSet(), "", "", 0, 0))

    var dialog by remember { mutableStateOf<BackupDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<BackupFile?>(null) }
    var deleting by remember { mutableStateOf<BackupFile?>(null) }
    var sharing by remember { mutableStateOf<BackupFile?>(null) }
    var pickingCard by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf<List<BackupFile>?>(null) }

    LaunchedEffect(refresh, settings.folderLabel) { saved = BackupRepository.list(context) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    val restore = rememberRestoreFlow(onDone = { refresh++ })

    val onFolderPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val toCard = pickingCard
                val app = context.applicationContext
                scope.launch(Dispatchers.IO) {
                    // A card's root gets Hazel/Backups rather than files piling up at its top.
                    val folder = if (toCard) SdCards.hazelFolderIn(app, uri, BACKUP_CARD_SUBFOLDER) ?: uri else uri
                    runCatching { BackupRepository.setFolder(app, folder.toString(), MediaStoreHelper.describeTree(folder)) }
                        .onFailure { withContext(Dispatchers.Main) { toast(resources.getString(R.string.save_location_error_access)) } }
                }
            } catch (_: SecurityException) {
                toast(resources.getString(R.string.save_location_error_access))
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), onFolderPicked)
    val cardPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onFolderPicked(result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK })
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(restore)
    }

    fun openPlainPicker() = runCatching { folderPicker.launch(null) }
        .onFailure { toast(resources.getString(R.string.save_location_error_no_picker)) }

    SettingsScreen(
        title = stringResource(R.string.backup_title),
        description = stringResource(R.string.backup_description),
        onBack = onBack
    ) {
        BackupStatusCard(
            settings = settings,
            busy = busy,
            onBackup = { dialog = BackupDialog.BACKUP },
            onRestore = {
                runCatching { filePicker.launch(BACKUP_MIME_TYPES) }
                    .onFailure { toast(resources.getString(R.string.save_location_error_no_picker)) }
            }
        )

        val files = saved
        SettingsSection(
            title = stringResource(R.string.backup_section_saved),
            rows = if (files.isNullOrEmpty()) listOf<@Composable () -> Unit>({
                ActionSettingRow(
                    icon = Icons.Filled.Inventory2,
                    title = stringResource(R.string.backup_saved_empty),
                    summary = settings.folderLabel.ifBlank { BackupRepository.DEFAULT_FOLDER_LABEL },
                    enabled = false,
                    onClick = {}
                )
            }) else files.map { file ->
                @Composable {
                    ActionSettingRow(
                        icon = if (file.automatic) Icons.Filled.Schedule else Icons.Filled.Backup,
                        title = backupDate(context, file.madeAt),
                        summary = stringResource(
                            if (file.automatic) R.string.backup_kind_auto else R.string.backup_kind_manual,
                            Formatter.formatShortFileSize(context, file.size)
                        ),
                        onClick = { actionsFor = file }
                    )
                }
            }
        )

        SettingsSection(
            title = stringResource(R.string.backup_section_where),
            rows = listOf<@Composable () -> Unit>(
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.SystemUpdate,
                        title = stringResource(R.string.backup_auto),
                        summary = stringResource(R.string.backup_auto_summary),
                        checked = settings.beforeUpdates,
                        onCheckedChange = { on -> scope.launch { BackupRepository.setAutoBackup(context, on) } }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Event,
                        title = stringResource(R.string.backup_schedule),
                        value = stringResource(settings.schedule.label),
                        onClick = { dialog = BackupDialog.SCHEDULE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.History,
                        title = stringResource(R.string.backup_keep),
                        value = keepLabel(settings.keep),
                        onClick = { dialog = BackupDialog.KEEP }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Category,
                        title = stringResource(R.string.backup_include),
                        value = BackupCategory.entries.filter { it in settings.include }
                            .joinToString { resources.getString(it.label) },
                        onClick = { dialog = BackupDialog.INCLUDE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Folder,
                        title = stringResource(R.string.backup_folder),
                        value = settings.folderLabel.ifBlank { BackupRepository.DEFAULT_FOLDER_LABEL },
                        onClick = { dialog = BackupDialog.FOLDER }
                    )
                }
            )
        )
    }

    val allCategories = BackupCategory.entries.map { it to stringResource(it.label) }

    when (dialog) {
        BackupDialog.BACKUP -> MultiChoiceDialog(
            title = stringResource(R.string.backup_choose),
            choices = allCategories,
            selected = BackupCategory.entries.toSet(),
            onConfirm = { chosen ->
                dialog = null
                if (chosen.isEmpty()) return@MultiChoiceDialog
                busy = true
                scope.launch {
                    val name = BackupRepository.backup(context, chosen)
                    busy = false
                    toast(name?.let { resources.getString(R.string.backup_saved, it) } ?: resources.getString(R.string.backup_failed))
                    refresh++
                }
            },
            onDismiss = { dialog = null }
        )
        BackupDialog.SCHEDULE -> SingleChoiceDialog(
            title = stringResource(R.string.backup_schedule),
            choices = BackupSchedule.entries.map { it to stringResource(it.label) },
            selected = settings.schedule,
            onSelect = { choice ->
                dialog = null
                scope.launch { BackupRepository.setSchedule(context, choice) }
            },
            onDismiss = { dialog = null }
        )
        BackupDialog.KEEP -> SingleChoiceDialog(
            title = stringResource(R.string.backup_keep),
            choices = BackupRepository.KEEP_CHOICES.map { it to keepLabel(it) },
            selected = settings.keep,
            onSelect = { keep ->
                dialog = null
                scope.launch {
                    BackupRepository.setKeep(context, keep)
                    refresh++
                }
            },
            onDismiss = { dialog = null }
        )
        BackupDialog.INCLUDE -> MultiChoiceDialog(
            title = stringResource(R.string.backup_include),
            choices = allCategories,
            selected = settings.include,
            onConfirm = { chosen ->
                dialog = null
                scope.launch { BackupRepository.setInclude(context, chosen) }
            },
            onDismiss = { dialog = null }
        )
        BackupDialog.FOLDER -> SaveDirDialog(
            isVideo = false,
            saveDir = SaveDir(settings.folderUri, settings.folderLabel),
            label = settings.folderLabel,
            title = stringResource(R.string.backup_folder),
            body = stringResource(R.string.backup_folder_body),
            internalPath = BackupRepository.DEFAULT_FOLDER_LABEL,
            cardFolder = BACKUP_CARD_FOLDER,
            onOpen = { openFolder(context, settings.folderUri) },
            onPick = { card ->
                dialog = null
                pickingCard = card != null
                if (card != null) {
                    runCatching { cardPicker.launch(SdCards.pickerIntent(context, card)) }.onFailure { openPlainPicker() }
                } else {
                    openPlainPicker()
                }
            },
            onReset = {
                dialog = null
                scope.launch { BackupRepository.setFolder(context, "", "") }
            },
            onDismiss = { dialog = null }
        )
        null -> Unit
    }

    actionsFor?.let { file ->
        BackupFileSheet(
            file = file,
            folderUri = settings.folderUri,
            folderLabel = settings.folderLabel,
            onRestore = {
                actionsFor = null
                restore(file.uri)
            },
            onShare = { sharing = file },
            onDelete = {
                actionsFor = null
                deleting = file
            },
            onDismiss = { actionsFor = null }
        )
    }

    sharing?.let { file ->
        ConfirmSettingDialog(
            title = stringResource(R.string.backup_share_title),
            body = stringResource(R.string.backup_share_body),
            confirm = stringResource(R.string.backup_share),
            onConfirm = {
                sharing = null
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_STREAM, file.uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(Intent.createChooser(send, file.name)) }
                    .onFailure { toast(resources.getString(R.string.backup_share_failed)) }
            },
            onDismiss = { sharing = null }
        )
    }

    deleting?.let { file ->
        ConfirmSettingDialog(
            title = stringResource(R.string.backup_delete_title),
            body = stringResource(R.string.backup_delete_body),
            confirm = stringResource(R.string.backup_delete),
            onConfirm = {
                deleting = null
                scope.launch {
                    if (!BackupRepository.delete(context, file)) toast(resources.getString(R.string.backup_delete_failed))
                    refresh++
                }
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun keepLabel(keep: Int): String =
    if (keep <= 0) stringResource(R.string.backup_keep_all) else stringResource(R.string.backup_keep_count, keep)

/** When the last backup was made and how big it was, with the two main actions under it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupStatusCard(settings: BackupSettings, busy: Boolean, onBackup: () -> Unit, onRestore: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = tonalFill(0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Backup, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(R.string.backup_last),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (settings.lastAt > 0) stringResource(
                            R.string.backup_item_summary,
                            backupDate(context, settings.lastAt),
                            Formatter.formatShortFileSize(context, settings.lastSize)
                        ) else stringResource(R.string.backup_none_yet),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton(stringResource(R.string.backup_restore), filled = false, onClick = onRestore, modifier = Modifier.weight(1f))
                DialogButton(
                    stringResource(R.string.backup_backup),
                    filled = true,
                    onClick = { if (!busy) onBackup() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
