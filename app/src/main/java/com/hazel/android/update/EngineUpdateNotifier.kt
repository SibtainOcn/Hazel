package com.hazel.android.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Posts engine update notices (yt-dlp, gallery-dl), each behind its toggle on the update screens. */
object EngineUpdateNotifier {

    enum class Event { AVAILABLE, INSTALLED, FAILED }

    enum class Engine(
        val notificationId: Int,
        /** yt-dlp keeps the plain event names it was stored under before gallery-dl existed. */
        val postedKeyPrefix: String,
        @param:StringRes val availableTitle: Int,
        @param:StringRes val availableText: Int,
        @param:StringRes val installedTitle: Int,
        @param:StringRes val installedText: Int,
        @param:StringRes val failedTitle: Int,
        @param:StringRes val failedText: Int
    ) {
        YT_DLP(
            1101, "",
            R.string.ytdlp_update_available_title, R.string.ytdlp_update_available_text,
            R.string.ytdlp_update_installed_title, R.string.ytdlp_update_installed_text,
            R.string.ytdlp_update_failed_title, R.string.ytdlp_update_failed_text
        ),
        GALLERY_DL(
            1102, "gallery_dl_",
            R.string.gallery_dl_update_available_title, R.string.gallery_dl_update_available_text,
            R.string.gallery_dl_update_installed_title, R.string.gallery_dl_update_installed_text,
            R.string.gallery_dl_update_failed_title, R.string.gallery_dl_update_failed_text
        )
    }

    private const val CHANNEL_ID = "ytdlp_updates"
    private const val PREFS = "ytdlp_update_notifier"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun post(context: Context, engine: Engine, event: Event, version: String) {
        val app = context.applicationContext
        scope.launch {
            val enabled = when (event) {
                Event.AVAILABLE -> SettingsRepository.getUpdateNotifyAvailable(app)
                Event.INSTALLED -> SettingsRepository.getUpdateNotifyComplete(app)
                Event.FAILED -> SettingsRepository.getUpdateNotifyFailed(app)
            }.first()
            if (!enabled || alreadyPosted(app, engine, event, version)) return@launch
            runCatching { show(app, engine, event, version) }
        }
    }

    /** Available and failed notices are posted once per release, not on every launch. */
    private fun alreadyPosted(context: Context, engine: Engine, event: Event, version: String): Boolean {
        if (event == Event.INSTALLED) return false
        val key = engine.postedKeyPrefix + event.name
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(key, null) == version) return true
        prefs.edit().putString(key, version).apply()
        return false
    }

    private fun show(context: Context, engine: Engine, event: Event, version: String) {
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
            Event.AVAILABLE -> engine.availableTitle to engine.availableText
            Event.INSTALLED -> engine.installedTitle to engine.installedText
            Event.FAILED -> engine.failedTitle to engine.failedText
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
                    context, engine.notificationId, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        mgr.notify(engine.notificationId, builder.build())
    }
}
