package com.hazel.android.ui.screens.more

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.util.openInAppBrowser

/**
 * Where the app asks for support, and says what it is asking for.
 *
 * Hazel takes nothing from anyone: no advertisement, no measurement of what people
 * download, and no part of it kept back for a paying tier. That is a decision rather than a
 * stage on the way to something else, and this screen exists because a decision like that
 * only holds if the people who value it are given a way to say so.
 *
 * Nothing here is a wall. Everything the app does is available to everyone whether or not
 * anybody ever opens this screen, and the ways of helping that cost nothing sit next to the
 * ones that cost money because they are worth as much.
 */
@Composable
fun SponsorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Independent theme-adaptive color tokens matching GettingStartedDialog black shades aesthetic
    val surfaceColor = if (isDark) Color(0xFF121418) else Color(0xFFFFFFFF)
    val cardBorder = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE5E7EB)
    val textPrimary = if (isDark) Color(0xFFF9FAFB) else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFFA0A7B5) else Color(0xFF475569)
    val textMuted = if (isDark) Color(0xFF717784) else Color(0xFF94A3B8)
    val badgeBg = if (isDark) Color(0xFF1A1D24) else Color(0xFFF1F5F9)
    val badgeBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFE2E8F0)
    val iconTint = if (isDark) Color.White else Color(0xFF0F172A)
    val chevronTint = if (isDark) Color(0xFF717784) else Color(0xFF94A3B8)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.sponsor_back),
                    tint = textPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.sponsor_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
        }

        HeroCard(
            isDark = isDark,
            surfaceColor = surfaceColor,
            cardBorder = cardBorder,
            badgeBg = badgeBg,
            badgeBorder = badgeBorder,
            iconTint = iconTint,
            textPrimary = textPrimary,
            textSecondary = textSecondary
        )

        Spacer(modifier = Modifier.height(28.dp))

        SectionLabel(
            text = stringResource(R.string.sponsor_section_ways),
            color = textPrimary
        )

        SupportCard(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.sponsor_github_title),
            subtitle = stringResource(R.string.sponsor_github_subtitle),
            enabled = SPONSORS_URL.isNotBlank(),
            surfaceColor = surfaceColor,
            cardBorder = cardBorder,
            badgeBg = badgeBg,
            badgeBorder = badgeBorder,
            iconTint = iconTint,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            chevronTint = chevronTint,
            onClick = { openLink(context, SPONSORS_URL) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SupportCard(
            icon = Icons.Filled.Coffee,
            title = stringResource(R.string.sponsor_bmac_title),
            subtitle = stringResource(R.string.sponsor_bmac_subtitle),
            enabled = BMAC_URL.isNotBlank(),
            surfaceColor = surfaceColor,
            cardBorder = cardBorder,
            badgeBg = badgeBg,
            badgeBorder = badgeBorder,
            iconTint = iconTint,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            chevronTint = chevronTint,
            onClick = { openLink(context, BMAC_URL) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        SectionLabel(
            text = stringResource(R.string.sponsor_section_free),
            color = textPrimary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                HelpRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.sponsor_star_title),
                    subtitle = stringResource(R.string.sponsor_star_subtitle),
                    iconTint = iconTint,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { openLink(context, SOURCE_URL) }
                )
                HelpRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.sponsor_share_title),
                    subtitle = stringResource(R.string.sponsor_share_subtitle),
                    iconTint = iconTint,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { shareApp(context) }
                )
                HelpRow(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.sponsor_issues_title),
                    subtitle = stringResource(R.string.sponsor_issues_subtitle),
                    iconTint = iconTint,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { openLink(context, ISSUES_URL) }
                )
                HelpRow(
                    icon = Icons.Filled.Code,
                    title = stringResource(R.string.sponsor_source_title),
                    subtitle = stringResource(R.string.sponsor_source_subtitle),
                    iconTint = iconTint,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { openLink(context, SOURCE_URL) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            stringResource(R.string.sponsor_footer),
            style = MaterialTheme.typography.bodySmall,
            color = textMuted
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

/** The block at the top, which is the part that has something to say. */
@Composable
private fun HeroCard(
    isDark: Boolean,
    surfaceColor: Color,
    cardBorder: Color,
    badgeBg: Color,
    badgeBorder: Color,
    iconTint: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) listOf(
                            Color(0xFF1A1D24),
                            Color(0xFF121418)
                        ) else listOf(
                            Color(0xFFF9FAFB),
                            Color(0xFFFFFFFF)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(badgeBg)
                    .border(1.dp, badgeBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = iconTint
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                stringResource(R.string.sponsor_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                stringResource(R.string.sponsor_hero_body),
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                stringResource(R.string.sponsor_hero_closing),
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

/** One place money can go, drawn large enough to be the point of the screen. */
@Composable
private fun SupportCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    surfaceColor: Color,
    cardBorder: Color,
    badgeBg: Color,
    badgeBorder: Color,
    iconTint: Color,
    textPrimary: Color,
    textSecondary: Color,
    chevronTint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(badgeBg)
                    .border(1.dp, badgeBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (enabled) iconTint else textSecondary.copy(alpha = 0.45f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) textPrimary else textSecondary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = chevronTint
                )
            }
        }
    }
}

/** One of the ways of helping that costs nothing. */
@Composable
private fun HelpRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconTint
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                color = textPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )
        }
    }
}

/** Hands the app's address to whatever the user shares with. */
private fun shareApp(context: Context) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$SHARE_MESSAGE\n$SOURCE_URL")
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(share, "Share Hazel").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Opens an address without leaving the app.
 *
 * A sponsor page, an issue tracker and a source listing are all things people glance at and
 * come straight back from, and a full app switch for each of them turns a glance into a
 * journey. The in-app browser keeps the app underneath and falls back to a real browser on
 * a device that has no support for it.
 */
private fun openLink(context: Context, url: String) {
    if (url.isBlank()) return
    openInAppBrowser(context, url)
}

private const val SHARE_MESSAGE = "Hazel, a downloader with no ads and nothing held back:"

private const val SOURCE_URL = "https://github.com/SibtainOcn/Hazel"
private const val ISSUES_URL = "https://github.com/SibtainOcn/Hazel/issues"
private const val SPONSORS_URL = "https://github.com/sponsors/SibtainOcn"

private const val BMAC_URL = "https://buymeacoffee.com/sibtainocean"
