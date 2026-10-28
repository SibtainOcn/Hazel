package com.hazel.android.ui.screens.more

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Translate
import com.hazel.android.ui.components.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import com.hazel.android.ui.components.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.components.EqualWidthActions
import com.hazel.android.ui.components.keepFlingInSheet
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
 * it is worth the extra tap. Confirm sits at the bottom, full size, under the thumb, and is
 * only enabled once something other than the current language is picked.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LanguageSheet(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))

    // What is picked but not yet applied. It starts on whatever is in force, so opening the
    // sheet and confirming without touching anything changes nothing.
    var draft by remember(current) { mutableStateOf(current) }
    val changed = !draft.equals(current, ignoreCase = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            // ── Heading: the icon, the title, and what is in force or picked ──
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.language_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AnimatedContent(
                        targetState = changed,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "languageSubtitle"
                    ) { isChanged ->
                        Text(
                            if (isChanged) {
                                stringResource(R.string.language_selected, labelFor(draft))
                            } else {
                                stringResource(R.string.language_current, labelFor(current))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isChanged) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── The languages, two to a row ──
            // Opens on the language in use, a row above it left in view.
            val chosen = remember(current) {
                AppLocale.LANGUAGES.indexOfFirst { it.tag.equals(current, ignoreCase = true) } + 1
            }
            val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (chosen - 2).coerceAtLeast(0))
            LazyVerticalGrid(
                state = grid,
                // One column once the font is large enough that two would cut most names short.
                columns = GridCells.Fixed(if (LocalDensity.current.fontScale >= 1.7f) 1 else 2),
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .keepFlingInSheet(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
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

            // ── Actions, full width under the thumb ──
            // Half the width each while both labels fit; one above the other when not.
            EqualWidthActions(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                spacing = 10.dp
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 52.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        stringResource(R.string.language_cancel),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Button(
                    onClick = { onConfirm(draft) },
                    enabled = changed,
                    modifier = Modifier.heightIn(min = 52.dp),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        stringResource(R.string.language_confirm),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * One language.
 *
 * Flat and borderless. The chosen card fills with the accent, rounds its corners out and
 * shows a tick on a scalloped badge beside the English name; the others are quiet tonal
 * tiles. The corner change is sprung, so moving the choice reads as one card letting go and another taking it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageCard(
    native: String,
    english: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        if (selected) 28.dp else 16.dp,
        spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "languageCorner"
    )
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "languageContainer"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
            Text(
                native,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    english,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(22.dp)
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
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
        icon = { Icon(Icons.Outlined.Translate, contentDescription = null) },
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
