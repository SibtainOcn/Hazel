package com.hazel.android.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.data.SettingsRepository
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
        data class Updating(val info: YtDlpUpdater.ReleaseInfo) : UiState()
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

    init {
        viewModelScope.launch {
            _channel.value = YtDlpUpdater.Channel.fromLabel(
                SettingsRepository.getYtDlpChannel(getApplication()).first()
            )
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

    /** Ask GitHub for the channel's latest release and compare it to what's installed. */
    fun checkForUpdate() {
        if (_uiState.value is UiState.Updating) return
        _uiState.value = UiState.Checking
        viewModelScope.launch {
            refreshInstalledVersion()
            val info = YtDlpUpdater.latestRelease(_channel.value)
            _uiState.value = when {
                info == null ->
                    UiState.Error("Couldn't reach GitHub. Check your connection.")
                YtDlpUpdater.isNewer(info.version, _installedVersion.value) ->
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

        _uiState.value = UiState.Updating(info)

        viewModelScope.launch {
            try {
                YtDlpUpdater.install(getApplication(), info.channel)
                refreshInstalledVersion()
                _uiState.value = UiState.Installed(_installedVersion.value ?: info.version)
            } catch (_: Exception) {
                _uiState.value = UiState.Error("Update failed. Please try again.", info)
            }
        }
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
