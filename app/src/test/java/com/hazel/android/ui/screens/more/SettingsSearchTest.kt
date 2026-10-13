package com.hazel.android.ui.screens.more

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchTest {

    private fun setting(title: String, detail: String = "", screen: String = "Screen") =
        SettingText(SettingEntry(0, null, 0, "r"), title, detail, screen)

    private val proxy = setting("Proxy", "Send every connection through a server", "Advanced")
    private val speed = setting("Speed limit", "How fast a download is allowed to go", "Downloads")
    private val cafe = setting("Café mode", "", "Appearance")
    private val all = listOf(proxy, speed, cafe)

    @Test
    fun everyWordMustBeFound() {
        assertEquals(listOf(speed), SettingsMatcher.find("speed limit", all).map { it.text })
        assertEquals(emptyList(), SettingsMatcher.find("speed proxy", all))
        assertEquals(emptyList(), SettingsMatcher.find("   ", all))
    }

    @Test
    fun aWordInTheNameRanksAboveOneInTheLine() {
        val download = setting("Download folder")
        val hits = SettingsMatcher.find("download", listOf(speed, download)).map { it.text }
        assertEquals(listOf(download, speed), hits)
    }

    @Test
    fun caseAndAccentsAreIgnoredAndMarksPointAtTheShownText() {
        val hit = SettingsMatcher.find("CAFE", all).single()
        assertEquals(cafe, hit.text)
        assertEquals(listOf(0..3), hit.titleMarks)
        assertEquals("Café", hit.text.title.substring(hit.titleMarks[0]))
    }

    @Test
    fun theLineIsMarkedWhereAWordIsInIt() {
        val hit = SettingsMatcher.find("server", all).single()
        assertEquals(emptyList(), hit.titleMarks)
        assertEquals("server", proxy.detail.substring(hit.detailMarks.single()))
    }

    @Test
    fun theScreenNameFindsItsSettings() {
        assertEquals(listOf(proxy), SettingsMatcher.find("advanced", all).map { it.text })
    }

    @Test
    fun foldingKeepsEveryCharacterInPlace() {
        listOf("Café", "Ünïcødé", "設定を検索", "Поиск", "İstanbul").forEach {
            assertEquals(it.length, SettingsMatcher.fold(it).length, it)
        }
    }

    @Test
    fun everyResultOpensAScreenThatExists() {
        val nav = File("src/main/java/com/hazel/android/ui/navigation/AppNavigation.kt").readText()
        SETTINGS_INDEX.forEach { entry ->
            assertTrue(entry.route != null || entry.action != null, "$entry goes nowhere")
            val route = entry.route ?: return@forEach
            val declared = if (route.startsWith("docs/")) "\"docs/{page}" else "composable(\"$route\")"
            assertTrue(nav.contains(declared), "No screen for $route")
        }
    }
}
