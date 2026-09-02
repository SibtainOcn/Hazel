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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Support Hazel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HeroCard()

        Spacer(modifier = Modifier.height(28.dp))

        SectionLabel("Ways to support")

        SupportCard(
            icon = Icons.Filled.Favorite,
            title = "GitHub Sponsors",
            subtitle = "Monthly or one off, cancel whenever. Handled entirely by GitHub.",
            enabled = SPONSORS_URL.isNotBlank(),
            onClick = { openLink(context, SPONSORS_URL) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SupportCard(
            icon = Icons.Filled.Coffee,
            title = "Ko-fi",
            // The address is not published yet. The card says so rather than being hidden,
            // since a support option that appears later looks like an afterthought and one
            // that is coming reads as a plan.
            subtitle = if (KOFI_URL.isBlank()) "Opening soon" else "A one off, no account needed.",
            enabled = KOFI_URL.isNotBlank(),
            onClick = { openLink(context, KOFI_URL) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        SectionLabel("Costs nothing, helps as much")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                HelpRow(
                    icon = Icons.Filled.Star,
                    title = "Star the project",
                    subtitle = "The one number people look at before trying an app.",
                    onClick = { openLink(context, SOURCE_URL) }
                )
                HelpRow(
                    icon = Icons.Filled.Share,
                    title = "Tell someone about it",
                    subtitle = "Word of mouth is the whole of this app's marketing.",
                    onClick = { shareApp(context) }
                )
                HelpRow(
                    icon = Icons.Filled.BugReport,
                    title = "Report what breaks",
                    subtitle = "A site that stopped working is a fix nobody else can write.",
                    onClick = { openLink(context, ISSUES_URL) }
                )
                HelpRow(
                    icon = Icons.Filled.Code,
                    title = "Read the source",
                    subtitle = "Every line of it is public, and pull requests are welcome.",
                    onClick = { openLink(context, SOURCE_URL) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "No advertisements, nothing measured about what you download, and no feature " +
                    "held back for anyone. Support changes how much time goes into Hazel, " +
                    "not what it will do for you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

/** The block at the top, which is the part that has something to say. */
@Composable
private fun HeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .background(
                    // A wash rather than a fill. The accent is the only colour in the app,
                    // and at this strength it reads as light falling on the card instead of
                    // as a second surface competing with everything under it.
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                "Free, and staying that way",
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Hazel is built in evenings and weekends, and it is given away because a " +
                        "downloader that charges for the download is a worse downloader. " +
                        "Sites change constantly, and keeping up with them is the work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "If the app saved you an afternoon, anything below puts that time back " +
                        "into it. If it did not, the ways that cost nothing help just as much.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
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
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (enabled) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
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
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
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

/** Filled in when the page exists. Blank keeps the card on screen and out of reach. */
private const val KOFI_URL = ""
