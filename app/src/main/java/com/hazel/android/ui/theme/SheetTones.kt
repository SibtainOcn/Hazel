package com.hazel.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The near-black tones the download sheets are drawn in, for one link or a set of links.
 *
 * The sheets cover most of the screen and are mostly artwork, so they are kept darker than the
 * app's ordinary surfaces: flat black behind, one step up for rows and sections, and one more
 * for fields and buttons inside them.
 *
 * On a light theme or with glass on, the scheme's own surfaces are used instead, so every
 * theme, accent and glass style keeps the colours it was drawn for.
 */
private val SHEET_BLACK = Color(0xFF000000)
private val RAISED_BLACK = Color(0xFF0A0A0A)
private val BUTTON_BLACK = Color(0xFF1C1C1E)

private val isDarkScheme: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalDarkTheme.current

/** Behind the sheet itself. */
val sheetBlackColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isDarkScheme && !LocalGlass.current) SHEET_BLACK else MaterialTheme.colorScheme.surface

/**
 * The action bar's own buttons, one step up from the bar so each reads as something to tap
 * rather than as a label printed on it.
 */
val sheetButtonColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isDarkScheme && !LocalGlass.current) BUTTON_BLACK else MaterialTheme.colorScheme.surfaceContainerHighest

/** The rows, the action bar, and the rows inside the sheets it opens. */
val sheetRaisedColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isDarkScheme && !LocalGlass.current) RAISED_BLACK else MaterialTheme.colorScheme.surfaceContainerHigh
