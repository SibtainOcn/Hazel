package com.hazel.android.ui.player

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.components.player.OfflineMedia
import com.hazel.android.ui.components.player.OfflinePlayer
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.HazelTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hazel in the system's "Open with" list for audio and video files: sound plays in the music
 * sheet over the app it came from, a video full screen. Closing the player closes this too.
 */
class PlayerActivity : ComponentActivity() {

    private var media by mutableStateOf<OfflineMedia?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (!open(intent)) return

        setContent {
            val dark by remember(this) { SettingsRepository.isDarkTheme(this) }.collectAsState(initial = null)
            val accent by remember(this) { SettingsRepository.getAccentColor(this) }.collectAsState(initial = null)
            val glass by remember(this) { SettingsRepository.isGlassEnabled(this) }.collectAsState(initial = null)
            val style by remember(this) { SettingsRepository.getGlassStyle(this) }.collectAsState(initial = null)
            val current = media
            // Drawn once the saved look is known, so it never shows in the wrong colours first.
            if (accent == null || glass == null || style == null || current == null) return@setContent
            HazelTheme(
                darkTheme = dark ?: true,
                accentName = accent!!,
                glass = glass!!,
                glassStyle = GlassStyle.fromKey(style!!)
            ) {
                key(current.uri) { OfflinePlayer(current, onClose = ::close) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        open(intent)
    }

    /** Takes the file from [intent]; false, with the screen closed, when there is none to play. */
    private fun open(intent: Intent?): Boolean {
        val uri = intent?.data
        val type = intent?.type ?: uri?.let { runCatching { contentResolver.getType(it) }.getOrNull() }.orEmpty()
        val video = type.startsWith("video/")
        if (uri == null || (!video && !type.startsWith("audio/"))) {
            Toast.makeText(this, R.string.media_open_no_app_here, Toast.LENGTH_SHORT).show()
            close()
            return false
        }
        val file = OfflineMedia(
            uri = uri.toString(),
            title = "",
            artist = "",
            thumbnail = null,
            isVideo = video,
            mimeType = type
        )
        media = file
        // The name shown until the file's own title tag is read.
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { displayName(uri) }
            if (media?.uri == file.uri && name.isNotBlank()) media = file.copy(title = name)
        }
        return true
    }

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty().substringBeforeLast('.') else ""
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringBeforeLast('.').orEmpty()

    private fun close() {
        finishAndRemoveTask()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
