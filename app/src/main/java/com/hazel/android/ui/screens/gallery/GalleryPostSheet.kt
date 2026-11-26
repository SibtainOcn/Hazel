package com.hazel.android.ui.screens.gallery

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import com.hazel.android.ui.screens.download.batch.TWO_PANE_MIN_WIDTH_DP
import com.hazel.android.ui.screens.download.batch.TWO_PANE_MAX_HEIGHT_DP
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Tune
import com.hazel.android.ui.components.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.ui.components.DropdownMenu
import com.hazel.android.ui.components.DropdownMenuItem
import com.hazel.android.ui.components.FlatChip
import com.hazel.android.ui.components.MenuTone
import com.hazel.android.ui.components.ShapeIconButton
import com.hazel.android.ui.components.keepFlingInSheet
import com.hazel.android.ui.components.rememberScrollShrink
import com.hazel.android.ui.components.scrollShrink
import com.hazel.android.ui.screens.download.BadgeTone
import com.hazel.android.ui.screens.download.CookiesDialog
import com.hazel.android.ui.screens.download.FEEDBACK_MS
import com.hazel.android.ui.screens.download.Feedback
import com.hazel.android.ui.screens.download.FeedbackToast
import com.hazel.android.ui.screens.download.FilenameTemplateSheet
import com.hazel.android.ui.screens.download.LinkOptionsDialog
import com.hazel.android.ui.screens.download.MetaBadge
import com.hazel.android.ui.screens.download.OptionChip
import com.hazel.android.ui.screens.download.SheetSection
import com.hazel.android.ui.screens.download.batch.BATCH_SIDE
import com.hazel.android.ui.screens.download.batch.BatchDownloadButton
import com.hazel.android.ui.screens.download.batch.BatchRowCard
import com.hazel.android.ui.screens.download.batch.ChoiceRow
import com.hazel.android.ui.screens.download.batch.ChoiceSheet
import com.hazel.android.ui.screens.download.batch.KeyboardOverSheet
import com.hazel.android.ui.screens.download.batch.LinkSearchField
import com.hazel.android.ui.screens.download.batch.SearchBounds
import com.hazel.android.ui.screens.download.batch.Segment
import com.hazel.android.ui.screens.download.batch.SheetButtonGroup
import com.hazel.android.ui.screens.download.batch.endsSearchOnTouchOutside
import com.hazel.android.ui.screens.download.rememberIncognitoSwitch
import com.hazel.android.ui.screens.download.rememberSaveKindPicker
import com.hazel.android.ui.screens.download.rememberUseCookies
import com.hazel.android.ui.theme.sheetRaisedColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.hazel.android.gallery.GalleryPost
import androidx.compose.runtime.DisposableEffect

internal enum class GalleryFilter { ALL, IMAGES, VIDEOS }

/** One download the sheet hands back: files counted from 1 (null for the whole post) and their settings. */
internal class GalleryPick(
    val indexes: List<Int>?,
    val options: GalleryOptions,
    val title: String? = null,
    val author: String? = null
)

/** A file adjusted on its own sheet: its settings, title and author, for that file alone. */
private class FileChoice(val options: GalleryOptions, val title: String, val author: String)

/**
 * What lets Download all hand a post's videos to yt-dlp: a video's row opens yt-dlp's sheet,
 * and a video adjusted there downloads with yt-dlp, apart from the files left to gallery-dl.
 */
internal class PostVideos(val post: GalleryPost, val links: GalleryVideoLinks, val onMissed: (Int) -> Unit)

private enum class PostDialog { NONE, KIND, SIZE, FILENAME, COOKIES, SAVE_DIR, LINK }

/**
 * Download all for a post, laid out as the video sheet for a set of links is: the header and
 * Download, the count with search, selection and the list menu, the files (ticked only once
 * selecting starts, swiped to be removed), the options for all of them and the button group.
 * A file's row opens that file's own sheet, whose OK keeps its choice for this download.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GalleryPostSheet(
    items: List<GalleryItem>,
    postUrl: String,
    /** The files for gallery-dl, and the job handing the videos adjusted on yt-dlp's sheet to its queue. */
    onDownload: (picks: List<GalleryPick>, videos: Job?) -> Unit,
    onDismiss: () -> Unit,
    postVideos: PostVideos? = null
) {
    val context = LocalContext.current
    // Expanded to its content, as the set-of-links sheet is: the list is held to a few rows,
    // so the sheet stops at its usual height and does not cover the screen.
    var options by rememberGalleryOptions()
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }
        .collectAsState(initial = SaveDirs())
    val pickSaveDir = rememberSaveKindPicker(saveDirs)
    val cookiesOn = rememberUseCookies()
    var dialog by remember { mutableStateOf(PostDialog.NONE) }
    var listMenuOpen by remember { mutableStateOf(false) }
    val current = options ?: GalleryOptions()
    val update: (GalleryOptions) -> Unit = {
        options = it
        saveGalleryOptions(context, it)
    }

    var kept by remember(items) { mutableStateOf(items) }
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember(items) { mutableStateOf(emptySet<Int>()) }
    var choices by remember(items) { mutableStateOf(emptyMap<Int, FileChoice>()) }
    var opened by remember { mutableStateOf<GalleryItem?>(null) }
    var videoPicks by remember(items) { mutableStateOf(emptyMap<Int, VideoDownload>()) }
    var openedVideo by remember { mutableStateOf<GalleryItem?>(null) }
    var handedOff by remember(items) { mutableStateOf(false) }
    DisposableEffect(items) {
        onDispose { if (!handedOff) videoPicks.values.forEach { it.read.cancel() } }
    }
    fun open(item: GalleryItem) {
        if (item.isVideo && postVideos != null) openedVideo = item else opened = item
    }
    var pendingRemove by remember { mutableStateOf<GalleryItem?>(null) }
    var adjustOpen by rememberSaveable { mutableStateOf(false) }

    val images = kept.count { !it.isVideo }
    val videos = kept.size - images
    val ofKind = when (filter) {
        GalleryFilter.ALL -> kept
        GalleryFilter.IMAGES -> kept.filter { !it.isVideo }
        GalleryFilter.VIDEOS -> kept.filter { it.isVideo }
    }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = remember(ofKind, query) {
        val q = query.trim()
        if (q.isEmpty()) ofKind else ofKind.filter { it.matches(q) }
    }
    val closeSearch = {
        query = ""
        searchOpen = false
    }
    val focusManager = LocalFocusManager.current
    val bounds = remember { SearchBounds() }

    // What Download takes: the ticked files, or every file of the chosen kind when none are.
    val targets = ofKind.filter { selected.isEmpty() || it.index in selected }
    fun picks(base: GalleryOptions): List<GalleryPick> {
        val (own, shared) = targets.filter { it.index !in videoPicks }.partition { it.index in choices }
        val whole = shared.size == items.size
        return buildList {
            if (shared.isNotEmpty()) add(GalleryPick(if (whole) null else shared.map { it.index }, base))
            own.forEach { file ->
                val choice = choices.getValue(file.index)
                add(GalleryPick(listOf(file.index), choice.options, choice.title, choice.author))
            }
        }
    }
    fun endSelection() {
        selecting = false
        selected = emptySet()
    }
    fun toggle(item: GalleryItem) {
        selected = if (item.index in selected) selected - item.index else selected + item.index
    }

    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var shownFeedback by remember { mutableStateOf("") }
    LaunchedEffect(feedback) {
        val shownNow = feedback ?: return@LaunchedEffect
        shownFeedback = shownNow.message
        delay(FEEDBACK_MS)
        feedback = null
    }
    // A phone on its side is wide and short: the sheet takes the width and splits in two.
    val configuration = LocalConfiguration.current
    val twoPane = configuration.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP &&
        configuration.screenHeightDp < TWO_PANE_MAX_HEIGHT_DP

    GallerySheetFrame(
        onDismiss = onDismiss,
        sheetMaxWidth = if (twoPane) Dp.Unspecified else BottomSheetDefaults.SheetMaxWidth
    ) {
        Box(
            modifier = Modifier.endsSearchOnTouchOutside(searchOpen, bounds) {
                if (query.isBlank()) closeSearch() else focusManager.clearFocus()
            }
        ) {
        KeyboardOverSheet()

        // The sheet's parts, each written once and placed by the layout below: stacked, or
        // side by side on a phone held sideways, as the set-of-links sheet does.
        val titleBlock: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier) {
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
        }
        val downloadButton: @Composable (Modifier) -> Unit = { modifier ->
            BatchDownloadButton(
                enabled = options != null && targets.isNotEmpty(),
                onClick = {
                    options?.let { base ->
                        handedOff = true
                        val toYtDlp = targets.mapNotNull { videoPicks[it.index] }
                        val job = postVideos?.takeIf { toYtDlp.isNotEmpty() }?.let { v ->
                            v.links.download(toYtDlp, saveDirs, v.onMissed)
                        }
                        onDownload(picks(base), job)
                    }
                },
                modifier = modifier
            )
        }
        val listBar: @Composable () -> Unit = {
            // The count, and the controls that act on the list itself.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = BATCH_SIDE, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchOpen) {
                    LinkSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = closeSearch,
                        hint = stringResource(R.string.gallery_search_files),
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { bounds.field = it }
                    )
                } else {
                    Text(
                        if (selecting) pluralStringResource(R.plurals.batch_selected, selected.size, selected.size)
                        else pluralStringResource(R.plurals.gallery_items, ofKind.size, ofKind.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ShapeIconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.batch_search_open), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                ShapeIconButton(
                    onClick = { if (selecting) endSelection() else selecting = true },
                    active = selecting
                ) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = stringResource(if (selecting) R.string.batch_stop_selecting else R.string.batch_start_selecting),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box {
                    ShapeIconButton(onClick = { listMenuOpen = true }) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert),
                            contentDescription = stringResource(R.string.batch_list_options),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = listMenuOpen, onDismissRequest = { listMenuOpen = false }) {
                        if (!selecting) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_select_links)) },
                                icon = Icons.Filled.Checklist,
                                tone = MenuTone.Accent,
                                onClick = {
                                    listMenuOpen = false
                                    selecting = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_select_all)) },
                                icon = Icons.Filled.SelectAll,
                                tone = MenuTone.Accent,
                                onClick = {
                                    listMenuOpen = false
                                    selected = ofKind.map { it.index }.toSet()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_invert)) },
                                icon = Icons.Filled.SwapVert,
                                tone = MenuTone.Info,
                                onClick = {
                                    listMenuOpen = false
                                    selected = ofKind.map { it.index }.toSet() - selected
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_remove_selected)) },
                                icon = Icons.Filled.Delete,
                                tone = MenuTone.Danger,
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    listMenuOpen = false
                                    kept = kept.filter { it.index !in selected }
                                    endSelection()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_done)) },
                                icon = Icons.Filled.Done,
                                tone = MenuTone.Neutral,
                                onClick = {
                                    listMenuOpen = false
                                    endSelection()
                                }
                            )
                        }
                    }
                }
            }

        }

        val listState = rememberLazyListState()
        val shrink = rememberScrollShrink()
        LaunchedEffect(query) { if (query.isNotEmpty()) listState.scrollToItem(0) }
        val fileList: @Composable (Modifier) -> Unit = { modifier ->
            if (searchOpen && shown.isEmpty()) {
                Text(
                    stringResource(R.string.batch_search_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = BATCH_SIDE + 4.dp, vertical = 16.dp)
                )
            }
            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxWidth()
                    .keepFlingInSheet()
                    .nestedScroll(shrink),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = BATCH_SIDE),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.index }) { item ->
                    Box(modifier = Modifier.scrollShrink(shrink)) {
                        BatchRowCard(
                            thumbnail = item.thumbnail ?: item.url.takeUnless { item.isVideo },
                            corner = "#${item.index}",
                            title = (choices[item.index]?.title ?: videoPicks[item.index]?.plan?.title)
                                ?.takeIf { it.isNotBlank() } ?: defaultTitle(item),
                            typeIcon = if (item.isVideo) Icons.Filled.Videocam else Icons.Filled.Image,
                            typeDescription = stringResource(if (item.isVideo) R.string.gallery_video else R.string.gallery_image),
                            selectionMode = selecting,
                            checked = item.index in selected,
                            onClick = { if (selecting) toggle(item) else open(item) },
                            onLongClick = {
                                if (!selecting) selecting = true
                                toggle(item)
                            },
                            onTypeClick = { if (selecting) toggle(item) else open(item) },
                            removeAsked = pendingRemove?.index == item.index,
                            onAskRemove = { pendingRemove = item }
                        ) {
                            // Marked when the file carries a choice of its own, as an adjusted link is.
                            val adjusted = item.index in choices || item.index in videoPicks
                            MetaBadge(galleryTag(item), tone = if (adjusted) BadgeTone.SOLID else BadgeTone.NEUTRAL)
                        }
                    }
                }
            }
        }
        val adjustSection: @Composable () -> Unit = {
            val filenameLabel = stringResource(R.string.format_sheet_filename_template)
            val skipLabel = stringResource(R.string.gallery_skip_downloaded)
            val metadataLabel = stringResource(R.string.gallery_metadata)
            val postDateLabel = stringResource(R.string.gallery_post_date)
            val zipLabel = stringResource(R.string.gallery_pack_zip)
            val cookiesLabel = stringResource(R.string.cookies_title)
            val filenameOn = current.safeTemplate != null
            Box(modifier = Modifier.padding(horizontal = BATCH_SIDE)) {
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
                        if (items.any { it.caption.isNotBlank() }) {
                            OptionChip(stringResource(R.string.gallery_caption), Icons.Filled.FormatQuote, active = current.embedCaption, compact = true) {
                                update(current.copy(embedCaption = !current.embedCaption))
                            }
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
            }

        }
        val actionBar: @Composable () -> Unit = {
            // Kind, size and folder, with the link and incognito at the end: the set sheet's order.
            val incognito = rememberIncognitoSwitch { message -> feedback = Feedback(message) }
            val accent = MaterialTheme.colorScheme.primary
            // layout-safe: icons and a short size label, each segment sharing the width
            SheetButtonGroup(modifier = Modifier.padding(horizontal = BATCH_SIDE)) {
                Segment(
                    position = 0, count = 5, weight = 1f,
                    description = stringResource(R.string.batch_bar_download_type),
                    onClick = { dialog = PostDialog.KIND },
                    icon = rememberVectorPainter(filter.icon)
                )
                Segment(
                    position = 1, count = 5, weight = 1.6f,
                    description = stringResource(R.string.gallery_image_size),
                    onClick = { dialog = PostDialog.SIZE },
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
                    description = stringResource(R.string.sheet_link_options),
                    onClick = { dialog = PostDialog.LINK },
                    enabled = postUrl.isNotBlank(),
                    icon = rememberVectorPainter(Icons.Filled.Link)
                )
                Segment(
                    position = 4, count = 5, weight = 1f,
                    description = stringResource(if (incognito.on) R.string.nav_incognito_on else R.string.incognito_off),
                    onClick = incognito.toggle,
                    icon = painterResource(R.drawable.incognito),
                    fill = if (incognito.on) accent.copy(alpha = 0.22f) else null,
                    tint = if (incognito.on) accent else null
                )
            }

        }

        if (twoPane) {
            // The list down one side, as tall as the sheet; the heading, the settings and the
            // buttons down the other, with Download kept in view under them.
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    listBar()
                    Spacer(modifier = Modifier.height(8.dp))
                    fileList(Modifier.weight(1f))
                    Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        titleBlock(Modifier.padding(horizontal = BATCH_SIDE))
                        Spacer(modifier = Modifier.height(12.dp))
                        adjustSection()
                        Spacer(modifier = Modifier.height(12.dp))
                        actionBar()
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    downloadButton(
                        Modifier
                            .padding(horizontal = BATCH_SIDE)
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                    Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                // The button moves under the heading only where a word of it would otherwise break.
                SheetHeaderLayout(modifier = Modifier.padding(horizontal = BATCH_SIDE)) {
                    titleBlock(Modifier)
                    downloadButton(Modifier.height(44.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                listBar()
                Spacer(modifier = Modifier.height(8.dp))
                // A few rows on a phone, more on a tablet; while searching the list keeps its
                // full height so the sheet does not shrink and drop the field behind the keyboard.
                val listMaxHeight = if (configuration.smallestScreenWidthDp >= 600) {
                    maxOf((configuration.screenHeightDp * 0.45f).dp, 280.dp)
                } else {
                    280.dp
                }
                fileList(
                    Modifier
                        .weight(1f, fill = false)
                        .heightIn(min = if (searchOpen) listMaxHeight else 0.dp, max = listMaxHeight)
                )
                Spacer(modifier = Modifier.height(10.dp))
                adjustSection()
                Spacer(modifier = Modifier.height(4.dp))
                actionBar()
                Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = BATCH_SIDE)
        ) {
            FeedbackToast(shownFeedback)
        }
        }
    }

    pendingRemove?.let { item ->
        val keep = { pendingRemove = null }
        AlertDialog(
            onDismissRequest = keep,
            title = { Text(stringResource(R.string.gallery_remove_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.batch_remove_body, "#${item.index} · ${defaultTitle(item)}"),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                FlatChip(
                    label = stringResource(R.string.batch_card_remove),
                    selected = true,
                    onClick = {
                        pendingRemove = null
                        kept = kept - item
                        selected = selected - item.index
                        choices = choices - item.index
                        videoPicks[item.index]?.read?.cancel()
                        videoPicks = videoPicks - item.index
                    }
                )
            },
            dismissButton = { FlatChip(label = stringResource(R.string.options_text_input_cancel), onClick = keep) }
        )
    }

    opened?.let { item ->
        GalleryItemSheet(
            item = item,
            postUrl = postUrl,
            confirmAsApply = true,
            onDownload = { chosenOptions, title, author, _ ->
                choices = choices + (item.index to FileChoice(chosenOptions, title, author))
                opened = null
            },
            onDismiss = { opened = null }
        )
    }

    postVideos?.let { shared -> openedVideo?.let { item ->
        GalleryVideoSheet(
            post = shared.post,
            items = listOf(item),
            links = shared.links,
            onQueued = {},
            onMissed = shared.onMissed,
            onDismiss = { openedVideo = null },
            initial = videoPicks[item.index],
            onApply = { pick ->
                videoPicks[item.index]?.read?.takeIf { it !== pick.read }?.cancel()
                videoPicks = videoPicks + (item.index to pick)
                openedVideo = null
            }
        )
    } }

    when (dialog) {
        PostDialog.NONE -> Unit
        PostDialog.KIND -> ChoiceSheet(
            title = stringResource(R.string.batch_type_sheet_title),
            subtitle = stringResource(R.string.gallery_type_sheet_subtitle),
            onDismiss = { dialog = PostDialog.NONE }
        ) { close ->
            GalleryFilter.entries.forEach { kind ->
                val count = when (kind) {
                    GalleryFilter.ALL -> kept.size
                    GalleryFilter.IMAGES -> images
                    GalleryFilter.VIDEOS -> videos
                }
                if (kind == GalleryFilter.ALL || count > 0) {
                    ChoiceRow(
                        label = stringResource(kind.label, count),
                        icon = kind.icon,
                        selected = filter == kind,
                        onClick = {
                            close {
                                filter = kind
                                dialog = PostDialog.NONE
                            }
                        }
                    )
                }
            }
        }
        PostDialog.SIZE -> {
            val sample = kept.firstOrNull { !it.isVideo } ?: kept.firstOrNull()
            if (sample == null) {
                dialog = PostDialog.NONE
            } else {
                GalleryQualitySheet(
                    choices = sizeChoices(sample),
                    selected = current.imageSize,
                    onConfirm = {
                        update(current.copy(imageSize = it))
                        dialog = PostDialog.NONE
                    },
                    onDismiss = { dialog = PostDialog.NONE }
                )
            }
        }
        PostDialog.LINK -> LinkOptionsDialog(
            links = listOf(postUrl),
            onFeedback = { message -> feedback = Feedback(message) },
            onDismiss = { dialog = PostDialog.NONE }
        )
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

private val GalleryFilter.icon
    get() = when (this) {
        GalleryFilter.ALL -> Icons.Filled.Collections
        GalleryFilter.IMAGES -> Icons.Filled.Image
        GalleryFilter.VIDEOS -> Icons.Filled.Videocam
    }

private val GalleryFilter.label
    get() = when (this) {
        GalleryFilter.ALL -> R.string.gallery_filter_all
        GalleryFilter.IMAGES -> R.string.gallery_filter_images
        GalleryFilter.VIDEOS -> R.string.gallery_filter_videos
    }

/** Whether a file's title, author or number holds [query], ignoring case. */
private fun GalleryItem.matches(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        author.contains(query, ignoreCase = true) ||
        "#$index" == query.trim() || index.toString() == query.trim()
