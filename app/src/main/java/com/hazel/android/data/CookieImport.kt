package com.hazel.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.Reader

/**
 * Turns cookies copied or exported from a browser into Netscape cookie-file lines, the form
 * yt-dlp and gallery-dl read. Takes a Netscape file (tabs or spaces) or a JSON export such as
 * Cookie-Editor's. Lines that are not cookies are dropped, so junk is never saved.
 */
internal object CookieImport {

    /** Anything longer is not a cookie export. */
    const val MAX_CHARS = 4_000_000

    private const val HTTP_ONLY = "#HttpOnly_"

    fun toNetscapeLines(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.length > MAX_CHARS) return emptyList()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            runCatching { return fromJson(trimmed) }
        }
        return trimmed.lineSequence().mapNotNull(::netscapeLine).toList()
    }

    /** Reads at most [MAX_CHARS] from [reader]; null when there is more. */
    fun readLimited(reader: Reader): String? {
        val out = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) return out.toString()
            out.append(buffer, 0, read)
            if (out.length > MAX_CHARS) return null
        }
    }

    private fun netscapeLine(raw: String): String? {
        val line = raw.trim()
        if (line.isEmpty() || (line.startsWith("#") && !line.startsWith(HTTP_ONLY))) return null
        // Copying through chat or notes apps often turns the tabs into spaces.
        val fields = line.split('\t').takeIf { it.size >= 7 } ?: line.split(Regex("\\s+"), limit = 7)
        if (fields.size < 7) return null
        val (domain, sub, path, secure, expires) = fields
        val ok = domain.removePrefix(HTTP_ONLY).contains('.') &&
            sub.isFlag() && secure.isFlag() && path.startsWith("/") &&
            expires.toDoubleOrNull() != null && fields[5].isNotEmpty()
        return if (ok) fields.take(7).joinToString("\t") else null
    }

    private fun fromJson(text: String): List<String> {
        val array = if (text.startsWith("[")) JSONArray(text) else JSONObject(text).optJSONArray("cookies") ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::jsonLine) }
    }

    private fun jsonLine(cookie: JSONObject): String? {
        val name = cookie.optString("name")
        var domain = cookie.optString("domain").trim()
        if (name.isEmpty() || !domain.contains('.') || name.any { it == '\t' || it == '\n' }) return null
        if (!cookie.optBoolean("hostOnly", domain.startsWith(".")) && !domain.startsWith(".")) domain = ".$domain"
        val value = cookie.optString("value").replace(Regex("[\\t\\r\\n]"), "")
        val path = cookie.optString("path").ifBlank { "/" }
        val expires = listOf("expirationDate", "expires", "expiry")
            .firstNotNullOfOrNull { key -> cookie.opt(key)?.toString()?.toDoubleOrNull() }
            ?.toLong()?.coerceAtLeast(0) ?: 0L
        val prefix = if (cookie.optBoolean("httpOnly")) HTTP_ONLY else ""
        return listOf(
            prefix + domain,
            flag(domain.startsWith(".")),
            path,
            flag(cookie.optBoolean("secure")),
            expires.toString(),
            name,
            value
        ).joinToString("\t")
    }

    private fun String.isFlag() = equals("TRUE", ignoreCase = true) || equals("FALSE", ignoreCase = true)

    private fun flag(on: Boolean) = if (on) "TRUE" else "FALSE"
}
