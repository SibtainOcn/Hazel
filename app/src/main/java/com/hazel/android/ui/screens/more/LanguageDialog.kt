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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.util.AppLocale

/**
 * The language picker.
 *
 * A sheet rather than a dialog, and a grid of cards rather than a list of rows, because ten
 * languages in one column is a scroll for something that fits on one screen in two. Each
 * card names the language in itself with the English underneath: somebody hunting for their
 * own language is looking for the word they would write, and a list of English names is no
 * use to exactly the person who most needs this screen.
 *
 * Choosing a card does not change anything. The heading says what the app is in now, and
 * switches to what has been picked, and only Confirm applies it. A language is the one
 * setting where a mis-tap leaves you unable to read the screen you would use to undo it, so
 * it is worth the extra tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // What is picked but not yet applied. It starts on whatever is in force, so opening the
    // sheet and confirming without touching anything changes nothing.
    var draft by remember(current) { mutableStateOf(current) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // ── Heading, with the actions on the right ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.language_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (draft == current) {
                            stringResource(R.string.language_current, labelFor(current))
                        } else {
                            stringResource(R.string.language_selected, labelFor(draft))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.language_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                Surface(
                    onClick = { onConfirm(draft) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        stringResource(R.string.language_confirm),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ── The languages, two to a row ──
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 460.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = AppLocale.SYSTEM) {
                    LanguageCard(
                        native = stringResource(R.string.language_system),
                        english = stringResource(R.string.language_system_description),
                        selected = draft.isBlank(),
                        onClick = { draft = AppLocale.SYSTEM }
                    )
                }
                items(AppLocale.LANGUAGES, key = { it.tag }) { language ->
                    LanguageCard(
                        native = language.endonym,
                        english = language.english,
                        selected = draft.equals(language.tag, ignoreCase = true),
                        onClick = { draft = language.tag }
                    )
                }
            }
        }
    }
}

/**
 * One language.
 *
 * The mark for the chosen one is a dot in the corner rather than a tick across the card: at
 * this size a tick competes with the two lines of text, and the border and fill are already
 * carrying the state. The dot is there for the case where a card is read on its own.
 */
@Composable
private fun LanguageCard(
    native: String,
    english: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest
        else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    native,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    english,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** The name to show for a tag in the heading, which is the language in itself. */
@Composable
private fun labelFor(tag: String): String {
    if (tag.isBlank()) return stringResource(R.string.language_system)
    return AppLocale.LANGUAGES.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.endonym
        ?: tag
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
