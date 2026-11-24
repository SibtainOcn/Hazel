package com.hazel.android.ui.screens.cookies

import androidx.webkit.UserAgentMetadata

/**
 * How the sign-in page presents itself and where it starts.
 *
 * Sites such as Instagram, Facebook and Google grey out or refuse their sign-in form when the
 * user agent says it is an embedded WebView (`; wv`, `Version/4.0`). The page is shown with a
 * plain Chrome user agent built from the WebView's own Chrome version, so it stays current on
 * every device without a hard-coded version going stale, and carries no device model. The
 * Client Hints ([metadata]) say the same, since by default they still name Android WebView.
 */
internal object SignInBrowser {

    private val CHROME_VERSION = Regex("""Chrome/(\d+)((?:\.\d+)*)""")
    private val ANDROID_VERSION = Regex("""Android (\d+)""")
    private const val FALLBACK_MAJOR = "131"

    fun mobileUserAgent(webViewUserAgent: String): String =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/${chromeMajor(webViewUserAgent)}.0.0.0 Mobile Safari/537.36"

    fun desktopUserAgent(webViewUserAgent: String): String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/${chromeMajor(webViewUserAgent)}.0.0.0 Safari/537.36"

    private fun chromeMajor(userAgent: String): String =
        CHROME_VERSION.find(userAgent)?.groupValues?.get(1) ?: FALLBACK_MAJOR

    private fun chromeFull(userAgent: String): String =
        CHROME_VERSION.find(userAgent)?.let { it.groupValues[1] + it.groupValues[2] }
            ?.takeIf { version -> version.count { it == '.' } == 3 }
            ?: "${chromeMajor(userAgent)}.0.0.0"

    /** Client Hints matching [mobileUserAgent] or [desktopUserAgent]. */
    fun metadata(webViewUserAgent: String, desktop: Boolean): UserAgentMetadata {
        val major = chromeMajor(webViewUserAgent)
        val full = chromeFull(webViewUserAgent)
        fun brand(name: String, majorVersion: String, fullVersion: String) =
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(name).setMajorVersion(majorVersion).setFullVersion(fullVersion).build()
        val builder = UserAgentMetadata.Builder()
            .setBrandVersionList(
                listOf(
                    brand("Not_A Brand", "24", "24.0.0.0"),
                    brand("Chromium", major, full),
                    brand("Google Chrome", major, full)
                )
            )
            .setFullVersion(full)
            .setModel("")
            .setMobile(!desktop)
        if (desktop) {
            builder.setPlatform("Windows").setPlatformVersion("15.0.0").setArchitecture("x86").setBitness(64)
        } else {
            val android = ANDROID_VERSION.find(webViewUserAgent)?.groupValues?.get(1) ?: "10"
            builder.setPlatform("Android").setPlatformVersion("$android.0.0")
        }
        return builder.build()
    }

    /**
     * The page to open for [url]. A site's bare home page is swapped for its sign-in form
     * where the home page is only an "open the app" landing; any other address opens as given.
     */
    fun startUrl(url: String): String {
        val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return url
        if (!parsed.path.isNullOrEmpty() && parsed.path != "/") return url
        if (parsed.query != null) return url
        val host = parsed.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return url
        return LOGIN_PAGES[host] ?: url
    }

    /** Whether the page may navigate to [url]; app links (intent://, instagram://) may not. */
    fun opensInPage(url: String?): Boolean {
        val scheme = url?.substringBefore(':', "")?.lowercase() ?: return false
        return scheme in WEB_SCHEMES
    }

    private val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript")

    private val LOGIN_PAGES = mapOf(
        "instagram.com" to "https://www.instagram.com/accounts/login/",
        "facebook.com" to "https://www.facebook.com/login/",
        "threads.net" to "https://www.threads.net/login",
        "threads.com" to "https://www.threads.com/login",
        "x.com" to "https://x.com/i/flow/login",
        "twitter.com" to "https://x.com/i/flow/login",
        "tiktok.com" to "https://www.tiktok.com/login",
        "reddit.com" to "https://www.reddit.com/login/"
    )

    /** Sites offered as one-tap sign-ins, as display name and home address. */
    val QUICK_SITES = listOf(
        "Instagram" to "https://www.instagram.com",
        "YouTube" to "https://www.youtube.com",
        "X" to "https://x.com",
        "Facebook" to "https://www.facebook.com",
        "TikTok" to "https://www.tiktok.com",
        "Reddit" to "https://www.reddit.com"
    )
}
