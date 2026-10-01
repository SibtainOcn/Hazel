package com.hazel.android.download

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Which step of the quality ladder a video stream stands on, for grouping a format list by
 * resolution.
 *
 * The raw height is not that step. A film cropped to 1920×800 is a 1080p stream, a vertical
 * clip of 1080×1920 is too, and an encoder that rounds to 1072 or 1088 lines has not made a
 * new quality. So the picture is measured the way the ladder itself is named: by the height
 * a 16:9 frame of the same detail would have, taken from whichever side carries more of it,
 * and then put on the nearest familiar step where it is close to one. A size that is near
 * none of them (a 5K master, a webcam's 600 lines) keeps its own number rather than being
 * pushed onto a step it does not belong to.
 *
 * Returns 0 for a stream with no picture size at all, which has no step to stand on.
 */
fun MediaFormat.qualityRung(): Int {
    if (!hasVideo) return 0
    val (width, measured) = frameSize() ?: (0 to height)
    if (measured <= 0) return 0

    val short = if (width > 0) min(width, measured) else measured
    val long = if (width > 0) max(width, measured) else 0
    val equivalent = max(short, long * 9 / 16)

    val nearest = LADDER.minBy { abs(it - equivalent) }
    return if (abs(nearest - equivalent) <= nearest * SNAP_TOLERANCE) nearest else equivalent
}

/** The steps sites name their streams by. */
private val LADDER = intArrayOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)

/** How far off a step a picture can be and still be counted as that step. */
private const val SNAP_TOLERANCE = 0.08

/**
 * The width and height written into the label as "1920x1080", which is where both sides of
 * the picture are reported; the format itself carries only the height.
 */
private fun MediaFormat.frameSize(): Pair<Int, Int>? {
    val match = FRAME.find(label) ?: return null
    val w = match.groupValues[1].toIntOrNull() ?: return null
    val h = match.groupValues[2].toIntOrNull() ?: return null
    return if (w > 0 && h > 0) w to h else null
}

private val FRAME = Regex("""(\d{2,5})\s*[xX×]\s*(\d{2,5})""")
