package com.hazel.android.ui.screens.more

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SponsorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.sponsor_back),
                    tint = colors.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.sponsor_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            DeveloperCard(onClick = { openLink(context, DEVELOPER_URL) })

            Spacer(modifier = Modifier.height(24.dp))

            HeroCard()

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.sponsor_section_ways))
            ConnectedRows(
                rows = listOf(
                    {
                        SupportRow(
                            icon = Icons.Filled.Favorite,
                            shape = MaterialShapes.Cookie6Sided.toShape(),
                            title = stringResource(R.string.sponsor_github_title),
                            subtitle = stringResource(R.string.sponsor_github_subtitle),
                            enabled = SPONSORS_URL.isNotBlank(),
                            onClick = { openLink(context, SPONSORS_URL) }
                        )
                    },
                    {
                        SupportRow(
                            icon = Icons.Filled.Coffee,
                            shape = MaterialShapes.Cookie7Sided.toShape(),
                            title = stringResource(R.string.sponsor_bmac_title),
                            subtitle = stringResource(R.string.sponsor_bmac_subtitle),
                            enabled = BMAC_URL.isNotBlank(),
                            onClick = { openLink(context, BMAC_URL) }
                        )
                    }
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.sponsor_section_free))
            ConnectedRows(
                rows = listOf(
                    {
                        SupportRow(
                            icon = Icons.Filled.Star,
                            shape = MaterialShapes.Sunny.toShape(),
                            title = stringResource(R.string.sponsor_star_title),
                            subtitle = stringResource(R.string.sponsor_star_subtitle),
                            onClick = { openLink(context, SOURCE_URL) }
                        )
                    },
                    {
                        SupportRow(
                            icon = Icons.Filled.Share,
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            title = stringResource(R.string.sponsor_share_title),
                            subtitle = stringResource(R.string.sponsor_share_subtitle),
                            onClick = { shareApp(context) }
                        )
                    },
                    {
                        SupportRow(
                            icon = Icons.Filled.BugReport,
                            shape = MaterialShapes.Pentagon.toShape(),
                            title = stringResource(R.string.sponsor_issues_title),
                            subtitle = stringResource(R.string.sponsor_issues_subtitle),
                            onClick = { openLink(context, ISSUES_URL) }
                        )
                    },
                    {
                        SupportRow(
                            icon = Icons.Filled.Code,
                            shape = MaterialShapes.Cookie4Sided.toShape(),
                            title = stringResource(R.string.sponsor_source_title),
                            subtitle = stringResource(R.string.sponsor_source_subtitle),
                            onClick = { openLink(context, SOURCE_URL) }
                        )
                    }
                )
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * The person behind the app, set apart from the rows below: a flat card in the accent's
 * container colour, the avatar on the same 9-sided cookie as the heart, and the way to the profile above the name.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeveloperCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialShapes.Clover4Leaf.toShape())
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.sponsor_section_developer),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.sponsor_developer_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                stringResource(R.string.sponsor_developer_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

/** The block that says why, with the heart on an accent cookie. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .padding(24.dp)
    ) {
        ShapedIcon(Icons.Filled.Favorite, MaterialShapes.Cookie9Sided.toShape(), size = 52.dp)
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            stringResource(R.string.sponsor_hero_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(R.string.sponsor_hero_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

/** One way of helping, as a row of a connected group: the icon on its own accent shape. */
@Composable
private fun SupportRow(
    icon: ImageVector,
    shape: Shape,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShapedIcon(icon, shape)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (enabled) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
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
private const val DEVELOPER_URL = "https://github.com/sibtainocn"
private const val ISSUES_URL = "https://github.com/SibtainOcn/Hazel/issues"
private const val SPONSORS_URL = "https://github.com/sponsors/SibtainOcn"

private const val BMAC_URL = "https://buymeacoffee.com/sibtainocean"
