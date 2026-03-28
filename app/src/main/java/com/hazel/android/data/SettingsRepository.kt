package com.hazel.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hazel_settings")

object SettingsRepository {

    private val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
    private val DOWNLOAD_PATH_KEY = stringPreferencesKey("download_path")
    private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    private val FOLDER_PLAYLIST_KEY = booleanPreferencesKey("folder_playlist")
    private val FOLDER_MULTI_KEY = booleanPreferencesKey("folder_multi_links")
    private val FOLDER_BULK_KEY = booleanPreferencesKey("folder_bulk")

    // Theme
    fun isDarkTheme(context: Context): Flow<Boolean?> {
        return context.dataStore.data.map { prefs -> prefs[DARK_THEME_KEY] }
    }

    suspend fun setDarkTheme(context: Context, isDark: Boolean) {
        context.dataStore.edit { prefs -> prefs[DARK_THEME_KEY] = isDark }
    }

    // Accent color
    fun getAccentColor(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs -> prefs[ACCENT_COLOR_KEY] ?: "White" }
    }

    suspend fun setAccentColor(context: Context, name: String) {
        context.dataStore.edit { prefs -> prefs[ACCENT_COLOR_KEY] = name }
    }

    // Download location
    fun getDownloadPath(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs -> prefs[DOWNLOAD_PATH_KEY] ?: "Hazel" }
    }

    suspend fun setDownloadPath(context: Context, path: String) {
        context.dataStore.edit { prefs -> prefs[DOWNLOAD_PATH_KEY] = path }
    }

    // Separate folder toggles — Playlist ON by default, others OFF
    fun getFolderPlaylist(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs -> prefs[FOLDER_PLAYLIST_KEY] ?: true }
    }
    suspend fun setFolderPlaylist(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[FOLDER_PLAYLIST_KEY] = enabled }
    }

    fun getFolderMultiLinks(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs -> prefs[FOLDER_MULTI_KEY] ?: false }
    }
    suspend fun setFolderMultiLinks(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[FOLDER_MULTI_KEY] = enabled }
    }

    fun getFolderBulk(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs -> prefs[FOLDER_BULK_KEY] ?: false }
    }
    suspend fun setFolderBulk(context: Context, enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[FOLDER_BULK_KEY] = enabled }
    }

    // Quality persistence
    private val VIDEO_QUALITY_KEY = stringPreferencesKey("video_quality")
    private val AUDIO_FORMAT_KEY = stringPreferencesKey("audio_format")

    fun getVideoQuality(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs -> prefs[VIDEO_QUALITY_KEY] ?: "1080p" }
    }
    suspend fun setVideoQuality(context: Context, quality: String) {
        context.dataStore.edit { prefs -> prefs[VIDEO_QUALITY_KEY] = quality }
    }

    fun getAudioFormat(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs -> prefs[AUDIO_FORMAT_KEY] ?: "MP3 · 320kbps" }
    }
    suspend fun setAudioFormat(context: Context, format: String) {
        context.dataStore.edit { prefs -> prefs[AUDIO_FORMAT_KEY] = format }
    }

    // Player music directory persistence
    private val PLAYER_FOLDER_KEY = stringPreferencesKey("player_folder")

    fun getPlayerFolder(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs -> prefs[PLAYER_FOLDER_KEY] }
    }
    suspend fun setPlayerFolder(context: Context, folder: String?) {
        context.dataStore.edit { prefs ->
            if (folder == null) prefs.remove(PLAYER_FOLDER_KEY)
            else prefs[PLAYER_FOLDER_KEY] = folder
        }
    }
}
