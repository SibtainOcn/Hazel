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
import java.io.File

class HazelUpdateViewModel(application: Application) : AndroidViewModel(application) {

    sealed class UiState {
        data object Idle : UiState()
        data object Checking : UiState()
        data class Available(val info: HazelUpdater.ReleaseInfo) : UiState()
        data class Downloading(
            val info: HazelUpdater.ReleaseInfo,
            val progressBytes: Long = 0L,
            val totalBytes: Long = 0L,
            val speedBps: Long = 0L,
            val etaSeconds: Long = 0L
        ) : UiState()
        data class ReadyToInstall(val info: HazelUpdater.ReleaseInfo, val apkFile: File) : UiState()
        data class Error(val message: String, val info: HazelUpdater.ReleaseInfo? = null) : UiState()
        data object FdroidManaged : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Checking)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _channel = MutableStateFlow(HazelUpdater.Channel.STABLE)
    val channel: StateFlow<HazelUpdater.Channel> = _channel.asStateFlow()

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

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            if (HazelUpdater.isFdroid()) {
                _uiState.value = UiState.FdroidManaged
                return@launch
            }

            _channel.value = HazelUpdater.Channel.fromLabel(
                SettingsRepository.getHazelChannel(getApplication()).first()
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

    fun setChannel(channel: HazelUpdater.Channel) {
        if (_channel.value == channel) return
        if (_uiState.value is UiState.Downloading) return
        _channel.value = channel
        viewModelScope.launch {
            SettingsRepository.setHazelChannel(getApplication(), channel.label)
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

    fun checkForUpdate() {
        if (HazelUpdater.isFdroid()) {
            _uiState.value = UiState.FdroidManaged
            return
        }
        if (_uiState.value is UiState.Downloading) return
        _uiState.value = UiState.Checking

        viewModelScope.launch {
            val info = HazelUpdater.latestRelease(_channel.value)
            _uiState.value = when {
                info == null -> UiState.Error("Couldn't reach GitHub. Check your connection.")
                HazelUpdater.isNewer(info.version) -> UiState.Available(info)
                else -> UiState.Idle
            }
        }
    }

    fun startDownload() {
        val info = when (val s = _uiState.value) {
            is UiState.Available -> s.info
            is UiState.Error -> s.info ?: return
            else -> return
        }

        downloadJob?.cancel()
        _uiState.value = UiState.Downloading(info, 0L, info.binarySize, 0L, 0L)

        downloadJob = viewModelScope.launch {
            try {
                val apkFile = HazelUpdater.downloadApk(getApplication(), info) { bytes, total, speed, eta ->
                    _uiState.value = UiState.Downloading(info, bytes, total, speed, eta)
                }
                _uiState.value = UiState.ReadyToInstall(info, apkFile)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Download failed. Please try again.", info)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val info = when (val s = _uiState.value) {
            is UiState.Downloading -> s.info
            else -> null
        }
        _uiState.value = if (info != null) UiState.Available(info) else UiState.Idle
    }

    fun installUpdate() {
        val ready = _uiState.value as? UiState.ReadyToInstall ?: return
        HazelUpdater.installApk(getApplication(), ready.apkFile)
    }

    fun dismiss() {
        if (_uiState.value is UiState.Downloading) return
        _uiState.value = UiState.Idle
    }
}
