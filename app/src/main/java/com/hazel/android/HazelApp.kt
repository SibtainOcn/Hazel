package com.hazel.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import android.util.Log
import com.hazel.android.data.SettingsRepository
import com.hazel.android.update.YtDlpUpdater
import com.hazel.android.utils.CrashLogger
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize storage paths for downloads/conversions
        com.hazel.android.util.StoragePaths.init(this)

        // Install crash logger — captures uncaught exceptions in-memory
        CrashLogger.install(this)

        initLibraries()
    }

    private fun initLibraries() {
        applicationScope.launch {
            try {
                YoutubeDL.getInstance().init(this@HazelApp)
                FFmpeg.getInstance().init(this@HazelApp)
                Log.i("Hazel", "yt-dlp + FFmpeg initialized")
            } catch (e: Exception) {
                Log.e("Hazel", "Library init failed: ${e.message}")
            }

            // Update the yt-dlp binary on the channel selected in the update screen.
            // The same path is triggered manually from the yt-dlp update screen.
            try {
                val channel = YtDlpUpdater.Channel.fromLabel(
                    SettingsRepository.getYtDlpChannel(this@HazelApp).first()
                )
                val status = YtDlpUpdater.install(this@HazelApp, channel)
                Log.i("Hazel", "yt-dlp update (${channel.label}): $status")
            } catch (e: Exception) {
                Log.w("Hazel", "yt-dlp update failed: ${e.message}")
            }
        }
    }

    companion object {
        lateinit var instance: HazelApp
            private set
    }
}
