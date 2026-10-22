package com.hazel.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hazel.android.MainActivity
import com.hazel.android.R
import com.hazel.android.ui.share.ShareOverlayActivity

/** Launcher long-press shortcuts and share-sheet targets (categories match res/xml/shortcuts.xml). */
object AppShortcuts {

    private const val SHARE_CATEGORY = "com.hazel.android.category.SHARE_LINK"

    private class Jump(val id: String, val route: String, @StringRes val label: Int, @DrawableRes val icon: Int)

    private val jumps = listOf(
        Jump("search", "download", R.string.shortcut_search, R.drawable.shortcut_search),
        Jump("downloads", "history", R.string.nav_history, R.drawable.shortcut_downloads),
        Jump("queue", "queue", R.string.nav_queue, R.drawable.shortcut_queue),
        Jump("settings", "more", R.string.nav_more, R.drawable.shortcut_settings)
    )

    fun publish(context: Context) {
        runCatching {
            val app = context.applicationContext
            val launcher = jumps.map { jump ->
                ShortcutInfoCompat.Builder(app, jump.id)
                    .setShortLabel(app.getString(jump.label))
                    .setIcon(IconCompat.createWithResource(app, jump.icon))
                    .setIntent(
                        Intent(app, MainActivity::class.java)
                            .setAction(Intent.ACTION_VIEW)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(MainActivity.EXTRA_NAVIGATE_TO, jump.route)
                    )
                    .build()
            }
            val share = listOf(
                shareTarget(app, "share_download", app.getString(R.string.share_shortcut_download), ShareOverlayActivity::class.java.name),
                shareTarget(app, "share_instant", app.getString(R.string.instant_label), "${app.packageName}.InstantShareActivity")
            )
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(app).coerceAtLeast(1)
            val wanted = (launcher + share).take(max)
            val current = ShortcutManagerCompat.getDynamicShortcuts(app)
                .associate { it.id to it.shortLabel.toString() }
            if (current == wanted.associate { it.id to it.shortLabel.toString() }) return@runCatching
            ShortcutManagerCompat.setDynamicShortcuts(app, wanted)
        }
    }

    private fun shareTarget(context: Context, id: String, label: String, target: String) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setCategories(setOf(SHARE_CATEGORY))
            .setActivity(ComponentName(context, MainActivity::class.java))
            .setIntent(
                Intent(Intent.ACTION_SEND)
                    .setComponent(ComponentName(context.packageName, target))
                    .setType("text/plain")
            )
            .setLongLived(true)
            .setExcludedFromSurfaces(ShortcutInfoCompat.SURFACE_LAUNCHER)
            .build()

    /** Tells the system a share target was used, so share sheets rank it higher next time. */
    fun reportShared(context: Context, instant: Boolean) {
        runCatching {
            ShortcutManagerCompat.reportShortcutUsed(context.applicationContext, if (instant) "share_instant" else "share_download")
        }
    }
}
