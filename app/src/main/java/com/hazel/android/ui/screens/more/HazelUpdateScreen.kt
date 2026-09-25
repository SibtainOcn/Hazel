package com.hazel.android.ui.screens.more

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import com.hazel.android.update.HazelUpdateViewModel
import com.hazel.android.update.HazelUpdater
import com.hazel.android.update.UpdateListGroup
import com.hazel.android.update.UpdateListItem
import com.hazel.android.update.UpdateSectionLabel
import com.hazel.android.update.UpdateSegmentedButton
import com.hazel.android.update.UpdateSwitch
import com.hazel.android.update.UpdateTokens
import com.hazel.android.update.UpdateTopBar
import com.hazel.android.util.openInAppBrowser
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazelUpdateScreen(
    onBack: () -> Unit,
    viewModel: HazelUpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
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

    val installedVersion = HazelUpdater.installedVersion()

    Scaffold(
        topBar = {
            UpdateTopBar(
                title = "Hazel updates",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = uiState !is HazelUpdateViewModel.UiState.Downloading
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
        containerColor = UpdateTokens.Bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            // ── Hero Status Card ──
            HazelStatusCard(
                state = uiState,
                installedVersion = installedVersion,
                onCheckNow = { viewModel.checkForUpdate() },
                onDownload = { viewModel.startDownload() },
                onCancel = { viewModel.cancelDownload() },
                onInstall = { viewModel.installUpdate() },
                onOpenFdroid = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HazelUpdater.FDROID_PACKAGE_URL)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        openInAppBrowser(context, HazelUpdater.FDROID_PACKAGE_URL)
                    }
                },
                onViewChangelog = { ver, notes ->
                    changelogVersion = ver
                    changelogText = notes.ifBlank { "No detailed changelog provided for this release." }
                    showChangelogSheet = true
                }
            )

            // ── Update Channel (GitHub release builds only; F-Droid relies strictly on F-Droid repo releases) ──
            if (!HazelUpdater.isFdroid()) {
                UpdateSectionLabel(text = "Update channel", isFirst = true)
                val channels = listOf("Stable", "Beta", "Nightly")
                val selectedIdx = when (channel) {
                    HazelUpdater.Channel.STABLE -> 0
                    HazelUpdater.Channel.BETA -> 1
                    HazelUpdater.Channel.NIGHTLY -> 2
                }
                UpdateSegmentedButton(
                    options = channels,
                    selectedIndex = selectedIdx,
                    onSelect = { idx ->
                        when (idx) {
                            0 -> viewModel.setChannel(HazelUpdater.Channel.STABLE)
                            1 -> viewModel.setChannel(HazelUpdater.Channel.BETA)
                            2 -> viewModel.setChannel(HazelUpdater.Channel.NIGHTLY)
                        }
                    }
                )
            }

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
                    secondary = "github.com/${HazelUpdater.REPO_NAME}",
                    showDivider = false,
                    onClick = { openInAppBrowser(context, HazelUpdater.GITHUB_REPO_URL) },
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
                    text = "What's New in v$changelogVersion",
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
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun HazelStatusCard(
    state: HazelUpdateViewModel.UiState,
    installedVersion: String,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenFdroid: () -> Unit,
    onViewChangelog: (version: String, notes: String) -> Unit
) {
    val containerColor = when (state) {
        is HazelUpdateViewModel.UiState.Available -> UpdateTokens.UpdateContainer
        is HazelUpdateViewModel.UiState.Downloading -> UpdateTokens.RunContainer
        is HazelUpdateViewModel.UiState.ReadyToInstall -> UpdateTokens.AccentContainer
        is HazelUpdateViewModel.UiState.Idle -> UpdateTokens.AccentContainer
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
                        is HazelUpdateViewModel.UiState.Idle -> {
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
                        }
                        is HazelUpdateViewModel.UiState.Available -> {
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
                        is HazelUpdateViewModel.UiState.Downloading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulsingDot(color = UpdateTokens.RunStrong)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Downloading update…",
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
                                text = "Downloading release package",
                                fontSize = 14.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is HazelUpdateViewModel.UiState.ReadyToInstall -> {
                            Text(
                                text = "Download complete",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.AccentStrong
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Ready to install v${state.info.version}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Verified against project public key",
                                fontSize = 14.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is HazelUpdateViewModel.UiState.Checking -> {
                            Text(
                                text = "Checking for updates…",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.RunStrong
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (HazelUpdater.isFdroid()) "Connecting to F-Droid…" else "Connecting to GitHub…",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface,
                                lineHeight = 26.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (HazelUpdater.isFdroid()) "Querying F-Droid release feed" else "Querying repository releases",
                                fontSize = 13.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is HazelUpdateViewModel.UiState.FdroidManaged -> {
                            Text(
                                text = "F-Droid Distribution",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.AccentStrong
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Up to date",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.OnSurface,
                                lineHeight = 26.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Version $installedVersion installed",
                                fontSize = 13.sp,
                                color = UpdateTokens.OnSurfaceVar
                            )
                        }
                        is HazelUpdateViewModel.UiState.Error -> {
                            Text(
                                text = "Notice",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = UpdateTokens.Danger
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Check couldn't finish",
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

                // ── Status Badge ──
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when (state) {
                                is HazelUpdateViewModel.UiState.Available -> Color(0xFFFFCB80).copy(alpha = 0.14f)
                                is HazelUpdateViewModel.UiState.Downloading -> Color(0xFFA8CDFF).copy(alpha = 0.14f)
                                else -> Color(0xFF8FD6B8).copy(alpha = 0.14f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (state) {
                        is HazelUpdateViewModel.UiState.Available -> {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = UpdateTokens.Update,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        is HazelUpdateViewModel.UiState.Downloading -> {
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

            // ── Download Progress Figures ──
            if (state is HazelUpdateViewModel.UiState.Downloading) {
                Spacer(modifier = Modifier.height(20.dp))
                val pct = if (state.totalBytes > 0) {
                    (state.progressBytes.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f

                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = UpdateTokens.RunStrong,
                    trackColor = Color.White.copy(alpha = 0.10f)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val downloadedMb = state.progressBytes / (1024f * 1024f)
                    val totalMb = state.totalBytes / (1024f * 1024f)
                    Text(
                        text = String.format(Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UpdateTokens.OnSurface
                    )
                    Text(
                        text = "${(pct * 100).toInt()}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UpdateTokens.RunStrong
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val speedMb = state.speedBps / (1024f * 1024f)
                    Text(
                        text = if (state.speedBps > 0) String.format(Locale.US, "%.1f MB/s", speedMb) else "Connecting...",
                        fontSize = 12.5.sp,
                        color = UpdateTokens.OnSurfaceDim
                    )
                    Text(
                        text = if (state.etaSeconds > 0) "${state.etaSeconds}s remaining" else "",
                        fontSize = 12.5.sp,
                        color = UpdateTokens.OnSurfaceDim
                    )
                }
            }

            // ── Actions Row ──
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    is HazelUpdateViewModel.UiState.Idle -> {
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
                    is HazelUpdateViewModel.UiState.Available -> {
                        val isFdroid = HazelUpdater.isFdroid()
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(UpdateTokens.Update)
                                .clickable(onClick = if (isFdroid) onOpenFdroid else onDownload)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isFdroid) "Open in F-Droid" else "Download update",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.UpdateOn
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onViewChangelog(state.info.version, state.info.changelog) }
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
                    is HazelUpdateViewModel.UiState.Downloading -> {
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
                    is HazelUpdateViewModel.UiState.ReadyToInstall -> {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(UpdateTokens.Accent)
                                .clickable(onClick = onInstall)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Install update",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.AccentOn
                            )
                        }
                    }
                    is HazelUpdateViewModel.UiState.FdroidManaged -> {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(UpdateTokens.Accent)
                                .clickable(onClick = onOpenFdroid)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Open F-Droid",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UpdateTokens.AccentOn
                            )
                        }
                    }
                    is HazelUpdateViewModel.UiState.Error -> {
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

            // Download size info when available
            if (state is HazelUpdateViewModel.UiState.Available && state.info.binarySize > 0) {
                Spacer(modifier = Modifier.height(14.dp))
                val mb = state.info.binarySize / (1024f * 1024f)
                Text(
                    text = String.format(Locale.US, "%.1f MB Download size", mb),
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
