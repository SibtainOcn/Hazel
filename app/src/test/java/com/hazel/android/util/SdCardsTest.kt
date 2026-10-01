package com.hazel.android.util

import com.hazel.android.data.DownloadQueueRepository
import com.hazel.android.data.QueuedDownload
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.SaveFallback
import com.hazel.android.download.workDirName
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Saving to an SD card runs through a different branch on almost every Android version the
 * app supports, and through folder ids no other part of the app sees. A mistake in either
 * shows up only on a device with a card in it, so the parts that decide are checked here.
 */
class SdCardsTest {

    // ── Picker route per version, from the app's minimum to the newest ──

    @Test
    fun `android 7 and 7_1 open the plain picker`() {
        for (sdk in 24..25) assertEquals(SdCards.PickerRoute.PLAIN, SdCards.pickerRoute(sdk), "sdk $sdk")
    }

    @Test
    fun `android 8 and 9 start the picker at the card's root`() {
        for (sdk in 26..28) assertEquals(SdCards.PickerRoute.INITIAL_URI, SdCards.pickerRoute(sdk), "sdk $sdk")
    }

    @Test
    fun `android 10 and newer let the volume build the picker`() {
        for (sdk in 29..36) assertEquals(SdCards.PickerRoute.VOLUME_INTENT, SdCards.pickerRoute(sdk), "sdk $sdk")
    }

    // ── Folder ids ──

    @Test
    fun `volume is read from a storage document id`() {
        assertEquals("1A2B-3C4D", SdCards.volumeOfDocumentId("1A2B-3C4D:Music/Hazel"))
        assertEquals("1A2B-3C4D", SdCards.volumeOfDocumentId("1A2B-3C4D:"))
        assertEquals("primary", SdCards.volumeOfDocumentId("primary:Download"))
        assertNull(SdCards.volumeOfDocumentId("no-colon-here"))
        assertNull(SdCards.volumeOfDocumentId(":path"))
    }

    @Test
    fun `internal storage is never taken for a card`() {
        assertNull(SdCards.cardVolumeOfDocumentId("primary:Download/Hazel"))
        assertNull(SdCards.cardVolumeOfDocumentId("PRIMARY:Download"))
        assertEquals("0000-1111", SdCards.cardVolumeOfDocumentId("0000-1111:Hazel/Video"))
    }

    @Test
    fun `only a volume's top counts as its root`() {
        assertTrue(SdCards.isVolumeRoot("1A2B-3C4D:"))
        assertFalse(SdCards.isVolumeRoot("1A2B-3C4D:Movies"))
        assertFalse(SdCards.isVolumeRoot("1A2B-3C4D"))
    }

    @Test
    fun `kind folders match the built-in layout`() {
        assertEquals("Video", SdCards.kindFolderName(isVideo = true))
        assertEquals("Audio", SdCards.kindFolderName(isVideo = false))
    }

    @Test
    fun `a folder made inside a picked tree is told apart from the tree`() {
        assertTrue(MediaStoreHelper.isDocumentInTree(listOf("tree", "1A2B-3C4D:", "document", "1A2B-3C4D:Hazel/Video")))
        assertFalse(MediaStoreHelper.isDocumentInTree(listOf("tree", "1A2B-3C4D:")))
        assertFalse(MediaStoreHelper.isDocumentInTree(listOf("document", "1A2B-3C4D:Hazel")))
        assertFalse(MediaStoreHelper.isDocumentInTree(emptyList()))
    }

    // ── Labels ──

    @Test
    fun `labels name the card when it is known`() {
        val names = mapOf("1A2B-3C4D" to "SanDisk SD card")
        assertEquals("SanDisk SD card/Hazel/Video", MediaStoreHelper.describeDocumentId("1A2B-3C4D:Hazel/Video", names::get))
        assertEquals("SanDisk SD card", MediaStoreHelper.describeDocumentId("1A2B-3C4D:", names::get))
    }

    @Test
    fun `labels fall back to the volume id for a card that is out`() {
        assertEquals("9999-0000/Music", MediaStoreHelper.describeDocumentId("9999-0000:Music") { null })
        assertEquals("9999-0000", MediaStoreHelper.describeDocumentId("9999-0000:") { null })
    }

    @Test
    fun `internal labels are unchanged`() {
        assertEquals("Movies/Clips", MediaStoreHelper.describeDocumentId("primary:Movies/Clips") { "never" })
        assertEquals("Internal storage", MediaStoreHelper.describeDocumentId("primary:") { "never" })
        assertEquals("odd-id", MediaStoreHelper.describeDocumentId("odd-id") { "never" })
    }

    // ── Working folders ──

    @Test
    fun `a download for a card works on the card`() {
        val root = Files.createTempDirectory("sd").toFile()
        val internal = File(root, "internal").apply { mkdirs() }
        val card = File(root, "card/Android/data/com.hazel.android/files").apply { mkdirs() }
        try {
            assertEquals(File(card, "Hazel"), SdCards.chooseWorkRoot(card, internal))
            assertTrue(File(card, "Hazel").isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `no card folder means working internally`() {
        val internal = Files.createTempDirectory("internal").toFile()
        try {
            assertEquals(internal, SdCards.chooseWorkRoot(null, internal))
        } finally {
            internal.deleteRecursively()
        }
    }

    @Test
    fun `a card that cannot hold the folder falls back to internal`() {
        val root = Files.createTempDirectory("sd").toFile()
        val internal = File(root, "internal").apply { mkdirs() }
        // A file where the folder should go stands in for a card that refuses it.
        val card = File(root, "card").apply { mkdirs() }
        File(card, "Hazel").writeText("in the way")
        try {
            assertEquals(internal, SdCards.chooseWorkRoot(card, internal))
            // A card that was pulled out leaves a path that cannot be made at all.
            assertEquals(internal, SdCards.chooseWorkRoot(File(root, "gone/\u0000"), internal))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `cleanup covers internal and every card that has working files`() {
        val root = Files.createTempDirectory("roots").toFile()
        val internal = File(root, "internal").apply { mkdirs() }
        val usedCard = File(root, "cardA").apply { File(this, "Hazel").mkdirs() }
        val unusedCard = File(root, "cardB").apply { mkdirs() }
        try {
            val roots = SdCards.workRootsAmong(internal, listOf(usedCard, unusedCard))
            assertEquals(listOf(internal, File(usedCard, "Hazel")), roots)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a link keeps the same working folder name on every storage`() {
        val url = "https://example.com/watch?v=abc"
        assertEquals(workDirName(url), workDirName(url))
        assertTrue(workDirName(url).startsWith("dl_"))
        assertFalse(workDirName(url) == workDirName("$url&t=1"))
    }

    // ── Queue ──

    @Test
    fun `a queued download keeps its card folder across restarts`() {
        val cardFolder = "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3A/document/1A2B-3C4D%3AHazel%2FVideo"
        val queued = QueuedDownload(
            url = "https://example.com/watch?v=1",
            title = "t", author = "a", thumbnail = "", durationSeconds = 1,
            formatId = "18", selector = "18", formatLabel = "360p", ext = "mp4",
            hasVideo = true, hasAudio = true, isGeneric = false, fileSizeBytes = 1L,
            mergeAudioSelector = null, mergeAudioSizeBytes = 0L,
            treeUri = cardFolder,
            requiresSignIn = false, audioLanguage = null,
            options = DownloadOptions(), paused = true
        )
        val decoded = DownloadQueueRepository.decodeItem(DownloadQueueRepository.encodeItem(queued))
        assertEquals(cardFolder, decoded?.treeUri)
        assertEquals(true, decoded?.paused)
    }

    // ── Saved somewhere else ──

    @Test
    fun `a batch that missed the card is reported once with every title`() {
        var notice: SaveFallback? = null
        for (title in listOf("One", "Two", "Three")) {
            notice = SaveFallback.adding(notice, "SanDisk SD card/Hazel/Video", "Download/Hazel/Video", title)
        }
        assertEquals(listOf("One", "Two", "Three"), notice?.titles)
    }

    @Test
    fun `a different folder starts a new notice`() {
        val first = SaveFallback.adding(null, "Card A/Hazel/Audio", "Download/Hazel/Audio", "One")
        val second = SaveFallback.adding(first, "Card B/Hazel/Audio", "Download/Hazel/Audio", "Two")
        assertEquals(listOf("Two"), second.titles)
        assertEquals("Card B/Hazel/Audio", second.wanted)
    }
}
