package com.hazel.android.download.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Completions for a half-typed search, from Google's public suggestion endpoint.
 *
 * Only asked when the user turned suggestions on, since every keystroke leaves the device.
 * Any failure is an empty list: suggestions are a convenience, never a reason to stop typing.
 */
object SearchSuggestions {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    suspend fun forQuery(query: String, source: SearchSource): List<String> = withContext(Dispatchers.IO) {
        val text = query.trim()
        if (text.length < MIN_LENGTH) return@withContext emptyList()
        runCatching {
            // "yt" narrows the completions to what people search on YouTube and its music
            // app; every other site gets Google's general completions.
            val site = if (source == SearchSource.YOUTUBE || source == SearchSource.YOUTUBE_MUSIC) "&ds=yt" else ""
            val request = Request.Builder()
                .url("$ENDPOINT?client=firefox$site&q=${URLEncoder.encode(text, "UTF-8")}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                parse(response.body.string())
            }
        }.getOrDefault(emptyList())
    }

    /** The endpoint answers `[query, [completion, ...], ...]`. */
    internal fun parse(body: String): List<String> {
        val list = runCatching { JSONArray(body).getJSONArray(1) }.getOrNull() ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optString(it).takeIf { s -> s.isNotBlank() } }
            .take(MAX_SUGGESTIONS)
    }

    private const val ENDPOINT = "https://suggestqueries.google.com/complete/search"
    private const val MIN_LENGTH = 2
    private const val MAX_SUGGESTIONS = 8
}
