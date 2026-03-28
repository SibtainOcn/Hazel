package com.hazel.android

import android.app.Application
import android.util.Log
import com.hazel.android.utils.CrashLogger
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HazelApp : Application() {

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

            // Auto-update yt-dlp binary from GitHub
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(this@HazelApp)
                Log.i("Hazel", "yt-dlp update: $status")
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
