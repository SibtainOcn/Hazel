package com.hazel.android.gallery

import android.content.Context
import java.io.File

/**
 * gallery-dl's log of each run, kept in the cache: the last [KEEP] runs, each cut to its last
 * [MAX_BYTES], with sign-in values taken out. Blocking; call off the main thread.
 */
object GalleryLogs {

    const val DIRECTORY_NAME = "gallery-logs"
    internal const val MAX_BYTES = 64 * 1024
    internal const val KEEP = 5
    private const val RAW_PREFIX = "run-"
    private const val LOG_PREFIX = "gallery-"
    private const val SUFFIX = ".log"
    private const val REDACTED = "<redacted>"

    fun dir(context: Context): File = File(context.cacheDir, DIRECTORY_NAME)

    /** Where one run's stderr goes while it runs. */
    fun rawFile(dir: File): File {
        dir.mkdirs()
        return File.createTempFile(RAW_PREFIX, SUFFIX, dir)
    }

    /**
     * Turns a finished run's raw log into a kept one: its tail, redacted, saved beside the last
     * few. The raw file is removed. Returns the kept text.
     */
    fun keep(dir: File, raw: File, secrets: List<String>, now: Long = System.currentTimeMillis()): String {
        val text = try {
            redact(tail(raw, MAX_BYTES), secrets)
        } finally {
            raw.delete()
        }
        if (text.isNotBlank()) {
            runCatching { File(dir, "$LOG_PREFIX$now-${raw.nameWithoutExtension.takeLast(6)}$SUFFIX").writeText(text) }
        }
        prune(dir)
        return text
    }

    /** Last [maxBytes] of [file], starting at a whole line. */
    internal fun tail(file: File, maxBytes: Int): String = runCatching {
        val length = file.length()
        file.inputStream().use { input ->
            val skip = (length - maxBytes).coerceAtLeast(0)
            input.skip(skip)
            val text = input.readBytes().decodeToString()
            if (skip > 0) text.substringAfter('\n') else text
        }
    }.getOrDefault("")

    /** Takes out cookie values, tokens and the given [secrets] (such as the cookie file's path). */
    internal fun redact(text: String, secrets: List<String>): String {
        var out = text
        secrets.filter { it.length >= 4 }.forEach { out = out.replace(it, REDACTED) }
        out = HEADER.replace(out) { "${it.groupValues[1]}: $REDACTED" }
        out = BEARER.replace(out) { "${it.groupValues[1]} $REDACTED" }
        out = PAIR.replace(out) { "${it.groupValues[1]}=$REDACTED" }
        return out
    }

    /** Leaves the newest [KEEP] kept logs. */
    internal fun prune(dir: File) {
        val files = dir.listFiles() ?: return
        files.filter { it.name.startsWith(LOG_PREFIX) }
            .sortedByDescending { it.name.removePrefix(LOG_PREFIX).substringBefore('-').toLongOrNull() ?: 0L }
            .drop(KEEP)
            .forEach { it.delete() }
    }

    private val HEADER = Regex("""(?im)\b(cookie|set-cookie|authorization|x-csrf-token|x-ig-www-claim)\s*:\s*[^\r\n]*""")
    private val BEARER = Regex("""(?i)\b(bearer|basic)\s+[A-Za-z0-9._~+/=-]{8,}""")
    private val PAIR = Regex(
        """(?i)\b(sessionid|session|csrftoken|ds_user_id|auth_token|ct0|access_token|refresh_token|token|sid|password|passwd|api_key|apikey|key)=([^&\s;"',]+)"""
    )
}
