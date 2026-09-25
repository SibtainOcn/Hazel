package com.hazel.android.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single source-of-truth for the yt-dlp updater screen.
 *
 * The download engine (yt-dlp) is updated independently of the app: the binary is
 * fetched from the yt-dlp GitHub releases and swapped in place, so extractor fixes
 * apply without an app release.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    // ── Public state flows ──

    sealed class UiState {
        /** Nothing pending — the installed binary matches the channel's latest release */
        data object Idle : UiState()
        /** Reading the latest release from GitHub */
        data object Checking : UiState()
        /** A newer yt-dlp build exists, user hasn't acted yet */
        data class Available(val info: YtDlpUpdater.ReleaseInfo) : UiState()
        /** Binary download + install in progress */
        data class Updating(
            val info: YtDlpUpdater.ReleaseInfo,
            val progressBytes: Long = 0L,
            val totalBytes: Long = 0L,
            val speedBps: Long = 0L,
            val etaSeconds: Long = 0L
        ) : UiState()
        /** The new binary is installed and live */
        data class Installed(val version: String) : UiState()
        /** Error while checking or installing */
        data class Error(val message: String, val info: YtDlpUpdater.ReleaseInfo? = null) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Checking)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Version of the yt-dlp binary currently on the device (null while unknown) */
    private val _installedVersion = MutableStateFlow<String?>(null)
    val installedVersion: StateFlow<String?> = _installedVersion.asStateFlow()

    /** Release channel the user picked (Stable / Nightly / Master) */
    private val _channel = MutableStateFlow(YtDlpUpdater.Channel.STABLE)
    val channel: StateFlow<YtDlpUpdater.Channel> = _channel.asStateFlow()

    private val _autoDownload = MutableStateFlow(true)
    val autoDownload: StateFlow<Boolean> = _autoDownload.asStateFlow()

    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _notifyAvailable = MutableStateFlow(true)
    val notifyAvailable: StateFlow<Boolean> = _notifyAvailable.asStateFlow()

    private val _notifyComplete = MutableStateFlow(true)
    val notifyComplete: StateFlow<Boolean> = _notifyComplete.asStateFlow()

    private val _notifyFailed = MutableStateFlow(true)
    val notifyFailed: StateFlow<Boolean> = _notifyFailed.asStateFlow()

    private val _verifySignature = MutableStateFlow(true)
    val verifySignature: StateFlow<Boolean> = _verifySignature.asStateFlow()

    private var updateJob: Job? = null

    init {
        viewModelScope.launch {
            _channel.value = YtDlpUpdater.Channel.fromLabel(
                SettingsRepository.getYtDlpChannel(getApplication()).first()
            )
            _autoDownload.value = SettingsRepository.getUpdateAutoDownload(getApplication()).first()
            _wifiOnly.value = SettingsRepository.getUpdateWifiOnly(getApplication()).first()
            _notifyAvailable.value = SettingsRepository.getUpdateNotifyAvailable(getApplication()).first()
            _notifyComplete.value = SettingsRepository.getUpdateNotifyComplete(getApplication()).first()
            _notifyFailed.value = SettingsRepository.getUpdateNotifyFailed(getApplication()).first()
            _verifySignature.value = SettingsRepository.getUpdateVerifySignature(getApplication()).first()

            checkForUpdate()
        }
    }

    // ── Actions ──

    /** Switch release channel and immediately re-check against it. */
    fun setChannel(channel: YtDlpUpdater.Channel) {
        if (_channel.value == channel) return
        if (_uiState.value is UiState.Updating) return
        _channel.value = channel
        viewModelScope.launch {
            SettingsRepository.setYtDlpChannel(getApplication(), channel.label)
        }
        checkForUpdate()
    }

    fun setAutoDownload(enabled: Boolean) {
        _autoDownload.value = enabled
        viewModelScope.launch { SettingsRepository.setUpdateAutoDownload(getApplication(), enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
        viewModelScope.launch { SettingsRepository.setUpdateWifiOnly(getApplication(), enabled) }
    }

    fun setNotifyAvailable(enabled: Boolean) {
        _notifyAvailable.value = enabled
        viewModelScope.launch { SettingsRepository.setUpdateNotifyAvailable(getApplication(), enabled) }
    }

    fun setNotifyComplete(enabled: Boolean) {
        _notifyComplete.value = enabled
        viewModelScope.launch { SettingsRepository.setUpdateNotifyComplete(getApplication(), enabled) }
    }

    fun setNotifyFailed(enabled: Boolean) {
        _notifyFailed.value = enabled
        viewModelScope.launch { SettingsRepository.setUpdateNotifyFailed(getApplication(), enabled) }
    }

    fun setVerifySignature(enabled: Boolean) {
        _verifySignature.value = enabled
        viewModelScope.launch { SettingsRepository.setUpdateVerifySignature(getApplication(), enabled) }
    }

    /** Ask GitHub for the channel's latest release and compare it to what's installed. */
    fun checkForUpdate() {
        if (_uiState.value is UiState.Updating) return
        _uiState.value = UiState.Checking
        viewModelScope.launch {
            refreshInstalledVersion()
            val info = YtDlpUpdater.latestRelease(_channel.value)
            val isAvailable = info != null && YtDlpUpdater.isNewer(info.version, _installedVersion.value)
            SettingsRepository.setYtDlpUpdateAvailable(getApplication(), isAvailable)
            _uiState.value = when {
                info == null ->
                    UiState.Error("Couldn't reach GitHub. Check your connection.")
                isAvailable ->
                    UiState.Available(info)
                else -> UiState.Idle
            }
        }
    }

    /** Download the newer binary and swap it in. */
    fun startUpdate() {
        val info = when (val s = _uiState.value) {
            is UiState.Available -> s.info
            is UiState.Error -> s.info ?: return
            else -> return
        }

        updateJob?.cancel()
        _uiState.value = UiState.Updating(info, 0L, info.binarySize, 0L, 0L)

        updateJob = viewModelScope.launch {
            try {
                YtDlpUpdater.install(getApplication(), info.channel)
                refreshInstalledVersion()
                SettingsRepository.setYtDlpUpdateAvailable(getApplication(), false)
                _uiState.value = UiState.Installed(_installedVersion.value ?: info.version)
            } catch (_: Exception) {
                _uiState.value = UiState.Error("Update failed. Please try again.", info)
            }
        }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
        val info = when (val s = _uiState.value) {
            is UiState.Updating -> s.info
            else -> null
        }
        _uiState.value = if (info != null) UiState.Available(info) else UiState.Idle
    }

    /** Dismiss a finished/failed run and return to the neutral state. */
    fun dismissCompletely() {
        if (_uiState.value is UiState.Updating) return
        _uiState.value = UiState.Idle
    }

    private suspend fun refreshInstalledVersion() {
        _installedVersion.value = YtDlpUpdater.installedVersion(getApplication())
    }
}
