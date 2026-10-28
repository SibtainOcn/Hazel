package com.hazel.android.download

import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks the format picks against a real link, read by the installed yt-dlp. Skipped unless
 * HAZEL_HARNESS_JSON names a `yt-dlp -J <url>` dump, so CI never needs the network:
 *
 *   yt-dlp -J <url> > link.json
 *   HAZEL_HARNESS_JSON=link.json ./gradlew :app:testGithubDebugUnitTest --tests "*FormatPickHarnessTest" --rerun
 *
 * Every pick is written to `link.json.picks` (choice|codec|format id|vcodec or acodec|height),
 * ready to pass to `yt-dlp --simulate -f "<id>+bestaudio/<id>/bv*+ba/b" -S res:<height>`.
 */
class FormatPickHarnessTest {

    @Test
    fun `picks on a real link keep the chosen height and the preferred codec`() {
        val path = System.getenv("HAZEL_HARNESS_JSON")?.takeIf { it.isNotBlank() } ?: return
        val root = JSONObject(File(path).readText())
        val info = MediaProbe.parse(root.optString("webpage_url", "https://example.com/x"), root)
        val concrete = info.videoFormats.filter { !it.isGeneric }
        val out = StringBuilder()

        val choices = listOf(720, 0).map { h -> h to (if (h > 0) GenericFormats.heightCeiling(h) else MediaProbe.BEST_VIDEO) }
        for ((height, choice) in choices) {
            val plain = assertNotNull(GenericFormats.applyTo(info, choice, null))
            out.append("${height.takeIf { it > 0 } ?: "best"}|none|${plain.formatId}|${plain.vcodec}|${plain.height}\n")
            if (height > 0) assertTrue(plain.height <= height, "no preference went over $height: ${plain.formatId}")
            assertDirect(concrete, plain)

            for (codec in VideoCodec.entries) {
                val picked = assertNotNull(GenericFormats.applyTo(info, choice, null, codec))
                out.append("${height.takeIf { it > 0 } ?: "best"}|$codec|${picked.formatId}|${picked.vcodec}|${picked.height}\n")
                // A codec never costs resolution, and is taken whenever the link has it there.
                assertEquals(plain.height, picked.height, "$codec changed the height")
                if (concrete.any { it.height == plain.height && codec.matches(it) }) {
                    assertTrue(codec.matches(picked), "$codec offered at ${plain.height}p but ${picked.formatId} picked")
                    assertDirect(concrete.filter { codec.matches(it) }, picked)
                }
            }
        }

        val audio = info.audioFormats.filter { !it.isGeneric }
        for (codec in listOf<AudioCodec?>(null) + AudioCodec.entries) {
            val picked = assertNotNull(GenericFormats.applyTo(info, BatchAudioFormats.BEST, null, audioCodec = codec))
            out.append("audio|${codec ?: "none"}|${picked.formatId}|${picked.acodec}|0\n")
            if (codec != null && audio.any { codec.matches(it) }) {
                assertTrue(codec.matches(picked), "$codec offered but ${picked.formatId} picked")
            }
        }
        File("$path.picks").writeText(out.toString())
    }

    /** An HLS pick only where no direct stream of that height exists. */
    private fun assertDirect(pool: List<MediaFormat>, picked: MediaFormat) {
        if (picked.hls) {
            assertTrue(pool.none { !it.hls && it.height == picked.height }, "HLS ${picked.formatId} picked over a direct stream")
        }
    }
}
