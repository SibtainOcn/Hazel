package com.hazel.android.ui.screens.more

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.screens.download.isIgnoringBatteryOptimizations
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.text.font.FontWeight
import com.hazel.android.download.formatFileSize
import com.hazel.android.update.YtDlpUpdater
import com.hazel.android.util.TempStorage

@Composable
fun MoreScreen(
    onNavigateToAppearance: () -> Unit = {},
    onNavigateToConverter: () -> Unit = {},
    onNavigateToStorageLocations: () -> Unit = {},
    onNavigateToCookies: () -> Unit = {},
    onNavigateToFetchSettings: () -> Unit = {},
    onNavigateToDirectShare: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onNavigateToStorageCleanup: () -> Unit = {},
    onNavigateToUpdate: () -> Unit = {}
) {
    val context = LocalContext.current

    // Read when the screen appears so the row can show what clearing would free.
    var tempBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { tempBytes = TempStorage.totalBytes(context) }

    // Read once as the screen is built, then again on every return to it, so coming back
    // from the system settings shows the answer that was just given.
    //
    // The first read happens here rather than being left to the resume below. Inside a
    // navigation destination the lifecycle is the back stack entry's own, so it resumes on
    // every visit to this tab, which meant the screen drew once on an assumed answer and
    // corrected itself a moment later: the card appeared out of nowhere and pushed
    // everything under it down. The call is a single lookup, cheap enough to make before
    // the first frame instead of guessing.
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // yt-dlp engine version, as recorded by the last in-app engine update

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // The screen names itself, the way the downloads list does. The app's name used to
        // sit above it in a bar of its own; with that gone this is what the screen opens
        // on, rather than the first card arriving with no introduction.
        Text(
            "More",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Android suspends a long network job shortly after the app leaves the foreground,
        // so without this exemption a download stops when the screen is turned off. It
        // heads the screen while it is missing and disappears once granted, because at that
        // point there is nothing left to do about it.
        if (!batteryExempt) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = onOpenBatterySettings
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.battery_charge),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Background downloads",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Downloads keep running in the background on their own. Some " +
                                    "phones still cut them short to save battery. Allow " +
                                    "unrestricted use to rule that out.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                .copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // ── Main options card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            // Download location, read-only
            ListItem(
                headlineContent = { Text("Downloads") },
                leadingContent = {
                    Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToStorageLocations() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Appearance: theme and accent colour
            ListItem(
                headlineContent = { Text("Appearance") },
                leadingContent = {
                    Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToAppearance() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Cookies: saved sign-ins that let the downloader reach gated media
            ListItem(
                headlineContent = { Text("Cookies") },
                leadingContent = {
                    Icon(Icons.Filled.Cookie, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToCookies() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Link reading: network bounds used while resolving a pasted link
            ListItem(
                headlineContent = { Text("Link Reading") },
                leadingContent = {
                    Icon(Icons.Filled.Speed, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToFetchSettings() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // What the second share target does, which is the only place those choices can
            // be made: sharing to it never opens the sheet.
            ListItem(
                headlineContent = { Text("Hazel Instant") },
                leadingContent = {
                    Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToDirectShare() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Working files the app holds, with the running total so it is visible without
            // opening the screen.
            ListItem(
                headlineContent = { Text("Temporary Files") },
                leadingContent = {
                    Icon(
                        Icons.Filled.DeleteSweep, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatFileSize(tempBytes).ifBlank { "0 KB" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToStorageCleanup() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // The one tool the app has, reached directly. It used to sit behind a Tools
            // screen that held nothing else, which is a tap and a screen to say one word.
            ListItem(
                headlineContent = { Text("Offline convert video to audio") },
                leadingContent = {
                    Icon(
                        painterResource(R.drawable.music), null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToConverter() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Check for updates, which opens the dedicated yt-dlp update screen
            ListItem(
                headlineContent = { Text("yt-dlp Engine Update") },
                leadingContent = {
                    Icon(Icons.Filled.Update, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onNavigateToUpdate() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Sits at the end, under everything the app can do for itself. No mark on the
            // right: the line under it already says where it goes, and the chevrons above
            // mean "another screen in here", which this is not.
            ListItem(
                headlineContent = { Text("Source code") },
                leadingContent = {
                    Icon(Icons.Filled.Code, null, tint = MaterialTheme.colorScheme.primary)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { openExternally(context, SOURCE_URL) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // Opened in the user's own browser rather than in the app's, because a licence
            // is a thing people save, share and read next to something else, and none of
            // that is possible in a window that closes when this screen does.
            ListItem(
                headlineContent = { Text("License") },
                leadingContent = {
                    Icon(
                        Icons.Filled.Gavel, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { openExternally(context, LICENSE_URL) }
            )
        }
    }
}

/**
 * Hands an address to whatever the user browses with.
 *
 * Failure is swallowed: a device with no browser at all cannot be helped by a crash, and
 * nothing that opens this way is load-bearing.
 */
private fun openExternally(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private const val SOURCE_URL = "https://github.com/SibtainOcn/Hazel"
private const val LICENSE_URL = "https://github.com/SibtainOcn/Hazel/blob/main/LICENSE"
