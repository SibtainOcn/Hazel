package com.hazel.android.ui.screens.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

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
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(24.dp),
        // Full width less a margin, in line with the app's other dialogs. Each step is a
        // sentence, and at the default dialog width they wrap into five lines apiece.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        title = {
            Text(
                "Getting started",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                GuideStep(
                    icon = Icons.Filled.Link,
                    text = "Paste a link into the search field. Several can be queued at " +
                            "once, and a playlist or channel resolves to every item it holds."
                )
                GuideStep(
                    icon = Icons.Filled.Tune,
                    text = "Tap a card to choose the exact format. Every quality the source " +
                            "actually reports is listed, with its size and codec."
                )
                GuideStep(
                    icon = Icons.Filled.Bolt,
                    text = "Share a link to Hazel Direct from any app to download straight " +
                            "away at a quality you set once, with nothing to confirm."
                )
                GuideStep(
                    icon = Icons.Filled.Download,
                    text = "Downloads holds everything finished. Tapping a row opens the " +
                            "file in whatever the device uses for that kind of media."
                )
                GuideStep(
                    icon = Icons.Filled.BatteryChargingFull,
                    text = "Set this app's battery use to unrestricted, or Android will " +
                            "suspend a download as soon as you leave the app."
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
    Row(modifier = Modifier.padding(bottom = 18.dp)) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth()
        )
    }
    Spacer(modifier = Modifier.height(0.dp))
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
