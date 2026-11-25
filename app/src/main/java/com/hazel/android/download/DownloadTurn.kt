package com.hazel.android.download

import kotlinx.coroutines.sync.Mutex

/**
 * One download at a time across both engines: a yt-dlp run and a gallery job each hold the
 * turn while they run, so they never share the foreground service or its notification.
 * The lock is fair, so a job waiting for the turn goes before the next run that asks.
 */
object DownloadTurn {

    private val mutex = Mutex()

    suspend fun acquire(owner: Any) = mutex.lock(owner)

    /** Safe to call more than once, and by an owner that never got the turn. */
    fun release(owner: Any) {
        if (mutex.holdsLock(owner)) mutex.unlock(owner)
    }
}
