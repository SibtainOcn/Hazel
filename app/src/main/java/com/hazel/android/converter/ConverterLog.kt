package com.hazel.android.converter

/** Severity of a single line in the converter's step list. */
enum class LogLevel { INFO, SUCCESS, WARN, ERROR }

/**
 * One step in the converter's progress list. [durationMs] is filled in once the step
 * finishes, which is what the UI uses to stop shimmering the active row.
 */
data class LogEntry(
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long? = null
)
