package com.hazel.android.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns the cookies a WebView holds after a sign-in into the Netscape cookie file yt-dlp
 * reads.
 *
 * `CookieManager.getCookie` only returns name and value pairs, which is not enough: yt-dlp
 * needs the domain, path, secure flag and expiry for each cookie. Those live in the
 * WebView's own SQLite store, so the store is read directly after flushing the in-memory
 * cookies to it.
 */
object CookieExtractor {

    private const val TABLE = "cookies"

    private val COLUMNS = arrayOf(
        "host_key", "expires_utc", "path", "name", "value", "is_secure"
    )

    /**
     * Reads every cookie the WebView currently holds.
     *
     * @param url the site the cookies were collected for, recorded as a comment in the text.
     * @return the cookie file body, or a failure when no cookies were collected or the store
     *   could not be read.
     */
    suspend fun extract(context: Context, url: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val manager = CookieManager.getInstance()
                if (!manager.hasCookies()) error("No cookies were collected. Sign in first.")
                manager.flush()

                val store = findCookieStore(context) ?: error("Cookie store not found")

                val cookies = mutableListOf<String>()
                SQLiteDatabase.openDatabase(
                    store.absolutePath, null, SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    db.query(TABLE, COLUMNS, null, null, null, null, null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val host = cursor.getString(cursor.getColumnIndexOrThrow("host_key"))
                            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                            val value = cursor.getString(cursor.getColumnIndexOrThrow("value"))
                            val path = cursor.getString(cursor.getColumnIndexOrThrow("path"))
                            val secure =
                                cursor.getLong(cursor.getColumnIndexOrThrow("is_secure")) == 1L
                            val expiry =
                                cursor.getLong(cursor.getColumnIndexOrThrow("expires_utc"))

                            if (name.isNullOrBlank() || host.isNullOrBlank()) continue

                            cookies += netscapeLine(
                                domain = if (host.startsWith(".")) host else ".$host",
                                path = path?.ifBlank { "/" } ?: "/",
                                secure = secure,
                                expiry = toUnixSeconds(expiry),
                                name = name,
                                value = value.orEmpty()
                            )
                        }
                    }
                }

                if (cookies.isEmpty()) error("No cookies were collected. Sign in first.")

                buildString {
                    append("# $url\n")
                    cookies.forEach { append(it).append('\n') }
                }
            }
        }

    /** Clears every cookie the WebView holds, so a sign-in starts from a clean state. */
    fun clearAll() {
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }
    }

    /**
     * One tab separated cookie record.
     *
     * The second field marks whether the cookie applies to subdomains, which is true for
     * every domain written with a leading dot.
     */
    private fun netscapeLine(
        domain: String,
        path: String,
        secure: Boolean,
        expiry: Long,
        name: String,
        value: String
    ): String = listOf(
        domain,
        "TRUE",
        path,
        secure.toString().uppercase(),
        expiry.toString(),
        name,
        value
    ).joinToString("\t")

    /**
     * Converts a WebView expiry to the Unix seconds a Netscape file uses.
     *
     * The store keeps expiry as microseconds since 1601-01-01. A cookie with no expiry is
     * written as 0, which marks it as a session cookie.
     */
    private fun toUnixSeconds(chromiumMicros: Long): Long {
        if (chromiumMicros <= 0) return 0
        val seconds = chromiumMicros / 1_000_000 - EPOCH_DIFFERENCE_SECONDS
        return if (seconds > 0) seconds else 0
    }

    /** Seconds between 1601-01-01 and 1970-01-01. */
    private const val EPOCH_DIFFERENCE_SECONDS = 11_644_473_600L

    /**
     * Locates the WebView cookie store inside the app's private data directory. Its exact
     * path has moved between Android versions, so it is searched for by name.
     */
    private fun findCookieStore(context: Context): File? =
        runCatching {
            context.dataDir.walkTopDown()
                .maxDepth(6)
                .firstOrNull { it.isFile && it.name == "Cookies" }
        }.getOrNull()
}
