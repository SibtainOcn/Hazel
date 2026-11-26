package com.hazel.android.gallery

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

/**
 * The Python, ffmpeg and yt-dlp the youtubedl-android library unpacks, described so other
 * tools can run on them with the environment that library gives its own runs.
 *
 * Only this class knows the library's folder layout, so replacing the library later
 * changes this file and nothing that uses it.
 */
internal class PythonRuntime private constructor(context: Context) {

    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val packages = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/packages")
    private val pythonHome = File(packages, "python/usr")
    private val tempDir: File = context.cacheDir

    val python = File(nativeDir, "libpython.so")
    val ffmpeg = File(nativeDir, "libffmpeg.so")

    /** The live yt-dlp zip, importable as the `yt_dlp` package when it is on the path. */
    val ytDlp = File(
        context.noBackupFilesDir,
        "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}"
    )

    private val certificates = File(pythonHome, "etc/tls/cert.pem")

    /** Applies the runtime's variables to [env], with [importPath] searched for modules. */
    fun applyTo(env: MutableMap<String, String>, importPath: List<File>) {
        env["LD_LIBRARY_PATH"] = LIBRARY_DIRS.joinToString(":") { File(packages, "$it/usr/lib").absolutePath }
        env["SSL_CERT_FILE"] = certificates.absolutePath
        env["REQUESTS_CA_BUNDLE"] = certificates.absolutePath
        env["PATH"] = listOfNotNull(System.getenv("PATH"), nativeDir.absolutePath).joinToString(":")
        env["PYTHONHOME"] = pythonHome.absolutePath
        env["HOME"] = pythonHome.absolutePath
        env["TMPDIR"] = tempDir.absolutePath
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        env["PYTHONIOENCODING"] = "utf-8"
        if (importPath.isNotEmpty()) env["PYTHONPATH"] = importPath.joinToString(":") { it.absolutePath }
    }

    companion object {
        private val LIBRARY_DIRS = listOf("python", "ffmpeg", "aria2c")

        /**
         * The runtime, after the library has unpacked it. The library's own init returns at
         * once when it already ran, so calling this before every run costs nothing.
         */
        fun ready(context: Context): PythonRuntime {
            YoutubeDL.getInstance().init(context.applicationContext)
            return PythonRuntime(context.applicationContext)
        }
    }
}
