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
    fun `a video only stream is paired with an audio track to merge`() {
        val info = parse(complete)

        assertNotNull(info.mergeAudio)
        assertEquals("251", info.mergeAudio!!.formatId)
        assertFalse(info.bestVideo!!.hasAudio)
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



    // ---------- Artist resolution (Issues #1, #4) ----------

    @Test
    fun `artist field is preferred over uploader and channel`() {
        // JioSaavn shape: artist holds the performer, channel holds the record label.
        val info = parse(
            """
            {
              "title": "Tum Hi Ho",
              "artist": "Arijit Singh",
              "channel": "T-Series",
              "uploader": "T-Series",
              "duration": 261,
              "url": "https://cdn/song.m4a"
            }
            """.trimIndent()
        )

        assertEquals("Arijit Singh", info.uploader)
    }









    @Test
    fun `audio formats without acodec key are recognized as valid audio streams`() {
        // Shape returned by music extractors where vcodec is "none" but acodec is omitted
        val info = parse(
            """
            {
              "id": "cli-P2pu",
              "title": "Arz Kiya Hai",
              "artist": "Anuv Jain",
              "channel": "Music Label",
              "duration": 294,
              "formats": [
                {
                  "format_id": "128",
                  "ext": "m4a",
                  "abr": 128,
                  "tbr": 128,
                  "vcodec": "none",
                  "audio_ext": "m4a",
                  "video_ext": "none",
                  "resolution": "audio only",
                  "filesize_approx": 4704000
                },
                {
                  "format_id": "320",
                  "ext": "m4a",
                  "abr": 320,
                  "tbr": 320,
                  "vcodec": "none",
                  "audio_ext": "m4a",
                  "video_ext": "none",
                  "resolution": "audio only",
                  "filesize_approx": 11760000
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("Anuv Jain", info.uploader)
        assertTrue(info.hasResolvedFormats)
        assertEquals(2, info.audioFormats.size)
        // Sorted descending by bitrate
        assertEquals("320", info.audioFormats[0].formatId)
        assertEquals(320.0, info.audioFormats[0].bitrateKbps)
        assertEquals("M4A", info.audioFormats[0].codecLabel)
        assertEquals("128", info.audioFormats[1].formatId)
        assertEquals(128.0, info.audioFormats[1].bitrateKbps)
        assertEquals("M4A", info.audioFormats[1].codecLabel)
    }

    @Test
    fun `autoPick returns generic bounded format when concrete formats are pending`() {
        val fallback = MediaProbe.fallbackFor("https://youtube.com/watch?v=pending")
        assertFalse(fallback.hasResolvedFormats)

        val ceiling480 = fallback.autoPick(isVideo = true, maxHeight = 480)
        assertNotNull(ceiling480)
        assertEquals(480, ceiling480.height)
        assertEquals("480p", ceiling480.shortLabel)
        assertEquals("bv*[height<=480]+ba/b[height<=480]/bv*+ba/b", ceiling480.selector)
        assertTrue(ceiling480.isGeneric)

        val autoBest = fallback.autoPick(isVideo = true, maxHeight = 0)
        assertNotNull(autoBest)
        assertEquals("Best quality", autoBest.shortLabel)
        assertEquals("bv*+ba/b", autoBest.selector)
    }



    // Shaped on an Instagram reel read in September 2026: no duration anywhere, DASH streams
    // with no segment list, and progressive files whose codecs are not reported at all.
    private val reelWithoutDuration = """
        {
          "id": "Dd4-yVGTSUg",
          "duration": null,
          "formats": [
            {"format_id": "dash-1a", "protocol": "https", "url": "https://cdn/a.mp4",
             "vcodec": "none", "acodec": "mp4a.40.5"},
            {"format_id": "1", "protocol": "https", "url": "https://cdn/1.mp4",
             "vcodec": null, "acodec": null,
             "http_headers": {"User-Agent": "UA", "Referer": "https://www.instagram.com/"}},
            {"format_id": "dash-1v", "protocol": "https", "url": "https://cdn/v.mp4",
             "vcodec": "vp09.00.31.08", "acodec": "none"}
          ]
        }
    """.trimIndent()





    @Test
    fun `the file asked for its length is the progressive one, with its headers`() {
        val (address, headers) = assertNotNull(MediaProbe.directMediaUrl(JSONObject(reelWithoutDuration)))
        assertEquals("https://cdn/1.mp4", address)
        assertEquals("https://www.instagram.com/", headers["Referer"])
    }


}

