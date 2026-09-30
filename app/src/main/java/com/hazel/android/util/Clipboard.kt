package com.hazel.android.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Puts [text] on the clipboard under a label naming the app, as a log or a link. */
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Hazel log", text))
}

/** The scheme and host of [url], which is the page a sign-in for it starts from. */
fun siteRootOf(url: String): String = runCatching {
    val parsed = java.net.URL(url)
    "${parsed.protocol}://${parsed.host}"
}.getOrDefault(url)
