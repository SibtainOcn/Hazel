package com.hazel.android.ui.screens.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.ui.components.keepFlingInSheet
import com.hazel.android.ui.screens.download.BadgeTone
import com.hazel.android.ui.screens.download.FormatRowFrame
import com.hazel.android.ui.screens.download.MetaBadge
import com.hazel.android.ui.screens.download.SheetConfirmPill
import com.hazel.android.ui.screens.more.groupShape
import com.hazel.android.ui.theme.sheetBlackColor

/** One choice of the gallery's quality picker, drawn as a format row is. */
internal class QualityChoice<T>(
    val value: T,
    /** What the row's cookie says, such as JPG or BEST. */
    val badge: String,
    val headline: String,
    /** Pixel size, drawn in the size tone. */
    val pixels: String? = null,
    /** Facts such as Best; always drawn in the crimson id tone. */
    val hints: List<String> = emptyList()
)

/**
 * The gallery's quality list in the video sheet's picker shell: Format / Select format over
 * the rows, the pick held until OK. Rows are the same format row frame the video list uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> GalleryQualitySheet(
    choices: List<QualityChoice<T>>,
    selected: T,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var draft by remember { mutableStateOf(selected) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBlackColor) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeaderLayout(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column {
                    Text(
                        stringResource(R.string.format_selection_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.format_selection_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                SheetConfirmPill(enabled = true, onClick = { onConfirm(draft) })
            }

            Column(
                modifier = Modifier
                    .keepFlingInSheet()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                choices.forEachIndexed { i, choice ->
                    FormatRowFrame(
                        badge = choice.badge,
                        headline = choice.headline,
                        corner = null,
                        selected = choice.value == draft,
                        onClick = { draft = choice.value },
                        compact = true,
                        shape = groupShape(i, choices.size)
                    ) {
                        QualityTags(choice)
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

/** A choice's facts under its headline, as a format row shows codec and size. */
@Composable
internal fun QualityTags(choice: QualityChoice<*>) {
    if (choice.pixels == null && choice.hints.isEmpty()) return
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        choice.pixels?.let { MetaBadge(it, tone = BadgeTone.SIZE) }
        choice.hints.forEach { MetaBadge(it, tone = BadgeTone.ID) }
    }
}
