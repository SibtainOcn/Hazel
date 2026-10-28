package com.hazel.android.update

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.hazel.android.BuildConfig
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The facts a bug report needs about this device and this build, each readable on its own. */
object DebugInfo {

    fun model(): String =
        "${Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) }} ${Build.MODEL}".trim()

    fun android(): String = "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}"

    fun abis(): String = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "unknown" }

    fun primaryAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    fun build(): String =
        "${if (HazelUpdater.isFdroid()) "F-Droid" else "GitHub"} · ${BuildConfig.BUILD_TYPE} · v${BuildConfig.VERSION_NAME}"

    fun versionCode(): String = BuildConfig.VERSION_CODE.toString()

    /** When this version was installed, or null if the system will not say. */
    fun installedAt(context: Context): Long? = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }.getOrNull()?.takeIf { it > 0 }

    fun installedLabel(context: Context): String? =
        installedAt(context)?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }

    /** Every fact as plain lines, for pasting into an issue. */
    fun report(context: Context, ytDlpVersion: String): String = buildString {
        appendLine("Hazel ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${build()}")
        appendLine("yt-dlp $ytDlpVersion")
        appendLine("${model()} · ${android()}")
        appendLine("ABI ${abis()}")
        append("Locale ${Locale.getDefault().toLanguageTag()}")
        installedLabel(context)?.let { append("\nInstalled $it") }
    }

    /** Puts [text] on the clipboard; false when the system refused. */
    fun copy(context: Context, text: String): Boolean = runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("Hazel", text))
        true
    }.getOrDefault(false)

    const val NEW_ISSUE_URL = "https://github.com/${HazelUpdater.REPO_NAME}/issues/new"
    const val RELEASES_URL = "https://github.com/${HazelUpdater.REPO_NAME}/releases"
}
