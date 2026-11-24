package com.hazel.android.update

import com.hazel.android.update.HazelUpdater.Channel
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HazelChannelsTest {

    private val arm64 = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

    private fun v(text: String) = HazelVersion.parse(text)!!

    @Test
    fun `versions order nightly, beta, stable, then the next version`() {
        val ordered = listOf(
            "1.1.17", "1.1.18-nightly.3", "1.1.18-nightly.12", "1.1.18-beta.1", "1.1.18-beta.2", "1.1.18", "1.1.19-nightly.1"
        ).map(::v)
        assertEquals(ordered, ordered.shuffled().sorted())
    }

    @Test
    fun `isNewer moves along a channel and onto its stable`() {
        assertTrue(HazelUpdater.isNewer("1.1.18-beta.2", "1.1.18-beta.1"))
        assertTrue(HazelUpdater.isNewer("1.1.18-nightly.13", "1.1.18-nightly.12"))
        assertTrue(HazelUpdater.isNewer("1.1.18", "1.1.18-beta.2"))
        assertTrue(HazelUpdater.isNewer("1.1.18-beta.1", "1.1.18-nightly.40"))
        assertFalse(HazelUpdater.isNewer("1.1.18-nightly.40", "1.1.18-beta.1"))
        assertFalse(HazelUpdater.isNewer("1.1.17", "1.1.18-nightly.1"))
        assertFalse(HazelUpdater.isNewer("nightly", "1.1.17"))
    }

    @Test
    fun `versions are read from titles and never from architecture words`() {
        assertEquals("1.1.18-nightly.3", HazelVersion.find("🌙 Hazel 1.1.18-nightly.3").toString())
        assertEquals("1.1.17", HazelVersion.find("Hazel-v1.1.17-arm64-v8a-stable.apk").toString())
        assertEquals(HazelVersion.Stage.BETA, v("v1.0.9-beta1").stage)
        assertEquals(1, v("v1.0.9-beta1").build)
        assertNull(HazelVersion.parse("nightly"))
    }

    @Test
    fun `a build ahead of the picked channel waits for its next release`() {
        assertTrue(HazelUpdater.waitsForChannel(Channel.STABLE, "1.1.18-nightly.3"))
        assertTrue(HazelUpdater.waitsForChannel(Channel.BETA, "1.1.18-nightly.3"))
        assertFalse(HazelUpdater.waitsForChannel(Channel.NIGHTLY, "1.1.18-nightly.3"))
        assertTrue(HazelUpdater.waitsForChannel(Channel.STABLE, "1.1.18-beta.1"))
        assertFalse(HazelUpdater.waitsForChannel(Channel.STABLE, "1.1.17"))
    }

    private fun asset(name: String, size: Long = 100) =
        """{"name": "$name", "browser_download_url": "https://example.com/$name", "size": $size}"""

    private fun release(tag: String, name: String, prerelease: Boolean, vararg assets: String) = """
        {"tag_name": "$tag", "name": "$name", "prerelease": $prerelease, "draft": false, "body": "notes",
         "assets": [${assets.joinToString(",")}]}
    """

    private val releases = JSONArray(
        "[" + listOf(
            release(
                "nightly", "🌙 Hazel 1.1.18-nightly.3", true,
                asset("Hazel-v1.1.18-nightly.3-arm64-v8a-nightly.apk"),
                asset("Hazel-v1.1.18-nightly.3-universal-nightly.apk")
            ),
            release(
                "v1.1.18-beta.1", "🌿 Hazel v1.1.18-beta.1", true,
                asset("Hazel-v1.1.18-beta.1-arm64-v8a-beta.apk")
            ),
            release(
                "v1.1.17", "🌿 Hazel v1.1.17", false,
                asset("Hazel-v1.1.17-fdroid-arm64-v8a-stable.apk"),
                asset("Hazel-v1.1.17-arm64-v8a-stable.apk"),
                asset("Hazel-v1.1.17-universal-stable.apk")
            )
        ).joinToString(",") + "]"
    )

    @Test
    fun `each channel takes the newest release it accepts`() {
        val stable = HazelUpdater.findReleaseMatchingChannel(releases, Channel.STABLE, arm64)!!
        assertEquals("1.1.17", stable.version)
        assertEquals("Hazel-v1.1.17-arm64-v8a-stable.apk", stable.assetName)

        val beta = HazelUpdater.findReleaseMatchingChannel(releases, Channel.BETA, arm64)!!
        assertEquals("1.1.18-beta.1", beta.version)
        assertEquals(Channel.BETA, beta.channel)

        // nightly.3 comes before beta.1 of the same version, so a nightly user moves onto the beta.
        val nightly = HazelUpdater.findReleaseMatchingChannel(releases, Channel.NIGHTLY, arm64)!!
        assertEquals("1.1.18-beta.1", nightly.version)
    }

    @Test
    fun `the rolling nightly release is read from its title`() {
        val later = JSONArray(
            "[" + release(
                "nightly", "🌙 Hazel 1.1.19-nightly.2", true,
                asset("Hazel-v1.1.19-nightly.2-arm64-v8a-nightly.apk")
            ) + "]"
        )
        val nightly = HazelUpdater.findReleaseMatchingChannel(later, Channel.NIGHTLY, arm64)!!
        assertEquals("1.1.19-nightly.2", nightly.version)
        assertEquals(Channel.NIGHTLY, nightly.channel)
        assertNull(HazelUpdater.findReleaseMatchingChannel(later, Channel.STABLE, arm64))
    }

    @Test
    fun `a stable newer than the last nightly moves nightly users onto it`() {
        val list = JSONArray(
            "[" + listOf(
                release("nightly", "🌙 Hazel 1.1.18-nightly.40", true, asset("Hazel-v1.1.18-nightly.40-arm64-v8a-nightly.apk")),
                release("v1.1.18", "🌿 Hazel v1.1.18", false, asset("Hazel-v1.1.18-arm64-v8a-stable.apk"))
            ).joinToString(",") + "]"
        )
        assertEquals("1.1.18", HazelUpdater.findReleaseMatchingChannel(list, Channel.NIGHTLY, arm64)!!.version)
    }

    @Test
    fun `GitHub installs never pick an F-Droid APK and match the architecture exactly`() {
        val fdroidOnly = JSONArray(
            "[" + release("v1.1.17", "Hazel v1.1.17", false, asset("Hazel-v1.1.17-fdroid-arm64-v8a-stable.apk")) + "]"
        )
        assertNull(HazelUpdater.findReleaseMatchingChannel(fdroidOnly, Channel.STABLE, arm64))

        val rel = JSONArray(
            "[" + release(
                "v1.1.17", "Hazel v1.1.17", false,
                asset("Hazel-v1.1.17-arm64-v8a-stable.apk"),
                asset("Hazel-v1.1.17-x86_64-stable.apk"),
                asset("Hazel-v1.1.17-x86-stable.apk")
            ) + "]"
        ).getJSONObject(0)
        assertEquals("Hazel-v1.1.17-x86-stable.apk", HazelUpdater.findBestApkAsset(rel, listOf("x86")).first)
        assertEquals(
            "Hazel-v1.1.17-x86_64-stable.apk",
            HazelUpdater.findBestApkAsset(rel, listOf("x86_64", "x86", "arm64-v8a")).first
        )
    }

    @Test
    fun `the release feed builds the nightly link from its tag`() {
        val feed = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:github.com,2008:Repository/1/nightly</id>
                <updated>2026-11-24T02:40:00Z</updated>
                <link rel="alternate" type="text/html" href="https://github.com/SibtainOcn/Hazel/releases/tag/nightly"/>
                <title>🌙 Hazel 1.1.18-nightly.3</title>
              </entry>
              <entry>
                <id>tag:github.com,2008:Repository/1/v1.1.17</id>
                <link rel="alternate" type="text/html" href="https://github.com/SibtainOcn/Hazel/releases/tag/v1.1.17"/>
                <title>🌿 Hazel v1.1.17</title>
              </entry>
            </feed>
        """.trimIndent()
        val nightly = HazelUpdater.parseReleasesAtom(feed, Channel.NIGHTLY, arm64)!!
        assertEquals("1.1.18-nightly.3", nightly.version)
        assertEquals(
            "https://github.com/SibtainOcn/Hazel/releases/download/nightly/Hazel-v1.1.18-nightly.3-arm64-v8a-nightly.apk",
            nightly.downloadUrl
        )
        assertEquals("1.1.17", HazelUpdater.parseReleasesAtom(feed, Channel.STABLE, arm64)!!.version)
    }
}
