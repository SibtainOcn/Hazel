package com.hazel.android.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.update.DebugInfo
import com.hazel.android.update.HazelUpdater
import com.hazel.android.update.UpdateTokens
import com.hazel.android.update.UpdateTopBar
import com.hazel.android.update.YtDlpUpdater
import com.hazel.android.util.openInAppBrowser

/**
 * The update hub, laid out like a download's properties: the versions at a glance, actions,
 * the two components, and the device facts a bug report needs, each one copyable.
 */
@Composable
fun SoftwareUpdateScreen(
    onBack: () -> Unit,
    onNavigateToHazelUpdate: () -> Unit,
    onNavigateToYtDlpUpdate: () -> Unit,
    onOpenWhatsNew: (version: String) -> Unit = {}
) {
    val context = LocalContext.current
    val isFdroid = HazelUpdater.isFdroid()
    val hazelVersion = HazelUpdater.installedVersion()
    val ytdlpVersion = remember(context) { YtDlpUpdater.cachedVersion(context) } ?: stringResource(R.string.upd_ytdlp_bundled)

    val hazelFresh by remember(context) { SettingsRepository.getHazelUpdateAvailable(context) }.collectAsState(initial = false)
    val ytDlpFresh by remember(context) { SettingsRepository.getYtDlpUpdateAvailable(context) }.collectAsState(initial = false)

    Scaffold(
        topBar = { UpdateTopBar(title = stringResource(R.string.upd_software_title), onBack = onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = UpdateTokens.Bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            JoinedTiles(
                listOf(
                    hazelVersion to stringResource(R.string.upd_tile_hazel),
                    ytdlpVersion to "yt-dlp",
                    DebugInfo.primaryAbi().substringBefore('-') to stringResource(R.string.upd_tile_cpu),
                    "API ${android.os.Build.VERSION.SDK_INT}" to stringResource(R.string.upd_tile_android)
                )
            )

            UpdateActionRow(
                listOf(
                    UpdateAction(Icons.AutoMirrored.Filled.List, R.string.upd_action_changelog) { onOpenWhatsNew(hazelVersion) },
                    UpdateAction(Icons.Filled.ContentCopy, R.string.upd_action_copy_info) {
                        copyAndTell(context, DebugInfo.report(context, ytdlpVersion))
                    },
                    UpdateAction(Icons.Filled.BugReport, R.string.upd_action_report) {
                        // The facts go on the clipboard, never into the link, and the user pastes them.
                        copyAndTell(context, DebugInfo.report(context, ytdlpVersion), R.string.upd_report_copied)
                        openInAppBrowser(context, DebugInfo.NEW_ISSUE_URL)
                    },
                    UpdateAction(Icons.Filled.NewReleases, R.string.upd_action_releases) {
                        openInAppBrowser(context, if (isFdroid) HazelUpdater.FDROID_PACKAGE_URL else DebugInfo.RELEASES_URL)
                    }
                )
            )

            ComponentGroup(
                R.string.upd_section_components,
                listOf(
                    ComponentLink(
                        Icons.Filled.Bolt, "Hazel",
                        stringResource(
                            when {
                                isFdroid -> R.string.upd_hazel_fdroid
                                hazelFresh -> R.string.upd_status_ready
                                else -> R.string.upd_status_current
                            },
                            hazelVersion
                        ),
                        fresh = hazelFresh,
                        onClick = onNavigateToHazelUpdate
                    ),
                    ComponentLink(
                        Icons.Filled.Code, "yt-dlp",
                        stringResource(if (ytDlpFresh) R.string.upd_status_ready else R.string.upd_status_current, ytdlpVersion),
                        fresh = ytDlpFresh,
                        onClick = onNavigateToYtDlpUpdate
                    )
                )
            )

            CopyGroup(
                R.string.upd_section_device,
                listOf(
                    stringResource(R.string.upd_label_model) to DebugInfo.model(),
                    stringResource(R.string.upd_label_android) to DebugInfo.android(),
                    stringResource(R.string.upd_label_arch) to DebugInfo.abis(),
                    stringResource(R.string.upd_label_build) to DebugInfo.build()
                )
            )
        }
    }
}
