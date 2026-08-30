package com.hazel.android.converter

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.PermissionHelper
import com.hazel.android.util.StoragePaths
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ConversionState(
    val isConverting: Boolean = false,
    val progress: Float = 0f,
    val inputFileName: String = "",
    val inputSizeBytes: Long = 0L,
    val inputFileUri: Uri? = null,
    val format: AudioFormat = AudioFormats.DEFAULT,
    /**
     * The one line the screen shows while a conversion runs, replaced as the engine
     * reports the next thing it is doing.
     *
     * A list of every line the engine ever printed is a log, and a log is something to read
     * afterwards when something went wrong. While the conversion is running the only useful
     * question is what it is doing now, and one line answers it without the screen growing
     * a scrolling panel that pushes everything else off the bottom.
     */
    val statusLine: String = "",
    val statusLevel: LogLevel = LogLevel.INFO,
    val error: String? = null,
    val isComplete: Boolean = false,
    val outputPath: String = "",
    val outputFileName: String = "",
    val outputSizeBytes: Long = 0L,
    /**
     * True when the finished file could not be published into the user's own Music folder
     * and stayed in the app's storage instead. Said out loud rather than swallowed: a file
     * the user cannot find is the same as a file that was never made.
     */
    val savedToAppStorage: Boolean = false
)

class ConverterViewModel : ViewModel() {

    private val _state = MutableStateFlow(ConversionState())
    val state: StateFlow<ConversionState> = _state.asStateFlow()

    private val outputDir: File
        get() = StoragePaths.tempConverted

    private fun say(message: String, level: LogLevel = LogLevel.INFO) {
        _state.value = _state.value.copy(statusLine = message, statusLevel = level)
    }

    // ── File selection ──

    /**
     * Takes a file the user picked through the system document picker.
     *
     * The name and size are read here rather than at conversion time, because both come
     * from a query against the provider that may not answer the same way later, and the
     * screen wants to show them the moment the picker closes.
     */
    fun selectFile(context: Context, uri: Uri) {
        val name = displayName(context, uri)
        _state.value = _state.value.copy(
            inputFileName = name,
            inputSizeBytes = fileSize(context, uri),
            inputFileUri = uri,
            error = null,
            isComplete = false,
            statusLine = "",
            progress = 0f
        )
    }

    fun setFormat(format: AudioFormat) {
        _state.value = _state.value.copy(format = format)
    }

    // ── Conversion, through yt-dlp's audio extraction, which runs the bundled FFmpeg ──

    fun convert(context: Context) {
        val uri = _state.value.inputFileUri ?: return
        if (_state.value.isConverting) return

        _state.value = _state.value.copy(
            isConverting = true,
            progress = 0f,
            error = null,
            isComplete = false,
            statusLine = "Preparing",
            statusLevel = LogLevel.INFO,
            savedToAppStorage = false
        )

        viewModelScope.launch(Dispatchers.IO) {
            var tempInput: File? = null
            try {
                val format = _state.value.format
                val sourceName = _state.value.inputFileName

                // The engine reads a path, not a content URI, so the picked file is copied
                // into the cache first. The original extension comes with it, because the
                // engine works out what it is holding from the name.
                say("Reading the file")
                tempInput = File(
                    context.cacheDir,
                    "convert_input_${System.currentTimeMillis()}.${sourceExtension(context, uri, sourceName)}"
                )
                val copied = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempInput.outputStream().use { output -> input.copyTo(output) }
                    }
                }.getOrNull()

                if (copied == null || tempInput.length() == 0L) {
                    finishWithError("That file could not be read. Pick it again.")
                    return@launch
                }

                say("Converting to ${format.name}")
                _state.value = _state.value.copy(progress = 0.05f)

                // Everything already in the output folder, so the file this run produces
                // can be told apart from anything an earlier run left behind.
                val before = outputDir.listFiles()?.map { it.name }?.toSet().orEmpty()

                val baseName = safeBaseName(sourceName)
                val request = YoutubeDLRequest("file://${tempInput.absolutePath}").apply {
                    addOption("--enable-file-urls")
                    addOption("-x")
                    addOption("--audio-format", format.id)
                    addOption("--audio-quality", "0")
                    addOption("-o", File(outputDir, "$baseName.%(ext)s").absolutePath)
                }

                YoutubeDL.getInstance().execute(request, null) { progress, _, line ->
                    if (progress > 0f) {
                        _state.value = _state.value.copy(
                            progress = (progress.coerceIn(0f, 100f) / 100f) * 0.9f
                        )
                    }
                    readable(line)?.let { say(it, levelOf(it)) }
                }

                val produced = outputDir.listFiles()
                    ?.filter { it.isFile && it.name !in before }
                    ?.maxByOrNull { it.lastModified() }
                    ?: outputDir.listFiles()
                        ?.filter { it.isFile && it.name.startsWith(baseName) }
                        ?.maxByOrNull { it.lastModified() }

                if (produced == null || produced.length() == 0L) {
                    finishWithError("The conversion produced nothing. Try another format.")
                    return@launch
                }

                // Read before the move, because moving it is what takes it out of this
                // folder, and a file that is no longer there reports a length of nothing.
                val outputName = produced.name
                val outputSize = produced.length()

                say("Saving to ${StoragePaths.CONVERTED_DISPLAY}")
                _state.value = _state.value.copy(progress = 0.95f)

                val published = publish(context)

                _state.value = _state.value.copy(
                    isConverting = false,
                    isComplete = true,
                    progress = 1f,
                    statusLine = "",
                    outputFileName = outputName,
                    outputSizeBytes = outputSize,
                    outputPath = if (published) StoragePaths.CONVERTED_DISPLAY
                    else "Hazel's own storage",
                    savedToAppStorage = !published
                )
            } catch (e: Exception) {
                finishWithError(readable(e.message.orEmpty()) ?: "The conversion failed.")
            } finally {
                tempInput?.delete()
            }
        }
    }

    /**
     * Moves the finished file into the user's own Music folder.
     *
     * @return false when it had to be left in the app's storage, which up to Android 10
     *   is what a refused storage permission means. The caller says so on screen rather
     *   than reporting a success the user cannot go and find.
     */
    private fun publish(context: Context): Boolean {
        if (!PermissionHelper.canWriteSharedStorage(context)) return false
        return runCatching {
            MediaStoreHelper.moveToPublicStorage(
                context, outputDir, StoragePaths.MUSIC_RELATIVE_PATH, isMusic = true
            )
            outputDir.listFiles()?.none { it.isFile } ?: true
        }.getOrDefault(false)
    }

    private fun finishWithError(message: String) {
        _state.value = _state.value.copy(
            isConverting = false,
            progress = 0f,
            statusLine = "",
            error = message
        )
    }

    fun resetState() {
        _state.value = ConversionState(format = _state.value.format)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    // ── Reading what the engine says ──

    /**
     * Trims an engine line down to something worth putting on screen, or nothing.
     *
     * Every line carries the stage that produced it in brackets, which repeats on every
     * line and says nothing. What is left is either a sentence about the work or a file
     * path, and a path fills the line without telling the reader anything they can use.
     */
    private fun readable(line: String): String? {
        val cleaned = line.trim()
            .replace(TAG_PREFIX, "")
            .trim()
        if (cleaned.isBlank()) return null
        if (NOISE.any { cleaned.startsWith(it, ignoreCase = true) }) return null
        if (cleaned.startsWith("/") || cleaned.contains("/storage/")) return null
        return cleaned.take(120)
    }

    private fun levelOf(line: String): LogLevel {
        val lower = line.lowercase()
        return when {
            "error" in lower -> LogLevel.ERROR
            "warning" in lower -> LogLevel.WARN
            else -> LogLevel.INFO
        }
    }

    // ── Reading the picked file ──

    private fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

        // A provider is under no obligation to answer either query, and some do not. The
        // last path segment is a poor name but it is a real one, and it keeps the rest of
        // the run from being built on the word "Unknown".
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Video"
    }

    private fun fileSize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else 0L
        } ?: 0L
    }.getOrDefault(0L)

    /**
     * The extension to give the cached copy, which is how the engine recognises the file.
     *
     * The name is asked first and the MIME type second, because a provider that hands back
     * a name with no extension will usually still say what the file is.
     */
    private fun sourceExtension(context: Context, uri: Uri, name: String): String {
        val fromName = name.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } }
        if (fromName != null) return fromName.lowercase()

        val fromMime = runCatching {
            MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(context.contentResolver.getType(uri))
        }.getOrNull()

        return fromMime?.takeIf { it.isNotBlank() } ?: "mp4"
    }

    /**
     * The picked name, made safe to use as a filename.
     *
     * A display name comes from the provider and is not a path component: it can carry
     * separators and characters the filesystem will not take, and the output template is
     * built out of it.
     */
    private fun safeBaseName(name: String): String {
        val stem = name.substringBeforeLast('.', name).trim()
        val safe = stem.map { if (it in ILLEGAL || it.code < 32) '_' else it }
            .joinToString("")
            .trim('_', ' ', '.')
        return safe.take(80).ifBlank { "audio" }
    }

    private companion object {
        val TAG_PREFIX = Regex("""^\[[^]]+]\s*""")
        val NOISE = listOf(
            "Extracting URL", "Downloading webpage", "Downloading 1 format",
            "Deleting original file"
        )
        const val ILLEGAL = "/\\:*?\"<>|"
    }
}
