package com.hazel.android.ui.screens.cookies

import kotlin.math.roundToInt

/** A page's zoom as a share of the scale it opened at, for the sign-in browser's zoom slider. */
internal class PageZoom {
    private var base = 0f
    private var current = 0f

    val percent: Int
        get() = if (base <= 0f || current <= 0f) 100 else (current / base * 100).roundToInt()

    /** A page finished loading at [scale]: that is 100%. */
    fun startAt(scale: Float) {
        base = scale
        current = scale
    }

    fun scaled(scale: Float) {
        current = scale
        if (base <= 0f) base = scale
    }

    fun clear() {
        base = 0f
        current = 0f
    }

    /** The factor for WebView.zoomBy that reaches [percent], inside the range it accepts; null before a page has a scale. */
    fun factorFor(percent: Int): Float? =
        if (base <= 0f || current <= 0f) null
        else (base * percent / 100f / current).coerceIn(MIN_FACTOR, MAX_FACTOR)

    private companion object {
        const val MIN_FACTOR = 0.01f
        const val MAX_FACTOR = 100f
    }
}
