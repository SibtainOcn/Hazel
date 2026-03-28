package com.hazel.android.converter

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.hazel.android.util.StoragePaths
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.download.LogEntry
import com.hazel.android.download.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ConversionState(
    val isConverting: Boolean = false,
    val progress: Float = 0f,
    val inputFileName: String = "",
    val inputFileUri: Uri? = null,
    val selectedQuality: String = "320kbps",
    val logs: List<LogEntry> = emptyList(),
    val error: String? = null,
    val isComplete: Boolean = false,
    val outputPath: String = "",
    val outputFileName: String = ""
)

class ConverterViewModel : ViewModel() {

    private val _state = MutableStateFlow(ConversionState())
    val state: StateFlow<ConversionState> = _state.asStateFlow()

    private val outputDir: File
        get() = StoragePaths.tempConverted

    // ── Log helpers ──

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

    // ── File selection ──

    fun selectFile(context: Context, uri: Uri) {
        val fileName = getFileName(context, uri) ?: "Unknown file"
        _state.value = _state.value.copy(
            inputFileName = fileName,
            inputFileUri = uri,
            error = null,
            isComplete = false
        )
    }

    fun setQuality(quality: String) {
        _state.value = _state.value.copy(selectedQuality = quality)
    }

    // ── Conversion — yt-dlp --extract-audio with --enable-file-urls ──

    fun convert(context: Context) {
        val uri = _state.value.inputFileUri ?: return
        if (_state.value.isConverting) return

        // No permission needed — converts write to app-specific dir (always writable)
        _state.value = _state.value.copy(
            isConverting = true, progress = 0f,
            error = null, isComplete = false, logs = emptyList()
        )

        viewModelScope.launch(Dispatchers.IO) {
            var tempInputFile: File? = null
            try {
                addLog("Preparing input file...", LogLevel.INFO)

                // Copy SAF file → temp WITH original extension (yt-dlp needs it)
                val originalExt = _state.value.inputFileName.substringAfterLast(".", "mp4")
                tempInputFile = File(
                    context.cacheDir,
                    "convert_input_${System.currentTimeMillis()}.${originalExt}"
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempInputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    addLog("Cannot read input file", LogLevel.ERROR)
                    closeLastLog()
                    _state.value = _state.value.copy(
                        isConverting = false, error = "Cannot read input file"
                    )
                    return@launch
                }

                val inputSize = tempInputFile.length()
                addLog(
                    "Input: ${_state.value.inputFileName} (${formatSize(inputSize)})",
                    LogLevel.SUCCESS
                )

                val quality = _state.value.selectedQuality
                addLog("Converting to $quality...", LogLevel.INFO)
                _state.value = _state.value.copy(progress = 0.1f)

                val baseName = _state.value.inputFileName.substringBeforeLast(".")
                val ext = when (quality) {
                    "AAC 256k" -> "m4a"
                    "FLAC" -> "flac"
                    else -> "mp3"
                }
                val outputFile = File(outputDir, "${baseName}.${ext}")

                // yt-dlp: --enable-file-urls allows local file:// paths
                // --extract-audio invokes bundled FFmpeg internally
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(
                    "file://${tempInputFile.absolutePath}"
                ).apply {
                    addOption("--enable-file-urls")
                    addOption("-x")
                    addOption("-o", outputFile.absolutePath.replace(".${ext}", ".%(ext)s"))

                    when (quality) {
                        "320kbps" -> {
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "0")
                        }
                        "AAC 256k" -> {
                            addOption("--audio-format", "m4a")
                            addOption("--audio-quality", "0")
                        }
                        "FLAC" -> {
                            addOption("--audio-format", "flac")
                            addOption("--audio-quality", "0")
                        }
                    }
                }

                addLog("Running Engine...", LogLevel.INFO)

                com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(
                    request, null
                ) { progress, _, line ->
                    val percent = progress.coerceIn(0f, 100f)
                    _state.value = _state.value.copy(progress = percent / 100f)

                    // Pipe ALL raw yt-dlp/FFmpeg output into normal log stream
                    if (line.isNotBlank()) {
                        // Clean line: strip [generic], [ExtractAudio], [ffmpeg] etc. prefixes
                        var cleaned = line.trim()
                            .replace(Regex("^\\[\\w+] "), "")  // strip [tag] prefix
                            .replace(Regex("^\\[\\w+:\\w+] "), "") // strip [tag:sub]
                            .trim()

                        // Skip noisy lines
                        if (cleaned.startsWith("Extracting URL:") ||
                            cleaned.startsWith("Downloading webpage") ||
                            cleaned.isBlank()) return@execute

                        val level = when {
                            cleaned.lowercase().contains("error") -> LogLevel.ERROR
                            cleaned.lowercase().contains("warning") -> LogLevel.WARN
                            cleaned.contains("Destination") || cleaned.contains("Output") -> LogLevel.SUCCESS
                            cleaned.contains("Stream") || cleaned.contains("Duration") -> LogLevel.SUCCESS
                            else -> LogLevel.INFO
                        }
                        addLog(cleaned, level)
                    }
                }

                addLog("Conversion complete", LogLevel.SUCCESS)
                _state.value = _state.value.copy(progress = 0.9f)

                // Find actual output (yt-dlp may adjust extension)
                val actualOutput = outputDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith(baseName) }
                    ?.maxByOrNull { it.lastModified() }
                    ?: outputFile

                if (!actualOutput.exists() || actualOutput.length() == 0L) {
                    addLog("Output file is empty or missing", LogLevel.ERROR)
                    closeLastLog()
                    _state.value = _state.value.copy(
                        isConverting = false, error = "Output file empty"
                    )
                    return@launch
                }

                // Move converted file from temp to public Music/Hazel/ via MediaStore
                addLog("Saving to Music...", LogLevel.INFO)
                try {
                    com.hazel.android.util.MediaStoreHelper.moveToPublicStorage(
                        context, outputDir, StoragePaths.MUSIC_RELATIVE_PATH, isMusic = true
                    )
                } catch (e: Exception) {
                    addLog("Note: Files saved to app storage", LogLevel.WARN)
                }

                val outputSize = actualOutput.length()
                addLog(
                    "Output: ${actualOutput.name} (${formatSize(outputSize)})",
                    LogLevel.SUCCESS
                )

                addLog("Cleaning temp files...", LogLevel.INFO)
                delay(100)
                addLog("Finished", LogLevel.SUCCESS)
                closeLastLog()

                // Save to history
                com.hazel.android.history.HistoryRepository.addEntry(
                    context,
                    com.hazel.android.history.HistoryEntry(
                        title = actualOutput.name, url = "", type = "convert",
                        path = StoragePaths.CONVERTED_DISPLAY, timestamp = System.currentTimeMillis()
                    )
                )

                _state.value = _state.value.copy(
                    isConverting = false,
                    isComplete = true,
                    progress = 1f,
                    outputFileName = actualOutput.name,
                    outputPath = StoragePaths.CONVERTED_DISPLAY
                )

            } catch (e: Exception) {
                val errorMsg = e.message ?: "Conversion failed"
                addLog("ERROR — $errorMsg", LogLevel.ERROR)
                closeLastLog()
                _state.value = _state.value.copy(
                    isConverting = false, error = errorMsg
                )
            } finally {
                // Always clean temp file — even on crash
                tempInputFile?.delete()
            }
        }
    }

    fun resetState() {
        _state.value = ConversionState()
    }

    // ── Helpers ──

    private fun getFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) cursor.getString(nameIdx) else null
            } else null
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
