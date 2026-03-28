package com.hazel.android.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.hazel.android.MainActivity
import com.hazel.android.R

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
                description = "Download progress — shows icon in status bar"
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

    private fun launchPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Show / update progress notification — always silent, no popup */
    fun showProgress(context: Context, progress: Int, title: String = "Downloading...") {
        createChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hazel")
            .setContentText(title)
            .setSubText("Working...")
            .setProgress(0, 0, true) // Indeterminate — infinite smooth animation
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

    /** Cancel the progress notification */
    fun cancelProgress(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.cancel(PROGRESS_NOTIFICATION_ID)
    }

    /** Show completion notification — sound only when app is minimized */
    fun showComplete(context: Context, fileName: String, isVideo: Boolean) {
        cancelProgress(context)

        val inForeground = isAppInForeground()
        // If app is in foreground, use the silent channel; if background, use the alert channel
        val channelId = if (inForeground) CHANNEL_PROGRESS else CHANNEL_COMPLETE
        val subtitle = if (isVideo) "Video saved" else "Audio saved"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download complete")
            .setContentText(fileName)
            .setSubText(subtitle)
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(0xFF000000.toInt())
            .setContentIntent(launchPendingIntent(context))
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

    /** Show error — silent */
    fun showError(context: Context, errorMsg: String) {
        cancelProgress(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download failed")
            .setContentText(errorMsg)
            .setAutoCancel(true)
            .setSilent(true)
            .setColorized(true)
            .setColor(0xFF000000.toInt())
            .setContentIntent(launchPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.notify(COMPLETE_NOTIFICATION_ID, builder.build())
    }
}
