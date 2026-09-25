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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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
    val verifySignature by viewModel.verifySignature.collectAsState()

    var showChangelogSheet by remember { mutableStateOf(false) }
    var changelogText by remember { mutableStateOf("") }
    var changelogVersion by remember { mutableStateOf("") }

    val displayInstalled = installedVersion ?: YtDlpUpdater.cachedVersion(context) ?: "Bundled"

    Scaffold(
        topBar = {
            UpdateTopBar(
                title = "yt-dlp updates",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = uiState !is UpdateViewModel.UiState.Updating
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = UpdateTokens.OnSurface
                        )
                    }
                }
            )
        },
        containerColor = UpdateTokens.Bg,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
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
                    changelogVersion = ver
                    changelogText = "Latest binary release for channel ${channel.label} from $repo.\n\nIncludes recent site extractor patches and streaming protocol fixes."
                    showChangelogSheet = true
                }
            )

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
            UpdateSectionLabel(text = "Source & verification")
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
                UpdateListItem(
                    icon = Icons.Filled.VerifiedUser,
                    primary = "Verify signing key",
                    secondary = "Match the release signature before installing",
                    showDivider = true,
                    trailing = {
                        UpdateSwitch(
                            checked = verifySignature,
                            onCheckedChange = { viewModel.setVerifySignature(it) }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── What's New Bottom Sheet ──
    if (showChangelogSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showChangelogSheet = false },
            sheetState = sheetState,
            containerColor = UpdateTokens.Surface,
            contentColor = UpdateTokens.OnSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "yt-dlp Release $changelogVersion",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UpdateTokens.OnSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = changelogText,
                    fontSize = 14.sp,
                    color = UpdateTokens.OnSurfaceVar,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(UpdateTokens.Accent)
                        .clickable {
                            openInAppBrowser(context, channel.releasesUrl)
                            showChangelogSheet = false
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "View on GitHub",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UpdateTokens.AccentOn
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

/** Backward compatibility alias */
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel()
) = YtDlpUpdateScreen(onBack = onBack, viewModel = viewModel)

@Composable
private fun YtDlpStatusCard(
    state: UpdateViewModel.UiState,
    installedVersion: String,
    channel: YtDlpUpdater.Channel,
    onCheckNow: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onViewChangelog: (version: String, repo: String) -> Unit
) {
    val containerColor = when (state) {
        is UpdateViewModel.UiState.Available -> UpdateTokens.UpdateContainer
        is UpdateViewModel.UiState.Updating -> UpdateTokens.RunContainer
        is UpdateViewModel.UiState.Installed -> UpdateTokens.AccentContainer
        is UpdateViewModel.UiState.Idle -> UpdateTokens.AccentContainer
        else -> UpdateTokens.Surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    when (state) {
                        is UpdateViewModel.UiState.Idle -> {
                            Text(
                                text = "Up to date",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.AccentStrong
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Version $installedVersion installed",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface,
                                lineHeight = 26.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No updates available on ${channel.label} channel",
                                fontSize = 13.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is UpdateViewModel.UiState.Available -> {
                            Text(
                                text = "Update available",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.UpdateStrong
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Version ${state.info.version} is ready",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface,
                                lineHeight = 26.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You have $installedVersion",
                                fontSize = 13.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is UpdateViewModel.UiState.Updating -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulsingDot(color = UpdateTokens.RunStrong)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Updating engine…",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = UpdateTokens.RunStrong
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Version ${state.info.version}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Swapping extractor binary",
                                fontSize = 14.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is UpdateViewModel.UiState.Installed -> {
                            Text(
                                text = "Installed",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.AccentStrong
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Engine updated to ${state.version}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Binary swap completed successfully",
                                fontSize = 14.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is UpdateViewModel.UiState.Checking -> {
                            Text(
                                text = "Checking for updates…",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.RunStrong
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Querying yt-dlp…",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Contacting repository feeds",
                                fontSize = 14.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is UpdateViewModel.UiState.Error -> {
                            Text(
                                text = "Notice",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.Danger
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Update check failed",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                fontSize = 14.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                    }
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when (state) {
                                is UpdateViewModel.UiState.Available -> Color(0xFFFFCB80).copy(alpha = if (UpdateTokens.isDark) 0.14f else 0.35f)
                                is UpdateViewModel.UiState.Updating -> Color(0xFFA8CDFF).copy(alpha = if (UpdateTokens.isDark) 0.14f else 0.35f)
                                else -> Color(0xFF8FD6B8).copy(alpha = if (UpdateTokens.isDark) 0.14f else 0.35f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        is UpdateViewModel.UiState.Available -> {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = UpdateTokens.Update,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        is UpdateViewModel.UiState.Updating -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = UpdateTokens.RunStrong,
                                strokeWidth = 2.5.dp
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = UpdateTokens.Accent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Continuous progress indicator while updating
            if (state is UpdateViewModel.UiState.Updating) {
                Spacer(modifier = Modifier.height(20.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = UpdateTokens.RunStrong,
                    trackColor = if (UpdateTokens.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Installing new binary",
                        fontSize = 13.sp,
                        color = UpdateTokens.OnSurfaceVar
                    )
                    Text(
                        text = "In progress",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = UpdateTokens.RunStrong
                    )
                }
            }

            // Actions row
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is UpdateViewModel.UiState.Idle -> {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(UpdateTokens.Accent)
                                .clickable(onClick = onCheckNow)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Check now",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.AccentOn
                            )
                        }
                    }
                    is UpdateViewModel.UiState.Available -> {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(UpdateTokens.Update)
                                .clickable(onClick = onUpdate)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Update engine",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.UpdateOn
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onViewChangelog(state.info.version, state.info.channel.repo) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "What's new",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.UpdateStrong
                            )
                        }
                    }
                    is UpdateViewModel.UiState.Updating -> {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(UpdateTokens.Surface2)
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.OnSurface
                            )
                        }
                    }
                    is UpdateViewModel.UiState.Installed -> {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(UpdateTokens.Accent)
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Done",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.AccentOn
                            )
                        }
                    }
                    is UpdateViewModel.UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(UpdateTokens.Surface2)
                                .clickable(onClick = onCheckNow)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Retry",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.OnSurface
                            )
                        }
                    }
                    else -> {}
                }
            }

            // Binary size when available
            if (state is UpdateViewModel.UiState.Available && state.info.binarySize > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                val mb = state.info.binarySize / (1024f * 1024f)
                Text(
                    text = String.format(Locale.US, "%.1f MB Binary size", mb),
                    fontSize = 12.5.sp,
                    color = UpdateTokens.OnSurfaceDim
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
