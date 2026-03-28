package com.hazel.android.ui.screens.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
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
import androidx.core.content.FileProvider
import com.hazel.android.R
import com.hazel.android.util.StoragePaths

/**
 * Shows all Hazel storage locations with an option to open each in the system file manager.
 */
@Composable
fun StorageLocationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

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
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Storage Locations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Info text
        Text(
            "Tap any location to open it in your file manager",
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
            // Downloads
            StorageLocationItem(
                icon = Icons.Filled.Download,
                title = "Downloads",
                path = StoragePaths.DOWNLOADS_DISPLAY,
                onClick = {
                    openFolderInFileManager(context, StoragePaths.finalDownloads)
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Converted
            StorageLocationItem(
                icon = Icons.Filled.AudioFile,
                title = "Converted Audio",
                path = StoragePaths.CONVERTED_DISPLAY,
                onClick = {
                    openFolderInFileManager(context, StoragePaths.finalConverted)
                }
            )
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
                    "All files are stored in your device's internal storage. " +
                    "You can access them anytime using any file manager app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    lineHeight = 16.sp
                )
            }
        }
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

/**
 * Opens a folder in the system's default file manager.
 * Uses Documents UI content URI for maximum compatibility across all Android versions.
 */
private fun openFolderInFileManager(context: android.content.Context, folder: java.io.File) {
    if (!folder.exists()) folder.mkdirs()
    try {
        // Strategy 1: Documents UI content URI — works on most stock file managers
        val relativePath = folder.absolutePath.substringAfter("/storage/emulated/0/")
        val encodedPath = relativePath.replace("/", "%2F")
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary:$encodedPath"
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            // Strategy 2: FileProvider URI with resource/folder MIME
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", folder
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                // Strategy 3: Let user choose file manager
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("file://${folder.absolutePath}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Open folder"))
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                    context, "Path: ${folder.absolutePath}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
