package com.hazel.android.update

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.hazel.android.ui.components.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.util.openInAppBrowser
import java.util.Locale
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import com.hazel.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtDlpUpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val installedVersion by viewModel.installedVersion.collectAsState()
    val channel by viewModel.channel.collectAsState()

    val autoDownload by viewModel.autoDownload.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val notifyAvailable by viewModel.notifyAvailable.collectAsState()
    val notifyComplete by viewModel.notifyComplete.collectAsState()
    val notifyFailed by viewModel.notifyFailed.collectAsState()

    val displayInstalled = installedVersion ?: remember(context) { YtDlpUpdater.cachedVersion(context) } ?: "Bundled"

    Scaffold(
        topBar = {
            UpdateTopBar(title = "yt-dlp updates", onBack = onBack)
        },
        containerColor = UpdateTokens.Bg,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
        ) {
            // ── Hero Status Card ──
            YtDlpStatusCard(
                state = uiState,
                installedVersion = displayInstalled,
                channel = channel,
                onCheckNow = { viewModel.checkForUpdate() },
                onUpdate = { viewModel.startUpdate() },
                onCancel = { viewModel.cancelUpdate() },
                onDismiss = { viewModel.dismissCompletely() },
                onViewChangelog = { ver, repo ->
                    val cleanRepo = repo.ifBlank { channel.repo }
                    val cleanVer = ver.trim()
                    val url = if (cleanVer.firstOrNull()?.isDigit() == true) {
                        "https://github.com/$cleanRepo/releases/tag/$cleanVer"
                    } else {
                        channel.releasesUrl
                    }
                    openInAppBrowser(context, url)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
            com.hazel.android.ui.screens.more.JoinedTiles(
                listOf(
                    channel.label to stringResource(R.string.upd_tile_channel),
                    displayInstalled to stringResource(R.string.upd_tile_version),
                    DebugInfo.primaryAbi().substringBefore('-') to stringResource(R.string.upd_tile_cpu)
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            val busy = uiState is UpdateViewModel.UiState.Updating || uiState is UpdateViewModel.UiState.Checking
            com.hazel.android.ui.screens.more.UpdateActionRow(
                listOf(
                    com.hazel.android.ui.screens.more.UpdateAction(Icons.Filled.Refresh, R.string.upd_action_check, enabled = !busy) {
                        viewModel.checkForUpdate()
                    },
                    com.hazel.android.ui.screens.more.UpdateAction(Icons.Filled.NewReleases, R.string.upd_action_releases) {
                        openInAppBrowser(context, channel.releasesUrl)
                    },
                    com.hazel.android.ui.screens.more.UpdateAction(Icons.Filled.ContentCopy, R.string.upd_action_copy) {
                        com.hazel.android.ui.screens.more.copyAndTell(context, "yt-dlp $displayInstalled (${channel.label}, ${DebugInfo.primaryAbi()})")
                    }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── Update Channel ──
            UpdateSectionLabel(text = "Update channel", isFirst = true)
            val channels = listOf("Stable", "Nightly", "Master")
            val selectedIdx = when (channel) {
                YtDlpUpdater.Channel.STABLE -> 0
                YtDlpUpdater.Channel.NIGHTLY -> 1
                YtDlpUpdater.Channel.MASTER -> 2
            }
            UpdateSegmentedButton(
                options = channels,
                selectedIndex = selectedIdx,
                onSelect = { idx ->
                    when (idx) {
                        0 -> viewModel.setChannel(YtDlpUpdater.Channel.STABLE)
                        1 -> viewModel.setChannel(YtDlpUpdater.Channel.NIGHTLY)
                        2 -> viewModel.setChannel(YtDlpUpdater.Channel.MASTER)
                    }
                }
            )

            // ── Automatic Updates ──
            UpdateSectionLabel(text = "Automatic updates")
            UpdateListGroup {
                UpdateListItem(
                    icon = Icons.Filled.CloudDownload,
                    primary = "Auto-download updates",
                    secondary = "Fetch new builds from the release feed automatically",
                    showDivider = false,
                    trailing = {
                        UpdateSwitch(
                            checked = autoDownload,
                            onCheckedChange = { viewModel.setAutoDownload(it) }
                        )
                    }
                )
                UpdateListItem(
                    icon = Icons.Filled.Wifi,
                    primary = "Install on Wi-Fi only",
                    secondary = "Skip downloads on mobile data",
                    showDivider = true,
                    trailing = {
                        UpdateSwitch(
                            checked = wifiOnly,
                            onCheckedChange = { viewModel.setWifiOnly(it) }
                        )
                    }
                )
            }

            // ── Notifications ──
            UpdateSectionLabel(text = "Notifications")
            UpdateListGroup {
                UpdateListItem(
                    icon = Icons.Filled.Notifications,
                    primary = "New version available",
                    secondary = "Notify when an update is ready to install",
                    showDivider = false,
                    trailing = {
                        UpdateSwitch(
                            checked = notifyAvailable,
                            onCheckedChange = { viewModel.setNotifyAvailable(it) }
                        )
                    }
                )
                UpdateListItem(
                    icon = Icons.Filled.VerifiedUser,
                    primary = "Install complete",
                    secondary = "Confirm once an update finishes installing",
                    showDivider = true,
                    trailing = {
                        UpdateSwitch(
                            checked = notifyComplete,
                            onCheckedChange = { viewModel.setNotifyComplete(it) }
                        )
                    }
                )
                UpdateListItem(
                    icon = Icons.Filled.Close,
                    primary = "Update failed",
                    secondary = "Alert if a download or install couldn't finish",
                    showDivider = true,
                    trailing = {
                        UpdateSwitch(
                            checked = notifyFailed,
                            onCheckedChange = { viewModel.setNotifyFailed(it) }
                        )
                    }
                )
            }

            // ── Source & Verification ──
            UpdateSectionLabel(text = "Source")
            UpdateListGroup {
                UpdateListItem(
                    icon = Icons.Filled.Security,
                    primary = "Repository",
                    secondary = "github.com/${channel.repo}",
                    showDivider = false,
                    onClick = { openInAppBrowser(context, channel.releasesUrl) },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = UpdateTokens.OnSurfaceDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }


}

/** Backward compatibility alias */
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel()
) = YtDlpUpdateScreen(onBack = onBack, viewModel = viewModel)

/** What the engine update is doing, drawn the same way as Hazel's own update status. */
@Composable
internal fun YtDlpStatusCard(
    state: UpdateViewModel.UiState,
    installedVersion: String,
    channel: YtDlpUpdater.Channel,
    onCheckNow: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onViewChangelog: (version: String, repo: String) -> Unit
) {
    val tone = when (state) {
        is UpdateViewModel.UiState.Available -> UpdateTokens.Update
        is UpdateViewModel.UiState.Updating, is UpdateViewModel.UiState.Checking -> UpdateTokens.RunStrong
        is UpdateViewModel.UiState.Error -> UpdateTokens.Danger
        else -> UpdateTokens.Accent
    }
    val onTone = when (state) {
        is UpdateViewModel.UiState.Available -> UpdateTokens.UpdateOn
        is UpdateViewModel.UiState.Error -> MaterialTheme.colorScheme.onError
        else -> UpdateTokens.AccentOn
    }
    val (title, line) = when (state) {
        is UpdateViewModel.UiState.Idle -> "Version $installedVersion" to "Up to date on ${channel.label}"
        is UpdateViewModel.UiState.Available -> "Version ${state.info.version}" to "Update available · you have $installedVersion"
        is UpdateViewModel.UiState.Updating -> "Version ${state.info.version}" to "Swapping extractor binary…"
        is UpdateViewModel.UiState.Installed -> "Version ${state.version}" to "Installed and live"
        is UpdateViewModel.UiState.Checking -> "Checking for updates…" to "Reading the ${channel.label} release feed"
        is UpdateViewModel.UiState.Error -> "Check couldn't finish" to state.message
    }
    val progress = (state as? UpdateViewModel.UiState.Updating)?.takeIf { it.totalBytes > 0 }
        ?.let { (it.progressBytes.toFloat() / it.totalBytes).coerceIn(0f, 1f) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        com.hazel.android.ui.screens.more.UpdateStatusHeader(
            title = title,
            line = line,
            tone = tone,
            onTone = onTone,
            icon = when (state) {
                is UpdateViewModel.UiState.Available, is UpdateViewModel.UiState.Updating -> Icons.Filled.Download
                is UpdateViewModel.UiState.Error -> Icons.Filled.ErrorOutline
                else -> Icons.Filled.VerifiedUser
            },
            working = state is UpdateViewModel.UiState.Updating || state is UpdateViewModel.UiState.Checking,
            progress = progress
        )

        if (state is UpdateViewModel.UiState.Updating) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UpdateWavyProgress(
                    progress = when {
                        state.totalBytes > 0 -> progress
                        state.progressBytes > 0 -> null
                        else -> 0f
                    }
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val mb = 1024f * 1024f
                    Text(
                        if (state.totalBytes > 0) String.format(Locale.US, "%.1f / %.1f MB", state.progressBytes / mb, state.totalBytes / mb)
                        else "Installing new binary",
                        style = MaterialTheme.typography.bodySmall,
                        color = UpdateTokens.OnSurfaceVar,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        progress?.let { "${(it * 100).toInt()}%" } ?: "In progress",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = UpdateTokens.RunStrong
                    )
                }
            }
        }

        com.hazel.android.ui.screens.more.UpdateStateButtons(
            when (state) {
                is UpdateViewModel.UiState.Idle -> listOf(
                    Triple("Release notes", false) { onViewChangelog(installedVersion, channel.repo) },
                    Triple("Check now", true, onCheckNow)
                )
                is UpdateViewModel.UiState.Available -> listOf(
                    Triple("What's new", false) { onViewChangelog(state.info.version, state.info.channel.repo) },
                    Triple("Update engine", true, onUpdate)
                )
                is UpdateViewModel.UiState.Updating -> listOf(Triple("Cancel", false, onCancel))
                is UpdateViewModel.UiState.Installed -> listOf(Triple("Done", true, onDismiss))
                is UpdateViewModel.UiState.Error -> listOf(Triple("Retry", true, onCheckNow))
                is UpdateViewModel.UiState.Checking -> emptyList()
            },
            tone,
            onTone
        )

        if (state is UpdateViewModel.UiState.Available && state.info.binarySize > 0) {
            Text(
                String.format(Locale.US, "%.1f MB binary", state.info.binarySize / (1024f * 1024f)),
                style = MaterialTheme.typography.bodySmall,
                color = UpdateTokens.OnSurfaceDim,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
