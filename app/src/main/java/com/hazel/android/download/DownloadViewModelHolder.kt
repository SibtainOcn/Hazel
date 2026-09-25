package com.hazel.android.download

import androidx.annotation.MainThread

/**
 * Manages the shared [DownloadViewModel] across Hazel activities.
 *
 * Both Hazel's main activity and the lightweight, transparent share overlay activity interact
 * with the exact same download pipeline, active queue, and progress flows. This architecture
 * prevents duplicated jobs, race conditions during disk queue restoration, and ensures that
 * any download queued via the share overlay smoothly reflects inside the main interface if the
 * user opens the app later.
 */
object DownloadViewModelHolder {

    @Volatile
    private var instance: DownloadViewModel? = null

    @MainThread
    fun get(): DownloadViewModel {
        return instance ?: synchronized(this) {
            instance ?: DownloadViewModel().also { instance = it }
        }
    }
}
