package com.hazel.android.ui.share

import com.hazel.android.ui.screens.download.rememberSaveDirPicker
import androidx.compose.ui.res.stringResource
import com.hazel.android.download.GenericFormats
import android.app.Activity
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadViewModelHolder
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.screens.download.FormatSheet
import com.hazel.android.ui.screens.download.NoResultsDialog
import com.hazel.android.ui.screens.download.batch.BatchDownloadSheet
import com.hazel.android.ui.theme.AccentColors
import com.hazel.android.ui.theme.HazelTypography
import com.hazel.android.util.AppLocale
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.StoragePaths
import com.hazel.android.util.UrlExtractor
import com.hazel.android.util.copyToClipboard
import com.hazel.android.util.siteRootOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Builds a dark-only color scheme using the user's selected accent color.
 * The overlay always renders in dark mode regardless of system setting,
 * but picks up the user's accent from Settings.
 */
private fun overlayColorScheme(accentName: String): androidx.compose.material3.ColorScheme {
    val accent = AccentColors.find { it.name == accentName } ?: AccentColors.first()
    return darkColorScheme(
        primary = accent.dark,
        onPrimary = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
        primaryContainer = accent.containerDark,
        onPrimaryContainer = accent.dark,
        secondary = accent.dark.copy(alpha = 0.8f),
        onSecondary = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
        secondaryContainer = accent.containerDark,
        onSecondaryContainer = accent.dark,
        tertiary = accent.dark,
        background = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
        onBackground = androidx.compose.ui.graphics.Color(0xFFF2F2F0),
        surface = androidx.compose.ui.graphics.Color(0xFF141414),
        onSurface = androidx.compose.ui.graphics.Color(0xFFF2F2F0),
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB8B8B4),
        outline = androidx.compose.ui.graphics.Color(0xFF2C2C2C),
        outlineVariant = androidx.compose.ui.graphics.Color(0xFF1F1F1F),
        surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF000000),
        surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF0A0A0A),
        surfaceContainer = androidx.compose.ui.graphics.Color(0xFF141414),
        surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
        surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF262626),
        // Set as the app sets it; left to Material, an error here was a pale pink.
        error = com.hazel.android.ui.theme.ErrorRed,
        onError = androidx.compose.ui.graphics.Color.White
    )
}

/**
 * The share target: a link shared from another app opens its download sheet over that app.
 *
 * The sheet opens at once, on what the link itself says, and fills in as the link is read in
 * the background: title, author, artwork and the full format list. The user never waits on
 * a loading screen. They can set everything up while the read runs, and a download asked
 * for before it finishes starts as soon as it does, with the sheet already gone. A link that
 * turns out to hold a collection moves to the set-of-links sheet instead.
 *
 * The user stays in the app they shared from throughout: the activity is transparent and
 * closes itself once the choice is made.
 */
class ShareOverlayActivity : ComponentActivity() {

    /**
     * The link the sheet is for. A state rather than a local, because the activity is a
     * single instance: a link shared while its sheet is still open, or left in the
     * background, is delivered to [onNewIntent] here rather than opening a sheet of its own.
     */
    private var sharedUrl by mutableStateOf("")

    /**
     * Whether the link came through Hazel Instant. Both share targets resolve to this class,
     * so the component name is the only place the difference shows. Instant does not ask:
     * it reads the link and downloads it with the saved settings.
     */
    private var instant by mutableStateOf(false)

    private fun Intent.isInstant() = component?.className?.endsWith(INSTANT_ALIAS) == true

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Transparent, so the app the link came from stays in view behind the sheet.
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        com.hazel.android.util.PermissionHelper.register(this)
        if (Build.VERSION.SDK_INT >= 33) {
            com.hazel.android.util.PermissionHelper.ensureNotificationPermission(this)
        }

        HazelApp.instance.startLibraryInit()

        val rawText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
        val firstUrl = UrlExtractor.extract(rawText)

        if (firstUrl.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.share_overlay_invalid_link), Toast.LENGTH_SHORT).show()
            closeOverlay()
            return
        }

        sharedUrl = firstUrl
        instant = intent.isInstant()
        // Only on a first start: an activity brought back after its process was reclaimed
        // must not download the same link a second time.
        if (instant && savedInstanceState == null) {
            DownloadViewModelHolder.get().instantDownload(this, firstUrl)
        }

        setContent {
            // Keyed on the link, so a second share delivered to this same sheet starts over
            // on the new link: its read, its placeholder and every choice on the sheet.
            val url = sharedUrl
            key(url) {
                val scope = rememberCoroutineScope()
                val downloadViewModel = remember { DownloadViewModelHolder.get() }
                val state by downloadViewModel.state.collectAsState()
                val formatsReading by downloadViewModel.formatsReading.collectAsState()

                val options by SettingsRepository.getDownloadOptions(this)
                    .collectAsState(initial = DownloadOptions())
                val saveDirs by SettingsRepository.getSaveDirs(this).collectAsState(initial = SaveDirs())
                // The kind whose folder the picker is choosing, set as it opens.
                val accentName by SettingsRepository.getAccentColor(this).collectAsState(initial = "Cyan")

                LaunchedEffect(url) {
                    // Instant was handed its link in onCreate or onNewIntent, outside the
                    // composition, so closing the dialog cannot cancel it.
                    if (instant) return@LaunchedEffect
                    downloadViewModel.fetchShare(url)
                    val app = applicationContext
                    scope.launch(Dispatchers.IO) {
                        if (!SettingsRepository.getIncognito(app).first()) {
                            SearchHistoryRepository.record(app, url)
                        }
                    }
                }

                // What the sheet shows before the read comes back: the link, and the generic
                // "best" rows, which are enough to download with.
                val bestVideo = stringResource(R.string.batch_quality_best)
                val bestAudio = stringResource(R.string.audio_quality_best)
                val worst = stringResource(R.string.batch_quality_worst)
                val placeholder = remember(url, bestVideo, bestAudio, worst) {
                    GenericFormats.placeholder(url, bestVideo, bestAudio, worst)
                }

                val pickSaveDir = rememberSaveDirPicker(saveDirs)

                val openSaveDir: (Boolean) -> Unit = { isVideo ->
                    MediaOpener.openLocation(this@ShareOverlayActivity, saveDirs.of(isVideo).uri, isVideo)
                }
                val resetSaveDir: (Boolean) -> Unit = { isVideo ->
                    scope.launch { SettingsRepository.resetSaveDir(this@ShareOverlayActivity, isVideo) }
                }

                // Signing in from the failure dialog reads the link again with the new cookies.
                val signInLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) downloadViewModel.fetchShare(url)
                }

                MaterialTheme(colorScheme = overlayColorScheme(accentName), typography = HazelTypography) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val readHere = state.url == url
                        val results = if (readHere) state.results else emptyList()
                        val failure = state.errorLog?.takeIf { readHere && !state.isFetching }
                        // A link on the failed list that fails to read again keeps its entry,
                        // with this attempt's log in place of the last one.
                        LaunchedEffect(failure) {
                            val log = failure ?: return@LaunchedEffect
                            com.hazel.android.data.FailedDownloadRepository.refreshLog(applicationContext, url, log)
                        }

                        when {
                            // A failed read is reported by a notification, as any download
                            // that fails in the background is.
                            instant -> InstantDialog(
                                onConfirm = { closeOverlay() },
                                onTune = {
                                    startActivity(
                                        Intent(this@ShareOverlayActivity, com.hazel.android.MainActivity::class.java)
                                            .putExtra(com.hazel.android.MainActivity.EXTRA_NAVIGATE_TO, "processing")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    )
                                    closeOverlay()
                                }
                            )

                            failure != null -> NoResultsDialog(
                                message = failure,
                                canFetchCookies = com.hazel.android.ui.screens.download.isCookieRelated(failure),
                                canContinue = false,
                                canAddCookies = true,
                                onCopyLog = {
                                    copyToClipboard(this@ShareOverlayActivity, failure)
                                    Toast.makeText(
                                        this@ShareOverlayActivity,
                                        getString(R.string.history_failed_log_copied),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onGetCookies = {
                                    downloadViewModel.clearErrorLog()
                                    signInLauncher.launch(
                                        CookieWebViewActivity.intent(this@ShareOverlayActivity, siteRootOf(url))
                                    )
                                },
                                onContinueAnyway = {},
                                onDismiss = {
                                    downloadViewModel.clearErrorLog()
                                    closeOverlay()
                                }
                            )

                            results.size > 1 -> BatchDownloadSheet(
                                results = results,
                                sourceUrl = url,
                                options = options,
                                onOptionsChange = { changed ->
                                    scope.launch { SettingsRepository.setDownloadOptions(this@ShareOverlayActivity, changed) }
                                },
                                saveDirs = saveDirs,
                                onOpenSaveDir = openSaveDir,
                                onPickSaveDir = pickSaveDir,
                                onResetSaveDir = resetSaveDir,
                                onResolveFormats = downloadViewModel::resolveFormats,
                                readingUrls = formatsReading,
                                onRefreshFormats = downloadViewModel::refreshFormats,
                                onRemove = downloadViewModel::removeResult,
                                onDownload = { plans ->
                                    downloadViewModel.startBatch(applicationContext, plans, options, saveDirs = saveDirs)
                                    announceStarted()
                                    closeOverlay()
                                },
                                onDismiss = { closeOverlay() }
                            )

                            else -> {
                                val resolved: MediaInfo? = results.singleOrNull()
                                val info = resolved ?: placeholder
                                LaunchedEffect(info.url, resolved != null) {
                                    if (resolved != null && !resolved.hasResolvedFormats) {
                                        downloadViewModel.resolveFormats(resolved)
                                    }
                                }
                                FormatSheet(
                                    info = info,
                                    options = options,
                                    onOptionsChange = { changed ->
                                        scope.launch { SettingsRepository.setDownloadOptions(this@ShareOverlayActivity, changed) }
                                    },
                                    saveDirs = saveDirs,
                                    // The ladder is a full answer on its own, so no skeleton
                                    // stands under it; the header says the link is being read.
                                    isLoadingFormats = resolved != null && info.url in formatsReading,
                                    isReadingLink = resolved == null && state.isFetching,
                                    onRefreshFormats = resolved?.let { item ->
                                        { source, fresh -> downloadViewModel.refreshFormats(listOf(item), source, fresh) }
                                    },
                                    onOpenSaveDir = openSaveDir,
                                    onPickSaveDir = pickSaveDir,
                                    onResetSaveDir = resetSaveDir,
                                    onDownload = { format, audioLanguage, title, author, oneOff ->
                                        if (resolved != null) {
                                            downloadViewModel.startDownload(
                                                context = applicationContext,
                                                format = format,
                                                options = options.with(oneOff),
                                                title = title,
                                                author = author,
                                                audioLanguage = audioLanguage,
                                                info = resolved,
                                                saveDirs = saveDirs
                                            )
                                        } else {
                                            // Not read yet: the choice waits for the read and
                                            // starts the moment it lands.
                                            downloadViewModel.downloadOnceRead(
                                                context = applicationContext,
                                                url = url,
                                                format = format,
                                                options = options.with(oneOff),
                                                title = title,
                                                author = author,
                                                audioLanguage = audioLanguage,
                                                treeUri = saveDirs.of(format.hasVideo).uri
                                            )
                                        }
                                        announceStarted()
                                        closeOverlay()
                                    },
                                    onDismiss = { closeOverlay() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = UrlExtractor.extract(intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.share_overlay_invalid_link), Toast.LENGTH_SHORT).show()
            return
        }
        instant = intent.isInstant()
        if (instant) DownloadViewModelHolder.get().instantDownload(this, url)
        sharedUrl = url
    }

    private fun announceStarted() {
        Toast.makeText(applicationContext, getString(R.string.share_overlay_download_started), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val INSTANT_ALIAS = "InstantShareActivity"
    }

    private fun closeOverlay() {
        finishAndRemoveTask()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
