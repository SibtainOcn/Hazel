package com.hazel.android.ui.screens.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.hazel.android.player.PlayerViewModel
import com.hazel.android.ui.components.HazelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val state by playerViewModel.state.collectAsState()

    // Use centralized PermissionHelper — no duplicate permission handling
    LaunchedEffect(Unit) {
        playerViewModel.connectToService(context)

        if (com.hazel.android.util.PermissionHelper.hasAllStoragePermissions(context)) {
            playerViewModel.loadTracks(context)
        } else {
            com.hazel.android.util.PermissionHelper.ensureStoragePermission(
                context,
                onReady = { playerViewModel.loadTracks(context) }
            )
        }
    }

    // Lifecycle-aware refresh: re-scan when returning to app
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    playerViewModel.refreshTracks()
                }
            }
        )
    }

    // SAF folder picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Convert SAF tree URI to a real path for display
            val docId = DocumentsContract.getTreeDocumentId(uri)
            // docId format: "primary:Download/Hazel" → extract path
            val path = if (docId.startsWith("primary:")) {
                "/storage/emulated/0/" + docId.substringAfter("primary:")
            } else docId
            playerViewModel.addCustomFolder(path)
            playerViewModel.setFolder(path, context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Track List Screen ──
        TrackListScreen(
            tracks = state.tracks,
            currentIndex = state.currentIndex,
            isPlaying = state.isPlaying,
            isLoading = state.isLoading,
            currentFolder = state.currentFolder,
            availableFolders = state.availableFolders,
            onTrackClick = { playerViewModel.playTrack(it) },
            onFolderSelected = { playerViewModel.setFolder(it, context) },
            onCustomFolderClick = { folderPickerLauncher.launch(null) }
        )

        // ── Mini Player Bar (anchored at bottom) ──
        if (state.currentIndex >= 0 && state.currentIndex < state.tracks.size && !state.showNowPlaying) {
            val track = state.tracks[state.currentIndex]
            MiniPlayer(
                title = track.title,
                artist = track.artist,
                filePath = track.filePath,
                albumArtUri = track.albumArtUri,
                isPlaying = state.isPlaying,
                progress = if (state.duration > 0) state.position.toFloat() / state.duration.toFloat() else 0f,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onClick = { playerViewModel.showNowPlaying() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Intercept system back button when Now Playing is open
        BackHandler(enabled = state.showNowPlaying && state.currentIndex >= 0) {
            playerViewModel.hideNowPlaying()
        }

        // ── Full-Screen Now Playing (Spotify-style) ──
        AnimatedVisibility(
            visible = state.showNowPlaying && state.currentIndex >= 0 && state.currentIndex < state.tracks.size,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            val track = state.tracks.getOrNull(state.currentIndex) ?: return@AnimatedVisibility
            NowPlayingScreen(
                title = track.title,
                artist = track.artist,
                album = track.album,
                filePath = track.filePath,
                albumArtUri = track.albumArtUri,
                isPlaying = state.isPlaying,
                position = state.position,
                duration = state.duration,
                isSeeking = state.isSeeking,
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode,
                onDismiss = { playerViewModel.hideNowPlaying() },
                onPlayPause = { playerViewModel.togglePlayPause() },
                onSkipNext = { playerViewModel.skipNext() },
                onSkipPrevious = { playerViewModel.skipPrevious() },
                onSeekStart = { playerViewModel.setSeekingState(true) },
                onSeekEnd = { playerViewModel.seekTo(it) },
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onToggleRepeat = { playerViewModel.toggleRepeat() }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TRACK LIST (VLC-style)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun TrackListScreen(
    tracks: List<com.hazel.android.player.AudioTrack>,
    currentIndex: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    currentFolder: String?,
    availableFolders: List<String>,
    onTrackClick: (Int) -> Unit,
    onFolderSelected: (String?) -> Unit,
    onCustomFolderClick: () -> Unit
) {
    var showFolderMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Filter tracks based on search query
    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else {
            val q = searchQuery.lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
    }

    // Map filtered indices back to original indices
    val filteredIndices = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks.indices.toList()
        else {
            val q = searchQuery.lowercase()
            tracks.indices.filter {
                tracks[it].title.lowercase().contains(q) || tracks[it].artist.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearching) {
                val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

                // Auto-focus and open keyboard when search expands
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                // Back press closes search bar
                BackHandler { searchQuery = ""; isSearching = false }

                // Expanded search bar
                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search music...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearching = false
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Close search",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                )
            } else {
                Text(
                    "Music Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )

                // Search icon button
                IconButton(
                    onClick = { isSearching = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // "Change Directory" chip button
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .clickable { showFolderMenu = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Change Directory",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }

                    // Redesigned dropdown — rounded, clean
                    MaterialTheme(
                        shapes = MaterialTheme.shapes.copy(
                            extraSmall = RoundedCornerShape(16.dp)
                        )
                    ) {
                        DropdownMenu(
                            expanded = showFolderMenu,
                            onDismissRequest = { showFolderMenu = false },
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainer,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "All Music",
                                        fontWeight = if (currentFolder == null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentFolder == null) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = { onFolderSelected(null); showFolderMenu = false }
                            )
                            availableFolders.forEach { folder ->
                                val displayName = folder.substringAfterLast("/")
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            displayName,
                                            fontWeight = if (folder == currentFolder) FontWeight.Bold else FontWeight.Normal,
                                            color = if (folder == currentFolder) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = { onFolderSelected(folder); showFolderMenu = false }
                                )
                            }
                            // Divider before Custom
                            androidx.compose.material3.HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Custom",
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showFolderMenu = false
                                    onCustomFolderClick()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Loading state — reusable M3 spinner
        HazelLoader(visible = isLoading)

        // Empty state
        if (filteredTracks.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (searchQuery.isNotBlank()) Icons.Filled.Search else Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank()) "No results for \"$searchQuery\"" else "No audio files found",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (searchQuery.isBlank()) {
                        Text(
                            "Download some music to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        } else {
            // Track count chip
            if (!isLoading && filteredTracks.isNotEmpty()) {
                Text(
                    if (searchQuery.isNotBlank()) "${filteredTracks.size} results"
                    else "${filteredTracks.size} tracks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)
            ) {
                itemsIndexed(
                    items = filteredTracks,
                    key = { _, track -> track.id },
                    contentType = { _, _ -> "track" }
                ) { filteredIndex, track ->
                    val originalIndex = filteredIndices[filteredIndex]
                    TrackListItem(
                        title = track.title,
                        artist = track.artist,
                        duration = formatDuration(track.duration),
                        filePath = track.filePath,
                        albumArtUri = track.albumArtUri,
                        isCurrent = originalIndex == currentIndex,
                        isPlaying = originalIndex == currentIndex && isPlaying,
                        onClick = { onTrackClick(originalIndex) }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TRACK LIST ITEM (Spotify-style with thumbnail)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun TrackListItem(
    title: String,
    artist: String,
    duration: String,
    filePath: String,
    albumArtUri: android.net.Uri?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon thumbnail — no per-track art loading in library
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent) {
                // Overlay play indicator for current track
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.hazel.android.R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$artist  ·  $duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  MINI PLAYER BAR
// ═══════════════════════════════════════════════════════════════
@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    filePath: String,
    albumArtUri: android.net.Uri?,
    isPlaying: Boolean,
    progress: Float,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val embeddedArt by rememberEmbeddedArtwork(filePath)
    Column(modifier = modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Progress bar (thin line at top)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini album art
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val miniArtModel = embeddedArt ?: albumArtUri
                if (miniArtModel != null) {
                    AsyncImage(
                        model = miniArtModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    artist, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  FULL-SCREEN NOW PLAYING (Spotify-identical)
// ═══════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingScreen(
    title: String,
    artist: String,
    album: String,
    filePath: String,
    albumArtUri: android.net.Uri?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isSeeking: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    // Local drag state — the fraction the user is dragging to
    var localSeekFraction by remember { mutableFloatStateOf(0f) }

    // Per-track embedded artwork
    val embeddedArt by rememberEmbeddedArtwork(filePath)
    val artModel = embeddedArt ?: albumArtUri

    // Accent color from theme
    val accentColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Theme adaptive colors
    val bgColor = if (isDark) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)
    val textPrimary = if (isDark) Color.White else Color(0xFF1A1A1A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF1A1A1A).copy(alpha = 0.6f)
    val controlColor = if (isDark) Color.White else Color(0xFF1A1A1A)
    val controlDim = controlColor.copy(alpha = 0.5f)
    val sliderInactive = if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFF1A1A1A).copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            // Consume ALL touches — prevents taps from leaking through
            // to the TrackListScreen underneath this overlay
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* no-op: just block passthrough */ }
    ) {
        // Background gradient overlay from album art (dark theme only)
        if (isDark && artModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artModel)
                    .size(50, 50)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.15f
            )
            // Dark overlay gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                )
        ) {
            // ── Top Bar — dismiss chevron, bigger + shifted up ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = controlColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.15f))

            // ── Album Art (large, centered) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFE8E8E8)),
                    contentAlignment = Alignment.Center
                ) {
                    if (artModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artModel)
                                .build(),
                            contentDescription = "Album art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = controlColor.copy(alpha = 0.15f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // ── Track Info ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "title"
                ) { currentTitle ->
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Seek Bar — Material3 Slider, industry-standard ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            ) {
                // Compute the raw progress fraction from ViewModel
                val rawFraction = if (duration > 0) position.toFloat() / duration.toFloat() else 0f

                // Smooth animation for auto-progress (only when not seeking)
                val animatedFraction by animateFloatAsState(
                    targetValue = rawFraction,
                    animationSpec = tween(
                        durationMillis = if (isSeeking) 0 else 100,
                        easing = LinearEasing
                    ),
                    label = "seekProgress"
                )

                // Display value: local drag position when seeking, animated progress otherwise
                val displayFraction = if (isSeeking) localSeekFraction else animatedFraction

                // Override minimum touch target so the Slider
                // doesn't expand beyond the visible 24.dp height
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides 0.dp
                ) {
                    Slider(
                        value = displayFraction.coerceIn(0f, 1f),
                        onValueChange = { newValue ->
                            if (!isSeeking) {
                                // First touch — notify ViewModel to stop updating position
                                onSeekStart()
                            }
                            localSeekFraction = newValue
                        },
                        onValueChangeFinished = {
                            // Commit the seek to the actual media player
                            onSeekEnd((localSeekFraction * duration).toLong())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = sliderInactive
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = sliderInactive
                                ),
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(if (isSeeking) (localSeekFraction * duration).toLong() else position),
                        style = MaterialTheme.typography.labelSmall,
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Playback Controls ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) accentColor else controlDim,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous
                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = controlColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause (big circle)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next
                IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = controlColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(onClick = onToggleRepeat, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> controlDim
                            else -> accentColor
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))
        }
    }
}

// ─── Utility ───
private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * Lazily extracts embedded artwork from an audio file using MediaMetadataRetriever.
 * Returns ByteArray on IO thread, cached per filePath via produceState.
 * Falls back to null (caller should use albumArtUri or placeholder).
 */
@Composable
private fun rememberEmbeddedArtwork(filePath: String) = produceState<ByteArray?>(null, filePath) {
    value = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                retriever.embeddedPicture  // returns ByteArray? or null
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {
            null  // graceful fallback — file missing, corrupt, or no embedded art
        }
    }
}
