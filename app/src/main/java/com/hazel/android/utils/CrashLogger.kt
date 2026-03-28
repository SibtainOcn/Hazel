package com.hazel.android.utils

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashLogger — lightweight in-memory error capture.
 *
 * Holds crash reports and download errors in RAM only — no disk files.
 * LogViewerScreen reads from [getLog] to display the current session's errors.
 * Buffer is cleared on app restart (ephemeral by design).
 */
object CrashLogger {

    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** In-memory log buffer — last session only */
    private val buffer = StringBuilder()

    /**
     * Install as the global uncaught exception handler.
     * Captures crash info to in-memory buffer, then delegates to default handler.
     */
    fun install(context: Context) {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                buffer.appendLine(buildCrashReport(context, thread, throwable))
            } catch (_: Exception) { }
            default?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Log a download error with context about the URL and platform.
     * Stored in-memory only — visible in LogViewerScreen during the same session.
     */
    fun logDownloadError(
        url: String,
        platform: String,
        error: String,
        logs: List<String> = emptyList()
    ) {
        try {
            buffer.appendLine("-------------------------------------")
            buffer.appendLine("  Hazel Download Error Report")
            buffer.appendLine("-------------------------------------")
            buffer.appendLine()
            buffer.appendLine("Time     : ${logDateFormat.format(Date())}")
            buffer.appendLine("URL      : $url")
            buffer.appendLine("Platform : $platform")
            buffer.appendLine("Error    : $error")
            buffer.appendLine()
            buffer.appendLine("─── Download Logs ───")
            logs.forEach { buffer.appendLine("  $it") }
            buffer.appendLine()
            buffer.appendLine("─── Device Info ───")
            buffer.appendLine("  Android  : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            buffer.appendLine("  Device   : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            buffer.appendLine("  ABI      : ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
            buffer.appendLine()
        } catch (_: Exception) { }
    }

    /** Get the full in-memory log content */
    fun getLog(): String {
        return if (buffer.isEmpty()) "" else buffer.toString()
    }

    /** Check if there are any logged errors this session */
    fun hasLogs(): Boolean = buffer.isNotEmpty()

    /** Clear the in-memory buffer */
    fun clear() {
        buffer.clear()
    }

    private fun buildCrashReport(context: Context, thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) { "unknown" }

        return buildString {
            appendLine("-------------------------------------")
            appendLine("  Hazel Crash Report")
            appendLine("-------------------------------------")
            appendLine()
            appendLine("Time     : ${logDateFormat.format(Date())}")
            appendLine("Thread   : ${thread.name}")
            appendLine("App Ver  : $versionName")
            appendLine()
            appendLine("─── Device Info ───")
            appendLine("  Android  : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("  Device   : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("  ABI      : ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            appendLine("─── Stack Trace ───")
            appendLine(sw.toString())
        }
    }
}
