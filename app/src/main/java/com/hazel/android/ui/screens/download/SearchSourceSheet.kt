package com.hazel.android.ui.screens.download

import com.hazel.android.ui.theme.glassBackdrop
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.hazel.android.ui.components.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hazel.android.HazelApp
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.ui.components.EqualWidthActions
import com.hazel.android.ui.screens.download.batch.batchSheetColor
import com.hazel.android.ui.screens.more.ChoiceRow
import com.hazel.android.ui.screens.more.DialogButton
import com.hazel.android.ui.screens.more.groupShape
import kotlinx.coroutines.launch

/**
 * Where words typed into search are sent. The sites sit in one scrolling group, and Search
 * the Web stays pinned under them, so it is always the next step when a site is not listed.
 * Picking a site saves it and closes the sheet.
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

    // The note on searching the Web shows the first time only. Until the setting is read it
    // counts as unread, so the note is never skipped by a tap that comes too soon.
    val noticeSeen by remember(context) { SettingsRepository.getWebSearchNoticeSeen(context) }
        .collectAsState(initial = null)
    var showNotice by rememberSaveable { mutableStateOf(false) }
    val searchWeb = {
        Toast.makeText(context, R.string.search_under_maintenance, Toast.LENGTH_SHORT).show()
    }

    val sites = SearchSource.entries.map { it to stringResource(it.labelRes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = batchSheetColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.fetch_settings_search_on),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(sites, key = { _, (source, _) -> source.name }) { index, (source, label) ->
                    ChoiceRow(
                        label = label,
                        description = null,
                        chosen = source == selected,
                        role = Role.RadioButton,
                        shape = groupShape(index, sites.size),
                        leading = { SiteMark(label) },
                        onClick = {
                            // On the app's scope: the sheet closes at once, and the choice
                            // must still be written.
                            HazelApp.instance.applicationScope.launch {
                                SettingsRepository.setSearchSource(context.applicationContext, source)
                            }
                            onDismiss()
                        }
                    )
                }
            }

            WebRow(onClick = { if (noticeSeen == true) searchWeb() else showNotice = true })
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
 * by a gap rather than drawn differently.
 */
@Composable
private fun WebRow(onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
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
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
            modifier = Modifier.glassBackdrop(RoundedCornerShape(28.dp)),
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
