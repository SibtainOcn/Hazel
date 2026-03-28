package com.hazel.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.data.UpdateChecker

/**
 * Sealed class representing the 3 states of the update dialog.
 */
sealed class UpdateDialogState {
    /** Update found — shows version, notes, download button */
    data class Found(val info: UpdateChecker.UpdateInfo) : UpdateDialogState()

    /** Downloading APK — shows progress bar */
    data class Downloading(
        val info: UpdateChecker.UpdateInfo,
        val downloaded: Long = 0,
        val total: Long = 0
    ) : UpdateDialogState()

    /** Download complete — shows install button */
    data class Ready(val info: UpdateChecker.UpdateInfo) : UpdateDialogState()
}

/**
 * Industry-grade Material 3 update dialog with 3 states:
 * Found → Downloading (with progress) → Ready to Install
 */
@Composable
fun UpdateDialog(
    state: UpdateDialogState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit = {},
    onCancel: () -> Unit = {},
    onInstall: () -> Unit = {}
) {
    val isDismissable = state !is UpdateDialogState.Downloading

    Dialog(
        onDismissRequest = { if (isDismissable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = isDismissable,
            dismissOnClickOutside = isDismissable
        )
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "update_dialog_state"
            ) { currentState ->
                when (currentState) {
                    is UpdateDialogState.Found -> FoundContent(
                        info = currentState.info,
                        onDismiss = onDismiss,
                        onDownload = onDownload
                    )
                    is UpdateDialogState.Downloading -> DownloadingContent(
                        info = currentState.info,
                        downloaded = currentState.downloaded,
                        total = currentState.total,
                        onCancel = onCancel
                    )
                    is UpdateDialogState.Ready -> ReadyContent(
                        info = currentState.info,
                        onDismiss = onDismiss,
                        onInstall = onInstall
                    )
                }
            }
        }
    }
}

// ─── State 1: Update Found ───

@Composable
private fun FoundContent(
    info: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.SystemUpdateAlt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Update Available",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "Version ${info.version}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        // Release notes
        if (info.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                info.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // APK size
        if (info.apkSize > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Size: ${formatFileSize(info.apkSize)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Later")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onDownload) {
                Text("Download Now", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── State 2: Downloading ───

@Composable
private fun DownloadingContent(
    info: UpdateChecker.UpdateInfo,
    downloaded: Long,
    total: Long,
    onCancel: () -> Unit
) {
    val progress = if (total > 0) (downloaded.toFloat() / total) else 0f
    val percentText = if (total > 0) "${(progress * 100).toInt()}%" else "..."

    Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Downloading Hazel v${info.version}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Progress bar
        if (total > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Progress text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (total > 0) "${formatFileSize(downloaded)} / ${formatFileSize(total)}"
                else formatFileSize(downloaded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                percentText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onCancel,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Cancel")
        }
    }
}

// ─── State 3: Ready to Install ───

@Composable
private fun ReadyContent(
    info: UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Ready to Install",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            "Hazel v${info.version}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "The app will close and Android will guide you through the installation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onDismiss) {
                Text("Not Now")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onInstall) {
                Text("Install Now", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Utility ───

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
