package com.hazel.android.ui.screens.more

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import com.hazel.android.data.SaveDirs
import com.hazel.android.ui.screens.download.SaveDirDialog
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaStoreHelper
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Switch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R

/**
 * Shows all Hazel storage locations with an option to open each in the system file manager.
 */
@Composable
fun StorageLocationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val wifiOnly by SettingsRepository.getWifiOnly(context).collectAsState(initial = false)
    val saveDirs by SettingsRepository.getSaveDirs(context).collectAsState(initial = SaveDirs())
    // The kind whose folder is being looked at or chosen, or null when neither is.
    var dirDialogFor by remember { mutableStateOf<Boolean?>(null) }
    var pickingVideoDir by remember { mutableStateOf(true) }

    // The same picker the download sheet uses, with the grant kept so the folder stays
    // writable on later launches. The choice is saved for the kind it was made for, and
    // every download of that kind goes there until it is changed or reset.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val forVideo = pickingVideoDir
                scope.launch {
                    SettingsRepository.setSaveDir(
                        context, forVideo, uri.toString(), MediaStoreHelper.describeTree(uri)
                    )
                }
            } catch (_: SecurityException) {
                // The provider refused a lasting grant, so the folder in use stays as it was.
            }
        }
    }
    val speedLimit by SettingsRepository.getSpeedLimit(context).collectAsState(initial = "")
    var limitMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Compact header with back arrow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = stringResource(R.string.storage_locations_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.storage_locations_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Info text
        Text(
            stringResource(R.string.storage_locations_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Locations card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // One row per kind: each is saved on its own, the built-in folder until a
            // folder is picked for it.
            StorageLocationItem(
                icon = Icons.Filled.MusicNote,
                title = stringResource(R.string.format_sheet_tab_audio),
                path = saveDirs.labelOf(isVideo = false),
                onClick = { dirDialogFor = false }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )
            StorageLocationItem(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.format_sheet_tab_video),
                path = saveDirs.labelOf(isVideo = true),
                onClick = { dirDialogFor = true }
            )

        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            stringResource(R.string.storage_locations_limits),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // Checked as a download starts, not while one runs: stopping a transfer partway
            // because the phone changed networks wastes the data it has already spent.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { scope.launch { SettingsRepository.setWifiOnly(context, !wifiOnly) } }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.storage_locations_wifi_only),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.storage_locations_wifi_only_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = { enabled ->
                        scope.launch { SettingsRepository.setWifiOnly(context, enabled) }
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Held locally while it is being typed and written once it reads as something
            // the engine will take, so a half-typed "1" is not saved as a one-byte ceiling.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.storage_locations_speed_limit),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.storage_locations_speed_limit_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box {
                    Surface(
                        onClick = { limitMenuOpen = true },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                SettingsRepository.speedLimitLabel(speedLimit),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = limitMenuOpen,
                        onDismissRequest = { limitMenuOpen = false }
                    ) {
                        SettingsRepository.SPEED_LIMITS.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    limitMenuOpen = false
                                    scope.launch {
                                        SettingsRepository.setSpeedLimit(context, value)
                                    }
                                },
                                trailingIcon = if (value == speedLimit) {
                                    {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hint
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.storage_locations_internal_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 16.sp
                )
            }
        }
    }

    dirDialogFor?.let { isVideo ->
        SaveDirDialog(
            label = saveDirs.labelOf(isVideo),
            isCustom = saveDirs.of(isVideo).isCustom,
            onOpen = {
                dirDialogFor = null
                MediaOpener.openLocation(context, saveDirs.of(isVideo).uri, isVideo)
            },
            onPick = {
                dirDialogFor = null
                pickingVideoDir = isVideo
                folderPicker.launch(saveDirs.of(isVideo).uri.takeIf { it.isNotBlank() }?.let(Uri::parse))
            },
            onReset = {
                dirDialogFor = null
                scope.launch { SettingsRepository.resetSaveDir(context, isVideo) }
            },
            onDismiss = { dirDialogFor = null }
        )
    }
}

@Composable
private fun StorageLocationItem(
    icon: ImageVector,
    title: String,
    path: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
