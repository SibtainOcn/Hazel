package com.hazel.android.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
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

    private val _installEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val installEvent: SharedFlow<Unit> = _installEvent.asSharedFlow()

    private val _channel = MutableStateFlow(HazelUpdater.Channel.STABLE)
    val channel: StateFlow<HazelUpdater.Channel> = _channel.asStateFlow()

    private var downloadJob: Job? = null

    val notifyNew: StateFlow<Boolean> = SettingsRepository.getHazelUpdateNotify(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val autoDownload: StateFlow<Boolean> = SettingsRepository.getHazelUpdateAutoDownload(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setNotifyNew(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setHazelUpdateNotify(getApplication(), enabled) }
    }

    val wifiOnly: StateFlow<Boolean> = SettingsRepository.getHazelUpdateWifiOnly(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setHazelUpdateWifiOnly(getApplication(), enabled) }
    }

    fun setAutoDownload(enabled: Boolean) {
        viewModelScope.launch { SettingsRepository.setHazelUpdateAutoDownload(getApplication(), enabled) }
    }

    init {
        viewModelScope.launch {
            _channel.value = HazelUpdater.Channel.fromLabel(
                SettingsRepository.getHazelChannel(getApplication()).first()
            )

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

    fun checkForUpdate() {
        if (_uiState.value is UiState.Downloading) return
        _uiState.value = UiState.Checking

        viewModelScope.launch {
            when (val result = HazelUpdater.latestReleaseResult(_channel.value)) {
                is HazelUpdater.CheckResult.Success -> {
                    val info = result.info
                    val isAvailable = HazelUpdater.isNewer(info.version)
                    SettingsRepository.setHazelUpdateAvailable(getApplication(), isAvailable, info.version)
                    val cachedApk = if (isAvailable) HazelUpdater.getCachedApk(getApplication(), info) else null
                    when {
                        !isAvailable -> _uiState.value = UiState.Idle
                        cachedApk != null -> _uiState.value = UiState.ReadyToInstall(info, cachedApk)
                        HazelUpdater.background.value?.version == info.version -> follow(info)
                        else -> _uiState.value = UiState.Available(info)
                    }
                }
                is HazelUpdater.CheckResult.NoReleaseFound -> {
                    SettingsRepository.setHazelUpdateAvailable(getApplication(), false)
                    _uiState.value = UiState.Idle
                }
                is HazelUpdater.CheckResult.NetworkError -> {
                    _uiState.value = UiState.Error(
                        if (HazelUpdater.isFdroid()) "Couldn't reach F-Droid repository. Check your connection."
                        else result.message
                    )
                }
            }
        }
    }

    fun startDownload() {
        val info = when (val s = _uiState.value) {
            is UiState.Available -> s.info
            is UiState.Error -> s.info ?: return
            else -> return
        }

        if (HazelUpdater.background.value?.version == info.version) {
            follow(info)
            return
        }

        downloadJob?.cancel()
        _uiState.value = UiState.Downloading(info, 0L, info.binarySize, 0L, 0L)

        downloadJob = viewModelScope.launch {
            try {
                val apkFile = HazelUpdater.downloadApk(getApplication(), info) { bytes, total, speed, eta ->
                    _uiState.value = UiState.Downloading(info, bytes, total, speed, eta)
                }
                _uiState.value = UiState.ReadyToInstall(info, apkFile)
                _installEvent.tryEmit(Unit)
            } catch (e: CancellationException) {
                val current = _uiState.value
                if (current is UiState.Downloading) {
                    _uiState.value = UiState.Available(info)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = UiState.Error(e.message ?: "Download failed. Please try again.", info)
            }
        }
    }

    /** Shows a download already running in the background, then its result, instead of starting another. */
    private fun follow(info: HazelUpdater.ReleaseInfo) {
        downloadJob?.cancel()
        _uiState.value = UiState.Downloading(info, 0L, info.binarySize, 0L, 0L)
        downloadJob = viewModelScope.launch {
            HazelUpdater.background.takeWhile { it != null }.filterNotNull().collect { bg ->
                _uiState.value = UiState.Downloading(info, bg.bytes, bg.total, bg.speedBps, bg.etaSeconds)
            }
            val apk = HazelUpdater.getCachedApk(getApplication(), info)
            _uiState.value = if (apk != null) UiState.ReadyToInstall(info, apk) else UiState.Available(info)
        }
    }

    fun cancelDownload() {
        val info = when (val s = _uiState.value) {
            is UiState.Downloading -> s.info
            else -> null
        }
        HazelUpdater.cancelActiveDownload()
        downloadJob?.cancel()
        downloadJob = null
        _uiState.value = if (info != null) UiState.Available(info) else UiState.Idle
    }

    fun installUpdate(customContext: android.content.Context? = null) {
        val ready = _uiState.value as? UiState.ReadyToInstall ?: return
        val targetContext = customContext ?: getApplication()
        if (HazelUpdater.canInstallApks(targetContext)) {
            viewModelScope.launch {
                SettingsRepository.setHazelUpdateAvailable(getApplication(), false)
            }
        }
        HazelUpdater.installApk(targetContext, ready.apkFile)
    }

    fun dismiss() {
        if (_uiState.value is UiState.Downloading) return
        _uiState.value = UiState.Idle
    }
}
