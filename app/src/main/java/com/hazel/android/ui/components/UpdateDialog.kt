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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.R
import com.hazel.android.data.UpdateChecker

/**
 * Sealed class representing the 3 states of the update dialog.
 */
sealed class UpdateDialogState {
    /** Update found — shows version, download button, changelog */
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

@Composable
fun UpdateDialog(
    state: UpdateDialogState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit = {},
    onCancel: () -> Unit = {},
    onInstall: () -> Unit = {},
    onChangelog: () -> Unit = {},
    onKeepInBackground: () -> Unit = {}
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
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
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
                        onDownload = onDownload,
                        onChangelog = onChangelog
                    )
                    is UpdateDialogState.Downloading -> DownloadingContent(
                        info = currentState.info,
                        downloaded = currentState.downloaded,
                        total = currentState.total,
                        onCancel = onCancel,
                        onKeepInBackground = onKeepInBackground
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
    onDownload: () -> Unit,
    onChangelog: () -> Unit
) {
    Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Install icon
        Icon(
            painter = painterResource(R.drawable.install_ic),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "Update Available",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "Version ${info.version}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        // APK size
        if (info.apkSize > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                formatFileSize(info.apkSize),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons row — borderless accent style
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Later
            AccentChipButton(
                text = "Later",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )

            // Download
            AccentChipButton(
                text = "Download",
                onClick = onDownload,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Changelog button — centered
        AccentChipButton(
            text = "Changelog",
            onClick = onChangelog
        )
    }
}

// ─── State 2: Downloading ───

@Composable
private fun DownloadingContent(
    info: UpdateChecker.UpdateInfo,
    downloaded: Long,
    total: Long,
    onCancel: () -> Unit,
    onKeepInBackground: () -> Unit
) {
    val progress = if (total > 0) (downloaded.toFloat() / total) else 0f
    val percentText = if (total > 0) "${(progress * 100).toInt()}%" else "..."

    Column(
        modifier = Modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Downloading v${info.version}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Progress bar
        if (total > 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (total > 0) "${formatFileSize(downloaded)} / ${formatFileSize(total)}"
                else formatFileSize(downloaded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                percentText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccentChipButton(
                text = "Cancel",
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            AccentChipButton(
                text = "Background",
                onClick = onKeepInBackground,
                modifier = Modifier.weight(1f)
            )
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
            painter = painterResource(R.drawable.install_ic),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "Ready to Install",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "Hazel v${info.version}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            "The app will close and Android will guide you through the installation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccentChipButton(
                text = "Not Now",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            AccentChipButton(
                text = "Install",
                onClick = onInstall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


@Composable
private fun AccentChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
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
