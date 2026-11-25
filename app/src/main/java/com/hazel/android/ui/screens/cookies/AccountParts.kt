package com.hazel.android.ui.screens.cookies

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.data.AccountState
import com.hazel.android.data.AccountStatus
import com.hazel.android.data.CookieEntry
import com.hazel.android.ui.theme.sheetButtonColor

/** Fill and ink of a state, shared by the pill and the Expired action. */
@Composable
internal fun stateColors(state: AccountState): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (state) {
        AccountState.ACTIVE -> scheme.primary.copy(alpha = 0.18f) to scheme.primary
        AccountState.EXPIRED -> scheme.error.copy(alpha = 0.18f) to scheme.error
        AccountState.SIGNED_OUT -> scheme.onSurface.copy(alpha = 0.10f) to scheme.onSurface.copy(alpha = 0.72f)
    }
}

@Composable
internal fun StatePill(state: AccountState, modifier: Modifier = Modifier) {
    val (fill, ink) = stateColors(state)
    Text(
        stringResource(
            when (state) {
                AccountState.ACTIVE -> R.string.cookies_state_active
                AccountState.EXPIRED -> R.string.cookies_expired
                AccountState.SIGNED_OUT -> R.string.cookies_state_signed_out
            }
        ),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
        color = ink,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

/** The site name with its state pill right after it; the name gives way first. */
@Composable
internal fun AccountTitle(name: String, state: AccountState, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = style,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(8.dp))
        StatePill(state)
    }
}

/** A cookie-shaped tile with the site's short mark, standing in for a fetched icon. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SiteMark(site: String?, name: String, size: Dp = 40.dp, dimmed: Boolean = false) {
    val mark = SHORT_MARKS[site] ?: (name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?")
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(size)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(accent.copy(alpha = if (dimmed) 0.08f else 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            mark,
            style = if (size > 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent.copy(alpha = if (dimmed) 0.5f else 1f),
            maxLines = 1
        )
    }
}

private val SHORT_MARKS = mapOf(
    "instagram.com" to "IG", "youtube.com" to "YT", "twitter.com" to "X",
    "reddit.com" to "R", "tiktok.com" to "T", "facebook.com" to "f"
)

/** The card's second line: expiry or why it is not signed in, and when it was last checked. */
@Composable
internal fun accountLine(status: AccountStatus, entry: CookieEntry?): String {
    val now = System.currentTimeMillis()
    val expiresMs = status.expiresAt?.times(1000)
    val main = when {
        entry == null -> stringResource(R.string.cookies_dialog_none_saved)
        status.state == AccountState.SIGNED_OUT -> stringResource(R.string.cookies_no_login)
        status.state == AccountState.EXPIRED && expiresMs != null && expiresMs < now ->
            stringResource(R.string.cookies_expired_ago, relative(expiresMs, now))
        status.state == AccountState.EXPIRED -> stringResource(R.string.cookies_signed_out_by_site)
        expiresMs == null -> stringResource(R.string.cookies_session_only)
        else -> stringResource(R.string.cookies_expires, relative(expiresMs, now))
    }
    val checkedAt = entry?.checkedAt ?: 0
    return if (checkedAt > 0 && status.state != AccountState.SIGNED_OUT) {
        main + " · " + stringResource(R.string.cookies_checked, relative(checkedAt, now))
    } else main
}

internal fun relative(atMs: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(atMs, now, DateUtils.MINUTE_IN_MILLIS).toString()

/** Two buttons as one connected M3 group: round outer ends, tight where they meet. */
@Composable
internal fun SplitButtons(
    startLabel: String,
    onStart: () -> Unit,
    endLabel: String,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
    startDanger: Boolean = false,
    endEnabled: Boolean = true,
    startEnabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SplitHalf(
            label = startLabel,
            onClick = onStart,
            enabled = startEnabled,
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            fill = if (startDanger) scheme.error.copy(alpha = 0.18f) else sheetButtonColor,
            ink = if (startDanger) scheme.error else scheme.onSurface
        )
        SplitHalf(
            label = endLabel,
            onClick = onEnd,
            enabled = endEnabled,
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp),
            fill = scheme.primary,
            ink = scheme.onPrimary
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SplitHalf(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    shape: RoundedCornerShape,
    fill: Color,
    ink: Color
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = if (enabled) fill else fill.copy(alpha = fill.alpha * 0.5f),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) ink else ink.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
