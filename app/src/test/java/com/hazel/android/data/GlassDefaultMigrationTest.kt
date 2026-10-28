package com.hazel.android.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class GlassDefaultMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val glass = SettingsRepository.GLASS_KEY
    private val settled = GlassDefaultMigration.SETTLED_KEY
    private val accent = stringPreferencesKey("accent_color")

    private fun migrated(start: Preferences, upgraded: Boolean): Preferences = runBlocking {
        val migration = GlassDefaultMigration { upgraded }
        if (migration.shouldMigrate(start)) migration.migrate(start) else start
    }

    @Test
    fun `fresh install starts with glass off`() {
        val out = migrated(emptyPreferences(), upgraded = false)
        assertNull(out[glass])
        assertEquals(true, out[settled])
    }

    @Test
    fun `update that never touched glass keeps it on`() {
        assertEquals(true, migrated(preferencesOf(accent to "White"), upgraded = false)[glass])
        assertEquals(true, migrated(emptyPreferences(), upgraded = true)[glass])
    }

    @Test
    fun `a saved choice is never changed`() {
        for (choice in listOf(true, false)) for (upgraded in listOf(true, false)) {
            assertEquals(choice, migrated(preferencesOf(glass to choice), upgraded)[glass])
        }
    }

    @Test
    fun `runs once, so a new user who adds settings later stays off`() {
        val first = migrated(emptyPreferences(), upgraded = false)
        val later = first.toMutablePreferences().apply { this[accent] = "Blue" }.toPreferences()
        assertFalse(runBlocking { GlassDefaultMigration { true }.shouldMigrate(later) })
        assertNull(migrated(later, upgraded = true)[glass])
    }

    @Test
    fun `a failing update check is never asked when settings already exist`() {
        var asked = false
        val out = runBlocking {
            GlassDefaultMigration { asked = true; error("no package manager") }
                .migrate(preferencesOf(accent to "White"))
        }
        assertEquals(true, out[glass])
        assertFalse(asked)
    }

    @Test
    fun `real store applies it before the first read, and only once`() = runBlocking {
        val file = tmp.newFile("hazel_settings.preferences_pb").also { it.delete() }

        fun open(upgraded: Boolean, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
            migrations = listOf(GlassDefaultMigration { upgraded }),
            scope = scope,
            produceFile = { file }
        )

        val scope1 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = open(upgraded = false, scope1)
        assertNull(store.data.first()[glass])
        assertEquals(true, store.data.first()[settled])
        scope1.cancel()

        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        assertNull(open(upgraded = true, scope2).data.first()[glass])
        scope2.cancel()
    }

    @Test
    fun `the mark stays on the device and out of backups`() {
        val prefs = preferencesOf(settled to true, booleanPreferencesKey("glass_theme") to true)
        val values = BackupRepository.export(prefs, BackupCategory.entries.toSet()).getJSONObject("values")
        assertFalse(values.has(GlassDefaultMigration.SETTLED_NAME))
    }

    @Test
    @Config(sdk = [24])
    fun `update check on old android`() = checkUpdateDetection()

    @Test
    @Config(sdk = [35])
    fun `update check on new android`() = checkUpdateDetection()

    private fun checkUpdateDetection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
        info.firstInstallTime = 1_000L
        info.lastUpdateTime = 1_000L
        assertFalse(wasUpdated(context))
        info.lastUpdateTime = 2_000L
        assertTrue(wasUpdated(context))
    }
}
