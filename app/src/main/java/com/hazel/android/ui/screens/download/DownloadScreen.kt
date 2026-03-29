package com.hazel.android.ui.screens.download

import android.content.ClipboardManager
import android.content.Intent
import android.net.TrafficStats
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.R
import com.hazel.android.download.DownloadViewModel
import com.hazel.android.download.LogLevel

@Composable
fun DownloadScreen(
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    onNavigateToMultiLinksReview: () -> Unit = {},
    onNavigateToBulkEditor: () -> Unit = {},
    onNavigateToGuide: () -> Unit = {},
    downloadViewModel: DownloadViewModel = viewModel()
) {
    var url by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(true) }
    // Persisted quality — separate for video & audio
    val persistedVideoQuality by com.hazel.android.data.SettingsRepository.getVideoQuality(LocalContext.current).collectAsState(initial = "1080p")
    val persistedAudioFormat by com.hazel.android.data.SettingsRepository.getAudioFormat(LocalContext.current).collectAsState(initial = "MP3 · 320kbps")
    var selectedQuality by remember { mutableStateOf("1080p") }
    // Sync from DataStore once loaded
    LaunchedEffect(persistedVideoQuality, persistedAudioFormat, isVideo) {
        selectedQuality = if (isVideo) persistedVideoQuality else persistedAudioFormat
    }
    var selectedMode by remember { mutableStateOf(downloadViewModel.selectedMode) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var logsExpanded by remember { mutableStateOf(true) }

    // Multi Links state — owned by ViewModel for navigation safety
    val multiLinkUrls = downloadViewModel.multiLinkUrls
    var multiLinkIndex by remember { mutableIntStateOf(multiLinkUrls.size + 1) }
    var urlError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val downloadState by downloadViewModel.state.collectAsState()

    fun getClipboardText(): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        return if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).text?.toString()
        } else null
    }

    // Auto-fill shared URL
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            url = sharedUrl
            onSharedUrlConsumed()
            downloadViewModel.startDownload(context, sharedUrl, true)
        }
    }

    // Auto-scroll page when new logs arrive
    val scrollState = rememberScrollState()
    LaunchedEffect(downloadState.logs.size) {
        if (downloadState.logs.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Smart network speed / status display
    var speedDisplay by remember { mutableStateOf("Waiting...") }
    var lastRxBytes by remember { mutableLongStateOf(TrafficStats.getTotalRxBytes()) }
    var downloadStartTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(downloadState.isDownloading) {
        if (downloadState.isDownloading) {
            lastRxBytes = TrafficStats.getTotalRxBytes()
            downloadStartTime = System.currentTimeMillis()
            // Show "Connecting..." for first 3 seconds
            speedDisplay = "Connecting..."
            while (downloadState.isDownloading) {
                kotlinx.coroutines.delay(1000L)
                val elapsed = System.currentTimeMillis() - downloadStartTime
                val currentRx = TrafficStats.getTotalRxBytes()
                val delta = currentRx - lastRxBytes
                lastRxBytes = currentRx
                speedDisplay = when {
                    elapsed < 3000 -> "Connecting..."
                    delta > 500 * 1024 -> formatNetworkSpeed(delta) // Show speed if > 500 KB/s
                    else -> "Working..."
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Bulk mode: Import File card instead of URL input
            if (selectedMode == "Bulk") {
                // SAF file picker launcher
                val bulkFilePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        val success = downloadViewModel.parseBulkFile(context, uri)
                        if (success && downloadViewModel.bulkUrls.isNotEmpty()) {
                            // Auto-start download
                            downloadViewModel.startDownload(
                                context,
                                downloadViewModel.bulkUrls.toList(),
                                isVideo,
                                "Bulk",
                                selectedQuality
                            )
                        }
                    }
                }

                Surface(
                    onClick = {
                        if (!downloadState.isDownloading) {
                            bulkFilePicker.launch(arrayOf("*/*"))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.UploadFile,
                            contentDescription = "Import file",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (downloadViewModel.bulkFileName.isNotBlank()) downloadViewModel.bulkFileName
                                else "Import File",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (downloadViewModel.bulkUrls.isNotEmpty()) "${downloadViewModel.bulkUrls.size} URLs loaded"
                                else "Pick any file with URLs (one per line)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Bulk file error
                if (downloadViewModel.bulkFileError.isNotBlank()) {
                    Text(
                        downloadViewModel.bulkFileError,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (downloadViewModel.bulkUrls.isNotEmpty()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            } else {
                // URL Input — softer focus colors (for Single, Playlist, Multi-Links)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; urlError = "" },
                    label = {
                        Text(
                            when (selectedMode) {
                                "Multi Links" -> "URL $multiLinkIndex"
                                "Playlist" -> "Playlist URL"
                                else -> "Paste URL here"
                            }
                        )
                    },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !downloadState.isDownloading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        cursorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    trailingIcon = {
                        if (url.isNotBlank()) {
                            IconButton(onClick = { url = "" }) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            IconButton(onClick = {
                                getClipboardText()?.let { url = it }
                            }) {
                                Icon(
                                    Icons.Filled.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                )
            }

            // Multi Links helper buttons
            if (selectedMode == "Multi Links" && !downloadState.isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Next URL chip
                    if (multiLinkUrls.size < 10) {
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                if (url.isNotBlank()) {
                                    val trimmed = url.trim()
                                    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                                        urlError = "Invalid URL format"
                                    } else if (multiLinkUrls.any { it.equals(trimmed, ignoreCase = true) }) {
                                        urlError = "Duplicate URL"
                                    } else {
                                        urlError = ""
                                        multiLinkUrls.add(trimmed)
                                        url = ""
                                        multiLinkIndex = multiLinkUrls.size + 1
                                    }
                                }
                            },
                            label = { Text("Next URL", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = { Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp)) },
                            enabled = url.isNotBlank(),
                            shape = RoundedCornerShape(20.dp)
                        )
                    } else {
                        Text(
                            "Limit reached (10)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    // View All chip
                    if (multiLinkUrls.isNotEmpty()) {
                        androidx.compose.material3.AssistChip(
                            onClick = {
                                if (url.isNotBlank()) {
                                    val trimmed = url.trim()
                                    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                        if (!multiLinkUrls.any { it.equals(trimmed, ignoreCase = true) }) {
                                            multiLinkUrls.add(trimmed)
                                            url = ""
                                            multiLinkIndex = multiLinkUrls.size + 1
                                        }
                                    }
                                }
                                onNavigateToMultiLinksReview()
                            },
                            label = { Text("View All (${multiLinkUrls.size})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                labelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // URL validation error
            if (urlError.isNotEmpty()) {
                Text(
                    urlError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Format Selection — Segmented Pill Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Video pill
                    Surface(
                        onClick = {
                            isVideo = true
                            downloadViewModel.isVideoSelection = true
                            // Auto-reset quality to video default if current is audio-only
                            val audioQualities = listOf("Best", "320kbps", "256kbps", "192kbps", "128kbps")
                            if (selectedQuality in audioQualities) selectedQuality = "1080p"
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(22.dp),
                        color = if (isVideo) MaterialTheme.colorScheme.surface
                                else androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Videocam, null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isVideo) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Video",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isVideo) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                        }
                    }
                    // Audio pill
                    Surface(
                        onClick = {
                            isVideo = false
                            downloadViewModel.isVideoSelection = false
                            // Auto-reset quality to audio default if current is video-only
                            val videoQualities = listOf("4K", "2K", "1080p", "720p", "480p", "360p")
                            if (selectedQuality in videoQualities) selectedQuality = "MP3 · 320kbps"
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(22.dp),
                        color = if (!isVideo) MaterialTheme.colorScheme.surface
                                else androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.MusicNote, null,
                                modifier = Modifier.size(18.dp),
                                tint = if (!isVideo) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Audio",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isVideo) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quality + Mode — Taller Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quality card
                Surface(
                    onClick = { showQualityDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                if (isVideo) "Video Quality" else "Audio Quality",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                selectedQuality,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Mode card
                Surface(
                    onClick = { showModeDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Mode",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                selectedMode,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quality picker dialog
            if (showQualityDialog) {
                val qualityOptions = if (isVideo) {
                    listOf("4K", "2K", "1080p", "720p", "480p", "360p")
                } else {
                    listOf("MP3 · 320kbps", "AAC · 256kbps", "FLAC", "WAV", "Opus")
                }
                val audioNotes = mapOf(
                    "MP3 · 320kbps" to "Universal format, works everywhere",
                    "AAC · 256kbps" to "Better quality, smaller file size",
                    "FLAC" to "Lossless Premium, large file",
                    "WAV" to "Raw uncompressed, large file",
                    "Opus" to "Modern codec, best quality/size ratio"
                )
                var tempQuality by remember(showQualityDialog) { mutableStateOf(selectedQuality) }
                AlertDialog(
                    onDismissRequest = { showQualityDialog = false },
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurface,
                    title = {
                        Text(
                            if (isVideo) "Video Quality" else "Audio Quality",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            qualityOptions.forEach { quality ->
                                val isSelected = tempQuality == quality
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { tempQuality = quality }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            quality,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                        // Show note for audio formats
                                        audioNotes[quality]?.let { note ->
                                            Text(
                                                note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check, null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                selectedQuality = tempQuality
                                showQualityDialog = false
                                // Persist to DataStore
                                kotlinx.coroutines.MainScope().launch {
                                    if (isVideo) com.hazel.android.data.SettingsRepository.setVideoQuality(context, tempQuality)
                                    else com.hazel.android.data.SettingsRepository.setAudioFormat(context, tempQuality)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text("Apply", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showQualityDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                )
            }

            // Mode picker dialog
            if (showModeDialog) {
                val modeOptions = listOf(
                    "Single" to "Download a single URL",
                    "Playlist" to "Download entire playlist",
                    "Multi Links" to "Add up to 10 URLs one by one",
                    "Bulk" to "Import URLs from TXT/CSV file"
                )
                var tempMode by remember(showModeDialog) { mutableStateOf(selectedMode) }
                AlertDialog(
                    onDismissRequest = { showModeDialog = false },
                    modifier = Modifier.clip(RoundedCornerShape(24.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurface,
                    title = {
                        Text(
                            "Download Mode",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    },
                    text = {
                        val scope = rememberCoroutineScope()
                        val folderPlaylist by com.hazel.android.data.SettingsRepository.getFolderPlaylist(context).collectAsState(initial = true)
                        val folderMulti by com.hazel.android.data.SettingsRepository.getFolderMultiLinks(context).collectAsState(initial = false)
                        val folderBulk by com.hazel.android.data.SettingsRepository.getFolderBulk(context).collectAsState(initial = false)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            modeOptions.forEach { (mode, desc) ->
                                val isSelected = tempMode == mode
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(
                                            if (isSelected) Modifier
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                            else Modifier
                                        )
                                        .clickable { tempMode = mode }
                                        .padding(vertical = 12.dp, horizontal = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                mode,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Icon(
                                                Icons.Filled.Check, null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    // Folder toggle — for all except Single
                                    if (mode != "Single") {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.FolderOpen, null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Separate folder",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                modifier = Modifier.weight(1f)
                                            )
                                            val currentToggle = when (mode) {
                                                "Playlist" -> folderPlaylist
                                                "Multi Links" -> folderMulti
                                                "Bulk" -> folderBulk
                                                else -> false
                                            }
                                            Switch(
                                                checked = currentToggle,
                                                onCheckedChange = { checked ->
                                                    scope.launch {
                                                        when (mode) {
                                                            "Playlist" -> com.hazel.android.data.SettingsRepository.setFolderPlaylist(context, checked)
                                                            "Multi Links" -> com.hazel.android.data.SettingsRepository.setFolderMultiLinks(context, checked)
                                                            "Bulk" -> com.hazel.android.data.SettingsRepository.setFolderBulk(context, checked)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .height(20.dp)
                                                    .graphicsLayer(scaleX = 0.7f, scaleY = 0.7f),
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (tempMode != selectedMode) {
                                    // Reset multi-links state when switching modes
                                    multiLinkUrls.clear()
                                    multiLinkIndex = 1
                                    url = ""
                                    downloadViewModel.clearBulkState()
                                }
                                selectedMode = tempMode
                                downloadViewModel.selectedMode = tempMode
                                showModeDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text("Apply", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showModeDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download Button
            Button(
                onClick = {
                    when (selectedMode) {
                        "Multi Links" -> {
                            // Add current URL if not empty, then start batch
                            val allUrls = multiLinkUrls.toMutableList()
                            if (url.isNotBlank()) allUrls.add(url)
                            if (allUrls.isNotEmpty()) {
                                downloadViewModel.startDownload(context, allUrls, isVideo, "Multi Links", selectedQuality)
                            }
                        }
                        "Bulk" -> {
                            // Start download from imported bulk URLs
                            if (downloadViewModel.bulkUrls.isNotEmpty()) {
                                downloadViewModel.startDownload(
                                    context,
                                    downloadViewModel.bulkUrls.toList(),
                                    isVideo,
                                    "Bulk",
                                    selectedQuality
                                )
                            }
                        }
                        else -> {
                            if (url.isNotBlank()) {
                                downloadViewModel.startDownload(context, listOf(url), isVideo, selectedMode, selectedQuality)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = (url.isNotBlank() || multiLinkUrls.isNotEmpty() || (selectedMode == "Bulk" && downloadViewModel.bulkUrls.isNotEmpty())) && !downloadState.isDownloading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (downloadState.isDownloading) {
                    // "Please wait" is fixed, dots animate: .  ..  ...
                    var dotCount by remember { mutableIntStateOf(1) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(500)
                            dotCount = (dotCount % 3) + 1
                        }
                    }
                    Text("Please wait", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        ".".repeat(dotCount).padEnd(3, ' '),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(24.dp)
                    )
                } else {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Download",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // ── Feature Highlights (shown when idle) ──
            AnimatedVisibility(
                visible = !downloadState.isDownloading && !downloadState.isComplete && downloadState.error == null,
                enter = fadeIn(tween(400))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val features = listOf(
                        "YouTube, Instagram & 3000+ platforms · up to 4K",
                        "Save entire playlists as audio or video in one tap",
                        "Share directly from any app or paste a URL to start",
                        "Multi-mode support: Single, Playlist, Bulk & Multi Links",
                        "High quality from Dailymotion, Archive.org & more",
                        "Convert any video to MP3, AAC or FLAC offline"
                    )
                    features.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.small_right_tick),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                feature,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Guide chip — borderless Surface matching Accent button
                    Surface(
                        onClick = onNavigateToGuide,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Guide",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ── Stats + Logs Section ──
            AnimatedVisibility(
                visible = downloadState.isDownloading || downloadState.isComplete || downloadState.error != null,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300))
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {

                    // Progress bar
                    if (downloadState.isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Speed display during download
                    if (downloadState.isDownloading) {
                        Text(
                            text = speedDisplay,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Progressive Log Console — collapsible
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        // Collapsible header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { logsExpanded = !logsExpanded }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Shell",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            val chevronRotation by animateFloatAsState(
                                targetValue = if (logsExpanded) 180f else 0f,
                                animationSpec = tween(200),
                                label = "chevron"
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (logsExpanded) "Collapse" else "Expand",
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = chevronRotation },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Expandable log content
                        AnimatedVisibility(
                            visible = logsExpanded,
                            enter = expandVertically(tween(200)),
                            exit = shrinkVertically(tween(200))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                downloadState.logs.forEach { log ->
                                    val isActive = log.durationMs == null && downloadState.isDownloading
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn(tween(200))
                                    ) {
                                        LogEntryRow(log, isActive)
                                    }
                                }

                                // Bottom collapse toggle — shown when 10+ logs
                                if (downloadState.logs.size >= 10) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { logsExpanded = false }
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Collapse",
                                            modifier = Modifier
                                                .size(18.dp)
                                                .graphicsLayer { rotationZ = 180f },
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Cancel button — shown during download
                    if (downloadState.isDownloading) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { downloadViewModel.cancelDownload(context) },
                                modifier = Modifier.height(42.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // ── Completed: Saved location + Open button ──
                    if (downloadState.isComplete) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    downloadState.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Saved to ${downloadState.savedPath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Successfully saved in device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Open folder button — opens the download folder in system file manager
                                    Button(
                                        onClick = {
                                            val folder = com.hazel.android.util.StoragePaths.finalDownloads
                                            // If downloads used a subfolder (playlist/batch), find it
                                            val targetFolder = if (downloadState.savedPath.contains("/")) {
                                                val sub = java.io.File(
                                                    android.os.Environment.getExternalStoragePublicDirectory(
                                                        android.os.Environment.DIRECTORY_DOWNLOADS
                                                    ),
                                                    downloadState.savedPath.substringAfter("Download/")
                                                )
                                                if (sub.exists() && sub.isDirectory) sub else folder
                                            } else folder
                                            openFolderInFileManager(context, targetFolder)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Text("Open", style = MaterialTheme.typography.labelLarge)
                                    }

                                    // Download another
                                    Button(
                                        onClick = {
                                            downloadViewModel.resetState()
                                            url = ""
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Text("New Download", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    // Error action chips (not shown on user cancel)
                    if (downloadState.error != null && downloadState.error != "Cancelled" && !downloadState.isComplete) {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Retry button — full width
                        Button(
                            onClick = { downloadViewModel.resetState() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        // FAB auto-paste
        FloatingActionButton(
            onClick = { getClipboardText()?.let { url = it } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Filled.ContentPaste, "Paste from clipboard", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LogEntryRow(log: com.hazel.android.download.LogEntry, isActive: Boolean = false) {
    val shimmerBrush = if (isActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
        val translateAnim by infiniteTransition.animateFloat(
            initialValue = -300f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerTranslate"
        )
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            ),
            start = androidx.compose.ui.geometry.Offset(translateAnim, 0f),
            end = androidx.compose.ui.geometry.Offset(translateAnim + 400f, 0f)
        )
    } else null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        val iconRes = when (log.level) {
            LogLevel.SUCCESS -> R.drawable.small_right_tick
            LogLevel.WARN -> R.drawable.small_warn
            LogLevel.ERROR -> R.drawable.small_error_x
            LogLevel.INFO -> R.drawable.small_chevron
        }
        val tintColor = when (log.level) {
            LogLevel.SUCCESS -> com.hazel.android.ui.theme.SuccessGreen
            LogLevel.WARN -> com.hazel.android.ui.theme.WarningAmber
            LogLevel.ERROR -> com.hazel.android.ui.theme.ErrorRed
            LogLevel.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        }

        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))

        if (shimmerBrush != null) {
            Text(
                text = log.message,
                style = androidx.compose.ui.text.TextStyle(
                    brush = shimmerBrush,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = log.message,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 13.sp,
                color = when (log.level) {
                    LogLevel.SUCCESS -> com.hazel.android.ui.theme.SuccessGreen
                    LogLevel.ERROR -> com.hazel.android.ui.theme.ErrorRed
                    LogLevel.WARN -> com.hazel.android.ui.theme.WarningAmber
                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatNetworkSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
        bytesPerSecond < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSecond / 1024.0)
        bytesPerSecond < 1024L * 1024 * 1024 -> "%.2f MB/s".format(bytesPerSecond / (1024.0 * 1024))
        else -> "%.2f GB/s".format(bytesPerSecond / (1024.0 * 1024 * 1024))
    }
}

/**
 * Opens a folder in the system's default file manager.
 * Uses Documents UI content URI for maximum compatibility across all Android versions.
 */
private fun openFolderInFileManager(context: android.content.Context, folder: java.io.File) {
    if (!folder.exists()) folder.mkdirs()
    try {
        // Strategy 1: Documents UI content URI — works on most stock file managers
        val relativePath = folder.absolutePath.substringAfter("/storage/emulated/0/")
        val encodedPath = relativePath.replace("/", "%2F")
        val uri = android.net.Uri.parse(
            "content://com.android.externalstorage.documents/document/primary:$encodedPath"
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            // Strategy 2: FileProvider URI with resource/folder MIME
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", folder
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                // Strategy 3: Let user choose file manager
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("file://${folder.absolutePath}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Open folder"))
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                    context, "Path: ${folder.absolutePath}", android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
