package com.hazel.android.ui.screens.download.batch

import com.hazel.android.ui.components.rememberScrollShrink
import com.hazel.android.ui.components.scrollShrink
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadPlan
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.newpipe.NewPipeEngine
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.WORST_HEIGHT
import com.hazel.android.download.BatchAudioFormats
import com.hazel.android.download.formatFileSize
import com.hazel.android.download.languageLabel
import com.hazel.android.ui.screens.download.AudioLanguageSheet
import com.hazel.android.ui.screens.download.ChaptersDialog
import com.hazel.android.ui.screens.download.CookiesDialog
import com.hazel.android.ui.screens.download.rememberUseCookies
import com.hazel.android.ui.screens.download.IncognitoButton
import com.hazel.android.ui.screens.download.SheetLinkButton
import com.hazel.android.ui.screens.download.FEEDBACK_MS
import com.hazel.android.ui.screens.download.FeedbackToast
import com.hazel.android.ui.screens.download.FilenameTemplateDialog
import com.hazel.android.ui.screens.download.FormatSheet
import com.hazel.android.ui.screens.download.OptionChip
import com.hazel.android.ui.screens.download.SheetSection
import com.hazel.android.ui.screens.download.SponsorBlockDialog
import com.hazel.android.ui.screens.download.SubtitlesDialog
import com.hazel.android.ui.screens.download.ThumbnailDialog
import com.hazel.android.ui.screens.download.FormatSelectionSheet
import com.hazel.android.data.SaveDirs
import com.hazel.android.util.SdCard
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Settings for a whole set of links, whether they came from several pasted urls or from
 * one playlist.
 *
 * The sheet is a list, an Adjust section under it, and a row of buttons below that. Tapping
 * a link opens that link's own format list, so a set can be part 1080p and part audio and
 * still go out in one download. The row of buttons holds the three choices a set is most
 * often changed by (kind, quality ceiling, folder); the adjust-download options and the
 * container sit in the Adjust section, which opens and closes the way the single download
 * sheet's sections do, so the list keeps the height while it is closed.
 *
 * A search button beside the count opens a field in the same row that narrows the list to
 * the links whose title, channel or address match, for a playlist too long to scroll.
 *
 * Ticking is a separate mode, reached by holding a link or from the list menu, and it
 * narrows what the buttons act on. It never decides what gets downloaded: the download
 * always covers every link in the list, which is why a set collected on purpose does not
 * have to be ticked again before it will go.
 *
 * The choices themselves live in [BatchDownloadState], which is what keeps a change from
 * one row and a change from the buttons from treading on each other.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatchDownloadSheet(
    results: List<MediaInfo>,
    options: DownloadOptions,
    onOptionsChange: (DownloadOptions) -> Unit,
    /** Where audio and video are saved; the set shows the one for its current kind. */
    saveDirs: SaveDirs,
    /** Each is given the kind in question: true for video. */
    onOpenSaveDir: (isVideo: Boolean) -> Unit,
    onPickSaveDir: (isVideo: Boolean, card: SdCard?) -> Unit,
    onResetSaveDir: (isVideo: Boolean) -> Unit,
    onResolveFormats: (MediaInfo) -> Unit,
    /** Links whose formats are being read right now. */
    readingUrls: Set<String> = emptySet(),
    /** Reads these links' formats again, with the given reader or the setting's. */
    onRefreshFormats: (List<MediaInfo>, ListingSource?) -> Unit = { _, _ -> },
    onRemove: (MediaInfo) -> Unit,
    onDownload: (List<DownloadPlan>) -> Unit,
    onDismiss: () -> Unit
) {
    // Opened at full height: the list of links and the controls under it do not fit in a
    // half sheet, and a partly open sheet would hide the download action.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val state = rememberBatchDownloadState(results)
    // The set starts from the download settings, and follows them if they change.
    LaunchedEffect(
        options.videoQuality, options.preferredVideoCodec,
        options.preferredAudioLanguage, options.preferredAudioCodec
    ) {
        state.applyPreferences(options)
    }
    var openSheet by remember { mutableStateOf(BatchSheet.NONE) }
    var listMenuOpen by remember { mutableStateOf(false) }

    // The link whose own sheet is open, held by url so the row keeps up with the formats
    // arriving for it rather than showing whatever it had when it was tapped.
    var focusedUrl by remember { mutableStateOf<String?>(null) }
    val focused = results.firstOrNull { it.url == focusedUrl }

    val plans = state.plans
    val totalBytes = state.totalBytes

    // ── Search ──
    //
    // The list narrowed to what matches, worked out once per query rather than per row.
    // Closing the search clears it, so the whole set is back the moment the field goes.
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = remember(results, query) {
        val q = query.trim()
        if (q.isEmpty()) results else results.filter { it.matches(q) }
    }
    val closeSearch = {
        query = ""
        searchOpen = false
    }
    val focusManager = LocalFocusManager.current
    // Where the field and the sheet are on screen, read only when a touch lands, so knowing
    // them costs nothing while nothing is touched.
    val bounds = remember { SearchBounds() }

    // The Adjust section, closed when the sheet opens so the list has the height.
    var adjustOpen by rememberSaveable { mutableStateOf(false) }

    // The last message from the footer, shown over the top of the sheet.
    var feedback by remember { mutableStateOf<BatchFeedback?>(null) }
    var shownFeedback by remember { mutableStateOf("") }
    LaunchedEffect(feedback) {
        val current = feedback ?: return@LaunchedEffect
        shownFeedback = current.message
        delay(FEEDBACK_MS)
        feedback = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = batchSheetColor
    ) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { bounds.sheet = it }
                // A touch anywhere outside the search field ends the search: with nothing
                // typed it closes, and with a query it only puts the keyboard away so the
                // narrowed list can be used. Watched on the way down, before the touch
                // reaches whatever it lands on, so a tap on a row still opens the row.
                .pointerInput(searchOpen) {
                    if (!searchOpen) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val sheet = bounds.sheet ?: return@awaitEachGesture
                        val field = bounds.field?.takeIf { it.isAttached } ?: return@awaitEachGesture
                        val atRoot = sheet.localToRoot(down.position)
                        if (!field.boundsInRoot().contains(atRoot)) {
                            if (query.isBlank()) closeSearch() else focusManager.clearFocus()
                        }
                    }
                }
        ) {
        KeyboardOverSheet()
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SIDE),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

                Surface(
                    onClick = { onDownload(plans) },
                    enabled = plans.isNotEmpty(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (plans.isNotEmpty()) 0.15f else 0.06f
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Download, null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.batch_download_action),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── What the list holds, and the controls that act on the list itself ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = SIDE, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchOpen) {
                    LinkSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = closeSearch,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { bounds.field = it }
                    )
                } else {
                    Text(
                        if (state.selectionMode) {
                            pluralStringResource(
                                R.plurals.batch_selected,
                                state.selected.size,
                                state.selected.size
                            )
                        } else {
                            pluralStringResource(R.plurals.batch_links, results.size, results.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (totalBytes > 0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        // The whole set added up, not one link's share of it. A link whose
                        // formats have not been read yet reports no size, so while any are
                        // outstanding the figure is marked as a floor rather than the total.
                        Text(
                            "~ ${formatFileSize(totalBytes)}" +
                                    if (state.allSizesKnown) "" else " +",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { searchOpen = true }) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.batch_search_open),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (state.selectionMode) state.endSelection() else state.startSelection()
                    }
                ) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = stringResource(
                            if (state.selectionMode) R.string.batch_stop_selecting
                            else R.string.batch_start_selecting
                        ),
                        tint = if (state.selectionMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { listMenuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.batch_list_options)
                        )
                    }
                    DropdownMenu(
                        expanded = listMenuOpen,
                        onDismissRequest = { listMenuOpen = false }
                    ) {
                        if (!state.selectionMode) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_select_links)) },
                                onClick = {
                                    listMenuOpen = false
                                    state.startSelection()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_select_all)) },
                                onClick = {
                                    listMenuOpen = false
                                    state.selectAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_invert)) },
                                onClick = {
                                    listMenuOpen = false
                                    state.invertSelection()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_remove_selected)) },
                                enabled = state.selected.isNotEmpty(),
                                onClick = {
                                    listMenuOpen = false
                                    results.filter { it.url in state.selected }.forEach(onRemove)
                                    state.endSelection()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.batch_menu_done)) },
                                onClick = {
                                    listMenuOpen = false
                                    state.endSelection()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (searchOpen && shown.isEmpty()) {
                Text(
                    stringResource(R.string.batch_search_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SIDE + 4.dp, vertical = 16.dp)
                )
            }

            val linkListState = rememberLazyListState()
            val shrink = rememberScrollShrink()
            // A new query starts at the top of what it found.
            LaunchedEffect(query) { if (query.isNotEmpty()) linkListState.scrollToItem(0) }
            LazyColumn(
                state = linkListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 280.dp)
                    .nestedScroll(shrink),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = SIDE
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { info -> info.url }) { info ->
                    val format = state.formatOf(info)
                    Box(modifier = Modifier.scrollShrink(shrink)) {
                    BatchDownloadCard(
                        info = info,
                        formatLabel = format?.shortLabel.orEmpty(),
                        sizeLabel = format?.sizeLabel.orEmpty(),
                        isVideo = state.isVideo(info),
                        isAdjusted = state.isAdjusted(info),
                        selectionMode = state.selectionMode,
                        checked = info.url in state.selected,
                        onClick = {
                            if (state.selectionMode) {
                                state.toggle(info)
                            } else {
                                // A link that came from a listing has no formats yet, so
                                // the read is started as the sheet opens and the list
                                // fills in under it.
                                onResolveFormats(info)
                                focusedUrl = info.url
                            }
                        },
                        onLongClick = {
                            if (state.selectionMode) state.toggle(info)
                            else state.startSelection(info)
                        },
                        onTypeClick = {
                            focusedUrl = info.url
                            openSheet = BatchSheet.ITEM_TYPE
                        },
                        onRemove = { onRemove(info) }
                    )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Adjust: the options and the container, for the whole set ──
            //
            // Each opens the dialog the single download sheet uses, so there is one place
            // where each of them is explained. Subtitles are left out of an audio download,
            // which has nothing to attach them to, and the bitrate only applies to one.
            val isVideo = state.videoTab
            val thumbnailLabel = stringResource(R.string.batch_bar_thumbnail)
            val chaptersLabel = stringResource(R.string.batch_bar_chapters)
            val subtitlesLabel = stringResource(R.string.batch_bar_subtitles)
            val bitrateLabel = stringResource(R.string.properties_bitrate)
            val sponsorBlockLabel = stringResource(R.string.batch_bar_sponsorblock)
            val filenameLabel = stringResource(R.string.batch_bar_filename_template)
            val showAudioLanguage = state.audioLanguages.size > 1
            val audioLanguageLabel = state.audioLanguage?.let(::languageLabel)
                ?: stringResource(R.string.direct_share_audio_language_default)

            val thumbnailOn = options.thumbnailBadge > 0
            val chaptersOn = options.chapterBadge(isVideo) > 0
            val subtitlesOn = isVideo && options.subtitleBadge > 0
            val bitrateOn = !isVideo && options.audioQuality.isNotBlank()
            val sponsorBlockOn = options.sponsorBlockBadge > 0
            val filenameOn = options.filenameTemplate.isNotBlank()
            val languageOn = showAudioLanguage && state.audioLanguage != null
            val cookiesLabel = stringResource(R.string.cookies_title)
            val cookiesOn = rememberUseCookies()

            val container = if (isVideo) options.videoContainer else options.audioContainer
            val containerLabel = container.ifBlank { "Default" }

            Box(modifier = Modifier.padding(horizontal = SIDE)) {
                SheetSection(
                    icon = Icons.Outlined.Tune,
                    title = if (isVideo) stringResource(R.string.format_sheet_adjust_video)
                    else stringResource(R.string.format_sheet_adjust_audio),
                    summary = listOfNotNull(
                        thumbnailLabel.takeIf { thumbnailOn },
                        chaptersLabel.takeIf { chaptersOn },
                        subtitlesLabel.takeIf { subtitlesOn },
                        bitrateLabel.takeIf { bitrateOn },
                        audioLanguageLabel.takeIf { languageOn },
                        sponsorBlockLabel.takeIf { sponsorBlockOn },
                        filenameLabel.takeIf { filenameOn },
                        cookiesLabel.takeIf { cookiesOn }
                    ).plus(containerLabel).joinToString(", "),
                    expanded = adjustOpen,
                    onToggle = { adjustOpen = !adjustOpen },
                    color = batchRaisedColor
                ) {
                    // Wraps rather than scrolling sideways: a chip past the edge is a
                    // setting nobody can see. The chips are a little trimmed so more of
                    // them share a line, and a long name is cut short rather than wrapped.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // The container is a chip like the rest, naming what it is set to,
                        // and opens the same container sheet as before.
                        OptionChip(
                            label = "${stringResource(R.string.batch_bar_container)} · $containerLabel",
                            icon = Icons.Filled.Description,
                            active = container.isNotBlank(),
                            compact = true,
                            onClick = { openSheet = BatchSheet.CONTAINER }
                        )
                        OptionChip(
                            label = thumbnailLabel,
                            compact = true,
                            icon = Icons.Filled.Image,
                            active = thumbnailOn,
                            onClick = { openSheet = BatchSheet.THUMBNAIL }
                        )
                        OptionChip(
                            label = chaptersLabel,
                            compact = true,
                            icon = Icons.Filled.Book,
                            active = chaptersOn,
                            onClick = { openSheet = BatchSheet.CHAPTERS }
                        )
                        if (isVideo) {
                            OptionChip(
                                label = subtitlesLabel,
                                compact = true,
                                icon = Icons.Filled.ClosedCaption,
                                active = subtitlesOn,
                                onClick = { openSheet = BatchSheet.SUBTITLES }
                            )
                        } else {
                            OptionChip(
                                label = bitrateLabel,
                                compact = true,
                                icon = Icons.Filled.HighQuality,
                                active = bitrateOn,
                                onClick = { openSheet = BatchSheet.BITRATE }
                            )
                        }
                        // Only the sets holding a source with several soundtracks have this.
                        if (showAudioLanguage) {
                            OptionChip(
                                label = audioLanguageLabel,
                                compact = true,
                                icon = Icons.Filled.Translate,
                                active = languageOn,
                                onClick = { openSheet = BatchSheet.AUDIO_LANGUAGE }
                            )
                        }
                        OptionChip(
                            label = sponsorBlockLabel,
                            compact = true,
                            icon = Icons.Filled.Paid,
                            active = sponsorBlockOn,
                            onClick = { openSheet = BatchSheet.SPONSORBLOCK }
                        )
                        OptionChip(
                            label = filenameLabel,
                            compact = true,
                            icon = Icons.Filled.Edit,
                            active = filenameOn,
                            onClick = { openSheet = BatchSheet.FILENAME }
                        )
                        OptionChip(
                            label = cookiesLabel,
                            compact = true,
                            icon = Icons.Filled.Cookie,
                            active = cookiesOn,
                            onClick = { openSheet = BatchSheet.COOKIES }
                        )
                    }
                }
            }

            val audioChoice = state.audioChoice
            val hqLabel = when {
                state.videoTab && state.maxHeight == WORST_HEIGHT -> stringResource(R.string.batch_bar_hq_value, "MIN")
                state.videoTab && state.maxHeight <= 0 -> stringResource(R.string.batch_bar_hq_auto)
                state.videoTab -> stringResource(R.string.batch_bar_hq_value, "${state.maxHeight}p")
                audioChoice == null -> stringResource(R.string.batch_bar_hq_best)
                else -> stringResource(R.string.batch_bar_hq_value, audioChoice.badgeText)
            }

            val qualityLabel = when {
                state.videoTab -> stringResource(qualityLabelFor(state.maxHeight))
                audioChoice == null -> stringResource(R.string.audio_quality_best)
                else -> audioChoice.label
            }

            Spacer(modifier = Modifier.height(4.dp))

            BatchActionBar(
                isVideo = state.videoTab,
                qualityLabel = qualityLabel,
                hqLabel = hqLabel,
                onDownloadType = { openSheet = BatchSheet.TYPE },
                onQuality = {
                    // The ladder answers at once; reading every link first is left to the
                    // sheet's update button, for the user who wants their exact formats.
                    openSheet = if (state.videoTab) BatchSheet.QUALITY else BatchSheet.AUDIO_FORMAT
                },
                onSaveDir = { openSheet = BatchSheet.SAVE_DIR },
                modifier = Modifier.padding(horizontal = SIDE),
                // The link and incognito sit at the end of the same row rather than on a
                // line of their own. The link button copies the set's addresses, or offers
                // to open the one link a set of one holds; incognito applies to the run.
                trailing = {
                    val say: (String) -> Unit = { message -> feedback = BatchFeedback(message) }
                    SheetLinkButton(links = remember(results) { results.map { it.url } }, onFeedback = say)
                    Spacer(modifier = Modifier.width(8.dp))
                    IncognitoButton(onFeedback = say)
                }
            )

            Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
        }

        // What the footer's buttons did, shown over the top of the sheet, where it is in
        // view however far down the footer sits.
        androidx.compose.animation.AnimatedVisibility(
            visible = feedback != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = SIDE)
        ) {
            FeedbackToast(shownFeedback)
        }
        }
    }

    // ── The link's own sheet, which is what tapping a row opens ──
    //
    // It is the single download sheet, not a second version of it: the same tabs, fields
    // and quality row, with its action handing the choice back to the set instead of
    // starting a download of its own.
    if (focused != null && openSheet == BatchSheet.NONE) {
        FormatSheet(
            info = focused,
            options = options,
            onOptionsChange = onOptionsChange,
            saveDirs = saveDirs,
            isLoadingFormats = focused.url in readingUrls,
            onRefreshFormats = { source -> onRefreshFormats(listOf(focused), source) },
            initialFormat = state.formatOf(focused),
            initialAudioLanguage = state.languageOf(focused),
            confirmAsApply = true,
            onOpenSaveDir = onOpenSaveDir,
            onPickSaveDir = onPickSaveDir,
            onResetSaveDir = onResetSaveDir,
            onDownload = { format, audioLanguage, title, author, _ ->
                state.setChoice(focused, format, title, author, audioLanguage)
                focusedUrl = null
            },
            onDismiss = { focusedUrl = null }
        )
    }

    when (openSheet) {
        BatchSheet.NONE -> Unit

        BatchSheet.TYPE -> BatchDownloadTypeSheet(
            isVideo = state.videoTab,
            onSelect = {
                state.setDownloadType(it)
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        // The same choice as above, aimed at one link instead of the set.
        BatchSheet.ITEM_TYPE -> {
            val info = focused
            if (info == null) {
                openSheet = BatchSheet.NONE
            } else {
                BatchDownloadTypeSheet(
                    isVideo = state.isVideo(info),
                    onSelect = { isVideo ->
                        state.defaultFor(info, isVideo)?.let { state.setFormat(info, it) }
                        openSheet = BatchSheet.NONE
                        focusedUrl = null
                    },
                    onDismiss = {
                        openSheet = BatchSheet.NONE
                        focusedUrl = null
                    }
                )
            }
        }

        // Every soundtrack the set offers, applied to whatever the buttons are aimed at.
        BatchSheet.AUDIO_LANGUAGE -> AudioLanguageSheet(
            languages = state.audioLanguages,
            selected = state.audioLanguage,
            onPick = {
                state.applyAudioLanguage(it)
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.QUALITY -> BatchQualitySheet(
            maxHeight = state.maxHeight,
            onSelect = {
                state.setQualityCeiling(it)
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        // The same format list a single link opens, holding what the targeted links share.
        BatchSheet.AUDIO_FORMAT -> {
            val targets = state.targets
            val bestLabel = stringResource(R.string.audio_quality_best)
            val worstLabel = stringResource(R.string.batch_quality_worst)
            val choices = remember(targets, bestLabel, worstLabel) {
                BatchAudioFormats.choices(targets, bestLabel, worstLabel)
            }
            FormatSelectionSheet(
                info = remember(choices) { choicesAsInfo(choices) },
                selected = currentAudioChoice(state, choices),
                onConfirm = { choice ->
                    state.chooseAudio(
                        choice.takeUnless { it.formatId == BatchAudioFormats.BEST.formatId }
                    )
                    openSheet = BatchSheet.NONE
                },
                onDismiss = { openSheet = BatchSheet.NONE },
                audioFirst = true,
                isLoadingFormats = targets.any { it.url in readingUrls },
                onRefresh = { source -> onRefreshFormats(targets, source) },
                canChooseSource = remember(targets) {
                    targets.isNotEmpty() && targets.all { NewPipeEngine.handlesStream(it.url) }
                }
            )
        }

        BatchSheet.BITRATE -> BatchBitrateSheet(
            currentQuality = options.audioQuality,
            onSelect = { choice ->
                onOptionsChange(options.copy(audioQuality = choice))
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.CONTAINER -> BatchContainerSheet(
            isVideo = state.videoTab,
            current = if (state.videoTab) options.videoContainer else options.audioContainer,
            onSelect = { choice ->
                onOptionsChange(
                    if (state.videoTab) options.copy(videoContainer = choice)
                    else options.copy(audioContainer = choice)
                )
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        // Sign-ins are kept per site; a set from one playlist is one site, so its first link
        // stands for where to sign in.
        BatchSheet.COOKIES -> CookiesDialog(
            url = results.firstOrNull()?.url.orEmpty(),
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.SAVE_DIR -> BatchSaveDirSheet(
            isVideo = state.videoTab,
            saveDir = saveDirs.of(state.videoTab),
            saveDirLabel = saveDirs.labelOf(state.videoTab),
            onOpen = {
                openSheet = BatchSheet.NONE
                onOpenSaveDir(state.videoTab)
            },
            onPick = { card ->
                openSheet = BatchSheet.NONE
                onPickSaveDir(state.videoTab, card)
            },
            onReset = {
                openSheet = BatchSheet.NONE
                onResetSaveDir(state.videoTab)
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        // The remaining settings each open the dialog the single download sheet uses, so
        // there is one place where each of them is explained.
        BatchSheet.THUMBNAIL -> ThumbnailDialog(
            options = options,
            onChange = onOptionsChange,
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.CHAPTERS -> ChaptersDialog(
            options = options,
            isVideo = state.videoTab,
            onChange = onOptionsChange,
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.SUBTITLES -> SubtitlesDialog(
            options = options,
            onChange = onOptionsChange,
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.SPONSORBLOCK -> SponsorBlockDialog(
            options = options,
            onConfirm = {
                onOptionsChange(it)
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )

        BatchSheet.FILENAME -> FilenameTemplateDialog(
            template = options.filenameTemplate,
            onConfirm = {
                onOptionsChange(options.copy(filenameTemplate = it))
                openSheet = BatchSheet.NONE
            },
            onDismiss = { openSheet = BatchSheet.NONE }
        )
    }
}

/**
 * The search field that takes the place of the count while searching: what is typed, and a
 * close button that clears it and puts the count back. It takes the keyboard as it opens.
 */
@Composable
private fun LinkSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(21.dp),
        color = batchButtonColor
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.batch_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.batch_search_close),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Lets the keyboard open over the sheet rather than lifting it.
 *
 * The bottom sheet pads its whole window by the keyboard's height, which pushed the list,
 * the Adjust section and the buttons up into a strip above the keys while searching. The
 * search field is at the top, where the keyboard never reaches, so the keyboard's height is
 * taken out of what the sheet's own view is told, and the keyboard simply covers the lower
 * part of the sheet until it is put away. Only this sheet's window is affected, and the
 * listeners are removed when the sheet goes.
 */
@Composable
private fun KeyboardOverSheet() {
    val view = LocalView.current
    DisposableEffect(view) {
        val host = view.parent as? View ?: return@DisposableEffect onDispose { }
        ViewCompat.setOnApplyWindowInsetsListener(host) { v, insets ->
            val withoutKeyboard = WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
                .build()
            ViewCompat.onApplyWindowInsets(v, withoutKeyboard)
        }
        // The keyboard's own slide is not passed on either, so nothing moves while it opens.
        ViewCompat.setWindowInsetsAnimationCallback(
            host,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat = insets
            }
        )
        ViewCompat.requestApplyInsets(host)
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(host, null)
            ViewCompat.setWindowInsetsAnimationCallback(host, null)
        }
    }
}

/** Whether a link's title, channel or address holds [query], ignoring case. */
private fun MediaInfo.matches(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        uploader.contains(query, ignoreCase = true) ||
        url.contains(query, ignoreCase = true)

/** Where the sheet and its search field were last laid out, read when a touch lands. */
private class SearchBounds {
    var sheet: LayoutCoordinates? = null
    var field: LayoutCoordinates? = null
}

/** How far the sheet's content sits from its sides: close to the edge, so the list gets the width. */
private val SIDE = 12.dp

/** One message from the footer; a new one for every tap, so a repeat starts its time over. */
private class BatchFeedback(val message: String)

/** Which sheet the buttons or a row's type button has opened, if any. */
private enum class BatchSheet {
    NONE, TYPE, ITEM_TYPE, QUALITY, AUDIO_FORMAT, BITRATE, CONTAINER, SAVE_DIR, AUDIO_LANGUAGE,
    THUMBNAIL, CHAPTERS, SUBTITLES, SPONSORBLOCK, FILENAME, COOKIES
}

/** The row among [choices] that stands for the set's current audio choice. */
private fun currentAudioChoice(
    state: BatchDownloadState,
    choices: List<MediaFormat>
): MediaFormat? {
    val id = state.audioChoice?.formatId ?: BatchAudioFormats.BEST.formatId
    return choices.firstOrNull { it.formatId == id }
}

/** The shared formats in the shape the format list reads. */
private fun choicesAsInfo(choices: List<MediaFormat>) = MediaInfo(
    url = "",
    title = "",
    uploader = "",
    thumbnail = null,
    durationSeconds = 0,
    videoFormats = emptyList(),
    audioFormats = choices
)

/** Short text for a format on the quality button, such as "OPUS 128K". */
private val MediaFormat.badgeText: String
    get() {
        if (isGeneric) return label.uppercase()
        val codec = codecLabel.ifBlank { displayContainer }
        val kbps = bitrateKbps.roundToInt()
        return if (kbps > 0) "$codec ${kbps}K" else codec
    }
