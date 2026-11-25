package com.hazel.android.data

import android.content.Context
import com.hazel.android.download.NetworkProxy.throughProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Asks a site, with one light page request carrying a saved sign-in, whether it still knows
 * the account. Goes through the proxy like every other request; downloads nothing.
 */
object SignInCheck {

    /** True when the site refused the sign-in, false when it is still signed in. */
    suspend fun run(context: Context, entry: CookieEntry): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val site = CookieRepository.siteOf(entry) ?: error("no site")
            val loginNames = CookieRepository.LOGIN_COOKIES[site].orEmpty()
            val jar = EntryJar(entry, loginNames)
            val client = OkHttpClient.Builder()
                .throughProxy()
                .cookieJar(jar)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            val userAgent = CookieRepository.getUserAgent(context).first()
            val request = Request.Builder()
                .url(PROBES[site] ?: "https://www.$site/")
                .apply { if (userAgent.isNotBlank()) header("User-Agent", userAgent) }
                .build()
            client.newCall(request).execute().use { response ->
                val landed = response.request.url
                val onLoginPage = LOGIN_MARKS.any { it in (landed.host + landed.encodedPath).lowercase() }
                onLoginPage || response.code == 401 || response.code == 403 || jar.loginCleared
            }
        }
    }

    /** Hands the request the saved cookies and notes a site clearing its login cookie. */
    private class EntryJar(entry: CookieEntry, private val loginNames: Set<String>) : CookieJar {
        private val saved: List<Cookie> = entry.content.lineSequence().mapNotNull(::toCookie).toList()
        @Volatile var loginCleared = false

        override fun loadForRequest(url: HttpUrl): List<Cookie> = saved.filter { it.matches(url) }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val now = System.currentTimeMillis()
            if (cookies.any { it.name in loginNames && (it.value.isEmpty() || it.value == "deleted" || it.expiresAt < now) }) {
                loginCleared = true
            }
        }

        private fun toCookie(line: String): Cookie? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || (trimmed.startsWith("#") && !trimmed.startsWith(HTTP_ONLY))) return null
            val fields = trimmed.removePrefix(HTTP_ONLY).split('\t')
            if (fields.size < 7) return null
            val domain = fields[0].removePrefix(".").takeIf { it.isNotBlank() } ?: return null
            return runCatching {
                Cookie.Builder()
                    .name(fields[5])
                    .value(fields[6])
                    .domain(domain)
                    .path(fields[2].ifBlank { "/" })
                    .apply {
                        if (fields[3].equals("TRUE", true)) secure()
                        fields[4].toLongOrNull()?.takeIf { it > 0 }?.let { expiresAt(it * 1000) }
                    }
                    .build()
            }.getOrNull()
        }
    }

    private const val HTTP_ONLY = "#HttpOnly_"

    /** A page each site only shows to a signed-in visitor, sending others to its login form. */
    private val PROBES = mapOf(
        "instagram.com" to "https://www.instagram.com/accounts/edit/",
        "youtube.com" to "https://www.youtube.com/account",
        "reddit.com" to "https://old.reddit.com/prefs/",
        "facebook.com" to "https://www.facebook.com/settings/",
        "twitter.com" to "https://x.com/settings/account",
        "tiktok.com" to "https://www.tiktok.com/setting"
    )

    private val LOGIN_MARKS = listOf("/login", "servicelogin", "/signin", "/i/flow/login", "login.php", "/accounts/login")
}
