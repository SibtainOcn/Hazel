package com.hazel.android.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.hazel.android.MainActivity
import com.hazel.android.R
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity

/**
 * Manages download notifications.
 * - Progress: silent, ongoing, in notification bar only (no popup/sound)
 * - Completion: sound + popup ONLY when app is minimized/background
 * - Cancel/Error: silent, brief
 */
object DownloadNotificationHelper {

    private const val CHANNEL_PROGRESS = "hazel_dl_progress"
    private const val CHANNEL_COMPLETE = "hazel_dl_complete"
    private const val PROGRESS_NOTIFICATION_ID = 1001
    private const val COMPLETE_NOTIFICATION_ID = 1002

    /**
     * Longest title the notification shows. A title past this length wraps onto a second
     * line and pushes the progress figures off the collapsed notification, which are the
     * part worth reading while a download runs.
     */
    private const val TITLE_LIMIT = 48

    /** Shortens a title to one line, ending on a word wherever that is possible. */
    private fun shortTitle(title: String): String {
        val clean = title.trim()
        if (clean.length <= TITLE_LIMIT) return clean
        val cut = clean.take(TITLE_LIMIT)
        val lastSpace = cut.lastIndexOf(' ')
        return (if (lastSpace > TITLE_LIMIT / 2) cut.take(lastSpace) else cut).trimEnd() + "\u2026"
    }

    /** Create notification channels (safe to call multiple times) */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)

            // Delete old channels if they exist (importance change needs new channel)
            try { mgr.deleteNotificationChannel("fetchkit_progress") } catch (_: Exception) {}
            try { mgr.deleteNotificationChannel("fetchkit_complete") } catch (_: Exception) {}

            // Progress channel — IMPORTANCE_DEFAULT shows icon in status bar
            // setSilent(true) on each notification ensures no sound/popup
            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS, "Download Progress",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Download progress, shows icon in status bar"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            mgr.createNotificationChannel(progressChannel)

            // Completion channel — sound + popup for background alerts
            val completeChannel = NotificationChannel(
                CHANNEL_COMPLETE, "Download Complete",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Download completion alerts"
                setShowBadge(true)
            }
            mgr.createNotificationChannel(completeChannel)
        }
    }

    private fun isAppInForeground(): Boolean {
        return try {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        } catch (_: Exception) { true }
    }

    /**
     * Opens the finished file in the device's default handler for its type, falling back to
     * opening the app when no location is known.
     */
    private fun openFileIntent(context: Context, fileUri: Uri?, isVideo: Boolean): PendingIntent {
        if (fileUri == null) return launchPendingIntent(context)

        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, if (isVideo) "video/*" else "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context, 1, view,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Reopens the app on the failure this notification is about.
     *
     * The reason travels in the intent rather than being looked up again once the app is
     * open, because by then it may not exist: the process can be gone, taking the state
     * that held it. Carrying it means the tap always lands on the dialog that offers the
     * log and the sign-in, rather than on an ordinary home screen with no explanation.
     */
    private fun failurePendingIntent(
        context: Context,
        message: String,
        signInUrl: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FAILURE_MESSAGE, message)
            signInUrl?.let { putExtra(EXTRA_FAILURE_URL, it) }
        }
        return PendingIntent.getActivity(
            context, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Extras naming the failure a notification tap should reopen the app on. */
    const val EXTRA_FAILURE_MESSAGE = "hazel.failure.message"
    const val EXTRA_FAILURE_URL = "hazel.failure.url"

    private fun launchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Shows or updates the progress notification. Always silent, never a popup.
     *
     * The media's own title heads the notification and the live yt-dlp line sits under it,
     * so the notification says what is being downloaded and how far along it is. Both are
     * kept to one line each: the title is shortened here, and the status line arrives with
     * its stage prefix and any file path already stripped out, because a path fills the
     * notification on its own and tells the reader nothing they can act on.
     *
     * @param progress percent complete, or a negative value while that is not yet known.
     */
    fun showProgress(
        context: Context,
        progress: Int,
        statusLine: String = "",
        mediaTitle: String = "",
        doneBytes: Long = 0L,
        totalBytes: Long = 0L
    ) {
        createChannels(context)

        val detail = progressLine(progress, statusLine, doneBytes, totalBytes)

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(shortTitle(mediaTitle).ifBlank { "Hazel" })
            .setContentText(detail)
            // Stated as a style as well as as content text. Some builders collapse a
            // notification carrying a progress bar down to its title alone, and the figures
            // under the bar are the whole reason to look at it.
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSubText(if (progress in 0..100) "$progress%" else null)
            .setProgress(100, progress.coerceIn(0, 100), progress < 0)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setColorized(true)
            .setColor(0xFF000000.toInt())
            .setContentIntent(launchPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.notify(PROGRESS_NOTIFICATION_ID, builder.build())
    }


    /**
     * The line under the title: how far along, how much of how much, and how fast.
     *
     * The percentage and the byte counts are the app's own, rather than read back out of
     * the engine's output, because that output is a moving target and a line that failed to
     * parse would leave the notification saying nothing. Speed and time remaining are taken
     * from the engine where it reports them, since it is the only thing that knows them,
     * and simply left out where it does not.
     */
    private fun progressLine(
        progress: Int,
        statusLine: String,
        doneBytes: Long,
        totalBytes: Long
    ): String {
        val parts = mutableListOf<String>()

        if (progress in 0..100) parts += "$progress%"

        if (totalBytes > 0) {
            parts += "${formatSize(doneBytes)} / ${formatSize(totalBytes)}"
        }

        RATE_PATTERN.find(statusLine)?.groupValues?.get(1)?.let { parts += it }
        ETA_PATTERN.find(statusLine)?.groupValues?.get(1)?.let { parts += "ETA $it" }

        // Nothing measurable yet, so whatever stage the engine named stands in for it.
        if (parts.isEmpty()) return statusLine.ifBlank { "Downloading" }

        return parts.joinToString("  ·  ")
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> "0 MB"
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        else -> "%.0f KB".format(bytes / 1024.0)
    }

    /** `at 2.35MiB/s` in a yt-dlp progress line. */
    private val RATE_PATTERN = Regex("""at\s+([\d.]+\s*[KMG]iB/s)""", RegexOption.IGNORE_CASE)

    /** `ETA 00:10` in a yt-dlp progress line. */
    private val ETA_PATTERN = Regex("""ETA\s+([\d:]+)""", RegexOption.IGNORE_CASE)

    /** Cancel the progress notification */
    fun cancelProgress(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.cancel(PROGRESS_NOTIFICATION_ID)
    }

    /**
     * Shows the completion notification. Sound only when the app is in the background.
     *
     * Tapping it opens the file in whatever app the device uses for that media type, which
     * is the one thing worth doing with a finished download. When the file's location could
     * not be resolved the notification falls back to opening Hazel.
     */
    fun showComplete(
        context: Context,
        title: String,
        isVideo: Boolean,
        fileUri: Uri? = null
    ) {
        cancelProgress(context)

        val inForeground = isAppInForeground()
        // If app is in foreground, use the silent channel; if background, use the alert channel
        val channelId = if (inForeground) CHANNEL_PROGRESS else CHANNEL_COMPLETE

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(shortTitle(title).ifBlank { "Hazel" })
            .setContentText(if (fileUri != null) "Tap to open" else "Saved")
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(0xFF000000.toInt())
            .setContentIntent(openFileIntent(context, fileUri, isVideo))
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        if (!inForeground) {
            // App is minimized — play system notification sound + heads-up popup
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            builder.setSound(soundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        } else {
            builder.setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
        }

        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.notify(COMPLETE_NOTIFICATION_ID, builder.build())
    }

    /** Show cancellation — silent, auto-dismiss after 4s */
    fun showCancelled(context: Context) {
        cancelProgress(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hazel")
            .setContentText("Download cancelled")
            .setAutoCancel(true)
            .setSilent(true)
            .setColorized(true)
            .setColor(0xFF000000.toInt())
            .setContentIntent(launchPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setTimeoutAfter(4000)

        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.notify(COMPLETE_NOTIFICATION_ID, builder.build())
    }

    /**
     * Reports a failure, and does it as loudly as a success is reported.
     *
     * A download is started and then left alone, so the app is usually in the background by
     * the time it fails. A silent failure there is one the user finds out about later, by
     * looking for a file that never arrived, which is worse than being told. It therefore
     * follows the same rule as completion: quiet while the app is open, audible when it is
     * not.
     *
     * @param title names the item where one failed inside a set, so a batch says which.
     * @param signInUrl the site to sign in to, where the failure was one that signing in
     *   would fix. It becomes an action on the notification, because the alternative is
     *   asking the user to reopen the app and repeat the link to be told the same thing.
     */
    fun showError(
        context: Context,
        errorMsg: String,
        title: String = "",
        signInUrl: String? = null
    ) {
        createChannels(context)
        cancelProgress(context)

        val inForeground = isAppInForeground()
        val channelId = if (inForeground) CHANNEL_PROGRESS else CHANNEL_COMPLETE

        val heading = if (title.isBlank()) "Download failed"
        else "Download failed: ${shortTitle(title)}"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(heading)
            .setContentText(errorMsg)
            // The reason can run past one line, and truncating it leaves the user with a
            // failure they cannot act on.
            .setStyle(NotificationCompat.BigTextStyle().bigText(errorMsg))
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(0xFF000000.toInt())
            .setContentIntent(failurePendingIntent(context, errorMsg, signInUrl))
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        if (inForeground) {
            builder.setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW)
        } else {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }

        if (signInUrl != null) {
            builder.addAction(
                0,
                "Sign in",
                PendingIntent.getActivity(
                    context,
                    2,
                    CookieWebViewActivity.intent(context, signInUrl)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.notify(COMPLETE_NOTIFICATION_ID, builder.build())
    }
}
