package com.hazel.android.ui.screens.gallery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.components.FormatRowShimmer
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.ui.components.ShimmerHost
import com.hazel.android.ui.components.ShimmerLabel
import com.hazel.android.ui.screens.download.IncognitoButton
import com.hazel.android.ui.screens.download.SHEET_TAB_PADDING
import com.hazel.android.ui.screens.download.SheetDownloadPill
import com.hazel.android.ui.screens.download.SheetLinkButton
import com.hazel.android.ui.screens.download.SheetSection
import com.hazel.android.ui.screens.download.SheetTab

/**
 * The image sheet while its link is read, built from the sheet's own parts so it keeps its
 * shape: header, tab row, and the four sections, with only the values still to come shimmering.
 * The quality row shimmers in its real colours, as the format list does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GallerySheetSkeleton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }.collectAsState(initial = SaveDirs())
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        SheetHeaderLayout {
            Column {
                ShimmerLabel(
                    stringResource(R.string.format_sheet_fetching),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.format_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            SheetDownloadPill(
                label = stringResource(R.string.format_sheet_download),
                icon = Icons.Filled.Download,
                enabled = false,
                onClick = {}
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.offset(x = -SHEET_TAB_PADDING)) {
                SheetTab(label = stringResource(R.string.gallery_image), selected = true, enabled = false, onClick = {})
            }
            Spacer(modifier = Modifier.weight(1f))
            SheetLinkButton(
                enabled = false,
                onClick = {},
                shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 8.dp, bottomEnd = 8.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            IncognitoButton(
                onFeedback = {},
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        SheetSection(
            icon = Icons.Outlined.Description,
            iconShape = MaterialShapes.Cookie6Sided,
            title = stringResource(R.string.format_sheet_section_details),
            summary = "",
            loading = true,
            expanded = false,
            onToggle = {}
        ) {}
        SheetSection(
            icon = Icons.Outlined.PhotoSizeSelectLarge,
            iconShape = MaterialShapes.Cookie7Sided,
            title = stringResource(R.string.gallery_image_size),
            summary = "",
            loading = true,
            expanded = true,
            onToggle = {}
        ) {
            ShimmerHost(modifier = Modifier.fillMaxWidth(), fast = true) { FormatRowShimmer() }
        }
        SheetSection(
            icon = Icons.Outlined.FolderOpen,
            iconShape = MaterialShapes.Pentagon,
            title = stringResource(R.string.format_sheet_save_dir),
            summary = saveDirs.labelOf(SaveKind.GALLERY),
            expanded = false,
            onToggle = {}
        ) {}
        SheetSection(
            icon = Icons.Outlined.Tune,
            iconPill = true,
            title = stringResource(R.string.gallery_adjust_image),
            summary = "",
            loading = true,
            expanded = false,
            onToggle = {}
        ) {}
        Spacer(modifier = Modifier.height(28.dp))
    }
}
