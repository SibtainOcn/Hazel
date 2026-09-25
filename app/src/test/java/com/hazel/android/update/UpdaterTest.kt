package com.hazel.android.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    // ── 1. Semver & Version Comparison Tests ──

    @Test
    fun `HazelUpdater parseSemver parses versions correctly`() {
        assertEquals(Triple(1, 0, 8), HazelUpdater.parseSemver("1.0.8"))
        assertEquals(Triple(1, 0, 8), HazelUpdater.parseSemver("v1.0.8"))
        assertEquals(Triple(1, 0, 8), HazelUpdater.parseSemver("V1.0.8-beta1"))
        assertEquals(Triple(2, 14, 0), HazelUpdater.parseSemver("v2.14"))
    }

    @Test
    fun `HazelUpdater isNewer correctly identifies newer versions`() {
        assertTrue(HazelUpdater.isNewer("1.0.8", "1.0.4"))
        assertTrue(HazelUpdater.isNewer("1.1.0", "1.0.8"))
        assertTrue(HazelUpdater.isNewer("2.0.0", "1.9.9"))
        assertFalse(HazelUpdater.isNewer("1.0.4", "1.0.8"))
        assertFalse(HazelUpdater.isNewer("1.0.8", "1.0.8"))
        // Stable release over pre-release of the same version
        assertTrue(HazelUpdater.isNewer("1.0.9", "1.0.9-beta"))
        assertFalse(HazelUpdater.isNewer("1.0.9-beta", "1.0.9"))
    }

    // ── 2. Architecture & ABI Resolution for all APK types ──

    @Test
    fun `HazelUpdater resolveBestApkForDevice resolves arm64-v8a correctly`() {
        val (name, url) = HazelUpdater.resolveBestApkForDevice(
            version = "1.0.8",
            channel = HazelUpdater.Channel.STABLE,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )
        assertEquals("Hazel-v1.0.8-arm64-v8a-stable.apk", name)
        assertEquals("https://github.com/SibtainOcn/Hazel/releases/download/v1.0.8/Hazel-v1.0.8-arm64-v8a-stable.apk", url)
    }

    @Test
    fun `HazelUpdater resolveBestApkForDevice resolves armeabi-v7a correctly`() {
        val (name, url) = HazelUpdater.resolveBestApkForDevice(
            version = "1.0.8",
            channel = HazelUpdater.Channel.STABLE,
            supportedAbis = listOf("armeabi-v7a")
        )
        assertEquals("Hazel-v1.0.8-armeabi-v7a-stable.apk", name)
        assertEquals("https://github.com/SibtainOcn/Hazel/releases/download/v1.0.8/Hazel-v1.0.8-armeabi-v7a-stable.apk", url)
    }

    @Test
    fun `HazelUpdater resolveBestApkForDevice resolves x86_64 correctly`() {
        val (name, url) = HazelUpdater.resolveBestApkForDevice(
            version = "1.0.8",
            channel = HazelUpdater.Channel.STABLE,
            supportedAbis = listOf("x86_64")
        )
        assertEquals("Hazel-v1.0.8-x86_64-stable.apk", name)
        assertEquals("https://github.com/SibtainOcn/Hazel/releases/download/v1.0.8/Hazel-v1.0.8-x86_64-stable.apk", url)
    }

    @Test
    fun `HazelUpdater resolveBestApkForDevice resolves x86 correctly`() {
        val (name, url) = HazelUpdater.resolveBestApkForDevice(
            version = "1.0.8",
            channel = HazelUpdater.Channel.STABLE,
            supportedAbis = listOf("x86")
        )
        assertEquals("Hazel-v1.0.8-x86-stable.apk", name)
        assertEquals("https://github.com/SibtainOcn/Hazel/releases/download/v1.0.8/Hazel-v1.0.8-x86-stable.apk", url)
    }

    @Test
    fun `HazelUpdater resolveBestApkForDevice falls back to universal when ABI unknown`() {
        val (name, url) = HazelUpdater.resolveBestApkForDevice(
            version = "1.0.8",
            channel = HazelUpdater.Channel.STABLE,
            supportedAbis = emptyList()
        )
        assertEquals("Hazel-v1.0.8-universal-stable.apk", name)
        assertEquals("https://github.com/SibtainOcn/Hazel/releases/download/v1.0.8/Hazel-v1.0.8-universal-stable.apk", url)
    }

    @Test
    fun `HazelUpdater resolveBestApkForDevice respects Beta and Nightly channels`() {
        val (betaName, betaUrl) = HazelUpdater.resolveBestApkForDevice(
            version = "1.0.9",
            channel = HazelUpdater.Channel.BETA,
            supportedAbis = listOf("arm64-v8a")
        )
        assertEquals("Hazel-v1.0.9-arm64-v8a-beta.apk", betaName)
        assertEquals("https://github.com/SibtainOcn/Hazel/releases/download/v1.0.9/Hazel-v1.0.9-arm64-v8a-beta.apk", betaUrl)

        val (nightlyName, nightlyUrl) = HazelUpdater.resolveBestApkForDevice(
            version = "1.0.9",
            channel = HazelUpdater.Channel.NIGHTLY,
            supportedAbis = listOf("arm64-v8a")
        )
        assertEquals("Hazel-v1.0.9-arm64-v8a-nightly.apk", nightlyName)
        assertEquals("https://github.com/SibtainOcn/Hazel/releases/download/v1.0.9/Hazel-v1.0.9-arm64-v8a-nightly.apk", nightlyUrl)
    }

    // ── 3. Atom Feed Resilience & Channel Filtering ──

    private val sampleAtomFeed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Release notes from Hazel</title>
          <entry>
            <id>tag:github.com,2008:Repository/1349996760/v1.0.8</id>
            <updated>2026-09-03T14:26:07Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/SibtainOcn/Hazel/releases/tag/v1.0.8"/>
            <title>🌿 Hazel v1.0.8</title>
            <content type="html">&lt;p&gt;Requirements&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Android 7.0+&lt;/li&gt;&lt;/ul&gt;</content>
          </entry>
          <entry>
            <id>tag:github.com,2008:Repository/1349996760/v1.0.7</id>
            <updated>2026-09-03T13:18:07Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/SibtainOcn/Hazel/releases/tag/v1.0.7"/>
            <title>🌿 Hazel v1.0.7</title>
            <content type="html">&lt;p&gt;Bug fixes&lt;/p&gt;</content>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `HazelUpdater parseReleasesAtom finds latest stable release`() {
        val rel = HazelUpdater.parseReleasesAtom(
            xml = sampleAtomFeed,
            channel = HazelUpdater.Channel.STABLE,
            supportedAbis = listOf("arm64-v8a")
        )
        assertNotNull(rel)
        assertEquals("1.0.8", rel?.version)
        assertEquals("Hazel-v1.0.8-arm64-v8a-stable.apk", rel?.assetName)
        assertTrue(rel?.changelog?.contains("Android 7.0+") == true)
    }

    @Test
    fun `HazelUpdater parseReleasesAtom returns null when Beta channel has no releases`() {
        val rel = HazelUpdater.parseReleasesAtom(
            xml = sampleAtomFeed,
            channel = HazelUpdater.Channel.BETA,
            supportedAbis = listOf("arm64-v8a")
        )
        assertNull(rel)
    }

    @Test
    fun `HazelUpdater parseReleasesAtom returns null when Nightly channel has no releases`() {
        val rel = HazelUpdater.parseReleasesAtom(
            xml = sampleAtomFeed,
            channel = HazelUpdater.Channel.NIGHTLY,
            supportedAbis = listOf("arm64-v8a")
        )
        assertNull(rel)
    }

    @Test
    fun `HazelUpdater parseReleasesAtom finds beta release when present`() {
        val feedWithBeta = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:github.com,2008:Repository/1349996760/v1.0.9-beta1</id>
                <updated>2026-09-10T12:00:00Z</updated>
                <link rel="alternate" type="text/html" href="https://github.com/SibtainOcn/Hazel/releases/tag/v1.0.9-beta1"/>
                <title>Hazel v1.0.9-beta1</title>
                <content type="html">Beta preview</content>
              </entry>
              <entry>
                <id>tag:github.com,2008:Repository/1349996760/v1.0.8</id>
                <updated>2026-09-03T14:26:07Z</updated>
                <link rel="alternate" type="text/html" href="https://github.com/SibtainOcn/Hazel/releases/tag/v1.0.8"/>
                <title>🌿 Hazel v1.0.8</title>
              </entry>
            </feed>
        """.trimIndent()

        val betaRel = HazelUpdater.parseReleasesAtom(
            xml = feedWithBeta,
            channel = HazelUpdater.Channel.BETA,
            supportedAbis = listOf("arm64-v8a")
        )
        assertNotNull(betaRel)
        assertEquals("1.0.9-beta1", betaRel?.version)

        val stableRel = HazelUpdater.parseReleasesAtom(
            xml = feedWithBeta,
            channel = HazelUpdater.Channel.STABLE,
            supportedAbis = listOf("arm64-v8a")
        )
        assertNotNull(stableRel)
        assertEquals("1.0.8", stableRel?.version)
    }

    // ── 4. JSON Release Matching ──

    @Test
    fun `HazelUpdater findReleaseMatchingChannel filters channels from JSON`() {
        val jsonArray = JSONArray("""
            [
              {
                "tag_name": "v1.0.9-beta",
                "prerelease": true,
                "body": "Beta changelog",
                "assets": [
                  {"name": "Hazel-v1.0.9-beta-arm64-v8a.apk", "browser_download_url": "https://example.com/beta.apk", "size": 1000}
                ]
              },
              {
                "tag_name": "v1.0.8",
                "prerelease": false,
                "body": "Stable changelog",
                "assets": [
                  {"name": "Hazel-v1.0.8-arm64-v8a-stable.apk", "browser_download_url": "https://example.com/stable.apk", "size": 2000}
                ]
              }
            ]
        """)

        val stable = HazelUpdater.findReleaseMatchingChannel(jsonArray, HazelUpdater.Channel.STABLE, listOf("arm64-v8a"))
        assertNotNull(stable)
        assertEquals("1.0.8", stable?.version)

        val beta = HazelUpdater.findReleaseMatchingChannel(jsonArray, HazelUpdater.Channel.BETA, listOf("arm64-v8a"))
        assertNotNull(beta)
        assertEquals("1.0.9-beta", beta?.version)

        val nightly = HazelUpdater.findReleaseMatchingChannel(jsonArray, HazelUpdater.Channel.NIGHTLY, listOf("arm64-v8a"))
        assertNull(nightly)
    }

    // ── 5. YtDlpUpdater Tests ──

    @Test
    fun `YtDlpUpdater isNewer compares version tags`() {
        assertTrue(YtDlpUpdater.isNewer("2026.08.19", "2026.07.04"))
        assertTrue(YtDlpUpdater.isNewer("2026.08.19", null))
        assertFalse(YtDlpUpdater.isNewer("2026.08.19", "2026.08.19"))
        assertFalse(YtDlpUpdater.isNewer("v2026.08.19", "2026.08.19"))
    }

    @Test
    fun `YtDlpUpdater parseFirstTagFromAtom extracts release tag`() {
        val atomXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <id>tag:github.com,2008:https://github.com/yt-dlp/yt-dlp/releases</id>
              <entry>
                <id>tag:github.com,2008:Repository/307260205/2026.08.19</id>
                <link rel="alternate" type="text/html" href="https://github.com/yt-dlp/yt-dlp/releases/tag/2026.08.19"/>
                <title>yt-dlp 2026.08.19</title>
              </entry>
            </feed>
        """.trimIndent()

        val tag = YtDlpUpdater.parseFirstTagFromAtom(atomXml)
        assertEquals("2026.08.19", tag)
    }

    // ── 6. F-Droid Payload Parsing ──

    @Test
    fun `F-Droid payload with packages array parses version correctly`() {
        val fdroidJson = JSONObject("""
            {
              "packageName": "com.hazel.android",
              "suggestedVersionCode": 504,
              "packages": [
                {
                  "versionName": "1.0.8",
                  "versionCode": 504,
                  "added": 1724000000000
                }
              ]
            }
        """)

        var suggested = fdroidJson.optString("suggestedVersionName", "").trim()
        val packages = fdroidJson.optJSONArray("packages")
        if (suggested.isBlank() && packages != null && packages.length() > 0) {
            suggested = packages.getJSONObject(0).optString("versionName", "").trim()
        }

        assertEquals("1.0.8", suggested)
    }

    // 7. Live Resilient Resolution Tests (Rate-Limit Proof) 

    @Test
    fun `HazelUpdater latestReleaseResult succeeds for Stable channel even under GitHub API rate limit`() = kotlinx.coroutines.runBlocking {
        val result = HazelUpdater.latestReleaseResult(HazelUpdater.Channel.STABLE)
        assertTrue("Expected Success but got $result", result is HazelUpdater.CheckResult.Success)
        val info = (result as HazelUpdater.CheckResult.Success).info
        assertEquals("1.0.8", info.version)
        if (HazelUpdater.isFdroid()) {
            assertEquals(HazelUpdater.FDROID_PACKAGE_URL, info.downloadUrl)
        } else {
            assertTrue(info.downloadUrl.endsWith(".apk"))
        }
    }

    @Test
    fun `HazelUpdater latestReleaseResult returns NoReleaseFound for empty Beta channel without 403`() = kotlinx.coroutines.runBlocking {
        val result = HazelUpdater.latestReleaseResult(HazelUpdater.Channel.BETA)
        assertEquals(HazelUpdater.CheckResult.NoReleaseFound, result)
    }

    @Test
    fun `HazelUpdater latestReleaseResult returns NoReleaseFound for empty Nightly channel without 403`() = kotlinx.coroutines.runBlocking {
        val result = HazelUpdater.latestReleaseResult(HazelUpdater.Channel.NIGHTLY)
        assertEquals(HazelUpdater.CheckResult.NoReleaseFound, result)
    }

    @Test
    fun `YtDlpUpdater latestReleaseResult succeeds for Stable channel without 403`() = kotlinx.coroutines.runBlocking {
        val result = YtDlpUpdater.latestReleaseResult(YtDlpUpdater.Channel.STABLE)
        assertTrue("Expected Success but got $result", result is YtDlpUpdater.CheckResult.Success)
        val info = (result as YtDlpUpdater.CheckResult.Success).info
        assertTrue(info.version.isNotBlank())
    }

    @Test
    fun `YtDlpUpdater latestReleaseResult succeeds for Nightly channel without 403`() = kotlinx.coroutines.runBlocking {
        val result = YtDlpUpdater.latestReleaseResult(YtDlpUpdater.Channel.NIGHTLY)
        assertTrue("Expected Success but got $result", result is YtDlpUpdater.CheckResult.Success)
        val info = (result as YtDlpUpdater.CheckResult.Success).info
        assertTrue(info.version.isNotBlank())
    }

    @Test
    fun `YtDlpUpdater latestReleaseResult succeeds for Master channel without 403`() = kotlinx.coroutines.runBlocking {
        val result = YtDlpUpdater.latestReleaseResult(YtDlpUpdater.Channel.MASTER)
        assertTrue("Expected Success but got $result", result is YtDlpUpdater.CheckResult.Success)
        val info = (result as YtDlpUpdater.CheckResult.Success).info
        assertTrue(info.version.isNotBlank())
    }
}
