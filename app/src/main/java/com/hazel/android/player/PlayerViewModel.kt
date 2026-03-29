package com.hazel.android.player

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.hazel.android.data.SettingsRepository
import com.hazel.android.util.StoragePaths
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AudioTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,       // ms
    val sizeBytes: Long,
    val albumArtUri: Uri?,
    val folderPath: String,
    val filePath: String      // full path for per-track artwork extraction
)

data class PlayerUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isLoading: Boolean = true,
    val showNowPlaying: Boolean = false,
    val isSeeking: Boolean = false,
    val currentFolder: String? = null,        // null = all sources
    val availableFolders: List<String> = emptyList()
)

class PlayerViewModel : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state = _state.asStateFlow()

    private var controller: MediaController? = null
    private var allTracks: List<AudioTrack> = emptyList()
    private var appContext: Context? = null
    private var contentObserver: ContentObserver? = null
    private var debounceJob: Job? = null
    private var playJob: Job? = null
    private var loadedTrackIds: List<Long> = emptyList()  // track IDs currently in controller

    private val hazelPath: String
        get() = StoragePaths.finalDownloads.absolutePath

    /** Connect to PlaybackService */
    fun connectToService(context: Context) {
        // If controller exists but is no longer connected, clear it to allow reconnect
        if (controller != null) {
            if (!controller!!.isConnected) {
                controller = null
                loadedTrackIds = emptyList()
            } else {
                return  // already connected
            }
        }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                setupPlayerListener()
                startPositionUpdater()
                syncStateFromController()
            } catch (_: Exception) { }
        }, MoreExecutors.directExecutor())
    }

    /** Scan ALL audio on device using MediaStore */
    fun loadTracks(context: Context) {
        // Store application context (leak-safe) and register observer on first call
        if (appContext == null) {
            appContext = context.applicationContext
            registerContentObserver(context.applicationContext)
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val tracks = withContext(Dispatchers.IO) { queryMediaStore(context) }
            allTracks = tracks

            val folders = tracks.map { it.folderPath }.distinct().sorted()

            // Load persisted folder from DataStore
            val savedFolder = SettingsRepository.getPlayerFolder(context).first()

            // Use saved folder if it has tracks, else Hazel default, else all
            val defaultFolder = when {
                savedFolder != null && tracks.any { it.folderPath == savedFolder } -> savedFolder
                tracks.any { it.folderPath == hazelPath } -> hazelPath
                else -> null
            }
            val filtered = if (defaultFolder != null) tracks.filter { it.folderPath == defaultFolder } else tracks

            _state.value = _state.value.copy(
                tracks = filtered,
                availableFolders = folders,
                currentFolder = defaultFolder,
                isLoading = false
            )
        }
    }

    /** Background re-scan — preserves current folder selection, debounced */
    fun refreshTracks() {
        val ctx = appContext ?: return
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500) // debounce rapid notifications
            val tracks = withContext(Dispatchers.IO) { queryMediaStore(ctx) }
            allTracks = tracks
            val folders = tracks.map { it.folderPath }.distinct().sorted()
            val currentFolder = _state.value.currentFolder
            val filtered = if (currentFolder != null) tracks.filter { it.folderPath == currentFolder } else tracks
            _state.value = _state.value.copy(
                tracks = filtered,
                availableFolders = folders,
                isLoading = false
            )
        }
    }

    private fun registerContentObserver(context: Context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshTracks()
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        contentObserver = observer
    }

    fun setFolder(folderPath: String?, context: Context? = null) {
        val filtered = if (folderPath == null) allTracks
        else allTracks.filter { it.folderPath == folderPath }
        _state.value = _state.value.copy(tracks = filtered, currentFolder = folderPath)

        // Persist to DataStore
        if (context != null) {
            viewModelScope.launch {
                SettingsRepository.setPlayerFolder(context, folderPath)
            }
        }
    }

    /** Add a custom folder from SAF picker to the folder list */
    fun addCustomFolder(path: String) {
        val current = _state.value.availableFolders
        if (path !in current) {
            _state.value = _state.value.copy(availableFolders = (current + path).sorted())
        }
    }

    fun playTrack(index: Int) {
        val tracks = _state.value.tracks
        if (index !in tracks.indices) return

        // Auto-reconnect if controller was released (e.g., after notification dismiss)
        val ctrl = controller
        if (ctrl == null || !ctrl.isConnected) {
            controller = null
            loadedTrackIds = emptyList()
            val ctx = appContext ?: return
            connectToService(ctx)
            // Retry after connection establishes
            viewModelScope.launch {
                delay(500)
                playTrack(index)
            }
            return
        }

        _state.value = _state.value.copy(showNowPlaying = true, currentIndex = index)

        // Check if the controller already has the same track list loaded
        val currentTrackIds = tracks.map { it.id }
        if (loadedTrackIds == currentTrackIds && ctrl.mediaItemCount == tracks.size) {
            // Same playlist — just seek to the new track instantly
            ctrl.seekToDefaultPosition(index)
            if (ctrl.playbackState == Player.STATE_IDLE || ctrl.playbackState == Player.STATE_ENDED) {
                ctrl.prepare()
            }
            ctrl.play()
        } else {
            // New playlist — build MediaItems synchronously (no IO, instant)
            val mediaItems = tracks.map { track ->
                MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .setArtworkUri(track.albumArtUri)
                            .build()
                    )
                    .build()
            }
            ctrl.setMediaItems(mediaItems, index, 0L)
            ctrl.prepare()
            ctrl.play()
            loadedTrackIds = currentTrackIds
        }

        // Update notification artwork for the current track
        updateNotificationArtwork(index)
    }

    /**
     * Extracts embedded artwork from the audio file and updates the MediaItem
     * so the notification shows the correct thumbnail. Called from both
     * playTrack() and onMediaItemTransition() (notification next/prev).
     */
    private fun updateNotificationArtwork(index: Int) {
        val tracks = _state.value.tracks
        if (index !in tracks.indices) return
        val ctrl = controller ?: return

        playJob?.cancel()
        playJob = viewModelScope.launch {
            val track = tracks[index]
            val artBytes = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(track.filePath)
                        retriever.embeddedPicture
                    } finally { retriever.release() }
                } catch (_: Exception) { null }
            }
            if (artBytes != null && ctrl.isConnected && ctrl.currentMediaItemIndex == index) {
                val updated = ctrl.currentMediaItem?.buildUpon()
                    ?.setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build()
                    )?.build()
                if (updated != null) {
                    ctrl.replaceMediaItem(index, updated)
                }
            }
        }
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            // Ensure controller is prepared before playing — fixes silent failure
            // when controller is in idle/stopped state (e.g., after service reconnect)
            if (ctrl.playbackState == Player.STATE_IDLE || ctrl.playbackState == Player.STATE_ENDED) {
                ctrl.prepare()
            }
            ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        // Immediately update UI position; keep isSeeking=true so the position
        // updater doesn't overwrite with stale controller position before seek completes
        _state.value = _state.value.copy(position = positionMs, isSeeking = true)
        controller?.seekTo(positionMs)
        // Release the guard after the controller has had time to seek
        viewModelScope.launch {
            delay(500)
            _state.value = _state.value.copy(isSeeking = false)
        }
    }

    /** Called by UI when user starts/stops dragging the seek bar */
    fun setSeekingState(seeking: Boolean) {
        _state.value = _state.value.copy(isSeeking = seeking)
    }

    fun skipNext() {
        val ctrl = controller ?: return
        ctrl.seekToNext()
    }

    fun skipPrevious() {
        val ctrl = controller ?: return
        ctrl.seekToPrevious()
    }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun toggleRepeat() {
        val ctrl = controller ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun showNowPlaying() {
        _state.value = _state.value.copy(showNowPlaying = true)
    }

    fun hideNowPlaying() {
        _state.value = _state.value.copy(showNowPlaying = false)
    }

    private fun syncStateFromController() {
        val ctrl = controller ?: return
        if (ctrl.mediaItemCount > 0) {
            _state.value = _state.value.copy(
                isPlaying = ctrl.isPlaying,
                currentIndex = ctrl.currentMediaItemIndex,
                duration = ctrl.duration.coerceAtLeast(0L),
                position = ctrl.currentPosition,
                shuffleEnabled = ctrl.shuffleModeEnabled,
                repeatMode = ctrl.repeatMode
            )
        }
    }

    private fun setupPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val ctrl = controller ?: return
                val newIndex = ctrl.currentMediaItemIndex
                _state.value = _state.value.copy(
                    currentIndex = newIndex,
                    duration = ctrl.duration.coerceAtLeast(0L),
                    position = 0L
                )
                // Update notification artwork for the new track
                updateNotificationArtwork(newIndex)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val ctrl = controller ?: return
                    _state.value = _state.value.copy(
                        duration = ctrl.duration.coerceAtLeast(0L),
                        currentIndex = ctrl.currentMediaItemIndex
                    )
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.value = _state.value.copy(shuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _state.value = _state.value.copy(repeatMode = repeatMode)
            }
        })
    }

    private fun startPositionUpdater() {
        viewModelScope.launch {
            while (isActive) {
                val ctrl = controller
                // Guard: don't overwrite position while user is dragging the seek bar
                if (ctrl != null && ctrl.isPlaying && !_state.value.isSeeking) {
                    _state.value = _state.value.copy(position = ctrl.currentPosition)
                }
                delay(100)
            }
        }
    }

    private fun queryMediaStore(context: Context): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA     // full path for folder grouping
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val filePath = cursor.getString(dataCol) ?: continue
                val folderPath = File(filePath).parent ?: continue

                val albumId = cursor.getLong(albumIdCol)
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), albumId
                )

                tracks.add(
                    AudioTrack(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown Artist",
                        album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Unknown Album",
                        duration = cursor.getLong(durationCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        albumArtUri = albumArtUri,
                        folderPath = folderPath,
                        filePath = filePath
                    )
                )
            }
        }
        return tracks
    }

    override fun onCleared() {
        // Unregister ContentObserver to prevent leaks
        appContext?.let { ctx ->
            contentObserver?.let { observer ->
                ctx.contentResolver.unregisterContentObserver(observer)
            }
        }
        contentObserver = null
        debounceJob?.cancel()
        controller?.release()
        controller = null
        super.onCleared()
    }
}
