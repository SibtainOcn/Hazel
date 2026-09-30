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
    fun onlyChosenCategoriesAreWritten() {
        val json = BackupRepository.export(source, setOf(BackupCategory.HISTORY))
        val values = json.getJSONObject("values")
        assertTrue(values.has("download_history"))
        assertFalse(values.has("speed_limit"))
        assertFalse(values.has("search_history"))
    }

    @Test
    fun listsMergeWithoutDuplicates() {
        val merged = JSONArray(
            BackupRepository.mergeLists("""[{"id":2,"url":"b"},{"id":1,"url":"a"}]""", """[{"id":1,"url":"a"},{"id":3,"url":"c"}]""", "id")
        )
        assertEquals(listOf(2L, 1L, 3L), (0 until merged.length()).map { merged.getJSONObject(it).getLong("id") })
        assertEquals("""["x","y"]""", BackupRepository.mergeLists("""["x"]""", """["x","y"]""", null))
        assertEquals("""["y"]""", BackupRepository.mergeLists(null, """["y"]""", null))
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
    fun otherFilesAreRefused() {
        assertFailsWith<BackupRepository.NotABackup> { BackupRepository.parse("""{"a":1}""") }
        assertFailsWith<BackupRepository.NotABackup> { BackupRepository.parse("not json") }
    }
}
