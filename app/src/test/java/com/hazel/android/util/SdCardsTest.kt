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
    fun `android 10 and newer let the volume build the picker`() {
        for (sdk in 29..36) assertEquals(SdCards.PickerRoute.VOLUME_INTENT, SdCards.pickerRoute(sdk), "sdk $sdk")
    }

    // ── Folder ids ──



    @Test
    fun `internal storage is never taken for a card`() {
        assertNull(SdCards.cardVolumeOfDocumentId("primary:Download/Hazel"))
        assertNull(SdCards.cardVolumeOfDocumentId("PRIMARY:Download"))
        assertEquals("0000-1111", SdCards.cardVolumeOfDocumentId("0000-1111:Hazel/Video"))
    }





    @Test
    fun `a folder made inside a picked tree is told apart from the tree`() {
        assertTrue(MediaStoreHelper.isDocumentInTree(listOf("tree", "1A2B-3C4D:", "document", "1A2B-3C4D:Hazel/Video")))
        assertFalse(MediaStoreHelper.isDocumentInTree(listOf("tree", "1A2B-3C4D:")))
        assertFalse(MediaStoreHelper.isDocumentInTree(listOf("document", "1A2B-3C4D:Hazel")))
        assertFalse(MediaStoreHelper.isDocumentInTree(emptyList()))
    }

    // ── Labels ──







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
    fun `document ids map to paths only when they are paths`() {
        val root = "/storage/emulated/0"
        assertEquals("/storage/emulated/0/Download/a/b.mp4", MediaStoreHelper.documentIdPath("primary:Download/a/b.mp4", root))
        assertEquals("/storage/1A2B-3C4D/Movies/b.mp4", MediaStoreHelper.documentIdPath("1A2B-3C4D:Movies/b.mp4", root))
        assertEquals("/storage/emulated/0/Download/x/b.mp4", MediaStoreHelper.documentIdPath("raw:/storage/emulated/0/Download/x/b.mp4", root))
        assertEquals(null, MediaStoreHelper.documentIdPath("msf:42", root))
        assertEquals(null, MediaStoreHelper.documentIdPath("primary:", root))
        assertEquals(null, MediaStoreHelper.documentIdPath("raw:relative", root))
        assertEquals(null, MediaStoreHelper.documentIdPath("1234", root))
    }
}
