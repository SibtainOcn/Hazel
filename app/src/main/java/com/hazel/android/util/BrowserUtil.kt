package com.hazel.android.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens a URL in Chrome Custom Tabs with a dark (black) theme.
 * Falls back to external browser if Custom Tabs is unavailable.
 */
fun openInAppBrowser(context: Context, url: String) {
    try {
        val darkParams = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.BLACK)
            .setNavigationBarColor(Color.BLACK)
            .setSecondaryToolbarColor(Color.BLACK)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(darkParams)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .setUrlBarHidingEnabled(true)
            .build()

        customTabsIntent.launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        // Custom Tabs unavailable — fall back to any installed browser
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // No browser installed at all — silently ignore
        }
    }
}
