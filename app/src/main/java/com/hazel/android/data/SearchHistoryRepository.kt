package com.hazel.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * Remembers the links that have been pasted, so a link used before can be picked from a
 * list instead of typed again.
 *
 * Newest first, capped at [LIMIT] entries. Adding a link that is already in the list moves
 * it to the top rather than creating a duplicate.
 */
object SearchHistoryRepository {

    private const val LIMIT = 60

    private val HISTORY_KEY = stringPreferencesKey("search_history")

    fun getHistory(context: Context): Flow<List<String>> =
        context.dataStore.data.map { prefs -> decode(prefs[HISTORY_KEY]) }

    suspend fun record(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        context.dataStore.edit { prefs ->
            val existing = decode(prefs[HISTORY_KEY])
            val updated = (listOf(trimmed) + existing.filterNot { it == trimmed }).take(LIMIT)
            prefs[HISTORY_KEY] = encode(updated)
        }
    }

    suspend fun remove(context: Context, query: String) {
        context.dataStore.edit { prefs ->
            prefs[HISTORY_KEY] = encode(decode(prefs[HISTORY_KEY]).filterNot { it == query })
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs -> prefs.remove(HISTORY_KEY) }
    }

    private fun encode(entries: List<String>): String =
        JSONArray().apply { entries.forEach { put(it) } }.toString()

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { s -> s.isNotBlank() } }
        }.getOrDefault(emptyList())
    }
}
