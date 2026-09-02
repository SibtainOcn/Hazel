package com.hazel.android.download

import org.json.JSONObject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * The parser is fed the shapes yt-dlp actually hands back, saved rather than fetched.
 *
 * Its whole job is to degrade instead of failing. YouTube fills in every field, and most
 * other extractors do not: Instagram commonly omits the title, the uploader, the duration,
 * the codec names and every per-format size, and sometimes describes the media as a single
 * direct stream with no format list at all. Each of those is a case where the app has to
 * carry on with less, and none of them raises anything if it stops working.
 */
class MediaProbeParseTest {

    private fun parse(json: String) = MediaProbe.parse("https://example.com/x", JSONObject(json))

    // A full payload, of the shape a well described source returns.
    private val complete = """
        {
          "id": "kUox2TPnpzo",
          "title": "Games Everyone Hated",
          "uploader": "L321",
          "duration": 1187,
          "thumbnails": [
            {"url": "https://img/small.jpg", "width": 120},
            {"url": "https://img/large.jpg", "width": 1280}
          ],
          "formats": [
            {"format_id": "399", "ext": "mp4", "vcodec": "av01.0.08M", "acodec": "none",
             "height": 1080, "fps": 60, "tbr": 3330.0, "filesize": 455606272},
            {"format_id": "137", "ext": "mp4", "vcodec": "avc1.640028", "acodec": "none",
             "height": 1080, "fps": 30, "tbr": 4710.0, "filesize": 644245094},
            {"format_id": "251", "ext": "webm", "vcodec": "none", "acodec": "opus",
             "tbr": 130.0, "filesize": 19293798}
          ]
        }
    """.trimIndent()

    @Test
    fun `a complete payload comes back whole`() {
        val info = parse(complete)

        assertEquals("Games Everyone Hated", info.title)
        assertEquals("L321", info.uploader)
        assertEquals(1187, info.durationSeconds)
        assertEquals("https://img/large.jpg", info.thumbnail)
    }

    @Test
    fun `video and audio streams are told apart`() {
        val info = parse(complete)

        assertTrue(info.videoFormats.any { it.formatId == "399" })
        assertTrue(info.videoFormats.any { it.formatId == "137" })
        assertTrue(info.audioFormats.any { it.formatId == "251" })
        assertFalse(info.videoFormats.any { it.formatId == "251" })
    }

    @Test
    fun `a size the source stated is taken as stated`() {
        val format = parse(complete).videoFormats.first { it.formatId == "399" }

        assertEquals(455_606_272L, format.fileSizeBytes)
        assertFalse(format.isEstimatedSize)
    }

    @Test
    fun `a size the source withheld is worked out from the bitrate and marked as a guess`() {
        val info = parse(
            """
            {
              "title": "No sizes here",
              "duration": 100,
              "formats": [
                {"format_id": "22", "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a",
                 "height": 720, "tbr": 1000.0}
              ]
            }
            """.trimIndent()
        )

        val format = info.videoFormats.first { !it.isGeneric }
        assertTrue(format.isEstimatedSize)
        assertTrue(format.fileSizeBytes > 0)
    }

    @Test
    fun `a source describing one direct stream still offers it as a real format`() {
        val info = parse("""{"title": "Bare", "url": "https://cdn/clip.mp4"}""")

        // No format list at all, which is the ordinary Instagram shape. The stream the
        // payload points at is offered under a synthesised id rather than the media being
        // called undownloadable, and it is assumed to carry both tracks because there is
        // nothing in the payload saying otherwise.
        val synthesised = info.videoFormats.first { !it.isGeneric }
        assertEquals("0", synthesised.formatId)
        assertTrue(synthesised.hasVideo)
        assertTrue(synthesised.hasAudio)
        assertTrue(info.hasResolvedFormats)
    }

    @Test
    fun `a best row stands in only where the source named no formats of that kind`() {
        // One direct stream and no format list: the video tab has a real entry, and the
        // audio tab has nothing of its own, so the generic row is what makes an audio
        // download of it possible at all.
        val bare = parse("""{"title": "Bare", "url": "https://cdn/clip.mp4"}""")
        assertTrue(bare.videoFormats.none { it.isGeneric })
        assertTrue(bare.audioFormats.single().isGeneric)
        assertTrue(bare.audioFormats.single().selector.isNotBlank())

        // A source that reported both kinds is left as it reported them.
        val full = parse(complete)
        assertTrue(full.videoFormats.none { it.isGeneric })
        assertTrue(full.audioFormats.none { it.isGeneric })
    }

    @Test
    fun `a payload with no title at all does not come back blank`() {
        val info = parse("""{"id": "abc123", "url": "https://cdn/clip.mp4"}""")
        assertTrue(info.title.isNotBlank())
    }

    @Test
    fun `a carousel wrapper falls through to the entry that carries the media`() {
        val info = parse(
            """
            {
              "_type": "playlist",
              "title": "A post with two parts",
              "entries": [
                {"id": "cover", "title": "Cover"},
                {"id": "clip", "title": "The clip", "duration": 30,
                 "formats": [
                   {"format_id": "0", "ext": "mp4", "vcodec": "avc1", "acodec": "mp4a",
                    "height": 720, "tbr": 900.0, "filesize": 3375000}
                 ]}
              ]
            }
            """.trimIndent()
        )

        assertEquals("The clip", info.title)
        assertTrue(info.videoFormats.any { it.formatId == "0" })
    }

    @Test
    fun `a format the source described as unavailable is not offered`() {
        val info = parse(
            """
            {
              "title": "Mixed",
              "formats": [
                {"format_id": "gone", "ext": "mp4", "vcodec": "none", "acodec": "none"},
                {"format_id": "good", "ext": "mp4", "vcodec": "avc1", "acodec": "none",
                 "height": 720, "tbr": 900.0, "filesize": 3375000}
              ]
            }
            """.trimIndent()
        )

        assertFalse(info.videoFormats.any { it.formatId == "gone" })
        assertFalse(info.audioFormats.any { it.formatId == "gone" })
    }

    @Test
    fun `the same format listed twice is only offered once`() {
        val info = parse(
            """
            {
              "title": "Repeats",
              "formats": [
                {"format_id": "137", "ext": "mp4", "vcodec": "avc1", "acodec": "none",
                 "height": 1080, "tbr": 4000.0, "filesize": 100},
                {"format_id": "137", "ext": "mp4", "vcodec": "avc1", "acodec": "none",
                 "height": 1080, "tbr": 4000.0, "filesize": 100}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, info.videoFormats.count { it.formatId == "137" })
    }

    @Test
    fun `a video only stream is paired with an audio track to merge`() {
        val info = parse(complete)

        assertNotNull(info.mergeAudio)
        assertEquals("251", info.mergeAudio!!.formatId)
        assertFalse(info.bestVideo!!.hasAudio)
    }

    @Test
    fun `the url asked about is the url reported back`() {
        val info = MediaProbe.parse("https://example.com/asked", JSONObject(complete))
        assertEquals("https://example.com/asked", info.url)
    }

    @Test
    fun `a source that states its own address is believed over the one asked about`() {
        // A share link and a shortened link both resolve to a canonical page, and the
        // engine reports that page. Keeping the asked address here would file the same
        // media under two identities.
        val info = MediaProbe.parse(
            "https://youtu.be/kUox2TPnpzo?si=abc",
            JSONObject("""{"title": "T", "webpage_url": "https://www.youtube.com/watch?v=kUox2TPnpzo", "url": "https://cdn/c.mp4"}""")
        )
        assertEquals("https://www.youtube.com/watch?v=kUox2TPnpzo", info.url)
    }

    @Test
    fun `a thumbnail list with no usable entry reports none rather than failing`() {
        val info = parse("""{"title": "No art", "thumbnails": [], "url": "https://cdn/c.mp4"}""")
        assertNull(info.thumbnail)
    }
}
