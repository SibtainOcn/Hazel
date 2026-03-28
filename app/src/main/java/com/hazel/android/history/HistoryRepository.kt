package com.hazel.android.history

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val title: String,
    val url: String,
    val type: String,        // "video", "audio", "convert"
    val path: String,
    val timestamp: Long
)

object HistoryRepository {

    private const val PREFS_NAME = "hazel_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 50

    fun addEntry(context: Context, entry: HistoryEntry) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = loadArray(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")

        val obj = JSONObject().apply {
            put("title", entry.title)
            put("url", entry.url)
            put("type", entry.type)
            put("path", entry.path)
            put("ts", entry.timestamp)
        }

        // Prepend new entry
        val updated = JSONArray()
        updated.put(obj)
        for (i in 0 until minOf(arr.length(), MAX_ENTRIES - 1)) {
            updated.put(arr.getJSONObject(i))
        }

        prefs.edit().putString(KEY_ENTRIES, updated.toString()).apply()
    }

    fun getEntries(context: Context): List<HistoryEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = loadArray(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")
        val list = mutableListOf<HistoryEntry>()
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                list.add(
                    HistoryEntry(
                        title = obj.optString("title", ""),
                        url = obj.optString("url", ""),
                        type = obj.optString("type", "video"),
                        path = obj.optString("path", ""),
                        timestamp = obj.optLong("ts", 0L)
                    )
                )
            } catch (_: Exception) { }
        }
        return list
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, "[]").apply()
    }

    private fun loadArray(json: String): JSONArray {
        return try { JSONArray(json) } catch (_: Exception) { JSONArray() }
    }
}
