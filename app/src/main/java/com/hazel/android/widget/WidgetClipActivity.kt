package com.hazel.android.widget

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.hazel.android.R
import com.hazel.android.ui.share.ShareOverlayActivity
import com.hazel.android.util.UrlExtractor

/**
 * An invisible stop between a widget button and the download sheet. Android only lets the
 * app on screen read the clipboard, so this comes to the front, reads the copied link once
 * it has focus, and hands it on: to the sheet for Paste, to Instant as audio for Audio.
 */
class WidgetClipActivity : Activity() {

    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = runCatching {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        }.getOrNull()
        val url = UrlExtractor.extract(text)

        if (url == null) {
            Toast.makeText(this, R.string.search_nothing_to_paste, Toast.LENGTH_SHORT).show()
        } else {
            val audio = intent.getBooleanExtra(EXTRA_AUDIO, false)
            val target = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (audio) {
                target.setClassName(this, "com.hazel.android.${ShareOverlayActivity.INSTANT_ALIAS}")
                    .putExtra(ShareOverlayActivity.EXTRA_AUDIO_ONLY, true)
            } else {
                target.setClass(this, ShareOverlayActivity::class.java)
            }
            runCatching { startActivity(target) }
                .onFailure { Toast.makeText(this, R.string.search_nothing_to_paste, Toast.LENGTH_SHORT).show() }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_AUDIO = "hazel.widget.audio"

        fun intent(context: Context, audio: Boolean): Intent =
            Intent(context, WidgetClipActivity::class.java)
                .putExtra(EXTRA_AUDIO, audio)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
