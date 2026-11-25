package com.hazel.android.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryDlPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State and actions of the gallery-dl update screen. */
class GalleryDlUpdateViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Checking : UiState
        data object Idle : UiState
        data class Available(val release: GalleryDlUpdater.Release) : UiState
        data class Updating(val release: GalleryDlUpdater.Release, val done: Long = 0L) : UiState
        data class Installed(val version: String) : UiState
        data class Error(val problem: GalleryDlUpdater.Problem, val release: GalleryDlUpdater.Release? = null) : UiState
    }

    private val app get() = getApplication<Application>()

    private val _state = MutableStateFlow<UiState>(UiState.Checking)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _installed = MutableStateFlow(GalleryDlPayload.BUNDLED_VERSION)
    val installed: StateFlow<String> = _installed.asStateFlow()

    private val _usesBundled = MutableStateFlow(true)
    val usesBundled: StateFlow<Boolean> = _usesBundled.asStateFlow()

    val autoDownload = SettingsRepository.getGalleryDlAutoUpdate(application).stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val wifiOnly = SettingsRepository.getGalleryDlWifiOnly(application).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyAvailable = SettingsRepository.getUpdateNotifyAvailable(application).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyComplete = SettingsRepository.getUpdateNotifyComplete(application).stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val notifyFailed = SettingsRepository.getUpdateNotifyFailed(application).stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var job: Job? = null

    init {
        check()
    }

    fun check() {
        if (_state.value is UiState.Updating) return
        _state.value = UiState.Checking
        job = viewModelScope.launch {
            refresh()
            _state.value = try {
                val release = GalleryDlUpdater.latest()
                val newer = GalleryDlUpdater.isNewer(release.version, _installed.value)
                SettingsRepository.setGalleryDlUpdateAvailable(app, newer)
                if (newer) UiState.Available(release) else UiState.Idle
            } catch (e: GalleryDlUpdater.UpdateException) {
                UiState.Error(e.problem)
            }
        }
    }

    fun install() {
        val release = when (val s = _state.value) {
            is UiState.Available -> s.release
            is UiState.Error -> s.release ?: return
            else -> return
        }
        job?.cancel()
        _state.value = UiState.Updating(release)
        job = viewModelScope.launch {
            _state.value = try {
                GalleryDlUpdater.install(app, release) { done, _ -> _state.value = UiState.Updating(release, done) }
                refresh()
                UiState.Installed(_installed.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GalleryDlUpdater.UpdateException) {
                UiState.Error(e.problem, release)
            } catch (e: Exception) {
                UiState.Error(GalleryDlUpdater.Problem.CONNECTION_LOST, release)
            }
        }
    }

    fun cancel() {
        val release = (_state.value as? UiState.Updating)?.release ?: return
        job?.cancel()
        _state.value = UiState.Available(release)
    }

    fun dismiss() {
        if (_state.value !is UiState.Updating) _state.value = UiState.Idle
    }

    /** Back to the bundled gallery-dl; false while a run holds the engine. */
    suspend fun useBundled(): Boolean {
        val done = withContext(Dispatchers.IO) { GalleryDlUpdater.useBundled(app) }
        if (done) check()
        return done
    }

    fun setAutoDownload(on: Boolean) = viewModelScope.launch { SettingsRepository.setGalleryDlAutoUpdate(app, on) }
    fun setWifiOnly(on: Boolean) = viewModelScope.launch { SettingsRepository.setGalleryDlWifiOnly(app, on) }
    fun setNotifyAvailable(on: Boolean) = viewModelScope.launch { SettingsRepository.setUpdateNotifyAvailable(app, on) }
    fun setNotifyComplete(on: Boolean) = viewModelScope.launch { SettingsRepository.setUpdateNotifyComplete(app, on) }
    fun setNotifyFailed(on: Boolean) = viewModelScope.launch { SettingsRepository.setUpdateNotifyFailed(app, on) }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        _installed.value = GalleryDlUpdater.installedVersion(app)
        _usesBundled.value = GalleryDlUpdater.usesBundled(app)
    }
}
