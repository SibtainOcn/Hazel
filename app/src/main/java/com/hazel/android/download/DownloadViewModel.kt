package com.hazel.android.download

import android.content.Context
import com.hazel.android.data.SettingsRepository
import android.media.MediaScannerConnection
import android.os.Environment
import com.hazel.android.util.StoragePaths
import com.hazel.android.HazelApp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.runtime.mutableStateListOf

// --- Log entry types ---
enum class LogLevel { INFO, SUCCESS, WARN, ERROR }

data class LogEntry(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long? = null
)

data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val speedMbps: String = "",
    val logs: List<LogEntry> = emptyList(),
    val fileName: String = "",
    val savedPath: String = "",
    val error: String? = null,
    val isComplete: Boolean = false,
    // Batch/playlist tracking
    val batchTotal: Int = 0,
    val batchCurrent: Int = 0,
    val batchFailures: Int = 0,
    val playlistName: String = "",
    val playlistCount: Int = 0,
)

class DownloadViewModel : ViewModel() {

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    // Multi Links shared URL list — survives navigation
    val multiLinkUrls = mutableStateListOf<String>()

    // Bulk mode — parsed URLs from imported file
    val bulkUrls = mutableStateListOf<String>()
    var bulkFileName: String = ""
        private set
    var bulkFileError: String = ""
        private set

    /**
     * Parse a text file (any extension) from SAF URI.
     * Extracts valid http/https URLs (one per line), rejects invalid lines.
     * Returns true if at least one valid URL was found.
     */
    fun parseBulkFile(context: Context, uri: android.net.Uri): Boolean {
        bulkUrls.clear()
        bulkFileError = ""
        bulkFileName = ""

        try {
            // Read file name
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) bulkFileName = it.getString(nameIndex) ?: "Unknown file"
                }
            }

            // Read content
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                bulkFileError = "Cannot read file"
                return false
            }

            val content = inputStream.bufferedReader().use { it.readText() }
            if (content.isBlank()) {
                bulkFileError = "File is empty"
                return false
            }

            val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
            val validUrls = mutableListOf<String>()
            val invalidLines = mutableListOf<Int>()

            // URL pattern: must start with http:// or https://
            val urlPattern = Regex("^https?://[\\w\\-./:@?&#=%~+!*'(),;]+$")

            lines.forEachIndexed { index, line ->
                when {
                    line.startsWith("#") || line.startsWith("//") -> { /* skip comments */ }
                    urlPattern.matches(line) -> validUrls.add(line)
                    else -> invalidLines.add(index + 1)
                }
            }

            if (validUrls.isEmpty()) {
                bulkFileError = if (invalidLines.isNotEmpty()) {
                    "No valid URLs found. ${invalidLines.size} lines contain invalid characters or are not URLs."
                } else {
                    "No URLs found in file"
                }
                return false
            }

            // Remove duplicates
            val uniqueUrls = validUrls.distinct()
            bulkUrls.addAll(uniqueUrls)

            if (invalidLines.isNotEmpty()) {
                bulkFileError = "Loaded ${uniqueUrls.size} URLs (${invalidLines.size} invalid lines skipped)"
            }

            return true
        } catch (e: Exception) {
            bulkFileError = "Error reading file: ${e.message?.take(50) ?: "Unknown error"}"
            return false
        }
    }

    fun clearBulkState() {
        bulkUrls.clear()
        bulkFileName = ""
        bulkFileError = ""
    }

    // Shared video/audio selection — used by bulk editor
    var isVideoSelection: Boolean = true
    var selectedMode: String = "Single"

    // Cancel support
    private var downloadJob: Job? = null
    private val processId = "hazel_download"
    @Volatile private var isCancelled = false

    // Notification context — set on each download start
    private var downloadContext: Context? = null
    private var downloadIsVideo: Boolean = true

    // Reusable OkHttp client — singleton
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val downloadDir: File
        get() = StoragePaths.tempDownloads

    // Persistent yt-dlp cache — avoids re-fetching player.js (~12s) on every process
    private val ytDlpCacheDir: File
        get() {
            val dir = File(HazelApp.instance.cacheDir, "yt-dlp")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * Creates a subfolder inside downloadDir for separate folder mode.
     * - Playlist: uses playlist name (first 10 chars, sanitized)
     * - Batch/Multi Links: auto-generates Batch_01, Batch_02, etc.
     */
    // Pre-compiled regex — avoids re-allocation per call
    private val folderNameRegex = Regex("[^a-zA-Z0-9_ -]")

    private fun resolveSeparateFolder(mode: String, playlistName: String = ""): File {
        return try {
            val baseName = when (mode) {
                "Playlist" -> {
                    val sanitized = playlistName.take(10)
                        .replace(folderNameRegex, "")
                        .trim()
                        .ifBlank { "Playlist" }
                    // If folder exists, append _1, _2, etc.
                    if (File(downloadDir, sanitized).exists()) {
                        var index = 1
                        while (File(downloadDir, "${sanitized}_$index").exists()) index++
                        "${sanitized}_$index"
                    } else sanitized
                }
                else -> {
                    // Find next available Batch_XX
                    var index = 1
                    while (File(downloadDir, "Batch_%02d".format(index)).exists()) index++
                    "Batch_%02d".format(index)
                }
            }
            val subDir = File(downloadDir, baseName)
            if (!subDir.exists()) subDir.mkdirs()
            subDir
        } catch (e: Exception) {
            // Fallback to default dir on any filesystem error
            downloadDir
        }
    }

    private fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        val now = System.currentTimeMillis()
        val current = _state.value
        val updatedLogs = current.logs.toMutableList()
        if (updatedLogs.isNotEmpty()) {
            val last = updatedLogs.last()
            if (last.durationMs == null) {
                updatedLogs[updatedLogs.lastIndex] = last.copy(durationMs = now - last.timestamp)
            }
        }
        updatedLogs.add(LogEntry(message, level, now))
        _state.value = current.copy(logs = updatedLogs)
    }

    private fun closeLastLog() {
        val now = System.currentTimeMillis()
        val current = _state.value
        val updatedLogs = current.logs.toMutableList()
        if (updatedLogs.isNotEmpty()) {
            val last = updatedLogs.last()
            if (last.durationMs == null) {
                updatedLogs[updatedLogs.lastIndex] = last.copy(durationMs = now - last.timestamp)
            }
        }
        _state.value = current.copy(logs = updatedLogs)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Main Entry Point — mode-aware
    // ═══════════════════════════════════════════════════════════════════
    var selectedQuality: String = "1080p"

    fun startDownload(context: Context, urls: List<String>, isVideo: Boolean, mode: String, quality: String = "1080p") {
        if (_state.value.isDownloading) return
        selectedQuality = quality

        // No permission needed — downloads go to app-specific dir (always writable)
        com.hazel.android.util.PermissionHelper.ensureNotificationPermission(context)

        _state.value = DownloadState(isDownloading = true)
        isCancelled = false
        downloadContext = context
        downloadIsVideo = isVideo
        DownloadNotificationHelper.showProgress(context, 0, "Starting download...")

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
                val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                if (caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
                    addLog("ERROR · No internet connection", LogLevel.ERROR)
                    closeLastLog()
                    _state.value = _state.value.copy(isDownloading = false, error = "No internet connection")
                    return@launch
                }

                // Pre-validate URL format
                val urlPattern = Regex("^https?://\\S+$")
                val invalidUrls = urls.filter { !urlPattern.matches(it.trim()) }
                if (invalidUrls.isNotEmpty()) {
                    addLog("ERROR — Invalid URL format", LogLevel.ERROR)
                    addLog("URLs must start with http:// or https://", LogLevel.WARN)
                    closeLastLog()
                    _state.value = _state.value.copy(
                        isDownloading = false,
                        error = "Invalid URL — must start with http:// or https://"
                    )
                    return@launch
                }

                val useSeparateFolder = when (mode) {
                    "Playlist" -> SettingsRepository.getFolderPlaylist(context).first()
                    "Multi Links" -> SettingsRepository.getFolderMultiLinks(context).first()
                    "Bulk" -> SettingsRepository.getFolderBulk(context).first()
                    else -> false
                }

                when (mode) {
                    "Single" -> executeSingleDownload(context, urls.first(), isVideo)
                    "Playlist" -> executePlaylistDownload(context, urls.first(), isVideo, useSeparateFolder)
                    "Multi Links", "Bulk" -> executeBatchDownload(context, urls, isVideo, useSeparateFolder)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isCancelled) return@launch
                val rawMsg = e.message ?: "Download failed"
                val cleanMsg = sanitizeError(rawMsg)
                addLog("ERROR — $cleanMsg", LogLevel.ERROR)
                closeLastLog()
                com.hazel.android.utils.CrashLogger.logDownloadError(
                    url = urls.firstOrNull() ?: "unknown", platform = "batch",
                    error = rawMsg, logs = _state.value.logs.map { "${it.level}: ${it.message}" }
                )
                DownloadNotificationHelper.showError(context, cleanMsg)
                _state.value = _state.value.copy(isDownloading = false, error = cleanMsg)
            }
        }
    }

    /** Share-intent convenience — always downloads at best available quality */
    fun startDownload(context: Context, url: String, isVideo: Boolean) {
        startDownload(context, listOf(url), isVideo, "Single", "best")
    }

    /**
     * Gracefully cancel the current download.
     * Kills yt-dlp process, cancels coroutine, cleans temp files, logs cancellation.
     */
    fun cancelDownload(context: Context) {
        isCancelled = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Kill the yt-dlp process
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Exception) { /* process may already be done */ }

            // Cancel the coroutine job
            downloadJob?.cancel()
            downloadJob = null

            addLog("Download cancelled by user", LogLevel.WARN)

            // Clean temp files
            try {
                val tempFiles = downloadDir.listFiles { f -> f.name.endsWith(".part") || f.name.endsWith(".ytdl") || f.name.endsWith(".temp") }
                val cleaned = tempFiles?.size ?: 0
                tempFiles?.forEach { it.delete() }
                if (cleaned > 0) addLog("Cleaned $cleaned temp files", LogLevel.INFO)
            } catch (_: Exception) { /* ignore cleanup errors */ }

            DownloadNotificationHelper.showCancelled(context)
            closeLastLog()
            _state.value = _state.value.copy(
                isDownloading = false,
                error = "Cancelled"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Single Download
    // ═══════════════════════════════════════════════════════════════════
    private suspend fun executeSingleDownload(context: Context, url: String, isVideo: Boolean) {
        val platform = detectPlatform(url)
        addLog("Validating URL...", LogLevel.INFO)

        val httpStatus = validateUrl(url)
        if (!handleHttpStatus(httpStatus)) return

        addLog("Fetching from $platform", LogLevel.SUCCESS)
        val request = buildRequest(url, isVideo, usePlaylist = false)
        addLog("Reading metadata...", LogLevel.INFO)
        if (isVideo) {
            executeYtDlp(request)
        } else {
            executeAudioWithRetry(url, false, downloadDir)
        }
        if (isCancelled) return
        addLog("Download completed", LogLevel.SUCCESS)

        addLog("Cleaning temp files...", LogLevel.INFO)
        finishDownload(context, downloadDir)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Playlist Download
    // ═══════════════════════════════════════════════════════════════════
    private suspend fun executePlaylistDownload(context: Context, url: String, isVideo: Boolean, useSeparateFolder: Boolean) {
        val platform = detectPlatform(url)
        addLog("Validating URL...", LogLevel.INFO)

        val httpStatus = validateUrl(url)
        if (!handleHttpStatus(httpStatus)) return

        addLog("Fetching from $platform", LogLevel.SUCCESS)
        addLog("Reading metadata...", LogLevel.INFO)

        val (pName, pCount) = fetchPlaylistInfo(url)
        if (pCount == 0) {
            addLog("ERROR — Playlist is empty or private", LogLevel.ERROR)
            closeLastLog()
            _state.value = _state.value.copy(isDownloading = false, error = "Playlist is empty or private")
            return
        }

        addLog("$pName ($pCount items)", LogLevel.SUCCESS)
        _state.value = _state.value.copy(playlistName = pName, playlistCount = pCount, batchTotal = pCount)

        // Resolve output folder
        val outputDir = if (useSeparateFolder) resolveSeparateFolder("Playlist", pName) else downloadDir
        if (useSeparateFolder) addLog("Saving to folder: ${outputDir.name}", LogLevel.INFO)

        if (isVideo) {
            val request = buildRequest(url, isVideo, usePlaylist = true, outputDir = outputDir)
            executeYtDlp(request)
        } else {
            executeAudioWithRetry(url, true, outputDir)
        }
        if (isCancelled) return

        // ── Professional summary ──
        val total = _state.value.batchTotal.coerceAtLeast(pCount)
        val errorLogs = _state.value.logs.filter { it.level == LogLevel.ERROR }
        val failures = errorLogs.size
        val succeeded = (total - failures).coerceAtLeast(0)

        if (failures == 0) {
            addLog("✓ $succeeded/$total downloaded successfully", LogLevel.SUCCESS)
        } else {
            addLog("✓ $succeeded/$total downloaded · $failures failed", LogLevel.WARN)
            errorLogs.takeLast(5).forEach { err ->
                val reason = err.message
                    .removePrefix("ERROR — ")
                    .removePrefix("ERROR - ")
                    .take(80)
                addLog("  ↳ $reason", LogLevel.ERROR)
            }
        }

        addLog("Cleaning temp files...", LogLevel.INFO)
        finishDownload(context, outputDir)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Batch Download (Multi Links / Bulk)
    // ═══════════════════════════════════════════════════════════════════
    private suspend fun executeBatchDownload(context: Context, urls: List<String>, isVideo: Boolean, useSeparateFolder: Boolean) {
        val validUrls = urls.filter { it.isNotBlank() }
        val total = validUrls.size
        val platform = detectPlatform(validUrls.firstOrNull() ?: "")

        // Resolve output folder
        val outputDir = if (useSeparateFolder) resolveSeparateFolder(selectedMode) else downloadDir
        if (useSeparateFolder) addLog("Saving to folder: ${outputDir.name}", LogLevel.INFO)

        addLog("Fetching from $platform", LogLevel.SUCCESS)
        addLog("Reading metadata...", LogLevel.INFO)
        _state.value = _state.value.copy(batchTotal = total, batchCurrent = 0, batchFailures = 0)

        // Write URLs to a temp batch file for single-process execution
        val batchFile = File(outputDir, ".hazel_batch.txt")
        var batchSucceeded = false
        try {
            batchFile.writeText(validUrls.joinToString("\n"))

            val request = buildBatchRequest(batchFile, isVideo, outputDir)
            executeYtDlp(request)

            if (isCancelled) return

            // ──  summary ──
            val errorLogs = _state.value.logs.filter { it.level == LogLevel.ERROR }
            val failures = errorLogs.size
            val succeeded = (total - failures).coerceAtLeast(0)

            if (failures == 0) {
                addLog("✓ $succeeded/$total downloaded successfully", LogLevel.SUCCESS)
            } else {
                addLog("✓ $succeeded/$total downloaded · $failures failed", LogLevel.WARN)
                errorLogs.takeLast(5).forEach { err ->
                    val reason = err.message
                        .removePrefix("ERROR — ")
                        .removePrefix("ERROR - ")
                        .take(80)
                    addLog("  ↳ $reason", LogLevel.ERROR)
                }
            }
            batchSucceeded = true
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (isCancelled) return
            val rawMsg = e.message ?: "Batch download failed"
            val cleanMsg = sanitizeError(rawMsg)
            addLog("ERROR — $cleanMsg", LogLevel.ERROR)
            closeLastLog()
            com.hazel.android.utils.CrashLogger.logDownloadError(
                url = validUrls.firstOrNull() ?: "batch", platform = "batch",
                error = rawMsg,
                logs = _state.value.logs.map { "${it.level}: ${it.message}" }
            )
            DownloadNotificationHelper.showError(context, cleanMsg)
            _state.value = _state.value.copy(isDownloading = false, error = cleanMsg)
        } finally {
            // Always clean up batch file
            try { batchFile.delete() } catch (_: Exception) {}
        }

        // Only finish+move files if batch actually downloaded something
        if (batchSucceeded) {
            addLog("Cleaning temp files...", LogLevel.INFO)
            finishDownload(context, outputDir)
        }
    }

    /**
     * Builds a yt-dlp request that reads URLs from a batch file.
     * Single process handles all URLs — shares Python startup, player API cache,
     * and network connections across all downloads.
     */
    private fun buildBatchRequest(batchFile: File, isVideo: Boolean, outputDir: File): YoutubeDLRequest {
        // Empty URL list — yt-dlp reads URLs exclusively from --batch-file
        // (YoutubeDLRequest appends urls to command; empty list = no stray positional arg)
        return YoutubeDLRequest(emptyList<String>()).apply {
            addOption("--batch-file", batchFile.absolutePath)
            addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")
            addOption("--restrict-filenames")
            addOption("--force-overwrites")
            addOption("--cache-dir", ytDlpCacheDir.absolutePath)

            if (isVideo) {
                val formatStr = when (selectedQuality) {
                    "best" -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                    "4K" -> "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/best[height<=2160]/best"
                    "2K" -> "bestvideo[height<=1440][ext=mp4]+bestaudio[ext=m4a]/best[height<=1440]/best"
                    "1080p" -> "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]/best"
                    "720p" -> "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best"
                    "480p" -> "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480]/best"
                    "360p" -> "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360]/best"
                    else -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                }
                addOption("-f", formatStr)
                addOption("--merge-output-format", "mp4")
            } else {
                val audioFormat = when {
                    selectedQuality.startsWith("MP3") -> "mp3"
                    selectedQuality.startsWith("AAC") -> "aac"
                    selectedQuality.startsWith("FLAC") -> "flac"
                    selectedQuality.startsWith("WAV") -> "wav"
                    selectedQuality.startsWith("Opus") -> "opus"
                    else -> "mp3"
                }
                val audioBitrate = when {
                    selectedQuality.startsWith("MP3") -> "320K"
                    selectedQuality.startsWith("AAC") -> "256K"
                    else -> "0"
                }
                addOption("-x")
                addOption("--audio-format", audioFormat)
                if (audioBitrate != "0") addOption("--audio-quality", audioBitrate)
            }
            addOption("--embed-thumbnail"); addOption("--embed-metadata")
            addOption("--no-playlist")
            addOption("--concurrent-fragments", "8")
            addOption("--buffer-size", "16K")
            addOption("--no-check-certificates")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Shared Helpers
    // ═══════════════════════════════════════════════════════════════════

    private fun buildRequest(url: String, isVideo: Boolean, usePlaylist: Boolean, outputDir: File = downloadDir, audioFormatOverride: String? = null, audioBitrateOverride: String? = null): YoutubeDLRequest {
        return YoutubeDLRequest(url).apply {
            addOption("-o", "${outputDir.absolutePath}/%(title)s.%(ext)s")
            // Sanitize filenames — prevents crash from Unicode chars (⧸ ｜ ：)
            addOption("--restrict-filenames")
            // Re-download even if file exists (user may change quality)
            addOption("--force-overwrites")

            if (isVideo) {
                // Map user quality selection to yt-dlp format string
                val formatStr = when (selectedQuality) {
                    "best" -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                    "4K" -> "bestvideo[height<=2160][ext=mp4]+bestaudio[ext=m4a]/best[height<=2160]/best"
                    "2K" -> "bestvideo[height<=1440][ext=mp4]+bestaudio[ext=m4a]/best[height<=1440]/best"
                    "1080p" -> "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]/best"
                    "720p" -> "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]/best"
                    "480p" -> "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480]/best"
                    "360p" -> "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360]/best"
                    else -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                }
                addOption("-f", formatStr)
                addOption("--merge-output-format", "mp4")
            } else {
                // Parse audio format from selectedQuality (e.g., "MP3 · 320kbps", "FLAC")
                val audioFormat = audioFormatOverride ?: when {
                    selectedQuality.startsWith("MP3") -> "mp3"
                    selectedQuality.startsWith("AAC") -> "aac"
                    selectedQuality.startsWith("FLAC") -> "flac"
                    selectedQuality.startsWith("WAV") -> "wav"
                    selectedQuality.startsWith("Opus") -> "opus"
                    else -> "mp3"
                }
                val audioBitrate = audioBitrateOverride ?: when {
                    selectedQuality.startsWith("MP3") -> "320K"
                    selectedQuality.startsWith("AAC") -> "256K"
                    else -> "0" // Best available for lossless/opus
                }
                addOption("-x")
                addOption("--audio-format", audioFormat)
                if (audioBitrate != "0") addOption("--audio-quality", audioBitrate)
            }
            addOption("--embed-thumbnail"); addOption("--embed-metadata")
            if (usePlaylist) { addOption("--yes-playlist"); addOption("--lazy-playlist") }
            else addOption("--no-playlist")
            addOption("--concurrent-fragments", "8")
            addOption("--buffer-size", "16K")
            addOption("--no-check-certificates")
            addOption("--cache-dir", ytDlpCacheDir.absolutePath)
        }
    }

    private fun executeYtDlp(request: YoutubeDLRequest) {
        var lastPhase = ""
        var currentItem = 0
        var totalItems = _state.value.batchTotal
        // Track phase cycling to detect new playlist items even without explicit "item X of Y" output
        var phaseHitWebpage = false

        // Pre-compiled regexes (allocated once per executeYtDlp call, NOT per callback)
        // Match all yt-dlp playlist-item patterns (broad):
        //   "[download] Downloading item 1 of 4"  |  "Downloading video 1 of 4"
        //   "Downloading playlist item 1 of 4"    |  "[youtube:tab] Playlist ...: Downloading 1 items of 29"
        val itemRegex = Regex("""(?:Downloading\s+(?:item|video|playlist\s*item))\s+(\d+)\s+of\s+(\d+)""", RegexOption.IGNORE_CASE)
        // Broader fallback: catches "item X of Y" anywhere in line
        val itemRegexBroad = Regex("""\bitem\s+(\d+)\s+of\s+(\d+)\b""", RegexOption.IGNORE_CASE)
        // Generic fallback: any "X of Y" — pre-compiled to avoid allocation in hot callback
        val genericOfRegex = Regex("""(\d+)\s+of\s+(\d+)""")

        YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
            val percent = progress.coerceIn(0f, 100f)
            val lower = line.lowercase()

            // ── Detect playlist item progression ──
            var itemDetected = false

            // Primary regex
            itemRegex.find(line)?.let { match ->
                val newItem = match.groupValues[1].toIntOrNull() ?: 0
                val newTotal = match.groupValues[2].toIntOrNull() ?: totalItems
                if (newItem > 0) {
                    currentItem = newItem; totalItems = newTotal; itemDetected = true
                }
            }

            // Broader fallback regex (catches "item X of Y" anywhere in line)
            if (!itemDetected) {
                itemRegexBroad.find(line)?.let { match ->
                    val newItem = match.groupValues[1].toIntOrNull() ?: 0
                    val newTotal = match.groupValues[2].toIntOrNull() ?: totalItems
                    if (newItem > 0) {
                        currentItem = newItem; totalItems = newTotal; itemDetected = true
                    }
                }
            }

            // Fallback: match "X of Y" but only in download-context lines,
            // excluding fragment/format lines which also contain "X of Y"
            if (!itemDetected && currentItem == 0 && totalItems > 0) {
                if ("fragment" !in lower && "format" !in lower && "%" !in line) {
                    val fallback = genericOfRegex.find(line)
                    if (fallback != null) {
                        val newItem = fallback.groupValues[1].toIntOrNull() ?: 0
                        val newTotal = fallback.groupValues[2].toIntOrNull() ?: totalItems
                        if (newItem > 0 && newTotal > 1) {
                            currentItem = newItem; totalItems = newTotal; itemDetected = true
                        }
                    }
                }
            }

            // Phase-based item tracking: when downloading a playlist and we see
            // "Fetching webpage..." repeat, it means yt-dlp moved to the next item
            if (!itemDetected && totalItems > 1 && "downloading webpage" in lower) {
                if (phaseHitWebpage) {
                    // We already saw "webpage" before — this is a new item
                    currentItem++
                    itemDetected = true
                }
                phaseHitWebpage = true
            }

            if (itemDetected && currentItem > 0) {
                _state.value = _state.value.copy(
                    batchCurrent = currentItem,
                    batchTotal = totalItems
                )
                // Reset phase tracking for the new item
                lastPhase = ""
            }

            val phase = detectPhase(line)
            if (phase.isNotEmpty() && phase != lastPhase) {
                val prefix = if (totalItems > 1 && currentItem > 0) "$currentItem/$totalItems · " else ""
                addLog("$prefix$phase", when {
                    phase.startsWith("ERROR") -> LogLevel.ERROR
                    phase.startsWith("WARN") -> LogLevel.WARN
                    else -> LogLevel.INFO
                })
                lastPhase = phase
            } else if (phase.isEmpty() && line.isNotBlank()) {
                // Forward interesting raw yt-dlp lines
                val trimmed = line.trim()
                    .removePrefix("[download]").removePrefix("[info]")
                    .removePrefix("[youtube]").removePrefix("[generic]")
                    .trim()
                // Show download progress lines (contain % and size info)
                if ("%" in line && ("MiB" in line || "KiB" in line || "GiB" in line || "ETA" in line)) {
                    // Replace last raw progress line instead of spamming
                    val logs = _state.value.logs.toMutableList()
                    val lastIdx = logs.indexOfLast { it.message.startsWith("↓ ") }
                    if (lastIdx >= 0) {
                        logs[lastIdx] = LogEntry("↓ $trimmed", LogLevel.INFO)
                        _state.value = _state.value.copy(logs = logs)
                    } else {
                        addLog("↓ $trimmed", LogLevel.INFO)
                    }
                } else if ("Destination:" in line || "format" in line.lowercase() || "filesize" in line.lowercase()) {
                    addLog(trimmed, LogLevel.INFO)
                }
            }
            _state.value = _state.value.copy(progress = percent / 100f)
            // Update notification progress (throttle: every 2%)
            if (percent.toInt() % 2 == 0) {
                downloadContext?.let { ctx ->
                    DownloadNotificationHelper.showProgress(ctx, percent.toInt())
                }
            }
        }
    }

    /**
     * Executes audio download with format-aware retry logic.
     * MP3/AAC: retries at progressively lower bitrates on failure.
     * FLAC/WAV/Opus: fail-fast with clear notification.
     */
    private fun executeAudioWithRetry(url: String, usePlaylist: Boolean, outputDir: File) {
        val format = when {
            selectedQuality.startsWith("MP3") -> "mp3"
            selectedQuality.startsWith("AAC") -> "aac"
            selectedQuality.startsWith("FLAC") -> "flac"
            selectedQuality.startsWith("WAV") -> "wav"
            selectedQuality.startsWith("Opus") -> "opus"
            else -> "mp3"
        }

        val bitrateFallbacks = when (format) {
            "mp3" -> listOf("320K", "256K", "192K", "128K")
            "aac" -> listOf("256K", "192K", "128K", "96K")
            else -> emptyList() // No fallbacks for lossless/opus
        }

        if (bitrateFallbacks.isEmpty()) {
            // FLAC / WAV / Opus — single attempt, fail-fast
            try {
                val request = buildRequest(url, false, usePlaylist, outputDir, format, "0")
                executeYtDlp(request)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                val msg = "${format.uppercase()} format not supported for this source. Try MP3 or AAC instead."
                addLog("ERROR — $msg", LogLevel.ERROR)
                closeLastLog()
                _state.value = _state.value.copy(isDownloading = false, error = msg)
            }
            return
        }

        // MP3 / AAC — retry at lower bitrates
        for ((idx, bitrate) in bitrateFallbacks.withIndex()) {
            try {
                val request = buildRequest(url, false, usePlaylist, outputDir, format, bitrate)
                executeYtDlp(request)
                return // Success
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if (idx < bitrateFallbacks.lastIndex) {
                    val nextBitrate = bitrateFallbacks[idx + 1]
                    addLog("${format.uppercase()} ${bitrate} failed, retrying at $nextBitrate...", LogLevel.WARN)
                } else {
                    val msg = "${format.uppercase()} download failed at all quality levels. Try a different format."
                    addLog("ERROR — $msg", LogLevel.ERROR)
                    closeLastLog()
                    _state.value = _state.value.copy(isDownloading = false, error = msg)
                }
            }
        }
    }

    private fun validateUrl(url: String): Int {
        return try {
            val req = okhttp3.Request.Builder().url(url).head().build()
            val resp = httpClient.newCall(req).execute()
            val code = resp.code; resp.close(); code
        } catch (_: Exception) { -1 }
    }

    private fun handleHttpStatus(httpStatus: Int): Boolean {
        when {
            httpStatus in 200..299 -> addLog("URL OK — HTTP $httpStatus", LogLevel.SUCCESS)
            httpStatus == 403 -> addLog("URL HTTP 403 (Forbidden) — trying anyway...", LogLevel.WARN)
            httpStatus == 404 -> {
                addLog("URL ERROR — HTTP 404 (Not Found)", LogLevel.ERROR); closeLastLog()
                _state.value = _state.value.copy(isDownloading = false, error = "URL not found (404)")
                return false
            }
            httpStatus in 400..499 -> addLog("URL HTTP $httpStatus — trying anyway...", LogLevel.WARN)
            httpStatus in 500..599 -> {
                addLog("URL ERROR — HTTP $httpStatus (Server Error)", LogLevel.ERROR); closeLastLog()
                _state.value = _state.value.copy(isDownloading = false, error = "Server error ($httpStatus)")
                return false
            }
            httpStatus == -1 -> addLog("URL validation skipped (network issue)", LogLevel.WARN)
        }
        return true
    }

    private fun fetchPlaylistInfo(url: String): Pair<String, Int> {
        return try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist"); addOption("--dump-json"); addOption("--no-download")
            }
            val output = YoutubeDL.getInstance().execute(request, null, null)
            val stdout = output.out ?: ""
            val lines = stdout.lines().filter { it.isNotBlank() }
            val titleRegex = """"playlist_title"\s*:\s*"([^"]+)"""".toRegex()
            val name = titleRegex.find(stdout)?.groupValues?.get(1) ?: "Playlist"
            Pair(name, lines.size)
        } catch (_: Exception) { Pair("Unknown Playlist", 0) }
    }

    private fun finishDownload(context: Context, outputDir: File) {
        // Determine the relative path for MediaStore
        // outputDir could be: tempDownloads, tempDownloads/PlaylistName, tempDownloads/Batch_01
        val subFolder = if (outputDir.absolutePath == downloadDir.absolutePath) {
            "" // Root download dir
        } else {
            "/" + outputDir.name // Subfolder (playlist/batch)
        }
        val mediaStoreRelPath = StoragePaths.DOWNLOAD_RELATIVE_PATH + subFolder

        // Get latest file info BEFORE moving
        val latestFile = outputDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
        val fileName = latestFile?.name ?: "File saved"
        val type = when (latestFile?.extension?.lowercase()) {
            "mp3", "m4a", "aac", "flac", "opus", "wav" -> "audio"
            else -> "video"
        }

        // Move files from temp dir to public storage via MediaStore
        addLog("Saving to Downloads...", LogLevel.INFO)
        val finalDir = try {
            com.hazel.android.util.MediaStoreHelper.moveToPublicStorage(
                context, outputDir, mediaStoreRelPath, isMusic = false
            )
        } catch (e: Exception) {
            // If MediaStore move fails, files remain in temp dir (still accessible)
            addLog("Note: Files saved to app storage", LogLevel.WARN)
            outputDir
        }

        // Scan final dir for gallery visibility
        com.hazel.android.util.MediaStoreHelper.scanFiles(context, finalDir)

        addLog("Finished", LogLevel.SUCCESS); closeLastLog()

        // Display path shows the public location
        val displayPath = StoragePaths.DOWNLOADS_DISPLAY + subFolder

        // Save to history
        com.hazel.android.history.HistoryRepository.addEntry(
            context,
            com.hazel.android.history.HistoryEntry(
                title = fileName, url = "", type = type,
                path = displayPath, timestamp = System.currentTimeMillis()
            )
        )

        _state.value = _state.value.copy(
            isDownloading = false, isComplete = true, progress = 1f,
            fileName = fileName, savedPath = displayPath
        )

        DownloadNotificationHelper.showComplete(context, fileName, isVideo = downloadIsVideo)
    }

    fun resetState() {
        _state.value = DownloadState()
        multiLinkUrls.clear()
    }

    private fun detectPlatform(url: String): String = when {
        "youtube.com" in url || "youtu.be" in url -> "YouTube"
        "instagram.com" in url -> "Instagram"
        "twitter.com" in url || "x.com" in url -> "X (Twitter)"
        "tiktok.com" in url -> "TikTok"
        "facebook.com" in url || "fb.watch" in url -> "Facebook"
        "reddit.com" in url -> "Reddit"
        "twitch.tv" in url -> "Twitch"
        "vimeo.com" in url -> "Vimeo"
        "soundcloud.com" in url -> "SoundCloud"
        "spotify.com" in url -> "Spotify"
        else -> "source"
    }

    private fun detectPhase(line: String): String {
        val lower = line.lowercase()
        return when {
            "downloading webpage" in lower -> "Fetching webpage..."
            "downloading api" in lower || "downloading json" in lower -> "Reading JSON data..."
            "downloading android" in lower || "downloading player" in lower -> "Fetching player API..."
            "downloading m3u8" in lower || "downloading manifest" in lower -> "Reading stream manifest..."
            "downloading format" in lower -> "Selecting best format..."
            "merging" in lower || "muxing" in lower -> "Merging streams..."
            "deleting original" in lower -> "Cleaning temp files..."
            "destination" in lower -> "Downloading file..."
            "error" in lower -> "ERROR — ${sanitizeError(line)}"
            "warning" in lower -> "WARN — ${line.take(60)}"
            "has already been downloaded" in lower -> "Item already exists, re-downloading"
            else -> ""
        }
    }

    /**
     * Translates raw yt-dlp error strings into clean, user-facing messages.
     * Keeps CrashLogger raw for debugging; only the UI/notification sees sanitized text.
     */
    private fun sanitizeError(raw: String): String {
        val lower = raw.lowercase()
        return when {
            "is not a valid url" in lower || "unsupported url" in lower ||
            "no suitable infoextractor" in lower
                -> "Invalid or unsupported URL"

            "video unavailable" in lower || "this video is unavailable" in lower
                -> "This video is unavailable"

            "private video" in lower || "sign in" in lower || "login required" in lower
                -> "This content is private or requires login"

            "geo restricted" in lower || "not available in your country" in lower ||
            "blocked" in lower
                -> "This content is not available in your region"

            "age" in lower && ("restricted" in lower || "gate" in lower || "verify" in lower)
                -> "Age-restricted content — cannot download"

            "copyright" in lower || "taken down" in lower || "dmca" in lower
                -> "Content removed due to copyright"

            "format" in lower && "not available" in lower ||
            "requested format not available" in lower
                -> "Requested quality not available — try a different one"

            "no video formats" in lower || "no audio formats" in lower
                -> "No downloadable format found for this URL"

            "unable to download" in lower && "http" in lower
                -> "Network error — check your connection and try again"

            "connection" in lower || "timed out" in lower || "timeout" in lower ||
            "network" in lower
                -> "Network error — check your connection"

            "ffmpeg" in lower || "postprocessor" in lower
                -> "Processing failed — try again"

            "live" in lower && ("event" in lower || "stream" in lower)
                -> "Live streams cannot be downloaded"

            "playlist" in lower && ("empty" in lower || "not exist" in lower)
                -> "Playlist is empty or does not exist"

            else -> {
                // Strip yt-dlp technical prefixes like "ERROR: [generic]" or "ERROR: "
                raw.replace(Regex("^ERROR\\s*[:—-]?\\s*(\\[[^]]*]\\s*)?" , RegexOption.IGNORE_CASE), "")
                    .trim()
                    .take(80)
                    .ifBlank { "Download failed" }
            }
        }
    }
}
