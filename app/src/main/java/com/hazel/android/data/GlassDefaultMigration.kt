package com.hazel.android.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Glass used to be on unless switched off, and is now off unless switched on. Before the
 * first read on an install that already ran, an unset glass choice is saved as on, so anyone
 * who had glass keeps it. A fresh install has nothing saved and takes the new default.
 * Runs once per install: the mark it leaves stops it for good.
 */
internal class GlassDefaultMigration(private val upgraded: () -> Boolean) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[SETTLED_KEY] == null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        if (prefs[SettingsRepository.GLASS_KEY] == null && (currentData.asMap().isNotEmpty() || upgraded())) {
            prefs[SettingsRepository.GLASS_KEY] = true
        }
        prefs[SETTLED_KEY] = true
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    companion object {
        const val SETTLED_NAME = "glass_default_settled"
        val SETTLED_KEY = booleanPreferencesKey(SETTLED_NAME)
    }
}

/**
 * Whether this install was updated over an earlier one. Unknown counts as yes, so a failed
 * lookup keeps glass as it was rather than turning it off.
 */
internal fun wasUpdated(context: Context): Boolean = runCatching {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    info.lastUpdateTime > info.firstInstallTime
}.getOrDefault(true)
