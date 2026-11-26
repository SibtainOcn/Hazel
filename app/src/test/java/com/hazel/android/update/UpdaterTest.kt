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
    fun `HazelUpdater parseReleasesAtom gives Beta the stable when no beta is newer`() {
        val rel = HazelUpdater.parseReleasesAtom(
            xml = sampleAtomFeed,
            channel = HazelUpdater.Channel.BETA,
            supportedAbis = listOf("arm64-v8a")
        )
        assertEquals("1.0.8", rel?.version)
        assertEquals(HazelUpdater.Channel.STABLE, rel?.channel)
    }

    @Test
    fun `HazelUpdater parseReleasesAtom gives Nightly the stable when no nightly is newer`() {
        val rel = HazelUpdater.parseReleasesAtom(
            xml = sampleAtomFeed,
            channel = HazelUpdater.Channel.NIGHTLY,
            supportedAbis = listOf("arm64-v8a")
        )
        assertEquals("1.0.8", rel?.version)
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

        // Nightly takes the newest it accepts, here the beta.
        val nightly = HazelUpdater.findReleaseMatchingChannel(jsonArray, HazelUpdater.Channel.NIGHTLY, listOf("arm64-v8a"))
        assertEquals("1.0.9-beta", nightly?.version)
    }

    // ── 5. YtDlpUpdater Tests ──

    @Test
    fun `YtDlpUpdater isNewer compares version tags`() {
        assertTrue(YtDlpUpdater.isNewer("2026.08.19", "2026.07.04"))
        assertTrue(YtDlpUpdater.isNewer("2026.08.19", null))
        assertFalse(YtDlpUpdater.isNewer("2026.08.19", "2026.08.19"))
        assertFalse(YtDlpUpdater.isNewer("v2026.08.19", "2026.08.19"))
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
                  "versionName": "1.0.9",
                  "versionCode": 505,
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

        assertEquals("1.0.9", suggested)
    }

    // 7. Live Resilient Resolution Tests (Rate-Limit Proof) 

    @Test
    fun `HazelUpdater latestReleaseResult succeeds for Stable channel even under GitHub API rate limit`() = kotlinx.coroutines.runBlocking {
        val result = HazelUpdater.latestReleaseResult(HazelUpdater.Channel.STABLE)
        assertTrue("Expected Success but got $result", result is HazelUpdater.CheckResult.Success)
        val info = (result as HazelUpdater.CheckResult.Success).info
        assertTrue("Version should match semver format", info.version.matches(Regex("""\d+\.\d+\.\d+.*""")))
        if (HazelUpdater.isFdroid()) {
            assertEquals(HazelUpdater.FDROID_PACKAGE_URL, info.downloadUrl)
        } else {
            assertTrue(info.downloadUrl.endsWith(".apk"))
        }
    }










}
