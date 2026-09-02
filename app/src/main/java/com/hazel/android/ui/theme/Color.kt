package com.hazel.android.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Theme — Primary background: #0A0A0A
val DarkBackground = Color(0xFF0A0A0A)
val DarkSurface = Color(0xFF141414)
val DarkSurfaceVariant = Color(0xFF1E1E1E)
val DarkOnBackground = Color(0xFFE0E0E0)
val DarkOnSurface = Color(0xFFCCCCCC)

/**
 * Secondary text on a dark surface: labels, one-line explanations, units beside a value.
 *
 * Named rather than left to Material, whose dark default is a violet-tinted grey that sits
 * oddly against the neutral greys everything else here is built from. Light enough to read
 * at a glance, which is the point of setting it: the alternative was dimming the primary
 * colour down with alpha, and that lands somewhere unreadable on one theme or the other.
 */
val DarkOnSurfaceVariant = Color(0xFFAFAFAF)

// Container tones for the dark theme, lightest last. Named one by one rather than left to
// Material, whose dark defaults derive their container tones from a violet neutral: the
// search field took the highest of them and read as a lilac bar across the top of an
// otherwise black screen.
val DarkSurfaceContainerLowest = Color(0xFF070707)
val DarkSurfaceContainerLow = Color(0xFF101010)
val DarkSurfaceContainer = Color(0xFF151515)
val DarkSurfaceContainerHigh = Color(0xFF1A1A1A)
val DarkSurfaceContainerHighest = Color(0xFF1F1F1F)
val DarkOutline = Color(0xFF3A3A3A)
val DarkOutlineVariant = Color(0xFF242424)

// Light Theme — a warm paper ground rather than a neutral white.
//
// A pure white background under a full-bleed thumbnail reads as glare, and Material's own
// light defaults tint their container tones violet, which fought the accent in the search
// field and the navigation bar. Every tone here is mixed towards paper instead, so the
// surfaces stay quiet and the accent is the only colour on the screen.
//
// The warmth is carried at low saturation on purpose. The search field and the navigation
// bar take the highest container tones and cover a lot of the screen, so a beige strong
// enough to read as a colour on a small swatch reads as a stain across a whole bar. These
// are warm against white and neutral against each other, which is the balance that lets the
// artwork on the cards be the only saturated thing in view.
val LightBackground = Color(0xFFF7F5F1)
val LightSurface = Color(0xFFFDFCFA)
val LightSurfaceVariant = Color(0xFFECE8E1)
val LightOnBackground = Color(0xFF1F1D1A)
val LightOnSurface = Color(0xFF44413B)
val LightOnSurfaceVariant = Color(0xFF5E5A53)
val LightOutline = Color(0xFF8D8880)
val LightOutlineVariant = Color(0xFFDEDAD2)

// Container tones, lightest to darkest. Cards sit on the lower ones; the search field and
// the navigation bar take the higher ones, which is what gives them an edge against the
// background without needing a border to carry it.
val LightSurfaceContainerLowest = Color(0xFFFFFEFC)
val LightSurfaceContainerLow = Color(0xFFFAF8F5)
val LightSurfaceContainer = Color(0xFFF5F2EE)
val LightSurfaceContainerHigh = Color(0xFFF0EDE8)
val LightSurfaceContainerHighest = Color(0xFFEAE6E0)
val LightSurfaceBright = Color(0xFFFDFCFA)
val LightSurfaceDim = Color(0xFFE2DED7)
val LightInverseSurface = Color(0xFF34322E)
val LightInverseOnSurface = Color(0xFFF7F4EF)

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

// Format badges
//
// The size is the figure a format list is scanned for, so it carries a colour of its own
// rather than another tone of the accent, which the codec and the bitrate beside it already
// use. Fixed rather than themed on purpose: the accent is a personal choice and this is a
// piece of information, and it should read the same whichever colour the app is set to.
val SizeBadgeContainer = Color(0xFF4A4458)
val SizeBadgeContent = Color(0xFFEADDFF)

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
