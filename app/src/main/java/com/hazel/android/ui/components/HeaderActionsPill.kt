package com.hazel.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.theme.sheetButtonColor

/**
 * The header's right side: a Support heart and Search in one round tonal pill, the same fill,
 * height and shape as [GitHubStarButton] opposite, so the two ends of the header match. Each
 * icon is its own button with a round ripple. The vertical padding keeps the header row as
 * tall as a 48dp button.
 */
@Composable
fun HeaderActionsPill(
    onSupport: () -> Unit,
    onSearch: () -> Unit,
    searchDescription: String,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(sheetButtonColor)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSupport),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = stringResource(R.string.sponsor_title),
                tint = ink,
                modifier = Modifier.size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSearch),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = searchDescription,
                tint = ink,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
