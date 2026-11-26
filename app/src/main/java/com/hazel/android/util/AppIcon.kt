package com.hazel.android.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.hazel.android.R

/**
 * Which icon Hazel shows on the home screen and in the share menu. Each icon has its own
 * launcher entry and its own four share entries in the manifest; exactly one set is on, and
 * the package manager's own state is the only record of which one, so nothing needs saving or
 * backing up.
 */
enum class AppIcon(private val suffix: String, private val launcher: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    CLASSIC("", "MainActivity", R.string.app_icon_classic, R.mipmap.ic_launcher),
    WHITE("White", "LauncherWhite", R.string.app_icon_white, R.mipmap.ic_launcher_white),
    GLASS("Glass", "LauncherGlass", R.string.app_icon_glass, R.mipmap.ic_launcher_glass);

    /** The launcher entry this icon uses, for shortcuts that must belong to the shown one. */
    fun launcherComponent(context: Context) = component(context, launcher)

    /** This icon's entry for one share target ([SHARE_TARGETS]). */
    fun shareComponent(context: Context, target: String) = component(context, target + suffix)

    private fun components(context: Context) =
        listOf(launcherComponent(context)) + SHARE_TARGETS.map { shareComponent(context, it) }

    companion object {
        /** Component classes live under the code's package, which is not the app id on every build. */
        private const val NAMESPACE = "com.hazel.android"

        const val VIDEO = "VideoShareActivity"
        const val INSTANT = "InstantShareActivity"
        const val GALLERY = "GalleryShareActivity"
        const val INSTANT_GALLERY = "InstantGalleryShareActivity"
        private val SHARE_TARGETS = listOf(VIDEO, INSTANT, GALLERY, INSTANT_GALLERY)

        private fun component(context: Context, name: String) = ComponentName(context.packageName, "$NAMESPACE.$name")

        /** A share entry's base name, whichever icon it belongs to. */
        fun baseName(className: String): String {
            val name = className.substringAfterLast('.')
            return entries.firstNotNullOfOrNull { icon ->
                icon.suffix.takeIf { it.isNotEmpty() && name.endsWith(it) }?.let { name.removeSuffix(it) }
            } ?: name
        }

        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull { icon ->
                when (pm.getComponentEnabledSetting(icon.launcherComponent(context))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == CLASSIC
                    else -> false
                }
            } ?: CLASSIC
        }

        /**
         * Shows [icon] and hides the others. The new set is turned on before the old one is
         * turned off, so there is never a moment without a launcher entry. The app is not
         * stopped here; [RestartActivity] restarts it.
         */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            fun set(component: ComponentName, on: Boolean) = pm.setComponentEnabledSetting(
                component,
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            icon.components(context).forEach { set(it, true) }
            entries.filter { it != icon }.forEach { other -> other.components(context).forEach { set(it, false) } }
        }
    }
}
