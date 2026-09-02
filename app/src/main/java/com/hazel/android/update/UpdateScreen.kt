package com.hazel.android.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.R
import com.hazel.android.ui.components.HazelLoadingIndicator
import com.hazel.android.util.openInAppBrowser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val installedVersion by viewModel.installedVersion.collectAsState()
    val channel by viewModel.channel.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.update_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.update_back)
                        )
                    }
                },
                actions = {
                    // Refresh / re-check button
                    val isLoading = uiState is UpdateViewModel.UiState.Checking ||
                            uiState is UpdateViewModel.UiState.Updating
                    IconButton(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.update_check_action),
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
            StatusHeader(uiState)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Version Info Card ──
            VersionCard(
                installedVersion = installedVersion,
                newVersion = extractVersion(uiState)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Install Progress Card (visible only while updating) ──
            val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
            val emphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

            AnimatedVisibility(
                visible = uiState is UpdateViewModel.UiState.Updating,
                enter = fadeIn(tween(250, easing = emphasizedDecelerate)) +
                        expandVertically(tween(250, easing = emphasizedDecelerate)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(250, easing = emphasizedDecelerate)),
                exit = fadeOut(tween(200, easing = emphasizedAccelerate)) +
                        shrinkVertically(tween(200, easing = emphasizedAccelerate))
            ) {
                val updating = uiState as? UpdateViewModel.UiState.Updating
                if (updating != null) {
                    InstallProgressCard(binarySize = updating.info.binarySize)
                }
            }

            // ── Action Buttons ──
            Spacer(modifier = Modifier.height(16.dp))
            ActionButtons(uiState, viewModel)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Info Section ──
            InfoCard(
                context = context,
                state = uiState,
                channel = channel,
                onChannelSelected = { viewModel.setChannel(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─── Status Header ───

@Composable
private fun StatusHeader(state: UpdateViewModel.UiState) {
    val accentColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isUpdating = state is UpdateViewModel.UiState.Updating

        if (isUpdating) {
            // ── Updating: infinite sweep loop (the binary swap reports no byte progress) ──
            val infiniteTransition = rememberInfiniteTransition(label = "install_sweep")
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
        } else if (state is UpdateViewModel.UiState.Checking) {
            // Indeterminate wait on the network: the expressive shape loader.
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                HazelLoadingIndicator(size = 56.dp)
            }
        } else if (state is UpdateViewModel.UiState.Installed) {
            // ── Installed: fully filled accent icon (static) ──
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
                is UpdateViewModel.UiState.Idle -> stringResource(R.string.update_status_up_to_date)
                is UpdateViewModel.UiState.Checking -> stringResource(R.string.update_status_checking)
                is UpdateViewModel.UiState.Available -> stringResource(R.string.update_status_available)
                is UpdateViewModel.UiState.Updating -> stringResource(R.string.update_status_installing)
                is UpdateViewModel.UiState.Installed -> stringResource(R.string.update_status_installed)
                is UpdateViewModel.UiState.Error -> stringResource(R.string.update_status_failed)
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = when (state) {
                is UpdateViewModel.UiState.Error -> MaterialTheme.colorScheme.error
                is UpdateViewModel.UiState.Installed -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when (state) {
                is UpdateViewModel.UiState.Idle ->
                    stringResource(R.string.update_subtitle_idle)
                is UpdateViewModel.UiState.Checking -> stringResource(R.string.update_subtitle_checking)
                is UpdateViewModel.UiState.Available ->
                    stringResource(R.string.update_subtitle_available, state.info.version)
                is UpdateViewModel.UiState.Updating ->
                    stringResource(R.string.update_subtitle_updating, state.info.version)
                is UpdateViewModel.UiState.Installed ->
                    stringResource(R.string.update_subtitle_installed, state.version)
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
    installedVersion: String?,
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
                        stringResource(R.string.update_installed_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        installedVersion ?: stringResource(R.string.update_version_unknown),
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
                            stringResource(R.string.update_new_version),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            newVersion,
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

// ─── Install Progress Card ───

@Composable
private fun InstallProgressCard(binarySize: Long) {
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
                    stringResource(R.string.update_installing_binary),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (binarySize > 0) {
                    Text(
                        formatFileSize(binarySize),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // yt-dlp's installer streams the binary without reporting bytes — indeterminate
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                stringResource(R.string.update_installing_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
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
                    text = stringResource(R.string.update_action_update_now),
                    onClick = { viewModel.startUpdate() },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )
            }
        }
        is UpdateViewModel.UiState.Updating -> {
            ActionChip(
                text = stringResource(R.string.update_action_installing),
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )
        }
        is UpdateViewModel.UiState.Installed -> {
            ActionChip(
                text = stringResource(R.string.update_action_done),
                onClick = { viewModel.dismissCompletely() },
                modifier = Modifier.fillMaxWidth(),
                isPrimary = true
            )
        }
        is UpdateViewModel.UiState.Error -> {
            ActionChip(
                text = stringResource(R.string.update_action_retry),
                onClick = {
                    if (state.info != null) viewModel.startUpdate() else viewModel.checkForUpdate()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        is UpdateViewModel.UiState.Idle -> {
            ActionChip(
                text = stringResource(R.string.update_action_check),
                onClick = { viewModel.checkForUpdate() },
                modifier = Modifier.fillMaxWidth()
            )
        }
        is UpdateViewModel.UiState.Checking -> {
            // Show disabled state
            ActionChip(
                text = stringResource(R.string.update_action_checking),
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

// ─── Info Card (Channel, Repository, Changelog) ───

@Composable
private fun InfoCard(
    context: android.content.Context,
    state: UpdateViewModel.UiState,
    channel: YtDlpUpdater.Channel,
    onChannelSelected: (YtDlpUpdater.Channel) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.update_info_heading),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            InfoRow(label = stringResource(R.string.update_info_component), value = "yt-dlp")

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            InfoRow(label = stringResource(R.string.update_info_repository), value = channel.repo)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            val binarySize = extractBinarySize(state)
            if (binarySize > 0) {
                InfoRow(label = stringResource(R.string.update_info_binary_size), value = formatFileSize(binarySize))

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            InfoRow(label = stringResource(R.string.update_info_distribution), value = stringResource(R.string.update_info_github_releases))

            Spacer(modifier = Modifier.height(14.dp))

            // Release channel picker
            Text(
                stringResource(R.string.update_release_channel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                YtDlpUpdater.Channel.entries.forEach { option ->
                    ChannelChip(
                        label = option.label,
                        selected = option == channel,
                        enabled = state !is UpdateViewModel.UiState.Updating,
                        onClick = { onChannelSelected(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Changelog button — yt-dlp release notes for the active channel
            Surface(
                onClick = { openInAppBrowser(context, channel.releasesUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.update_view_changelog),
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
private fun ChannelChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(10.dp),
        enabled = enabled,
        color = if (selected)
            MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.15f else 0.06f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.7f else 0.3f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected)
                    MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f)
            )
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
    is UpdateViewModel.UiState.Updating -> state.info.version
    is UpdateViewModel.UiState.Error -> state.info?.version
    else -> null
}

private fun extractBinarySize(state: UpdateViewModel.UiState): Long = when (state) {
    is UpdateViewModel.UiState.Available -> state.info.binarySize
    is UpdateViewModel.UiState.Updating -> state.info.binarySize
    is UpdateViewModel.UiState.Error -> state.info?.binarySize ?: 0L
    else -> 0L
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
