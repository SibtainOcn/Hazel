package com.hazel.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import android.util.Log
import com.hazel.android.update.YtDlpUpdater
import com.hazel.android.utils.CrashLogger
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class HazelApp : Application(), SingletonImageLoader.Factory {

    /**
     * Thumbnails come from arbitrary media hosts, so the shared loader is given an explicit
     * network fetcher rather than relying on whatever Coil can auto-detect.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()


    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Whether [startLibraryInit] has already run. */
    private val libraryInitStarted = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize storage paths for downloads/conversions
        com.hazel.android.util.StoragePaths.init(this)
        com.hazel.android.util.SdCards.init(this)

        // Install crash logger — captures uncaught exceptions in-memory
        CrashLogger.install(this)

        // Unpacking yt-dlp and FFmpeg is the heaviest thing the app ever does at launch,
        // and starting it here put it in competition with the work that gets the first
        // frame on screen. The UI asks for it once it has drawn instead. This is the
        // fallback for a start with no UI, such as a notification action resuming a
        // download after the process was killed. Whenever there is a UI it wins this
        // race, because the call only acts the first time.
        applicationScope.launch {
            delay(LIBRARY_INIT_FALLBACK_MS)
            startLibraryInit()
        }

        // The advanced engine settings are read by every request, so they are held in
        // memory and kept in step with storage from here on.
        applicationScope.launch {
            com.hazel.android.data.SettingsRepository.getAdvancedSettings(this@HazelApp).collect {
                com.hazel.android.download.AdvancedSettingsStore.current = it
            }
        }
    }

    /**
     * Unpack and update the download engine, once per process.
     *
     * Called from the UI as soon as it has drawn, so the work happens in the gap where the
     * user is looking at a finished screen rather than in the gap where they are waiting
     * for one. Safe to call from anywhere and as often as anything likes: every call after
     * the first returns immediately.
     */
    fun startLibraryInit() {
        if (!libraryInitStarted.compareAndSet(false, true)) return

        applicationScope.launch {
            try {
                YoutubeDL.getInstance().init(this@HazelApp)
                FFmpeg.getInstance().init(this@HazelApp)
                // A binary cut short by an older build's in-place update fails every run,
                // so it is replaced with the bundled copy before anything uses it.
                YtDlpUpdater.ensureValidBinary(this@HazelApp)
                // A build fetched last session while something was downloading goes live
                // now, before this session's first run.
                YtDlpUpdater.applyStagedUpdate(this@HazelApp)
                Log.i("Hazel", "yt-dlp + FFmpeg initialized")
            } catch (e: Exception) {
                Log.e("Hazel", "Library init failed: ${e.message}")
                return@launch
            }

            // Fetched in the background and swapped in only while nothing is running, so
            // a download started meanwhile keeps the binary it started with.
            YtDlpUpdater.autoUpdateIfAvailable(this@HazelApp)
        }
    }

    companion object {
        lateinit var instance: HazelApp
            private set

        /**
         * How long to wait for the UI to ask for the engine before starting it anyway.
         *
         * Long enough that a normal launch always gets there first, short enough that a
         * start with no UI is not left waiting on it.
         */
        private const val LIBRARY_INIT_FALLBACK_MS = 2_000L
    }
}
