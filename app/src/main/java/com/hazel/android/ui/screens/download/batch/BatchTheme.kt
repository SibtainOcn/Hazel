package com.hazel.android.ui.screens.download.batch

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The near-black tones the set-of-links sheet is drawn in.
 *
 * The sheet covers most of the screen and is mostly artwork, so it is kept darker than the
 * app's ordinary surfaces: flat black behind, and one step up from it for the rows and the
 * bar, which is enough to separate them without adding a grey cast behind every thumbnail.
 *
 * On a light theme none of that applies, so the scheme's own surfaces are used instead and
 * these read as the plain Material colours they replace.
 */
private val SHEET_BLACK = Color(0xFF000000)
private val RAISED_BLACK = Color(0xFF0A0A0A)

private val isDarkScheme: Boolean
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Behind the sheet itself. */
val batchSheetColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isDarkScheme) SHEET_BLACK else MaterialTheme.colorScheme.surface

/** The rows, the action bar, and the rows inside the sheets it opens. */
val batchRaisedColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isDarkScheme) RAISED_BLACK else MaterialTheme.colorScheme.surfaceContainerHigh
