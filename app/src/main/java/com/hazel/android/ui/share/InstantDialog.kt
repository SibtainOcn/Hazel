package com.hazel.android.ui.share

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.hazel.android.R

/**
 * What Hazel Instant shows over the app a link was shared from. The download has already
 * been handed over by the time it appears, so it only confirms: OK closes it, and the tune
 * button opens Synthesizing, where the settings Instant downloads with are changed.
 */
@Composable
fun InstantDialog(onConfirm: () -> Unit, onTune: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        icon = {
            Icon(
                painterResource(R.drawable.ic_hazel_bolt),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.instant_started), fontWeight = FontWeight.Bold) },
        dismissButton = {
            IconButton(onClick = onTune) {
                Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.instant_settings))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.instant_ok)) }
        }
    )
}
