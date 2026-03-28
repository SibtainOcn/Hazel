package com.hazel.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "hazel_download"
        const val NOTIFICATION_ID = 1
        const val EXTRA_URL = "extra_url"
        const val EXTRA_IS_VIDEO = "extra_is_video"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Preparing download...")
        startForeground(NOTIFICATION_ID, notification)

        // TODO: Execute yt-dlp download with progress updates
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hazel download progress"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, progress: Int = 0): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hazel")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()
    }
}
