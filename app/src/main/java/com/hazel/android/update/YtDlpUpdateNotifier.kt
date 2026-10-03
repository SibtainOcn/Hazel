package com.hazel.android.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Posts yt-dlp update notices, each behind its toggle on the yt-dlp update screen. */
object YtDlpUpdateNotifier {

    enum class Event { AVAILABLE, INSTALLED, FAILED }

    private const val CHANNEL_ID = "ytdlp_updates"
    private const val NOTIFICATION_ID = 1101
    private const val PREFS = "ytdlp_update_notifier"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun post(context: Context, event: Event, version: String) {
        val app = context.applicationContext
        scope.launch {
            val enabled = when (event) {
                Event.AVAILABLE -> SettingsRepository.getUpdateNotifyAvailable(app)
                Event.INSTALLED -> SettingsRepository.getUpdateNotifyComplete(app)
                Event.FAILED -> SettingsRepository.getUpdateNotifyFailed(app)
            }.first()
            if (!enabled || alreadyPosted(app, event, version)) return@launch
            runCatching { show(app, event, version) }
        }
    }

    /** Available and failed notices are posted once per release, not on every launch. */
    private fun alreadyPosted(context: Context, event: Event, version: String): Boolean {
        if (event == Event.INSTALLED) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(event.name, null) == version) return true
        prefs.edit().putString(event.name, version).apply()
        return false
    }

    private fun show(context: Context, event: Event, version: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.ytdlp_update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.ytdlp_update_channel_description) }
            )
        }

        val (title, text) = when (event) {
            Event.AVAILABLE -> R.string.ytdlp_update_available_title to R.string.ytdlp_update_available_text
            Event.INSTALLED -> R.string.ytdlp_update_installed_title to R.string.ytdlp_update_installed_text
            Event.FAILED -> R.string.ytdlp_update_failed_title to R.string.ytdlp_update_failed_text
        }
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title, version))
            .setContentText(context.getString(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (launch != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context, NOTIFICATION_ID, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        mgr.notify(NOTIFICATION_ID, builder.build())
    }
}
