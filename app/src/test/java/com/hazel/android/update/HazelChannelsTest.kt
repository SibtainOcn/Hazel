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


}
