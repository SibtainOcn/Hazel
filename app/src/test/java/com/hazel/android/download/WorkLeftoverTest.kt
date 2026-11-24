package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkLeftoverTest {

    @Test
    fun `yt-dlp working files and the trim marker are leftovers`() {
        listOf(
            "clip.mp4.part", "clip.f137.mp4.part-Frag12", "clip.mp4.ytdl", "clip.mp4.temp", LocalTrim.DONE_MARKER
        ).forEach { assertTrue(isWorkLeftover(it), it) }
    }

    @Test
    fun `a finished file whose title starts with a dot is kept`() {
        listOf("...And Justice for All.mp4", ".clip.webm", "clip.mp4", "clip.en.vtt", "Part 2.mp4")
            .forEach { assertFalse(isWorkLeftover(it), it) }
    }
}
