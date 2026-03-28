package com.hazel.android.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.data.AppUpdater
import com.hazel.android.data.UpdateChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Single source-of-truth for all update-related state.
 * Eliminates the duplicated mutableStateOf in MainActivity + SettingsScreen
 * that caused dialog flickering via rapid recomposition.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    // ── Public state flows ──

    sealed class UiState {
        /** No update activity */
        data object Idle : UiState()
        /** Checking GitHub for updates */
        data object Checking : UiState()
        /** Update available, user hasn't acted yet */
        data class Available(val info: UpdateChecker.UpdateInfo) : UiState()
        /** APK download in progress */
        data class Downloading(
            val info: UpdateChecker.UpdateInfo,
            val downloaded: Long = 0L,
            val total: Long = 0L
        ) : UiState()
        /** Download complete — ready to install */
        data class ReadyToInstall(val info: UpdateChecker.UpdateInfo) : UiState()
        /** Error during check or download */
        data class Error(val message: String, val info: UpdateChecker.UpdateInfo? = null) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** True when download is running but dialog was dismissed */
    private val _isBackgroundDownload = MutableStateFlow(false)
    val isBackgroundDownload: StateFlow<Boolean> = _isBackgroundDownload.asStateFlow()

    /** 0f..1f progress for the top-bar animated icon */
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    /** Whether the initial auto-check popup dialog should be shown */
    private val _showInitialDialog = MutableStateFlow(false)
    val showInitialDialog: StateFlow<Boolean> = _showInitialDialog.asStateFlow()

    // ── Internal ──

    private var downloadJob: Job? = null
    private var downloadedApk: File? = null
    private var hasAutoChecked = false

    /** The current app version pulled from package info */
    val currentVersion: String by lazy {
        try {
            val ctx = getApplication<Application>()
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
    }

    // ── Actions ──

    /**
     * Auto-check on first launch. Shows popup dialog if update found.
     */
    fun autoCheckOnLaunch() {
        if (hasAutoChecked) return
        hasAutoChecked = true
        viewModelScope.launch {
            try {
                val info = UpdateChecker.check(currentVersion) ?: return@launch
                val cached = AppUpdater.getCachedUpdate(getApplication())
                if (cached != null) {
                    downloadedApk = cached
                    _uiState.value = UiState.ReadyToInstall(info)
                } else {
                    _uiState.value = UiState.Available(info)
                }
                _showInitialDialog.value = true
            } catch (_: Exception) {
                // Network error on auto-check — silently ignore, user can manually retry
            }
        }
    }

    /**
     * Manual check (from Settings or UpdateScreen "Refresh" button).
     */
    fun checkForUpdate() {
        if (_uiState.value is UiState.Checking || _uiState.value is UiState.Downloading) return
        _uiState.value = UiState.Checking
        viewModelScope.launch {
            try {
                val info = UpdateChecker.check(currentVersion)
                if (info != null) {
                    val cached = AppUpdater.getCachedUpdate(getApplication())
                    if (cached != null) {
                        downloadedApk = cached
                        _uiState.value = UiState.ReadyToInstall(info)
                    } else {
                        _uiState.value = UiState.Available(info)
                    }
                } else {
                    _uiState.value = UiState.Idle
                }
            } catch (_: Exception) {
                _uiState.value = UiState.Error("Network error. Check your connection.", null)
            }
        }
    }

    /**
     * Start downloading the APK.
     */
    fun startDownload() {
        val info = when (val s = _uiState.value) {
            is UiState.Available -> s.info
            is UiState.Error -> s.info ?: return
            else -> return
        }

        val url = info.apkDownloadUrl
        if (url.isNullOrBlank()) {
            // No APK asset — fallback to browser
            val ctx = getApplication<Application>()
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(info.htmlUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            _uiState.value = UiState.Idle
            return
        }

        // Check if cached APK already exists (skip re-download)
        val cached = AppUpdater.getCachedUpdate(getApplication())
        if (cached != null) {
            downloadedApk = cached
            _uiState.value = UiState.ReadyToInstall(info)
            return
        }

        _uiState.value = UiState.Downloading(info, 0L, info.apkSize)
        _downloadProgress.value = 0f

        downloadJob = viewModelScope.launch {
            var lastEmitBytes = 0L
            val file = AppUpdater.downloadUpdate(
                context = getApplication(),
                url = url,
                onProgress = { dl, tot ->
                    // Throttle UI updates: emit every 32KB to reduce recomposition overhead
                    if (dl - lastEmitBytes >= 32_768 || dl == tot) {
                        lastEmitBytes = dl
                        _uiState.value = UiState.Downloading(info, dl, tot)
                        _downloadProgress.value = if (tot > 0) (dl.toFloat() / tot).coerceIn(0f, 1f) else 0f
                    }
                }
            )
            if (file != null) {
                downloadedApk = file
                _uiState.value = UiState.ReadyToInstall(info)
                _isBackgroundDownload.value = false
                _downloadProgress.value = 1f
            } else {
                _uiState.value = UiState.Error("Download failed. Please try again.", info)
                _isBackgroundDownload.value = false
                _downloadProgress.value = 0f
            }
        }
    }

    /**
     * Cancel an active download. Cleans up partial APK.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        AppUpdater.cleanupUpdates(getApplication())
        downloadedApk = null
        _isBackgroundDownload.value = false
        _downloadProgress.value = 0f

        // Return to Available state if we still have the info
        val currentInfo = when (val s = _uiState.value) {
            is UiState.Downloading -> s.info
            else -> null
        }
        _uiState.value = if (currentInfo != null) UiState.Available(currentInfo) else UiState.Idle
    }

    /**
     * Install the downloaded APK. Does NOT clean up — user might cancel system installer.
     * Cleanup happens on next launch via [cleanupIfUpdated].
     */
    fun installUpdate() {
        val apk = downloadedApk
        if (apk != null && apk.exists()) {
            AppUpdater.installUpdate(getApplication(), apk)
        }
        // Don't reset state — app might be killed during install
    }

    /**
     * Dismiss the initial popup dialog. Download continues if running.
     */
    fun dismissDialog() {
        _showInitialDialog.value = false
        if (_uiState.value is UiState.Downloading) {
            _isBackgroundDownload.value = true
        }
    }

    /**
     * Move download to background (dismiss UI, keep downloading).
     */
    fun backgroundDownload() {
        _showInitialDialog.value = false
        if (_uiState.value is UiState.Downloading) {
            _isBackgroundDownload.value = true
        }
    }

    /**
     * Dismiss everything and return to Idle.
     */
    fun dismissCompletely() {
        _showInitialDialog.value = false
        if (_uiState.value !is UiState.Downloading) {
            _uiState.value = UiState.Idle
            _downloadProgress.value = 0f
        }
    }

    /**
     * Whether the animated update indicator should be visible in the top bar.
     * Visible when: downloading (background), available, or ready to install.
     */
    fun shouldShowIndicator(state: UiState): Boolean {
        return when (state) {
            is UiState.Downloading -> true
            is UiState.Available -> true
            is UiState.ReadyToInstall -> true
            is UiState.Error -> true
            else -> false
        }
    }

    /**
     * Call on app launch: if the version has changed since last cached update,
     * clean up old APKs (update was installed successfully).
     */
    fun cleanupIfUpdated() {
        val ctx = getApplication<Application>()
        val cached = AppUpdater.getCachedUpdate(ctx)
        if (cached != null) {
            // If we have a cached APK but current version matches the latest,
            // the update was installed — clean up
            viewModelScope.launch {
                val latestInfo = UpdateChecker.check(currentVersion)
                if (latestInfo == null) {
                    // No newer version exists — we're up to date, clean cache
                    AppUpdater.cleanupUpdates(ctx)
                }
            }
        }
    }
}
