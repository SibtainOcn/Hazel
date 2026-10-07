package com.hazel.android.ui.screens.more

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hazel.android.R

/**
 * Where a standalone utility goes, once there is one.
 *
 * Empty at the moment. The converter used to live here and now sits directly under More,
 * because a screen whose whole content is one row is a tap spent on saying one word. Kept
 * rather than deleted: the next tool has somewhere to go, and nothing links here until it
 * does.
 */
@Composable
fun ToolsScreen(
    onBack: () -> Unit
) {
    SettingsScreen(title = stringResource(R.string.tools_title), onBack = onBack) {
        Text(
            stringResource(R.string.tools_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
