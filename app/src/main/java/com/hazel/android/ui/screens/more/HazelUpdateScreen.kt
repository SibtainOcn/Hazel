package com.hazel.android.ui.screens.more

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.R
import com.hazel.android.ui.components.AlertDialog
import com.hazel.android.update.DebugInfo
import com.hazel.android.update.HazelUpdateViewModel
import com.hazel.android.update.HazelUpdater
import com.hazel.android.update.UpdateSegmentedButton
import com.hazel.android.update.UpdateTokens
import com.hazel.android.update.UpdateTopBar
import com.hazel.android.update.UpdateWavyProgress
import com.hazel.android.util.openInAppBrowser
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazelUpdateScreen(
    onBack: () -> Unit,
    /** Opens What's new, the same page as under Documentation, as of the given version. */
    onOpenWhatsNew: (version: String) -> Unit = {},
    viewModel: HazelUpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val channel by viewModel.channel.collectAsState()

    var showInstallPermissionDialog by remember { mutableStateOf(false) }
    var waitingForInstallPermission by remember { mutableStateOf(false) }

    val triggerInstall: () -> Unit = {
        if (HazelUpdater.canInstallApks(context)) {
            viewModel.installUpdate(context)
        } else {
            showInstallPermissionDialog = true
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (HazelUpdater.canInstallApks(context)) {
            waitingForInstallPermission = false
            viewModel.installUpdate(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.installEvent.collect {
            triggerInstall()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (waitingForInstallPermission && HazelUpdater.canInstallApks(context)) {
                    waitingForInstallPermission = false
                    viewModel.installUpdate(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val installedVersion = HazelUpdater.installedVersion()

    if (showInstallPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showInstallPermissionDialog = false },
            title = {
                Text(
                    text = "Permission required",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UpdateTokens.OnSurface
                )
            },
            text = {
                Text(
                    text = "Hazel requires permission to install unknown apps to complete this update. You will be redirected to Settings to allow this.",
                    fontSize = 14.sp,
                    color = UpdateTokens.OnSurfaceVar,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInstallPermissionDialog = false
                        waitingForInstallPermission = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            try {
                                settingsLauncher.launch(settingsIntent)
                            } catch (_: Exception) {
                                waitingForInstallPermission = false
                                viewModel.installUpdate(context)
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Settings",
                        color = UpdateTokens.AccentStrong,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showInstallPermissionDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = UpdateTokens.OnSurfaceDim
                    )
                }
            },
            containerColor = UpdateTokens.Surface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    Scaffold(
        topBar = { UpdateTopBar(title = stringResource(R.string.upd_hazel_title), onBack = onBack) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = UpdateTokens.Bg
    ) { innerPadding ->
        val busy = uiState is HazelUpdateViewModel.UiState.Downloading || uiState is HazelUpdateViewModel.UiState.Checking
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HazelStatus(
                state = uiState,
                installedVersion = installedVersion,
                channel = channel,
                onCheckNow = { viewModel.checkForUpdate() },
                onDownload = { viewModel.startDownload() },
                onCancel = { viewModel.cancelDownload() },
                onInstall = { triggerInstall() },
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
                onViewChangelog = onOpenWhatsNew
            )

            val isFdroid = HazelUpdater.isFdroid()
            JoinedTiles(
                buildList {
                    if (!isFdroid) add(channel.label to stringResource(R.string.upd_tile_channel))
                    add(DebugInfo.versionCode() to stringResource(R.string.upd_tile_code))
                    DebugInfo.installedLabel(context)?.let { add(it to stringResource(R.string.upd_tile_installed)) }
                    add((if (isFdroid) "F-Droid" else "GitHub") to stringResource(R.string.upd_tile_source))
                }
            )

            UpdateActionRow(
                listOf(
                    UpdateAction(Icons.Filled.Refresh, R.string.upd_action_check, enabled = !busy) { viewModel.checkForUpdate() },
                    UpdateAction(Icons.AutoMirrored.Filled.List, R.string.upd_action_changelog) { onOpenWhatsNew(installedVersion) },
                    UpdateAction(Icons.Filled.NewReleases, R.string.upd_action_releases) {
                        openInAppBrowser(context, if (isFdroid) HazelUpdater.FDROID_PACKAGE_URL else DebugInfo.RELEASES_URL)
                    },
                    UpdateAction(Icons.Filled.ContentCopy, R.string.upd_action_copy) {
                        copyAndTell(context, "Hazel $installedVersion (${DebugInfo.versionCode()}) ${DebugInfo.build()}")
                    }
                )
            )

            // GitHub builds only; F-Droid ships what its own repository releases.
            if (!isFdroid) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.hazel.android.ui.screens.history.SectionLabel(R.string.upd_section_channel)
                    val selectedIdx = when (channel) {
                        HazelUpdater.Channel.STABLE -> 0
                        HazelUpdater.Channel.BETA -> 1
                        HazelUpdater.Channel.NIGHTLY -> 2
                    }
                    UpdateSegmentedButton(
                        options = listOf("Stable", "Beta", "Nightly"),
                        selectedIndex = selectedIdx,
                        onSelect = { idx ->
                            when (idx) {
                                0 -> viewModel.setChannel(HazelUpdater.Channel.STABLE)
                                1 -> viewModel.setChannel(HazelUpdater.Channel.BETA)
                                2 -> viewModel.setChannel(HazelUpdater.Channel.NIGHTLY)
                            }
                        }
                    )
                    Text(
                        stringResource(R.string.upd_channel_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            ComponentGroup(
                R.string.upd_section_source,
                listOf(
                    ComponentLink(
                        Icons.Filled.Security, stringResource(R.string.upd_repository),
                        "github.com/${HazelUpdater.REPO_NAME}", fresh = false
                    ) { openInAppBrowser(context, HazelUpdater.GITHUB_REPO_URL) }
                )
            )
        }
    }
}

/** What the update is doing, on a shaped mark that turns into live wavy progress while it works. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HazelStatus(
    state: HazelUpdateViewModel.UiState,
    installedVersion: String,
    channel: HazelUpdater.Channel,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenFdroid: () -> Unit,
    onViewChangelog: (version: String) -> Unit
) {
    val tone = when (state) {
        is HazelUpdateViewModel.UiState.Available -> UpdateTokens.Update
        is HazelUpdateViewModel.UiState.Downloading, is HazelUpdateViewModel.UiState.Checking -> UpdateTokens.RunStrong
        is HazelUpdateViewModel.UiState.Error -> UpdateTokens.Danger
        else -> UpdateTokens.Accent
    }
    val (title, line) = when (state) {
        is HazelUpdateViewModel.UiState.Idle ->
            "Version $installedVersion" to stringResource(R.string.upd_status_ok, channel.label)
        is HazelUpdateViewModel.UiState.Available -> "Version ${state.info.version}" to "Update available · you have $installedVersion"
        is HazelUpdateViewModel.UiState.Downloading -> "Version ${state.info.version}" to "Downloading update…"
        is HazelUpdateViewModel.UiState.ReadyToInstall -> "Version ${state.info.version}" to "Downloaded and ready to install"
        is HazelUpdateViewModel.UiState.Checking ->
            "Checking for updates…" to if (HazelUpdater.isFdroid()) "Connecting to F-Droid…" else "Connecting to GitHub…"
        is HazelUpdateViewModel.UiState.FdroidManaged -> "Version $installedVersion" to "Up to date · managed by F-Droid"
        is HazelUpdateViewModel.UiState.Error -> "Check couldn't finish" to state.message
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UpdateStatusHeader(
            title = title,
            line = line,
            tone = tone,
            onTone = onTone(state),
            icon = when (state) {
                is HazelUpdateViewModel.UiState.Available, is HazelUpdateViewModel.UiState.Downloading -> Icons.Filled.Download
                is HazelUpdateViewModel.UiState.ReadyToInstall -> Icons.Filled.InstallMobile
                is HazelUpdateViewModel.UiState.Error -> Icons.Filled.ErrorOutline
                else -> Icons.Filled.VerifiedUser
            },
            working = state is HazelUpdateViewModel.UiState.Downloading || state is HazelUpdateViewModel.UiState.Checking,
            progress = (state as? HazelUpdateViewModel.UiState.Downloading)?.takeIf { it.totalBytes > 0 }
                ?.let { (it.progressBytes.toFloat() / it.totalBytes).coerceIn(0f, 1f) }
        )

        if (state is HazelUpdateViewModel.UiState.Downloading) {
            val pct = if (state.totalBytes > 0) (state.progressBytes.toFloat() / state.totalBytes).coerceIn(0f, 1f) else 0f
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UpdateWavyProgress(progress = if (state.totalBytes > 0) pct else null)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val mb = 1024f * 1024f
                    val speed = if (state.speedBps > 0) String.format(Locale.US, "%.1f MB/s", state.speedBps / mb) else "Connecting…"
                    Text(
                        String.format(Locale.US, "%.1f / %.1f MB · %s", state.progressBytes / mb, state.totalBytes / mb, speed),
                        style = MaterialTheme.typography.bodySmall,
                        color = UpdateTokens.OnSurfaceVar,
                        modifier = Modifier.weight(1f)
                    )
                    val eta = if (state.etaSeconds > 0) " · ${state.etaSeconds}s" else ""
                    Text("${(pct * 100).toInt()}%$eta", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold, color = UpdateTokens.RunStrong)
                }
            }
        }

        val buttons: List<Triple<String, Boolean, () -> Unit>> = when (state) {
            is HazelUpdateViewModel.UiState.Idle -> listOf(
                Triple(stringResource(R.string.upd_action_changelog), false) { onViewChangelog(installedVersion) },
                Triple("Check now", true, onCheckNow)
            )
            is HazelUpdateViewModel.UiState.Available -> listOf(
                Triple("What's new", false) { onViewChangelog(state.info.version) },
                if (HazelUpdater.isFdroid()) Triple("Open in F-Droid", true, onOpenFdroid) else Triple("Download update", true, onDownload)
            )
            is HazelUpdateViewModel.UiState.Downloading -> listOf(Triple("Cancel", false, onCancel))
            is HazelUpdateViewModel.UiState.ReadyToInstall -> listOf(
                Triple("What's new", false) { onViewChangelog(state.info.version) },
                Triple("Install update", true, onInstall)
            )
            is HazelUpdateViewModel.UiState.FdroidManaged -> listOf(Triple("Open F-Droid", true, onOpenFdroid))
            is HazelUpdateViewModel.UiState.Error -> listOf(Triple("Retry", true, onCheckNow))
            is HazelUpdateViewModel.UiState.Checking -> emptyList()
        }
        UpdateStateButtons(buttons, tone, onTone(state))

        if (state is HazelUpdateViewModel.UiState.Available && state.info.binarySize > 0) {
            Text(
                String.format(Locale.US, "%.1f MB download", state.info.binarySize / (1024f * 1024f)),
                style = MaterialTheme.typography.bodySmall,
                color = UpdateTokens.OnSurfaceDim,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun onTone(state: HazelUpdateViewModel.UiState): Color = when (state) {
    is HazelUpdateViewModel.UiState.Available -> UpdateTokens.UpdateOn
    is HazelUpdateViewModel.UiState.Error -> MaterialTheme.colorScheme.onError
    else -> UpdateTokens.AccentOn
}

