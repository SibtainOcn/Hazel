package com.hazel.android.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.util.AppLocale

/**
 * The language picker.
 *
 * Each language is named in itself. Somebody hunting for their own language is looking for
 * the word they would write, not the English for it, and a list of English names is no use
 * to the person who most needs this screen. The English name sits underneath in smaller
 * type, so anyone who arrived here by accident can find their way back.
 *
 * The list leads with the device's own choice, which is what the app does when nothing has
 * been picked and the thing most people want if they ever come back to undo a decision.
 *
 * It scrolls rather than growing: the dialog is capped at roughly six rows so it stays a
 * dialog on a small screen instead of running off the bottom of it.
 */
@Composable
fun LanguageDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.language_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    LanguageRow(
                        endonym = stringResource(R.string.language_system),
                        english = stringResource(R.string.language_system_description),
                        selected = current.isBlank(),
                        onClick = { onPick(AppLocale.SYSTEM) }
                    )
                }
                items(AppLocale.LANGUAGES, key = { it.tag }) { language ->
                    LanguageRow(
                        endonym = language.endonym,
                        english = language.english,
                        selected = current.equals(language.tag, ignoreCase = true),
                        onClick = { onPick(language.tag) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.language_close))
            }
        }
    )
}

@Composable
private fun LanguageRow(
    endonym: String,
    english: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                endonym,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Left out where it would only repeat the line above it, which is English and
            // the device's own entry.
            if (english != endonym) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    english,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (selected) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * Said after the language is changed.
 *
 * On Android 13 and later the platform has already redrawn everything by the time this
 * appears, so it confirms rather than warns. Below that the activity is rebuilt behind it,
 * which is the same outcome by a different route, and either way a screen full of unfamiliar
 * words is worth one sentence of explanation in the language just chosen.
 */
@Composable
fun LanguageChangedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.language_changed_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = { Text(stringResource(R.string.language_changed_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.language_changed_ok))
            }
        }
    )
}
