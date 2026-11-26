package com.hazel.android.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hazel.android.util.StoragePaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SaveDirsTest {



    @Test
    fun eachKindKeepsItsOwnFolder() {
        val dirs = SaveDirs(
            audio = SaveDir("content://a", "Music"),
            video = SaveDir("content://v", ""),
            gallery = SaveDir("content://g", "Pictures/Hazel")
        )
        assertEquals("Pictures/Hazel", dirs.labelOf(SaveKind.GALLERY))
        assertEquals("content://g", dirs.of(SaveKind.GALLERY).uri)
        assertEquals(dirs.audio, dirs.of(isVideo = false))
        assertEquals(dirs.video, dirs.of(isVideo = true))
        assertEquals("Music", dirs.labelOf(isVideo = false))
        assertEquals("content://v", dirs.labelOf(isVideo = true))
        assertEquals("Download/Hazel/Video", SaveDirs().labelOf(isVideo = true))
        assertEquals("Download/Hazel/Audio", SaveDirs().labelOf(isVideo = false))
    }

    @Test
    fun galleryIgnoresTheFolderOlderVersionsKeptForBoth() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("download_tree_uri") to "content://old",
            stringPreferencesKey("download_tree_label") to "Old"
        )
        val dirs = SettingsRepository.saveDirsOf(prefs)
        assertEquals("content://old", dirs.audio.uri)
        assertEquals("content://old", dirs.video.uri)
        assertEquals(SaveDir(), dirs.gallery)

        prefs[stringPreferencesKey("download_tree_uri_gallery")] = "content://g"
        prefs[stringPreferencesKey("download_tree_label_gallery")] = "Pics"
        assertEquals(SaveDir("content://g", "Pics"), SettingsRepository.saveDirsOf(prefs).gallery)
    }



    @Test
    fun oldBackupWithoutGalleryKeepsThePickedGalleryFolder() {
        val old = mutablePreferencesOf(stringPreferencesKey("speed_limit") to "2M")
        val contents = BackupRepository.parse(BackupRepository.export(old, BackupCategory.entries.toSet()).toString())
        val target = mutablePreferencesOf(
            stringPreferencesKey("download_tree_uri_gallery") to "content://g",
            stringPreferencesKey("download_tree_label_gallery") to "Pics"
        )
        BackupRepository.apply(target, contents, BackupCategory.entries.toSet())
        assertEquals("2M", target[stringPreferencesKey("speed_limit")])
        assertEquals(SaveDir("content://g", "Pics"), SettingsRepository.saveDirsOf(target).gallery)
    }
}
