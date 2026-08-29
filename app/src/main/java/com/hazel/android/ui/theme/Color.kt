package com.hazel.android.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Theme — Primary background: #0A0A0A
val DarkBackground = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF141414)
val DarkSurfaceVariant = Color(0xFF1E1E1E)
val DarkOnBackground = Color(0xFFE0E0E0)
val DarkOnSurface = Color(0xFFCCCCCC)

// Light Theme — a warm paper ground rather than a neutral white.
//
// A pure white background under a full-bleed thumbnail reads as glare, and Material's own
// light defaults tint their container tones violet, which fought the accent in the search
// field and the navigation bar. Every tone here is mixed towards paper instead, so the
// surfaces stay quiet and the accent is the only colour on the screen.
val LightBackground = Color(0xFFF6F4EF)
val LightSurface = Color(0xFFFCFAF5)
val LightSurfaceVariant = Color(0xFFE9E5DB)
val LightOnBackground = Color(0xFF201D18)
val LightOnSurface = Color(0xFF43403A)
val LightOnSurfaceVariant = Color(0xFF5C574E)
val LightOutline = Color(0xFF8C867A)
val LightOutlineVariant = Color(0xFFDCD6C9)

// Container tones, lightest to darkest. Cards sit on the lower ones; the search field and
// the navigation bar take the higher ones, which is what gives them an edge against the
// background without needing a border to carry it.
val LightSurfaceContainerLowest = Color(0xFFFFFDF8)
val LightSurfaceContainerLow = Color(0xFFF9F6F0)
val LightSurfaceContainer = Color(0xFFF3EFE7)
val LightSurfaceContainerHigh = Color(0xFFEDE9DF)
val LightSurfaceContainerHighest = Color(0xFFE7E2D6)
val LightSurfaceBright = Color(0xFFFCFAF5)
val LightSurfaceDim = Color(0xFFDFDACE)
val LightInverseSurface = Color(0xFF35322C)
val LightInverseOnSurface = Color(0xFFF7F3EB)

// Default Brand — Hazel Cyan
val HazelCyan = Color(0xFF00BCD4)
val HazelCyanDark = Color(0xFF00ACC1)
val HazelCyanLight = Color(0xFF00838F)
val HazelCyanContainer = Color(0xFF002B31)
val HazelCyanContainerLight = Color(0xFFB2EBF2)

// Status Colors
val SuccessGreen = Color(0xFF4CAF50)
val ErrorRed = Color(0xFFCF6679)
val ErrorRedLight = Color(0xFFB00020)
val WarningAmber = Color(0xFFFFB74D)
val InfoBlue = Color(0xFF64B5F6)

// Progress
val ProgressTrackDark = Color(0xFF1A1A1A)
val ProgressTrackLight = Color(0xFFE0E0E0)

// ── Accent Color Palette (ChatGPT-style picker) ──
data class AccentColor(
    val name: String,
    val dark: Color,           // primary in dark theme
    val light: Color,          // primary in light theme
    val containerDark: Color,  // primaryContainer in dark
    val containerLight: Color  // primaryContainer in light
)

val AccentColors = listOf(
    AccentColor("Cyan",       Color(0xFF00BCD4), Color(0xFF00838F), Color(0xFF002B31), Color(0xFFB2EBF2)),
    AccentColor("Crimson",    Color(0xFFE53935), Color(0xFFC62828), Color(0xFF3B0A0A), Color(0xFFFFCDD2)),
    AccentColor("Pink",       Color(0xFFEC407A), Color(0xFFAD1457), Color(0xFF3B0720), Color(0xFFF8BBD0)),
    AccentColor("Orange",     Color(0xFFFF9800), Color(0xFFE65100), Color(0xFF331400), Color(0xFFFFE0B2)),
    AccentColor("Gold",       Color(0xFFFFD600), Color(0xFFC49000), Color(0xFF332B00), Color(0xFFFFF8E1)),
    AccentColor("Green",      Color(0xFF4CAF50), Color(0xFF2E7D32), Color(0xFF0A290B), Color(0xFFC8E6C9)),
    AccentColor("Teal",       Color(0xFF009688), Color(0xFF00695C), Color(0xFF001F1B), Color(0xFFB2DFDB)),
    AccentColor("Blue",       Color(0xFF2196F3), Color(0xFF1565C0), Color(0xFF051E33), Color(0xFFBBDEFB)),
    AccentColor("Indigo",     Color(0xFF5C6BC0), Color(0xFF283593), Color(0xFF0C1033), Color(0xFFC5CAE9)),
    AccentColor("Purple",     Color(0xFFAB47BC), Color(0xFF7B1FA2), Color(0xFF200A29), Color(0xFFE1BEE7)),
    AccentColor("White",      Color(0xFFE0E0E0), Color(0xFF616161), Color(0xFF1A1A1A), Color(0xFFF5F5F5)),
)
