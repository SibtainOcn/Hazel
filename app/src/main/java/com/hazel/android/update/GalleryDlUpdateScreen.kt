package com.hazel.android.update

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.R
import com.hazel.android.gallery.GalleryDlPayload
import com.hazel.android.ui.screens.more.JoinedTiles
import com.hazel.android.ui.screens.more.UpdateAction
import com.hazel.android.ui.screens.more.UpdateActionRow
import com.hazel.android.ui.screens.more.UpdateStateButtons
import com.hazel.android.ui.screens.more.UpdateStatusHeader
import com.hazel.android.ui.screens.more.copyAndTell
import com.hazel.android.util.openInAppBrowser
import kotlinx.coroutines.launch
import java.util.Locale

/** gallery-dl's update screen, laid out like yt-dlp's. */
@Composable
fun GalleryDlUpdateScreen(
    onBack: () -> Unit,
    viewModel: GalleryDlUpdateViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val installed by viewModel.installed.collectAsState()
    val usesBundled by viewModel.usesBundled.collectAsState()
    val autoDownload by viewModel.autoDownload.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val notifyAvailable by viewModel.notifyAvailable.collectAsState()
    val notifyComplete by viewModel.notifyComplete.collectAsState()
    val notifyFailed by viewModel.notifyFailed.collectAsState()
    val busy = state is GalleryDlUpdateViewModel.UiState.Updating || state is GalleryDlUpdateViewModel.UiState.Checking

    Scaffold(
        topBar = { UpdateTopBar(title = stringResource(R.string.upd_gallerydl_title), onBack = onBack) },
        containerColor = UpdateTokens.Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)
        ) {
            GalleryDlStatusCard(
                state = state,
                installed = installed,
                onCheck = viewModel::check,
                onInstall = viewModel::install,
                onCancel = viewModel::cancel,
                onDismiss = viewModel::dismiss,
                onReleaseNotes = { version -> openInAppBrowser(context, "${GalleryDlUpdater.RELEASES_URL}/tag/v$version") }
            )

            Spacer(modifier = Modifier.height(20.dp))
            JoinedTiles(
                listOf(
                    installed to stringResource(R.string.upd_tile_version),
                    // The copy shipped inside the app: used offline and whenever an update is reset.
                    GalleryDlPayload.BUNDLED_VERSION to stringResource(R.string.upd_tile_builtin)
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            UpdateActionRow(
                listOf(
                    UpdateAction(Icons.Filled.Refresh, R.string.upd_action_check, enabled = !busy, onClick = viewModel::check),
                    UpdateAction(Icons.Filled.NewReleases, R.string.upd_action_releases) {
                        openInAppBrowser(context, GalleryDlUpdater.RELEASES_URL)
                    },
                    UpdateAction(Icons.Filled.ContentCopy, R.string.upd_action_copy) {
                        copyAndTell(context, "gallery-dl $installed (${if (usesBundled) "bundled" else "PyPI"})")
                    },
                    UpdateAction(Icons.Filled.Inventory2, R.string.upd_action_use_builtin, enabled = !usesBundled && !busy) {
                        scope.launch {
                            val message = if (viewModel.useBundled()) {
                                context.getString(R.string.upd_bundled_restored, GalleryDlPayload.BUNDLED_VERSION)
                            } else {
                                context.getString(R.string.upd_busy)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            UpdateSectionLabel(text = stringResource(R.string.upd_section_automatic))
            UpdateListGroup {
                UpdateListItem(
                    icon = Icons.Filled.CloudDownload,
                    primary = stringResource(R.string.upd_auto_download_title),
                    secondary = stringResource(R.string.upd_gallerydl_auto_summary),
                    trailing = { UpdateSwitch(checked = autoDownload, onCheckedChange = { viewModel.setAutoDownload(it) }) }
                )
                UpdateListItem(
                    icon = Icons.Filled.Wifi,
                    primary = stringResource(R.string.upd_wifi_only_title),
                    secondary = stringResource(R.string.upd_wifi_only_summary),
                    trailing = { UpdateSwitch(checked = wifiOnly, onCheckedChange = { viewModel.setWifiOnly(it) }) }
                )
            }

            UpdateSectionLabel(text = stringResource(R.string.upd_section_notifications))
            UpdateListGroup {
                UpdateListItem(
                    icon = Icons.Filled.Notifications,
                    primary = stringResource(R.string.upd_notify_new_title),
                    secondary = stringResource(R.string.upd_notify_engines_summary),
                    trailing = { UpdateSwitch(checked = notifyAvailable, onCheckedChange = { viewModel.setNotifyAvailable(it) }) }
                )
                UpdateListItem(
                    icon = Icons.Filled.VerifiedUser,
                    primary = stringResource(R.string.upd_notify_installed_title),
                    trailing = { UpdateSwitch(checked = notifyComplete, onCheckedChange = { viewModel.setNotifyComplete(it) }) }
                )
                UpdateListItem(
                    icon = Icons.Filled.Close,
                    primary = stringResource(R.string.upd_notify_failed_title),
                    trailing = { UpdateSwitch(checked = notifyFailed, onCheckedChange = { viewModel.setNotifyFailed(it) }) }
                )
            }

            UpdateSectionLabel(text = stringResource(R.string.upd_section_source))
            UpdateListGroup {
                UpdateListItem(
                    icon = Icons.Filled.Security,
                    primary = stringResource(R.string.upd_repository),
                    secondary = "pypi.org/project/gallery-dl",
                    onClick = { openInAppBrowser(context, GalleryDlUpdater.PROJECT_URL) },
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
        }
    }
}

@Composable
internal fun GalleryDlStatusCard(
    state: GalleryDlUpdateViewModel.UiState,
    installed: String,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onReleaseNotes: (String) -> Unit
) {
    val tone = when (state) {
        is GalleryDlUpdateViewModel.UiState.Available -> UpdateTokens.Update
        is GalleryDlUpdateViewModel.UiState.Updating, GalleryDlUpdateViewModel.UiState.Checking -> UpdateTokens.RunStrong
        is GalleryDlUpdateViewModel.UiState.Error -> UpdateTokens.Danger
        else -> UpdateTokens.Accent
    }
    val onTone = when (state) {
        is GalleryDlUpdateViewModel.UiState.Available -> UpdateTokens.UpdateOn
        is GalleryDlUpdateViewModel.UiState.Error -> MaterialTheme.colorScheme.onError
        else -> UpdateTokens.AccentOn
    }
    val (title, line) = when (state) {
        GalleryDlUpdateViewModel.UiState.Checking ->
            stringResource(R.string.upd_status_checking) to "pypi.org"
        GalleryDlUpdateViewModel.UiState.Idle ->
            stringResource(R.string.upd_version, installed) to stringResource(R.string.upd_status_ok, "PyPI")
        is GalleryDlUpdateViewModel.UiState.Available ->
            stringResource(R.string.upd_version, state.release.version) to stringResource(R.string.upd_status_available, installed)
        is GalleryDlUpdateViewModel.UiState.Updating ->
            stringResource(R.string.upd_version, state.release.version) to stringResource(R.string.upd_status_downloading)
        is GalleryDlUpdateViewModel.UiState.Installed ->
            stringResource(R.string.upd_version, state.version) to stringResource(R.string.upd_status_live)
        is GalleryDlUpdateViewModel.UiState.Error ->
            stringResource(if (state.release != null) R.string.upd_status_update_failed else R.string.upd_status_check_failed) to
                stringResource(state.problem.message)
    }
    val updating = state as? GalleryDlUpdateViewModel.UiState.Updating
    val progress = updating?.let { (it.done.toFloat() / it.release.size).coerceIn(0f, 1f) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UpdateStatusHeader(
            title = title,
            line = line,
            tone = tone,
            onTone = onTone,
            icon = when (state) {
                is GalleryDlUpdateViewModel.UiState.Available, is GalleryDlUpdateViewModel.UiState.Updating -> Icons.Filled.Download
                is GalleryDlUpdateViewModel.UiState.Error -> Icons.Filled.ErrorOutline
                else -> Icons.Filled.VerifiedUser
            },
            working = state is GalleryDlUpdateViewModel.UiState.Updating || state == GalleryDlUpdateViewModel.UiState.Checking,
            progress = progress
        )

        if (updating != null && progress != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UpdateWavyProgress(progress = progress)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val mb = 1024f * 1024f
                    Text(
                        String.format(Locale.US, "%.1f / %.1f MB", updating.done / mb, updating.release.size / mb),
                        style = MaterialTheme.typography.bodySmall,
                        color = UpdateTokens.OnSurfaceVar,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = UpdateTokens.RunStrong
                    )
                }
            }
        }

        val whatsNew = stringResource(R.string.upd_btn_whats_new)
        UpdateStateButtons(
            when (state) {
                GalleryDlUpdateViewModel.UiState.Idle -> listOf(
                    Triple(whatsNew, false) { onReleaseNotes(installed) },
                    Triple(stringResource(R.string.upd_btn_check_now), true, onCheck)
                )
                is GalleryDlUpdateViewModel.UiState.Available -> listOf(
                    Triple(whatsNew, false) { onReleaseNotes(state.release.version) },
                    Triple(stringResource(R.string.upd_btn_install), true, onInstall)
                )
                is GalleryDlUpdateViewModel.UiState.Updating -> listOf(Triple(stringResource(R.string.upd_btn_cancel), false, onCancel))
                is GalleryDlUpdateViewModel.UiState.Installed -> listOf(Triple(stringResource(R.string.upd_btn_done), true, onDismiss))
                is GalleryDlUpdateViewModel.UiState.Error ->
                    listOf(Triple(stringResource(R.string.upd_btn_retry), true, if (state.release != null) onInstall else onCheck))
                GalleryDlUpdateViewModel.UiState.Checking -> emptyList()
            },
            tone,
            onTone
        )

        if (state is GalleryDlUpdateViewModel.UiState.Available) {
            Text(
                stringResource(R.string.upd_download_size, String.format(Locale.US, "%.1f", state.release.size / (1024f * 1024f))),
                style = MaterialTheme.typography.bodySmall,
                color = UpdateTokens.OnSurfaceDim,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}
