package com.hazel.android.download

import java.util.Locale

/**
 * What a read of a link depended on, beyond the link itself, so a remembered read is only
 * served back while it still answers the same question.
 *
 * The same address read with other YouTube player clients, other PO tokens, metadata in
 * another language, or through another browser imitation can list other formats or other
 * titles. Each read is stamped with [stampFor] as it is stored and checked against it as it
 * is looked up: a read made under other settings is a miss, and the next read replaces it.
 * Nothing has to remember to clear the cache when one of these changes, and changing a
 * setting back finds the earlier reads valid again for as long as they are kept.
 *
 * Only what changes the answer goes in. Timeouts, retries, request pacing and certificate
 * checks change how long a read takes, not what it finds, so they leave reads standing. The
 * sign-in is not here either: every change to the saved sign-ins clears the cache outright,
 * since a read made signed in must not outlive the sign-in it was made with.
 *
 * Also holds whether incognito is on, read from memory, so the cache can keep a read made in
 * incognito off the disk without waiting on storage.
 */
object ReadProfile {

    /** Incognito, kept in step with the setting by the app as it starts and on every change. */
    @Volatile
    var incognito: Boolean = false

    /** The stamp a read of [url] made now would carry. */
    fun stampFor(url: String, settings: AdvancedSettings = AdvancedSettingsStore.current): String {
        val parts = buildList {
            settings.impersonateTarget()?.let { add("imp=$it") }
            // The YouTube arguments go only to YouTube, so changing them leaves every other
            // site's reads alone.
            if (isYouTube(url) || url.contains("music.youtube.com")) {
                if (settings.playerClients.isNotEmpty()) add("clients=${settings.playerClients.joinToString(",")}")
                settings.validPoTokens().takeIf { it.isNotEmpty() }?.let { add("po=${it.joinToString(",")}") }
                settings.visitorData.trim().takeIf { it.isNotEmpty() }?.let { add("visitor=$it") }
                if (settings.autoPoTokens) add("autopo")
                if (settings.metadataInAppLanguage) add("lang=${Locale.getDefault().toLanguageTag()}")
                settings.youtubeExtraArgs.trim().takeIf { it.isNotEmpty() }?.let { add("args=$it") }
            }
        }
        // Blank for the settings nearly everyone has, so the common stamp costs nothing.
        if (parts.isEmpty()) return ""
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("\n").toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}
