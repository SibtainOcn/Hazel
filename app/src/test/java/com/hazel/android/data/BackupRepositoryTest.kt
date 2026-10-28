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

    private val everything = mutablePreferencesOf(
        booleanPreferencesKey("wifi_only") to true,
        stringPreferencesKey("download_history") to """[{"id":1}]""",
        stringPreferencesKey("download_queue") to """[{"url":"q"}]""",
        booleanPreferencesKey("download_queue_held") to true,
        stringPreferencesKey("failed_downloads") to """[{"url":"f"}]""",
        stringPreferencesKey("cookie_entries") to """[{"url":"c"}]""",
        stringPreferencesKey("adv_po_tokens") to "token",
        stringPreferencesKey("adv_visitor_data") to "visitor",
        stringPreferencesKey("search_history") to """["s"]""",
        booleanPreferencesKey("has_update_available") to true,
        stringPreferencesKey("hazel_update_version") to "9.9",
        booleanPreferencesKey("update_notify_off_applied") to true
    )

    @Test
    fun eachCategoryHoldsOnlyItsOwnKeys() {
        val expected = mapOf(
            BackupCategory.SETTINGS to setOf("wifi_only"),
            BackupCategory.HISTORY to setOf("download_history"),
            BackupCategory.QUEUE to setOf("download_queue", "download_queue_held"),
            BackupCategory.FAILED to setOf("failed_downloads"),
            BackupCategory.COOKIES to setOf("cookie_entries", "adv_po_tokens", "adv_visitor_data"),
            BackupCategory.SEARCH to setOf("search_history")
        )
        expected.forEach { (category, keys) ->
            assertEquals(keys, BackupRepository.export(everything, setOf(category)).getJSONObject("values").keys().asSequence().toSet(), category.id)
        }
        val chosen = setOf(BackupCategory.HISTORY, BackupCategory.COOKIES)
        val contents = BackupRepository.parse(BackupRepository.export(everything, chosen).toString())
        assertEquals(chosen, contents.categories)
        val target = mutablePreferencesOf()
        BackupRepository.apply(target, contents, setOf(BackupCategory.COOKIES))
        assertEquals(setOf("cookie_entries", "adv_po_tokens", "adv_visitor_data"), target.asMap().keys.map { it.name }.toSet())
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

    @Test
    fun previewCountsAndLanguage() {
        val contents = BackupRepository.parse(BackupRepository.export(source, BackupCategory.entries.toSet(), "de").toString())
        assertEquals(1, contents.counts[BackupCategory.HISTORY])
        assertEquals(1, contents.counts[BackupCategory.SEARCH])
        assertEquals("de", contents.language)
        val withoutSettings = BackupRepository.parse(BackupRepository.export(source, setOf(BackupCategory.HISTORY), "de").toString())
        assertNull(withoutSettings.language)
    }

    @Test
    fun scheduleAndFreshInstall() {
        val day = java.util.concurrent.TimeUnit.DAYS.toMillis(1)
        val daily = mutablePreferencesOf(stringPreferencesKey("backup_schedule") to "daily")
        assertTrue(BackupRepository.isDue(daily, day))
        daily[androidx.datastore.preferences.core.longPreferencesKey("backup_last_auto_at")] = day
        assertFalse(BackupRepository.isDue(daily, day + day / 2))
        assertTrue(BackupRepository.isDue(daily, day * 2))
        assertFalse(BackupRepository.isDue(mutablePreferencesOf(), day * 100))

        assertTrue(BackupRepository.looksFresh(mutablePreferencesOf(booleanPreferencesKey("backup_auto") to true)))
        assertFalse(BackupRepository.looksFresh(source))
    }
}
