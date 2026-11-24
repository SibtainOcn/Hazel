package com.hazel.android.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hazel.android.MainActivity
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Announces a new Hazel version, behind the toggle on the Hazel update screen. A tap opens
 * that screen, on Install when the update was already downloaded in the background.
 */
object HazelUpdateNotifier {

    enum class Event { AVAILABLE, READY }

    private const val CHANNEL_ID = "hazel_updates"
    private const val NOTIFICATION_ID = 1102
    private const val PREFS = "hazel_update_notifier"
    const val ROUTE = "hazel_update"

    suspend fun post(context: Context, event: Event, version: String) {
        val app = context.applicationContext
        if (!SettingsRepository.getHazelUpdateNotify(app).first()) return
        // Each notice is posted once per version; one that could not be shown is tried again.
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(event.name, null) == version) return
        if (runCatching { show(app, event, version) }.getOrDefault(false)) {
            prefs.edit().putString(event.name, version).apply()
        }
    }

    private fun show(context: Context, event: Event, version: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        // Covers notifications turned off for the app on any Android version, not only 13+.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        val mgr = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.hazel_update_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.hazel_update_channel_description) }
            )
        }

        val text = when (event) {
            Event.AVAILABLE -> R.string.hazel_update_available_text
            Event.READY -> R.string.hazel_update_ready_text
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, ROUTE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.hazel_update_available_title, version))
            .setContentText(context.getString(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, NOTIFICATION_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        mgr.notify(NOTIFICATION_ID, notification)
        return true
    }
}
