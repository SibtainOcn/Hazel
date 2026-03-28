package com.hazel.android.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.util.openInAppBrowser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Software Update",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Refresh / re-check button
                    val isLoading = uiState is UpdateViewModel.UiState.Checking ||
                            uiState is UpdateViewModel.UiState.Downloading
                    IconButton(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Check for updates",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // ── Status Header with large animated icon ──
            StatusHeader(uiState, progress)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Version Info Card ──
            VersionCard(
                currentVersion = viewModel.currentVersion,
                newVersion = extractVersion(uiState)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Download Progress Card (visible only during download) ──
            val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
            val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

            AnimatedVisibility(
                visible = uiState is UpdateViewModel.UiState.Downloading,
                enter = fadeIn(tween(250, easing = emphasizedDecelerate)) +
                        expandVertically(tween(250, easing = emphasizedDecelerate)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(250, easing = emphasizedDecelerate)),
                exit = fadeOut(tween(200, easing = emphasizedAccelerate)) +
                        shrinkVertically(tween(200, easing = emphasizedAccelerate))
            ) {
                val dlState = uiState as? UpdateViewModel.UiState.Downloading
                if (dlState != null) {
                    DownloadProgressCard(
                        downloaded = dlState.downloaded,
                        total = dlState.total,
                        progress = progress
                    )
                }
            }

            // ── Action Buttons ──
            Spacer(modifier = Modifier.height(16.dp))
            ActionButtons(uiState, viewModel)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Info Section ──
            InfoCard(context, uiState)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─── Status Header ───

@Composable
private fun StatusHeader(
    state: UpdateViewModel.UiState,
    progress: Float
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Only show the sweeping fill animation during actual download
        val isActuallyDownloading = state is UpdateViewModel.UiState.Downloading

        if (isActuallyDownloading) {
            // ── Downloading: infinite sweep loop (not tied to real progress) ──
            val infiniteTransition = rememberInfiniteTransition(label = "download_sweep")
            val sweepFraction by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sweep"
            )

            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                // Dim base
                Icon(
                    painter = painterResource(R.drawable.install_ic),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = dimColor
                )
                // Accent fill with mask — sweeps infinitely top-to-bottom
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.install_ic),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = accentColor
                    )
                    Canvas(modifier = Modifier.size(72.dp)) {
                        val fillHeight = size.height * sweepFraction
                        drawRect(
                            color = Color.Transparent,
                            topLeft = Offset(0f, fillHeight),
                            size = Size(size.width, size.height - fillHeight),
                            blendMode = BlendMode.Clear
                        )
                    }
                }
            }
        } else if (state is UpdateViewModel.UiState.ReadyToInstall) {
            // ── Ready: fully filled accent icon (static) ──
            Icon(
                painter = painterResource(R.drawable.install_ic),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = accentColor
            )
        } else {
            // ── Available / Checking / Idle / Error: static dimmed icon ──
            Icon(
                painter = painterResource(R.drawable.install_ic),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = if (state is UpdateViewModel.UiState.Error)
                    MaterialTheme.colorScheme.error else dimColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status text
        Text(
            text = when (state) {
                is UpdateViewModel.UiState.Idle -> "You're up to date"
                is UpdateViewModel.UiState.Checking -> "Checking for updates..."
                is UpdateViewModel.UiState.Available -> "Update Available"
                is UpdateViewModel.UiState.Downloading -> "Downloading Update..."
                is UpdateViewModel.UiState.ReadyToInstall -> "Ready to Install"
                is UpdateViewModel.UiState.Error -> "Update Failed"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = when (state) {
                is UpdateViewModel.UiState.Error -> MaterialTheme.colorScheme.error
                is UpdateViewModel.UiState.ReadyToInstall -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when (state) {
                is UpdateViewModel.UiState.Idle -> "Hazel is running the latest version"
                is UpdateViewModel.UiState.Checking -> "Contacting GitHub..."
                is UpdateViewModel.UiState.Available -> "Version ${state.info.version} is available"
                is UpdateViewModel.UiState.Downloading -> {
                    val pct = if (state.total > 0)
                        "${((state.downloaded.toFloat() / state.total) * 100).toInt()}%"
                    else "..."
                    "Downloading — $pct"
                }
                is UpdateViewModel.UiState.ReadyToInstall ->
                    "Hazel v${state.info.version} is ready to install"
                is UpdateViewModel.UiState.Error -> state.message
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

// ─── Version Card ───

@Composable
private fun VersionCard(
    currentVersion: String,
    newVersion: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Current Version",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "v$currentVersion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (newVersion != null) {
                    Icon(
                        painter = painterResource(R.drawable.small_chevron),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "New Version",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "v$newVersion",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ─── Download Progress Card ───

@Composable
private fun DownloadProgressCard(
    downloaded: Long,
    total: Long,
    progress: Float
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "dl_bar"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Download Progress",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (total > 0) "${(animatedProgress * 100).toInt()}%" else "...",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = StrokeCap.Round
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Size text
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
                if (total > 0) {
                    Text(
                        formatFileSize(total - downloaded) + " remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// ─── Action Buttons ───

@Composable
private fun ActionButtons(
    state: UpdateViewModel.UiState,
    viewModel: UpdateViewModel
) {
    when (state) {
        is UpdateViewModel.UiState.Available -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionChip(
                    text = "Download Now",
                    onClick = { viewModel.startDownload() },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )
            }
        }
        is UpdateViewModel.UiState.Downloading -> {
            ActionChip(
                text = "Cancel Download",
                onClick = { viewModel.cancelDownload() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        is UpdateViewModel.UiState.ReadyToInstall -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionChip(
                    text = "Not Now",
                    onClick = { viewModel.dismissCompletely() },
                    modifier = Modifier.weight(1f)
                )
                ActionChip(
                    text = "Install Now",
                    onClick = { viewModel.installUpdate() },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )
            }
        }
        is UpdateViewModel.UiState.Error -> {
            ActionChip(
                text = "Retry",
                onClick = { viewModel.startDownload() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        is UpdateViewModel.UiState.Idle -> {
            ActionChip(
                text = "Check for Updates",
                onClick = { viewModel.checkForUpdate() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        is UpdateViewModel.UiState.Checking -> {
            // Show disabled state
            ActionChip(
                text = "Checking...",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isPrimary)
            MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.15f else 0.06f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f),
        enabled = enabled
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                color = if (isPrimary)
                    MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.3f)
            )
        }
    }
}

// ─── Info Card (Repository, Changelog, etc.) ───

@Composable
private fun InfoCard(
    context: android.content.Context,
    state: UpdateViewModel.UiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Information",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            InfoRow(label = "Application", value = "Hazel")

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            InfoRow(label = "Repository", value = "SibtainOcn/Hazel")

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            val apkSize = extractApkSize(state)
            if (apkSize > 0) {
                InfoRow(label = "APK Size", value = formatFileSize(apkSize))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            InfoRow(label = "Distribution", value = "GitHub Releases")

            Spacer(modifier = Modifier.height(14.dp))

            // Changelog button
            Surface(
                onClick = {
                    openInAppBrowser(context, "https://sibtainocn.github.io/Hazel/")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "View Changelog",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Utilities ───

private fun extractVersion(state: UpdateViewModel.UiState): String? = when (state) {
    is UpdateViewModel.UiState.Available -> state.info.version
    is UpdateViewModel.UiState.Downloading -> state.info.version
    is UpdateViewModel.UiState.ReadyToInstall -> state.info.version
    is UpdateViewModel.UiState.Error -> state.info?.version
    else -> null
}

private fun extractApkSize(state: UpdateViewModel.UiState): Long = when (state) {
    is UpdateViewModel.UiState.Available -> state.info.apkSize
    is UpdateViewModel.UiState.Downloading -> state.info.apkSize
    is UpdateViewModel.UiState.ReadyToInstall -> state.info.apkSize
    is UpdateViewModel.UiState.Error -> state.info?.apkSize ?: 0L
    else -> 0L
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
