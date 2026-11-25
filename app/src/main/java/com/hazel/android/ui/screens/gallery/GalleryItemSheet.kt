package com.hazel.android.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryVideoMode
import com.hazel.android.ui.components.ArtworkPlaceholder
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.ui.screens.download.CookiesDialog
import com.hazel.android.ui.screens.download.CornerTag
import com.hazel.android.ui.screens.download.FIELD_GROUP_GAP
import com.hazel.android.ui.screens.download.FilenameTemplateSheet
import com.hazel.android.ui.screens.download.OptionChip
import com.hazel.android.ui.screens.download.SaveDirField
import com.hazel.android.ui.screens.download.SheetDownloadPill
import com.hazel.android.ui.screens.download.SheetSection
import com.hazel.android.ui.screens.download.fieldGroupShape
import com.hazel.android.ui.screens.download.rememberSaveKindPicker
import com.hazel.android.ui.screens.download.rememberUseCookies
import com.hazel.android.ui.theme.sheetBlackColor

private enum class ItemDialog { NONE, FILENAME, COOKIES, SAVE_DIR }

/**
 * One file of a post, in the single download sheet's shell: its preview, details, size or
 * video choice, save folder and the options gallery-dl supports. The choices apply to this
 * download only.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GalleryItemSheet(
    item: GalleryItem,
    total: Int,
    /** The post's link, for the cookies dialog's site. */
    postUrl: String,
    onDownload: (GalleryOptions) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var options by rememberGalleryOptions()
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }
        .collectAsState(initial = SaveDirs())
    val pickSaveDir = rememberSaveKindPicker(saveDirs)
    val cookiesOn = rememberUseCookies()
    var dialog by remember { mutableStateOf(ItemDialog.NONE) }
    var detailsOpen by remember { mutableStateOf(false) }
    var qualityOpen by remember { mutableStateOf(true) }
    var saveOpen by remember { mutableStateOf(false) }
    var adjustOpen by remember { mutableStateOf(true) }
    val current = options ?: GalleryOptions()
    val update: (GalleryOptions) -> Unit = { options = it }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBlackColor) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            SheetHeaderLayout {
                Column {
                    Text(
                        stringResource(R.string.format_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(
                            if (item.isVideo) R.string.gallery_sheet_video_of else R.string.gallery_sheet_image_of,
                            item.index, total
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                SheetDownloadPill(
                    label = stringResource(R.string.format_sheet_download),
                    icon = Icons.Filled.Download,
                    enabled = options != null,
                    onClick = { options?.let(onDownload) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                ArtworkPlaceholder(Modifier.fillMaxSize())
                item.thumbnail?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    CornerTag(text = previewTag(item))
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            SheetSection(
                icon = Icons.Outlined.Description,
                iconShape = MaterialShapes.Cookie6Sided,
                title = stringResource(R.string.format_sheet_section_details),
                summary = listOf(item.title, item.author).filter { it.isNotBlank() }.joinToString(" · "),
                expanded = detailsOpen,
                onToggle = { detailsOpen = !detailsOpen }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(FIELD_GROUP_GAP)) {
                    ReadOnlyField(stringResource(R.string.format_sheet_label_title), item.title, fieldGroupShape(0, 2))
                    ReadOnlyField(stringResource(R.string.format_sheet_label_author), item.author, fieldGroupShape(1, 2))
                }
            }

            if (item.isVideo) {
                SheetSection(
                    icon = Icons.Outlined.HighQuality,
                    iconShape = MaterialShapes.Cookie7Sided,
                    title = stringResource(R.string.format_sheet_video_quality),
                    summary = stringResource(
                        if (current.videoMode == GalleryVideoMode.BEST) R.string.gallery_video_best
                        else R.string.gallery_video_single
                    ),
                    expanded = qualityOpen,
                    onToggle = { qualityOpen = !qualityOpen }
                ) {
                    VideoModeChoices(current.videoMode) { update(current.copy(videoMode = it)) }
                }
            } else {
                SheetSection(
                    icon = Icons.Outlined.PhotoSizeSelectLarge,
                    iconShape = MaterialShapes.Cookie7Sided,
                    title = stringResource(R.string.gallery_image_size),
                    summary = imageSizeLabel(current.imageSize),
                    expanded = qualityOpen,
                    onToggle = { qualityOpen = !qualityOpen }
                ) {
                    ImageSizeChoices(current.imageSize) { update(current.copy(imageSize = it)) }
                }
            }

            SheetSection(
                icon = Icons.Outlined.FolderOpen,
                iconShape = MaterialShapes.Pentagon,
                title = stringResource(R.string.format_sheet_save_dir),
                summary = saveDirs.labelOf(SaveKind.GALLERY),
                expanded = saveOpen,
                onToggle = { saveOpen = !saveOpen }
            ) {
                SaveDirField(
                    label = saveDirs.labelOf(SaveKind.GALLERY),
                    onClick = { dialog = ItemDialog.SAVE_DIR }
                )
            }

            val filenameLabel = stringResource(R.string.format_sheet_filename_template)
            val metadataLabel = stringResource(R.string.gallery_metadata)
            val postDateLabel = stringResource(R.string.gallery_post_date)
            val cookiesLabel = stringResource(R.string.cookies_title)
            val filenameOn = current.safeTemplate != null
            SheetSection(
                icon = Icons.Outlined.Tune,
                iconPill = true,
                title = stringResource(if (item.isVideo) R.string.format_sheet_adjust_video else R.string.gallery_adjust_image),
                summary = listOfNotNull(
                    filenameLabel.takeIf { filenameOn },
                    metadataLabel.takeIf { current.writeMetadata },
                    postDateLabel.takeIf { current.keepPostDate },
                    cookiesLabel.takeIf { cookiesOn }
                ).joinToString(", "),
                expanded = adjustOpen,
                onToggle = { adjustOpen = !adjustOpen }
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.isVideo) {
                        OptionChip(
                            label = stringResource(R.string.gallery_video_options),
                            icon = Icons.Filled.SmartDisplay,
                            onClick = { showUnderMaintenance(context) }
                        )
                    }
                    OptionChip(
                        label = filenameLabel,
                        icon = Icons.Filled.Edit,
                        active = filenameOn,
                        onClick = { dialog = ItemDialog.FILENAME }
                    )
                    OptionChip(
                        label = metadataLabel,
                        icon = Icons.Filled.DataObject,
                        active = current.writeMetadata,
                        onClick = { update(current.copy(writeMetadata = !current.writeMetadata)) }
                    )
                    if (!item.isVideo) {
                        OptionChip(
                            label = stringResource(R.string.gallery_caption),
                            icon = Icons.Filled.FormatQuote,
                            onClick = { showUnderMaintenance(context) }
                        )
                    }
                    OptionChip(
                        label = postDateLabel,
                        icon = Icons.Filled.Schedule,
                        active = current.keepPostDate,
                        onClick = { update(current.copy(keepPostDate = !current.keepPostDate)) }
                    )
                    OptionChip(
                        label = cookiesLabel,
                        icon = Icons.Filled.Cookie,
                        active = cookiesOn,
                        onClick = { dialog = ItemDialog.COOKIES }
                    )
                    OptionChip(
                        label = stringResource(R.string.format_sheet_extra_args),
                        icon = Icons.Filled.Code,
                        onClick = { showUnderMaintenance(context) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    when (dialog) {
        ItemDialog.NONE -> Unit
        ItemDialog.FILENAME -> FilenameTemplateSheet(
            kind = GalleryTemplates,
            template = current.filenameTemplate,
            onConfirm = {
                update(current.copy(filenameTemplate = it))
                dialog = ItemDialog.NONE
            },
            onDismiss = { dialog = ItemDialog.NONE }
        )
        ItemDialog.COOKIES -> CookiesDialog(url = postUrl, onDismiss = { dialog = ItemDialog.NONE })
        ItemDialog.SAVE_DIR -> GallerySaveLocation(
            saveDirs = saveDirs,
            onPick = pickSaveDir,
            onDismiss = { dialog = ItemDialog.NONE }
        )
    }
}

/** `JPG · 1080 × 1350`, or the type alone when the site gave no size. */
private fun previewTag(item: GalleryItem): String {
    val type = item.extension.uppercase()
    return if (item.width > 0 && item.height > 0) "$type · ${item.width} × ${item.height}" else type
}
