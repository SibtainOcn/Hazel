package com.hazel.android.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.json.JSONArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupRepositoryTest {

    private val source = mutablePreferencesOf(
        booleanPreferencesKey("wifi_only") to true,
        intPreferencesKey("concurrent_fragments") to 4,
        stringPreferencesKey("speed_limit") to "1M",
        stringSetPreferencesKey("sheet_sponsorblock_filters") to setOf("sponsor", "intro"),
        stringPreferencesKey("download_history") to """[{"id":1,"url":"a"}]""",
        stringPreferencesKey("search_history") to """["x"]""",
        stringPreferencesKey("download_tree_uri") to "content://tree",
        booleanPreferencesKey("hazel_update_available") to true
    )

    private fun roundTrip(backedUp: Set<BackupCategory>, restored: Set<BackupCategory>) =
        mutablePreferencesOf().also { target ->
            val contents = BackupRepository.parse(BackupRepository.export(source, backedUp).toString())
            BackupRepository.apply(target, contents, restored)
        }

    @Test
    fun settingsKeepTheirTypes() {
        val restored = roundTrip(BackupCategory.entries.toSet(), setOf(BackupCategory.SETTINGS))
        assertEquals(true, restored[booleanPreferencesKey("wifi_only")])
        assertEquals(4, restored[intPreferencesKey("concurrent_fragments")])
        assertEquals("1M", restored[stringPreferencesKey("speed_limit")])
        assertEquals(setOf("sponsor", "intro"), restored[stringSetPreferencesKey("sheet_sponsorblock_filters")])
        assertNull(restored[stringPreferencesKey("download_history")])
    }

    @Test
    fun deviceOnlyKeysAreNeverCarried() {
        val restored = roundTrip(BackupCategory.entries.toSet(), BackupCategory.entries.toSet())
        assertNull(restored[stringPreferencesKey("download_tree_uri")])
        assertNull(restored[booleanPreferencesKey("hazel_update_available")])
    }





    @Test
    fun restoredQueueWaitsToBeResumed() {
        val backup = mutablePreferencesOf(
            stringPreferencesKey("download_queue") to """[{"url":"u","paused":false,"treeUri":"content://x"}]"""
        )
        val target = mutablePreferencesOf()
        val contents = BackupRepository.parse(BackupRepository.export(backup, setOf(BackupCategory.QUEUE)).toString())
        BackupRepository.apply(target, contents, setOf(BackupCategory.QUEUE))
        val item = JSONArray(target[stringPreferencesKey("download_queue")]).getJSONObject(0)
        assertTrue(item.getBoolean("paused"))
        assertEquals("", item.getString("treeUri"))
    }



    @Test
    fun newerFormatIsBlocked() {
        val json = BackupRepository.export(source, BackupCategory.entries.toSet())
            .put("formatVersion", BackupRepository.FORMAT_VERSION + 1)
            .put("appVersion", "9.9.9")
        val error = assertFailsWith<BackupRepository.NewerBackup> { BackupRepository.parse(json.toString()) }
        assertEquals("9.9.9", error.appVersion)
    }

    @Test
    fun unknownKeysAndFieldsAreIgnored() {
        val json = BackupRepository.export(source, setOf(BackupCategory.SETTINGS)).put("future", 1)
        json.getJSONObject("values").put("weird", org.json.JSONObject().put("t", "?").put("v", 1))
        val target = mutablePreferencesOf()
        BackupRepository.apply(target, BackupRepository.parse(json.toString()), setOf(BackupCategory.SETTINGS))
        assertEquals("1M", target[stringPreferencesKey("speed_limit")])
        assertFalse(target.asMap().keys.any { it.name == "weird" })
    }




}
