package com.hazel.android.util

import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * `Process` and `ProcessBuilder` calls that only exist from Android 8, with what Android 7
 * offers below it, so a child process never crashes the app with NoSuchMethodError there.
 */
object Processes {
    private val modern get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun kill(p: Process) {
        if (modern) p.destroyForcibly() else p.destroy()
    }

    fun isAlive(p: Process): Boolean =
        if (modern) p.isAlive else try { p.exitValue(); false } catch (_: IllegalThreadStateException) { true }

    fun waitFor(p: Process, timeout: Long, unit: TimeUnit): Boolean =
        com.hazel.android.download.LocalTrim.waitFor(p, unit.toMillis(timeout))

    /** Starts [builder] with its stderr written to [file]; below Android 8 a small thread copies it. */
    fun startWithErrorTo(builder: ProcessBuilder, file: File): Process {
        if (modern) return builder.redirectError(file).start()
        val process = builder.start()
        thread(isDaemon = true, name = "stderr-copy") {
            runCatching { process.errorStream.use { input -> file.outputStream().use { input.copyTo(it) } } }
        }
        return process
    }
}
