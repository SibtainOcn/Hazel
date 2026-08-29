package com.hazel.android.ui.screens.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shown once, the first time the app is opened.
 *
 * It covers the four things that are not discoverable by looking at the screen: that the
 * field takes several links at once, that there is a share target which skips the sheet
 * entirely, where finished files are listed, and that Android will suspend a download the
 * moment the app is left unless battery use is unrestricted. That last one is the reason
 * this dialog exists at all: a download that silently stops when the screen turns off reads
 * as the app being broken, and nothing in the app can fix it from the inside.
 */
@Composable
fun UserGuideDialog(
    onOpenBatterySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Getting started",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                GuideStep(
                    icon = Icons.Filled.Link,
                    text = "Paste a link into the search field to download it. " +
                            "You can add multiple links, playlists"
                )
                GuideStep(
                    icon = Icons.Filled.Tune,
                    text = "Tap a card to pick the exact format. All available" +
                            " qualities are listed with size and codec."
                )
                GuideStep(
                    icon = Icons.Filled.Bolt,
                    text = "Share a link to Hazel Direct from any app to" +
                            " download instantly at your preset quality."
                )
                GuideStep(
                    icon = Icons.Filled.Download,
                    text = "Finished files appear in Downloads. Tap any" +
                            " row to open it in the device's default player."
                )
                GuideStep(
                    icon = Icons.Filled.BatteryChargingFull,
                    text = "Set battery use to unrestricted, otherwise Android" +
                            " may stop downloads when you leave the app."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenBatterySettings) {
                Text("Battery settings", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun GuideStep(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(top = 12.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}

/**
 * Whether Android is already leaving this app alone in the background.
 *
 * A download is a long running network job, and outside this exemption the system stops it
 * shortly after the app loses the foreground, which the user experiences as downloads that
 * never finish.
 */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    return runCatching {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}

/**
 * Opens the system's own exemption screen.
 *
 * The request is made through the settings screen rather than the direct grant dialog where
 * possible, because the direct one is a prompt some builders reject outright, and landing on
 * a screen the user recognises is better than a request that silently does nothing.
 */
fun openBatterySettings(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    for (intent in listOf(direct, fallback, appDetails)) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}
