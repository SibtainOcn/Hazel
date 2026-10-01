package com.hazel.android.ui.screens.download.batch

import com.hazel.android.ui.components.rememberScrollShrink
import com.hazel.android.ui.components.scrollShrink
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.hazel.android.ui.screens.download.DownloadSheetFooter
import com.hazel.android.ui.screens.download.FilenameTemplateDialog
import com.hazel.android.ui.screens.download.FormatSheet
import com.hazel.android.ui.screens.download.SponsorBlockDialog
import com.hazel.android.ui.screens.download.SubtitlesDialog
import com.hazel.android.ui.screens.download.ThumbnailDialog
import com.hazel.android.ui.screens.download.FormatSelectionSheet
import com.hazel.android.data.SaveDirs
import com.hazel.android.util.SdCard
import kotlin.math.roundToInt

/**
 * Settings for a whole set of links, whether they came from several pasted urls or from
 * one playlist.
 *
 * The sheet is a list with a bar under it. Tapping a link opens that link's own format
 * list, so a set can be part 1080p and part audio and still go out in one download; the bar
 * along the bottom is where a change is made to all of them at once, each of its buttons
 * opening a sheet of its own so the list keeps the height of the screen.
 *
 * Ticking is a separate mode, reached by holding a link or from the list menu, and it
 * narrows what the bar acts on. It never decides what gets downloaded: the download always
 * covers every link in the list, which is why a set collected on purpose does not have to
 * be ticked again before it will go.
 *
 * The choices themselves live in [BatchDownloadState], which is what keeps a change from
 * one row and a change from the bar from treading on each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    // Opened at full height: the list of links and the bar under it do not fit in a half
    // sheet, and a partly open sheet would hide the download action.
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = batchSheetColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    fontWeight = FontWeight.Bold
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

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

            val linkListState = rememberLazyListState()
            val shrink = rememberScrollShrink()
            LazyColumn(
                state = linkListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 280.dp)
                    .nestedScroll(shrink),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { info -> info.url }) { info ->
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

            Spacer(modifier = Modifier.height(12.dp))

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

            BatchActionBar(
                isVideo = state.videoTab,
                qualityLabel = qualityLabel,
                hqLabel = hqLabel,
                containerLabel = containerLabelFor(options, state.videoTab),
                options = options,
                onDownloadType = { openSheet = BatchSheet.TYPE },
                onQuality = {
                    // The ladder answers at once; reading every link first is left to the
                    // sheet's update button, for the user who wants their exact formats.
                    openSheet = if (state.videoTab) BatchSheet.QUALITY else BatchSheet.AUDIO_FORMAT
                },
                onSaveDir = { openSheet = BatchSheet.SAVE_DIR },
                onContainer = { openSheet = BatchSheet.CONTAINER },
                onThumbnail = { openSheet = BatchSheet.THUMBNAIL },
                onChapters = { openSheet = BatchSheet.CHAPTERS },
                onSubtitles = { openSheet = BatchSheet.SUBTITLES },
                onSponsorBlock = { openSheet = BatchSheet.SPONSORBLOCK },
                onFilename = { openSheet = BatchSheet.FILENAME },
                showAudioLanguage = state.audioLanguages.size > 1,
                audioLanguageLabel = state.audioLanguage?.let(::languageLabel)
                    ?: stringResource(R.string.direct_share_audio_language_default),
                onAudioLanguage = { openSheet = BatchSheet.AUDIO_LANGUAGE },
                bitrateSet = options.audioQuality.isNotBlank(),
                onBitrate = { openSheet = BatchSheet.BITRATE }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // A set has no single address, so the one link it holds is named and a larger
            // set is counted. Incognito applies to the whole run either way.
            DownloadSheetFooter(
                label = results.singleOrNull()?.url
                    ?: pluralStringResource(R.plurals.batch_links, results.size, results.size),
                copyText = results.singleOrNull()?.url.orEmpty(),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
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

        // Every soundtrack the set offers, applied to whatever the action bar is aimed at.
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

/** Which sheet the action bar or a row's type button has opened, if any. */
private enum class BatchSheet {
    NONE, TYPE, ITEM_TYPE, QUALITY, AUDIO_FORMAT, BITRATE, CONTAINER, SAVE_DIR, AUDIO_LANGUAGE,
    THUMBNAIL, CHAPTERS, SUBTITLES, SPONSORBLOCK, FILENAME
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

/** The action bar's container label, which is the extension it will write. */
private fun containerLabelFor(options: DownloadOptions, isVideo: Boolean): String {
    val container = if (isVideo) options.videoContainer else options.audioContainer
    return if (container.isBlank()) ".EXT" else ".${container.uppercase()}"
}
