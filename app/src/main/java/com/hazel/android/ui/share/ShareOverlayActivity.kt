package com.hazel.android.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.download.DownloadOptions
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.DownloadViewModelHolder
import com.hazel.android.ui.screens.download.FormatSheet
import com.hazel.android.ui.screens.download.NoResultsDialog
import com.hazel.android.ui.screens.download.batch.BatchDownloadSheet
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.util.AppLocale
import com.hazel.android.util.LinkKey
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.StoragePaths
import com.hazel.android.util.UrlExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Transparent floating overlay activity that catches share intents from external apps.
 *
 * When a user shares a link from YouTube, Twitter/X, Instagram, or mobile browsers, this
 * activity launches over the host application with a translucent background. The user never
 * leaves their host application:
 *
 * - For Hazel Instant: Displays a minimal confirmation bottom sheet and enqueues the download
 *   instantly in the background, closing immediately upon confirmation.
 * - For Hazel: Opens the format selection sheet ([FormatSheet]) directly over the host app.
 *   Once the format is selected or cancelled, it finishes immediately, returning control to the
 *   calling application without an app switch.
 */
class ShareOverlayActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure window is completely transparent so the caller app remains fully visible
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Ensure download engine (yt-dlp and ffmpeg) is initialized
        HazelApp.instance.startLibraryInit()

        val rawText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
        val url = UrlExtractor.extract(rawText)

        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.share_overlay_invalid_link), Toast.LENGTH_SHORT).show()
            closeOverlay()
            return
        }

        // DirectShareActivity alias resolves here when user picked "Instant"
        val isDirect = intent?.component?.className?.endsWith("DirectShareActivity") == true
        val sourceLabel = sourceLabelFor(url)

        setContent {
            val scope = rememberCoroutineScope()
            val savedTheme by SettingsRepository.isDarkTheme(this).collectAsState(initial = null)
            val isDark = savedTheme ?: true
            val accentName by SettingsRepository.getAccentColor(this).collectAsState(initial = "White")

            val downloadViewModel = remember { DownloadViewModelHolder.get() }
            val state by downloadViewModel.state.collectAsState()

            val options by SettingsRepository.getDownloadOptions(this)
                .collectAsState(initial = DownloadOptions())
            val treeUri by SettingsRepository.getDownloadTreeUri(this)
                .collectAsState(initial = "")
            val treeLabel by SettingsRepository.getDownloadTreeLabel(this)
                .collectAsState(initial = "")
            val saveDirLabel = treeLabel.ifBlank { StoragePaths.DOWNLOADS_DISPLAY }

            // SAF Document tree picker for custom download folders
            val folderPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri: Uri? ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        scope.launch {
                            SettingsRepository.setDownloadTree(
                                this@ShareOverlayActivity,
                                uri.toString(),
                                MediaStoreHelper.describeTree(uri)
                            )
                        }
                    } catch (_: SecurityException) {
                        // Persistable grant was refused; default folder remains in use
                    }
                }
            }

            HazelTheme(darkTheme = isDark, accentName = accentName) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isDirect) {
                        InstantShareSheet(
                            url = url,
                            sourceLabel = sourceLabel,
                            onOpenSettings = {
                                val mainIntent = Intent(this@ShareOverlayActivity, com.hazel.android.MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtra(com.hazel.android.MainActivity.EXTRA_NAVIGATE_TO, "direct_share")
                                }
                                startActivity(mainIntent)
                                closeOverlay()
                            },
                            onDownload = {
                                downloadViewModel.startDirect(applicationContext, url, sourceLabel)
                                Toast.makeText(
                                    applicationContext,
                                    getString(R.string.share_overlay_download_started),
                                    Toast.LENGTH_SHORT
                                ).show()
                                closeOverlay()
                            },
                            onDismiss = { closeOverlay() }
                        )
                    } else {
                        // Regular share target: Probe metadata and render real-time FormatSheet
                        LaunchedEffect(url) {
                            downloadViewModel.onUrlChange(url)
                            downloadViewModel.fetchAll(listOf(url))
                        }

                        when {
                            // Fetching in progress and formats not ready yet
                            state.isFetching && state.info == null -> {
                                OverlayLoadingSheet(
                                    url = url,
                                    sourceLabel = sourceLabel,
                                    onDismiss = { closeOverlay() }
                                )
                            }

                            // Multiple links or playlist resolved
                            state.isMultiple && state.results.isNotEmpty() -> {
                                BatchDownloadSheet(
                                    results = state.results,
                                    options = options,
                                    onOptionsChange = { changed ->
                                        scope.launch { SettingsRepository.setDownloadOptions(this@ShareOverlayActivity, changed) }
                                    },
                                    saveDirLabel = saveDirLabel,
                                    isCustomSaveDir = treeUri.isNotBlank(),
                                    onOpenSaveDir = { MediaOpener.openLocation(this@ShareOverlayActivity, treeUri) },
                                    onPickSaveDir = {
                                        folderPicker.launch(treeUri.takeIf { it.isNotBlank() }?.let(Uri::parse))
                                    },
                                    onResetSaveDir = {
                                        scope.launch { SettingsRepository.clearDownloadTree(this@ShareOverlayActivity) }
                                    },
                                    onResolveFormats = { item -> downloadViewModel.resolveFormats(item) },
                                    onRemove = { item -> downloadViewModel.removeResult(item) },
                                    onDownload = { plans ->
                                        downloadViewModel.startBatch(
                                            context = applicationContext,
                                            plans = plans,
                                            options = options,
                                            treeUri = treeUri
                                        )
                                        Toast.makeText(
                                            applicationContext,
                                            getString(R.string.share_overlay_download_started),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        closeOverlay()
                                    },
                                    onDismiss = { closeOverlay() }
                                )
                            }

                            // Single media item resolved
                            state.info != null -> {
                                val currentInfo = state.info!!
                                FormatSheet(
                                    info = currentInfo,
                                    options = options,
                                    onOptionsChange = { changed ->
                                        scope.launch { SettingsRepository.setDownloadOptions(this@ShareOverlayActivity, changed) }
                                    },
                                    saveDirLabel = saveDirLabel,
                                    isCustomSaveDir = treeUri.isNotBlank(),
                                    isLoadingFormats = state.isFetching || !currentInfo.hasResolvedFormats,
                                    onOpenSaveDir = { MediaOpener.openLocation(this@ShareOverlayActivity, treeUri) },
                                    onPickSaveDir = {
                                        folderPicker.launch(treeUri.takeIf { it.isNotBlank() }?.let(Uri::parse))
                                    },
                                    onResetSaveDir = {
                                        scope.launch { SettingsRepository.clearDownloadTree(this@ShareOverlayActivity) }
                                    },
                                    onDownload = { format, audioLanguage, title, author ->
                                        downloadViewModel.startDownload(
                                            context = applicationContext,
                                            format = format,
                                            options = options,
                                            title = title,
                                            author = author,
                                            audioLanguage = audioLanguage,
                                            treeUri = treeUri
                                        )
                                        Toast.makeText(
                                            applicationContext,
                                            getString(R.string.share_overlay_download_started),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        closeOverlay()
                                    },
                                    onDismiss = { closeOverlay() }
                                )
                            }

                            // An error occurred during probe
                            state.errorLog != null -> {
                                val log = state.errorLog!!
                                NoResultsDialog(
                                    message = log,
                                    canFetchCookies = false,
                                    canContinue = false,
                                    onCopyLog = { downloadViewModel.clearErrorLog() },
                                    onGetCookies = { downloadViewModel.clearErrorLog() },
                                    onContinueAnyway = {},
                                    onDismiss = {
                                        downloadViewModel.clearErrorLog()
                                        closeOverlay()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }



    private fun closeOverlay() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun sourceLabelFor(link: String): String {
        val referrerPackage = referrer
            ?.takeIf { it.scheme == "android-app" }
            ?.host
            ?.takeIf { it != packageName }

        if (referrerPackage != null) {
            runCatching {
                val info = packageManager.getApplicationInfo(referrerPackage, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return runCatching { URI(link).host.orEmpty() }
            .getOrDefault("")
            .removePrefix("www.")
            .ifBlank { "the link" }
    }
}
