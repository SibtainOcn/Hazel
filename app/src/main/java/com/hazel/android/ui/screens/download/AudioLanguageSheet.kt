package com.hazel.android.ui.screens.download

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.download.languageLabel

/**
 * Which soundtrack a download takes.
 *
 * Built like the format sheet, because it is the same kind of question asked of the same
 * media: a list of what the source published, with the current answer marked. Only shown
 * where there is a choice, which is rare; most media has one soundtrack and this never
 * appears for it.
 *
 * The first entry is the source's own default. It stays selectable rather than being
 * dropped once a language is picked, since going back to whatever the uploader called the
 * original is otherwise impossible without knowing which one that was.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLanguageSheet(
    languages: List<String>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    // The tag, the name and whether this is the row that is currently set, worked out once
    // rather than per frame.
    val rows = remember(languages, selected) {
        listOf(LanguageRow(null, "Source default", selected == null)) +
                languages.map { code -> LanguageRow(code, languageLabel(code), code == selected) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    "Audio language",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select soundtrack",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = WindowInsets.navigationBars
                    .asPaddingValues()
                    .let { PaddingValues(bottom = it.calculateBottomPadding() + 24.dp) }
            ) {
                items(
                    count = rows.size,
                    key = { rows[it].code ?: "default" }
                ) { index ->
                    val row = rows[index]
                    LanguageRow(row = row, onClick = { onPick(row.code) })
                }
            }
        }
    }
}

/** One line of the sheet: a code, what it is called, and whether it is the current answer. */
private data class LanguageRow(
    val code: String?,
    val label: String,
    val selected: Boolean
)

@Composable
private fun LanguageRow(row: LanguageRow, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (row.selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    row.code?.uppercase() ?: "AUTO",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = if ((row.code?.length ?: 4) > 4) 11.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                row.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (row.selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (row.selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
