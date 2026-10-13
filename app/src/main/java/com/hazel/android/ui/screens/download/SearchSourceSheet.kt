package com.hazel.android.ui.screens.download

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.collectAsState
import com.hazel.android.ui.components.EqualWidthActions
import com.hazel.android.ui.screens.more.DialogButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.ui.screens.download.batch.batchSheetColor
import com.hazel.android.ui.screens.more.ChoiceRow
import com.hazel.android.ui.screens.more.groupShape
import kotlinx.coroutines.launch

/**
 * Where words typed into search are sent. The sites sit in one scrolling group, and Search
 * the Web stays pinned under them, so it is always the next step when a site is not listed.
 *
 * The search icon turns the header itself into a field; the clear button, or a tap anywhere
 * else in the sheet, turns it back. Picking a site saves it and closes the sheet.
 *
 * Search the Web is a row like the sites, set apart by a gap. The first tap explains what
 * it does and that it is in beta; after that it goes straight through. Searching the Web
 * itself is not built yet, so it says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSourceSheet(
    selected: SearchSource,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val closeSearch = {
        searching = false
        query = ""
    }

    // The note on searching the Web shows the first time only. Until the setting is read it
    // counts as unread, so the note is never skipped by a tap that comes too soon.
    val noticeSeen by remember(context) { SettingsRepository.getWebSearchNoticeSeen(context) }
        .collectAsState(initial = null)
    var showNotice by rememberSaveable { mutableStateOf(false) }
    val searchWeb = {
        Toast.makeText(context, R.string.search_under_maintenance, Toast.LENGTH_SHORT).show()
    }

    // Labels resolved once; the filter then runs on plain strings as the query changes.
    val labels = SearchSource.entries.map { it to stringResource(it.labelRes) }
    val shown = remember(query, labels) {
        val q = query.trim()
        if (q.isEmpty()) labels else labels.filter { (_, label) -> label.contains(q, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = batchSheetColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                // A tap on the sheet outside the field and the rows closes the search.
                .clickable(
                    enabled = searching,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = closeSearch
                )
        ) {
            Header(
                searching = searching,
                query = query,
                onQueryChange = { query = it },
                onOpenSearch = { searching = true },
                onCloseSearch = closeSearch
            )

            if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.search_source_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(shown, key = { _, (source, _) -> source.name }) { index, (source, label) ->
                        ChoiceRow(
                            label = label,
                            description = null,
                            chosen = source == selected,
                            role = Role.RadioButton,
                            shape = groupShape(index, shown.size),
                            highlight = query.trim(),
                            leading = { SiteMark(label) },
                            onClick = {
                                // On the app's scope: the sheet closes at once, and the
                                // choice must still be written.
                                HazelApp.instance.applicationScope.launch {
                                    SettingsRepository.setSearchSource(context.applicationContext, source)
                                }
                                onDismiss()
                            }
                        )
                    }
                }
            }

            WebRow(
                lit = shown.isEmpty(),
                onClick = {
                    if (noticeSeen == true) searchWeb() else showNotice = true
                }
            )
        }
    }

    if (showNotice) {
        WebSearchNotice(
            onContinue = {
                showNotice = false
                HazelApp.instance.applicationScope.launch {
                    SettingsRepository.setWebSearchNoticeSeen(context.applicationContext)
                }
                searchWeb()
            },
            onDismiss = { showNotice = false }
        )
    }
}

/** The title and its search icon, or, while searching, the field in their place. */
@Composable
private fun Header(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit
) {
    AnimatedContent(
        targetState = searching,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "sourceHeader",
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, bottom = 12.dp)
    ) { open ->
        if (!open) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.fetch_settings_search_on),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onOpenSearch,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search_source_find),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else {
            SearchField(query = query, onQueryChange = onQueryChange, onClose = onCloseSearch)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            // The field takes its own taps, so they do not reach the sheet and close it.
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { focus.requestFocus() }
            )
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    stringResource(R.string.search_source_find),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
        }
    }
}

/** A site's mark: its first letter on a soft tile of the accent. */
@Composable
private fun SiteMark(label: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.take(1),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Search the Web, pinned under the sites: a row of the same height and corners, set apart
 * by a gap rather than drawn differently. [lit] takes the accent when no site matched,
 * making it the obvious next step.
 */
@Composable
private fun WebRow(lit: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (lit) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 60.dp)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_web_planet),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                stringResource(R.string.search_web_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Medium,
                color = if (lit) accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Shown the first time Search the Web is tapped: what it does, that it is in beta, and that
 * not every site it finds will play or download.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebSearchNotice(onContinue: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_web_planet),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.search_web_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.search_web_beta),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.search_web_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                EqualWidthActions {
                    DialogButton(stringResource(R.string.download_cancel), filled = false, onClick = onDismiss)
                    DialogButton(stringResource(R.string.search_web_continue), filled = true, onClick = onContinue)
                }
            }
        }
    }
}
