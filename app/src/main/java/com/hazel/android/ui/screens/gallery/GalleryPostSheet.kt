package com.hazel.android.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryImageSize
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.ui.components.ArtworkPlaceholder
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.ui.components.ShapeIconButton
import com.hazel.android.ui.screens.download.CookiesDialog
import com.hazel.android.ui.screens.download.FilenameTemplateSheet
import com.hazel.android.ui.screens.download.MetaBadge
import com.hazel.android.ui.screens.download.OptionChip
import com.hazel.android.ui.screens.download.SHEET_TAB_PADDING
import com.hazel.android.ui.screens.download.SheetDownloadPill
import com.hazel.android.ui.screens.download.SheetSection
import com.hazel.android.ui.screens.download.SheetTab
import com.hazel.android.ui.screens.download.batch.Segment
import com.hazel.android.ui.screens.download.batch.SheetButtonGroup
import com.hazel.android.ui.screens.download.rememberSaveKindPicker
import com.hazel.android.ui.screens.download.rememberUseCookies
import com.hazel.android.ui.theme.sheetBlackColor
import com.hazel.android.ui.theme.sheetRaisedColor

internal enum class GalleryFilter { ALL, IMAGES, VIDEOS }

private enum class PostDialog { NONE, FILENAME, COOKIES, SAVE_DIR }

/**
 * Download all for a post, in the multi download sheet's shell: the post's files under a type
 * filter, ticked by default, the options that apply to all of them and the quick settings bar.
 * A row opens that file's own sheet. The choices apply to this download only.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GalleryPostSheet(
    items: List<GalleryItem>,
    postUrl: String,
    /** The files to save, counted from 1, or null for the whole post. */
    onDownload: (indexes: List<Int>?, options: GalleryOptions) -> Unit,
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
    var dialog by remember { mutableStateOf(PostDialog.NONE) }
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    var ticked by remember(items) { mutableStateOf(items.map { it.index }.toSet()) }
    var adjustOpen by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<GalleryItem?>(null) }
    val current = options ?: GalleryOptions()
    val update: (GalleryOptions) -> Unit = { options = it }

    val images = items.count { !it.isVideo }
    val videos = items.size - images
    val shown = when (filter) {
        GalleryFilter.ALL -> items
        GalleryFilter.IMAGES -> items.filter { !it.isVideo }
        GalleryFilter.VIDEOS -> items.filter { it.isVideo }
    }
    val chosen = shown.map { it.index }.filter { it in ticked }
    val allShownTicked = shown.isNotEmpty() && shown.all { it.index in ticked }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetBlackColor) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            SheetHeaderLayout {
                Column {
                    Text(
                        stringResource(R.string.batch_header_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.batch_header_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SheetDownloadPill(
                    label = stringResource(R.string.batch_download_action),
                    icon = Icons.Filled.Download,
                    enabled = options != null && chosen.isNotEmpty(),
                    onClick = {
                        val whole = filter == GalleryFilter.ALL && chosen.size == items.size
                        options?.let { onDownload(if (whole) null else chosen, it) }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pluralStringResource(R.plurals.gallery_items, items.size, items.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ShapeIconButton(
                    onClick = {
                        val ids = shown.map { it.index }
                        ticked = if (allShownTicked) ticked - ids.toSet() else ticked + ids
                    },
                    active = allShownTicked
                ) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = stringResource(R.string.batch_menu_select_all),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // layout-safe: three short tab labels with a count each
            Row(
                modifier = Modifier.offset(x = -SHEET_TAB_PADDING),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SheetTab(stringResource(R.string.gallery_filter_all, items.size), filter == GalleryFilter.ALL, true) {
                    filter = GalleryFilter.ALL
                }
                SheetTab(stringResource(R.string.gallery_filter_images, images), filter == GalleryFilter.IMAGES, images > 0) {
                    filter = GalleryFilter.IMAGES
                }
                SheetTab(stringResource(R.string.gallery_filter_videos, videos), filter == GalleryFilter.VIDEOS, videos > 0) {
                    filter = GalleryFilter.VIDEOS
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.index }) { item ->
                    GalleryPostRow(
                        item = item,
                        checked = item.index in ticked,
                        onCheckedChange = { on -> ticked = if (on) ticked + item.index else ticked - item.index },
                        onOpen = { opened = item }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            val filenameLabel = stringResource(R.string.format_sheet_filename_template)
            val skipLabel = stringResource(R.string.gallery_skip_downloaded)
            val metadataLabel = stringResource(R.string.gallery_metadata)
            val postDateLabel = stringResource(R.string.gallery_post_date)
            val zipLabel = stringResource(R.string.gallery_pack_zip)
            val cookiesLabel = stringResource(R.string.cookies_title)
            val filenameOn = current.safeTemplate != null
            SheetSection(
                icon = Icons.Outlined.Tune,
                iconPill = true,
                title = stringResource(R.string.gallery_adjust_all),
                summary = listOfNotNull(
                    filenameLabel.takeIf { filenameOn },
                    skipLabel.takeIf { current.skipDownloaded },
                    metadataLabel.takeIf { current.writeMetadata },
                    postDateLabel.takeIf { current.keepPostDate },
                    zipLabel.takeIf { current.packZip },
                    cookiesLabel.takeIf { cookiesOn }
                ).joinToString(", "),
                expanded = adjustOpen,
                onToggle = { adjustOpen = !adjustOpen },
                color = sheetRaisedColor
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OptionChip(filenameLabel, Icons.Filled.Edit, active = filenameOn, compact = true) {
                        dialog = PostDialog.FILENAME
                    }
                    OptionChip(skipLabel, Icons.Filled.SkipNext, active = current.skipDownloaded, compact = true) {
                        update(current.copy(skipDownloaded = !current.skipDownloaded))
                    }
                    OptionChip(metadataLabel, Icons.Filled.DataObject, active = current.writeMetadata, compact = true) {
                        update(current.copy(writeMetadata = !current.writeMetadata))
                    }
                    OptionChip(postDateLabel, Icons.Filled.Schedule, active = current.keepPostDate, compact = true) {
                        update(current.copy(keepPostDate = !current.keepPostDate))
                    }
                    OptionChip(zipLabel, Icons.Filled.FolderZip, active = current.packZip, compact = true) {
                        update(current.copy(packZip = !current.packZip))
                    }
                    OptionChip(cookiesLabel, Icons.Filled.Cookie, active = cookiesOn, compact = true) {
                        dialog = PostDialog.COOKIES
                    }
                }
            }

            val accent = MaterialTheme.colorScheme.primary
            SheetButtonGroup {
                Segment(
                    position = 0, count = 5, weight = 1f,
                    description = stringResource(R.string.batch_bar_download_type),
                    onClick = {
                        filter = GalleryFilter.entries[(filter.ordinal + 1) % GalleryFilter.entries.size]
                            .let { next -> if (next.isEmptyIn(images, videos)) GalleryFilter.ALL else next }
                    },
                    icon = rememberVectorPainter(
                        when (filter) {
                            GalleryFilter.ALL -> Icons.Filled.Collections
                            GalleryFilter.IMAGES -> Icons.Filled.Image
                            GalleryFilter.VIDEOS -> Icons.Filled.Videocam
                        }
                    )
                )
                Segment(
                    position = 1, count = 5, weight = 1.6f,
                    description = stringResource(R.string.gallery_image_size),
                    onClick = {
                        val sizes = GalleryImageSize.entries
                        update(current.copy(imageSize = sizes[(current.imageSize.ordinal + 1) % sizes.size]))
                    },
                    text = imageSizeLabel(current.imageSize).uppercase()
                )
                Segment(
                    position = 2, count = 5, weight = 1f,
                    description = stringResource(R.string.batch_bar_save_location),
                    onClick = { dialog = PostDialog.SAVE_DIR },
                    icon = rememberVectorPainter(Icons.Filled.Folder)
                )
                Segment(
                    position = 3, count = 5, weight = 1f,
                    description = zipLabel,
                    onClick = { update(current.copy(packZip = !current.packZip)) },
                    icon = rememberVectorPainter(Icons.Filled.FolderZip),
                    fill = if (current.packZip) accent.copy(alpha = 0.22f) else null,
                    tint = if (current.packZip) accent else null
                )
                Segment(
                    position = 4, count = 5, weight = 1f,
                    description = cookiesLabel,
                    onClick = { dialog = PostDialog.COOKIES },
                    icon = rememberVectorPainter(Icons.Filled.Cookie),
                    tint = if (cookiesOn) accent else null
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    opened?.let { item ->
        GalleryItemSheet(
            item = item,
            total = items.size,
            postUrl = postUrl,
            onDownload = { chosenOptions ->
                opened = null
                onDownload(listOf(item.index), chosenOptions)
            },
            onDismiss = { opened = null }
        )
    }

    when (dialog) {
        PostDialog.NONE -> Unit
        PostDialog.FILENAME -> FilenameTemplateSheet(
            kind = GalleryTemplates,
            template = current.filenameTemplate,
            onConfirm = {
                update(current.copy(filenameTemplate = it))
                dialog = PostDialog.NONE
            },
            onDismiss = { dialog = PostDialog.NONE }
        )
        PostDialog.COOKIES -> CookiesDialog(url = postUrl, onDismiss = { dialog = PostDialog.NONE })
        PostDialog.SAVE_DIR -> GallerySaveLocation(
            saveDirs = saveDirs,
            onPick = pickSaveDir,
            onDismiss = { dialog = PostDialog.NONE }
        )
    }
}

private fun GalleryFilter.isEmptyIn(images: Int, videos: Int): Boolean = when (this) {
    GalleryFilter.ALL -> false
    GalleryFilter.IMAGES -> images == 0
    GalleryFilter.VIDEOS -> videos == 0
}

/** One file of the post: tick, thumbnail, `#n · title`, its type and size, and a type icon. */
@Composable
private fun GalleryPostRow(
    item: GalleryItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = sheetRaisedColor
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
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
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    "#${item.index} · " + item.title.ifBlank {
                        stringResource(if (item.isVideo) R.string.gallery_video else R.string.gallery_image)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                MetaBadge(galleryTag(item))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                if (item.isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                contentDescription = stringResource(if (item.isVideo) R.string.gallery_video else R.string.gallery_image),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
