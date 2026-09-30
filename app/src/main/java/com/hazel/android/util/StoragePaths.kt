package com.hazel.android.util

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * StoragePaths — single source of truth for all Hazel folder locations.
 *
 * Two-tier approach:
 *   • TEMP paths: app-specific external (getExternalFilesDir) — always writable, NO permission
 *   • FINAL paths: public shared storage (Download/Hazel, Music/Hazel) — via MediaStore move
 *
 * Layout (temp — where yt-dlp writes):
 *   Android/data/com.hazel.android/files/Hazel/         ← downloads
 *   Android/data/com.hazel.android/files/Music/          ← converter output
 *
 * Layout (final — where user sees files):
 *   /storage/emulated/0/Download/Hazel/                  ← downloads
 *   /storage/emulated/0/Music/Hazel/                     ← converter output
 *
 */
object StoragePaths {

    private lateinit var appContext: Context

    /** Must be called once from HazelApp.onCreate() */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── TEMP paths (yt-dlp/ffmpeg write here, always writable, no permission) ──

    /** Temp download dir: Android/data/com.hazel.android/files/Hazel/ */
    val tempDownloads: File
        get() = ensureDir(
            File(appContext.getExternalFilesDir(null), "Hazel")
        )

    /** Temp converter output: Android/data/com.hazel.android/files/Music/ */
    val tempConverted: File
        get() = ensureDir(
            File(appContext.getExternalFilesDir(null), "Music")
        )

    // ── FINAL paths (public shared storage, where files end up after MediaStore move) ──

    /** Final download dir: /storage/emulated/0/Download/Hazel/ */
    val finalDownloads: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Hazel"
        )

    /** Where one kind lands by default: Download/Hazel/Audio or Download/Hazel/Video. */
    fun finalDownloads(isAudio: Boolean): File =
        File(finalDownloads, if (isAudio) "Audio" else "Video")

    /** Final converter dir: /storage/emulated/0/Music/Hazel/ */
    val finalConverted: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Hazel"
        )

    // ── Display paths (shown in UI) ──

    const val DOWNLOADS_DISPLAY = "Download/Hazel"
    const val DOWNLOADS_VIDEO_DISPLAY = "Download/Hazel/Video"
    const val DOWNLOADS_AUDIO_DISPLAY = "Download/Hazel/Audio"
    const val CONVERTED_DISPLAY = "Music/Hazel"

    // ── MediaStore relative paths (used for ContentValues) ──

    /** Relative path for MediaStore.Downloads: "Download/Hazel" */
    const val DOWNLOAD_RELATIVE_PATH = "Download/Hazel"
    const val DOWNLOAD_VIDEO_RELATIVE_PATH = "Download/Hazel/Video"
    const val DOWNLOAD_AUDIO_RELATIVE_PATH = "Download/Hazel/Audio"

    /**
     * Where a download lands when the user has not picked a folder: audio and video are kept
     * apart under the app's download folder, so a music library and a video library can each
     * be pointed at their own.
     */
    fun downloadRelativePath(isAudio: Boolean): String =
        if (isAudio) DOWNLOAD_AUDIO_RELATIVE_PATH else DOWNLOAD_VIDEO_RELATIVE_PATH

    /** [downloadRelativePath] as the UI shows it. */
    fun downloadsDisplay(isAudio: Boolean): String =
        if (isAudio) DOWNLOADS_AUDIO_DISPLAY else DOWNLOADS_VIDEO_DISPLAY

    /** Relative path for MediaStore.Audio: "Music/Hazel" */
    const val MUSIC_RELATIVE_PATH = "Music/Hazel"

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
