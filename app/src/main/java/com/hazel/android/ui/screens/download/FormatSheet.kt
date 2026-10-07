package com.hazel.android.ui.screens.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.hazel.android.ui.screens.download.batch.BatchContainerSheet
import kotlinx.coroutines.delay
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.hazel.android.data.SaveDirs
import com.hazel.android.util.SdCard
import com.hazel.android.ui.components.FlatChip
import com.hazel.android.ui.components.KeyboardOverSheet
import com.hazel.android.ui.components.keptAboveKeyboard
import com.hazel.android.ui.components.liftedOverKeyboard
import com.hazel.android.ui.components.rememberSheetKeyboard
import com.hazel.android.ui.components.ShimmerLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.download.OneOffOptions
import com.hazel.android.download.readableTitle
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Cookie
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.GenericFormats
import com.hazel.android.download.MediaFormat
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.extractor.newpipe.NewPipeEngine
import com.hazel.android.download.languageLabel
import com.hazel.android.ui.components.keepFlingInSheet

/**
 * Everything you can adjust before a download starts.
 *
 * The sheet opens part way up the screen and can be dragged the rest of the way, so the
 * media stays visible behind it while the first few controls are already in reach.
 *
 * Below the header and the Audio / Video tabs, the controls are grouped into sections that
 * open and close: the details that name the file, the quality, where it is saved, and the
 * adjustments. Each says what it is set to while it is closed, so the whole download can be
 * read without opening any of them, and any number can be open at once. Quality opens with
 * the sheet, since it is the one most people came to check.
 *
 * Only the currently chosen quality is shown here, as a single row. The full format list
 * lives in [FormatSelectionSheet], which gets a sheet of its own rather than growing this
 * one, because a source with a hundred formats would bury every control underneath them.
 *
 * The set-of-links sheet opens this same sheet for one of its links, so that adjusting one
 * link of a batch and adjusting a single download are the same screen with the same
 * controls. [initialFormat] is what that link is currently set to, and [confirmAsApply]
 * turns the download action into one that hands the choice back instead, since there the
 * download does not start until the whole set is sent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormatSheet(
    info: MediaInfo,
    options: DownloadOptions,
    onOptionsChange: (DownloadOptions) -> Unit,
    /** Where audio and video are saved; the sheet shows and changes the one for its tab. */
    saveDirs: SaveDirs,
    isLoadingFormats: Boolean = false,
    /** The link itself is still being read, so its details are not in yet. */
    isReadingLink: Boolean = false,
    /** Reads this link's formats again, with the given reader or the setting's. */
    onRefreshFormats: ((source: ListingSource?, fresh: Boolean) -> Unit)? = null,
    initialFormat: MediaFormat? = null,
    /** The soundtrack this link is already set to, for a link being adjusted again. */
    initialAudioLanguage: String? = null,
    confirmAsApply: Boolean = false,
    /**
     * Set when this media is already downloaded and still on the device, in which case the
     * header offers to play it beside the action that would fetch it again.
     */
    onPlay: (() -> Unit)? = null,
    /** Each is given the kind the sheet is on: true for video. */
    onOpenSaveDir: (isVideo: Boolean) -> Unit,
    /** Given the card to save to, or null to pick any folder. */
    onPickSaveDir: (isVideo: Boolean, card: SdCard?) -> Unit,
    onResetSaveDir: (isVideo: Boolean) -> Unit,
    onDownload: (
        format: MediaFormat,
        audioLanguage: String?,
        title: String,
        author: String,
        /** Set for this download alone: a part to keep, how to take a live stream. */
        oneOff: OneOffOptions
    ) -> Unit,
    onDismiss: () -> Unit
) {
    // Opened at full height. The sheet's own content is a full screen of format rows and
    // fields, so a half-height first stop showed only the header and made an extra drag a
    // condition of using it. The set-of-links sheet already opens this way.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Open on the tab the settings name, if the source has formats for it, or else on the
    // one it has. A format the sheet was opened with decides it outright.
    var videoTab by remember(info.url, options.sheetOpensOnAudio) {
        mutableStateOf(
            initialFormat?.hasVideo
                ?: (info.videoFormats.isNotEmpty() && !(options.sheetOpensOnAudio && info.audioFormats.isNotEmpty()))
        )
    }

    // What each tab is set to, held apart rather than as one selection. A single slot
    // meant the audio tab showed whatever the video tab had chosen, so an audio download
    // was offered at a video resolution.
    //
    // Both are keyed on whether the formats have arrived, because a card that came from a
    // listing opens this sheet before they have. Without that the sheet would keep the
    // stand-in it was opened with and never move to the real best.
    //
    // With nothing chosen yet, each starts from the preferences in the download settings:
    // the quality ceiling and codec for video, the language and codec for audio. They are
    // keyed on as well, because the saved settings arrive a moment after the sheet opens.
    val preferredLanguage = remember(info.url, info.hasResolvedFormats, options.preferredAudioLanguage) {
        info.languageMatching(options.preferredAudioLanguage)
    }
    // What was picked in the format list for this link, kept apart from the defaults. A link
    // opened before it was read offers its ladder, and a step picked from it then has to
    // survive the formats arriving: it is carried onto what the link turned out to offer,
    // and only without one does the sheet fall back to the setting's default.
    var chosenVideo by remember(info.url) { mutableStateOf<MediaFormat?>(null) }
    var chosenAudio by remember(info.url) { mutableStateOf<MediaFormat?>(null) }
    val carry: (MediaFormat) -> MediaFormat = { choice ->
        if (info.hasResolvedFormats) GenericFormats.applyTo(info, choice, preferredLanguage) ?: choice
        else choice
    }

    var pickedVideo by remember(
        info.url, info.hasResolvedFormats, options.videoQuality, options.preferredVideoCodec
    ) {
        mutableStateOf(
            chosenVideo?.let(carry)
                ?: initialFormat?.takeIf { !it.isGeneric && it.hasVideo }
                ?: info.autoPick(
                    true,
                    initialFormat?.height ?: options.videoQuality,
                    videoCodec = options.videoCodecPreference
                )
                ?: info.bestVideo
        )
    }
    var pickedAudio by remember(
        info.url, info.hasResolvedFormats, preferredLanguage, options.preferredAudioCodec
    ) {
        mutableStateOf(
            chosenAudio?.let(carry)
                ?: initialFormat?.takeIf { !it.isGeneric && !it.hasVideo }
                ?: info.bestAudioFor(preferredLanguage, options.audioCodecPreference)
                ?: info.bestAudio
        )
    }

    // The best concrete format is preselected on each tab, so the row shows what will
    // actually be downloaded rather than a placeholder.
    val selected = if (videoTab) pickedVideo else pickedAudio

    // Which soundtrack the download takes, for the few sources that publish several. Null
    // means the one the source itself leads with, which is what almost every link gets.
    var audioLanguage by remember(info.url, info.hasResolvedFormats, preferredLanguage) {
        mutableStateOf(initialAudioLanguage ?: preferredLanguage)
    }

    // Title and author are editable: they name the saved file and, where the value is
    // unambiguous, the metadata written into it.
    // A whole post handed over as the title is trimmed to what reads as one first.
    var title by remember(info.url) { mutableStateOf(readableTitle(info.title)) }
    var author by remember(info.url) { mutableStateOf(info.uploader) }
    // A sheet opened on a shared link before it was read starts with nothing to show, and
    // takes the title and author as they arrive. Anything typed in the meantime is kept.
    LaunchedEffect(info.title) { if (title.isBlank()) title = readableTitle(info.title) }
    LaunchedEffect(info.uploader) { if (author.isBlank()) author = info.uploader }

    var openDialog by remember { mutableStateOf(SheetDialog.NONE) }
    // A cut and the live options belong to this download, so they start empty for every
    // link and are never written to the saved settings.
    var oneOff by remember(info.url) { mutableStateOf(OneOffOptions()) }
    var formatSheetVisible by remember { mutableStateOf(false) }
    var languageSheetVisible by remember { mutableStateOf(false) }

    // Which sections are open, oldest first. Opening one leaves the others as they were, up
    // to [MAX_OPEN_SECTIONS]: past that the one opened longest ago closes, so the sheet never
    // grows past the screen and the section just opened is always the one in view. Kept as
    // one string so it survives a rotation or the process being recreated behind the sheet.
    // Only Quality starts open: a closed section composes nothing but its header, so the
    // sheet comes up with as little to lay out as possible, which is what keeps it quick
    // when it opens straight over another app from a shared link.
    var openSections by rememberSaveable(info.url) { mutableStateOf(SECTION_QUALITY) }
    val openList = openSections.split(',').filter { it.isNotBlank() }
    fun isOpen(section: String) = section in openList
    // A field being typed in is let go before its section closes, whether closed by hand or
    // by another opening past the limit. Taken away while it still held the keyboard, it
    // could be left unable to take it again until the sheet was reopened.
    val focusManager = LocalFocusManager.current
    fun toggle(section: String) {
        val next = if (section in openList) openList - section
        else (openList + section).takeLast(MAX_OPEN_SECTIONS)
        if (SECTION_DETAILS in openList && SECTION_DETAILS !in next) focusManager.clearFocus()
        openSections = next.joinToString(",")
    }
    val detailsOpen = isOpen(SECTION_DETAILS)
    val qualityOpen = isOpen(SECTION_QUALITY)
    val saveOpen = isOpen(SECTION_SAVE)
    val adjustOpen = isOpen(SECTION_ADJUST)

    val container = if (videoTab) options.videoContainer else options.audioContainer

    // Whether the quality row is still standing in for formats not read yet, and whether it
    // ever was in this sheet, so its badges spring in when they arrive and not on every open.
    val formatsPending = isReadingLink || (isLoadingFormats && !info.hasResolvedFormats)
    var waitedForFormats by remember(info.url) { mutableStateOf(false) }
    LaunchedEffect(formatsPending) { if (formatsPending) waitedForFormats = true }

    // The last message from the footer. A new object for every tap, so the same message
    // twice in a row starts its time over; the text is kept apart so it stays on screen
    // while the message fades out.
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var shownFeedback by remember { mutableStateOf("") }
    LaunchedEffect(feedback) {
        val current = feedback ?: return@LaunchedEffect
        shownFeedback = current.message
        delay(FEEDBACK_MS)
        feedback = null
    }

    // One step up from the section, so a field reads as a field inside it.
    val fieldColor = MaterialTheme.colorScheme.surfaceContainerHighest

    // Editing the title or author opens the keyboard over the sheet rather than lifting it,
    // unless the keyboard would cover the field; then the sheet rises by that much only.
    val keyboard = rememberSheetKeyboard()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.liftedOverKeyboard(keyboard),
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
      KeyboardOverSheet(keyboard)
      Box {
        Column(
            modifier = Modifier
                .keepFlingInSheet()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            // ── Header: title block on the left, download action on the right ──
            // Play stacks above the download action, or both move under the heading, only
            // where the heading would otherwise have a word broken to make room for them.
            SheetHeaderLayout {
                Column {
                    if (isReadingLink) {
                        // Still reading: the heading says so, with light running through
                        // the word, and the line under it stays so nothing moves when the
                        // read lands.
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
                    } else {
                        Text(
                            stringResource(R.string.format_sheet_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.format_sheet_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                // Left of the download action, and quieter than it. Playing what is already
                // there is the smaller of the two things to do here, and it should not be
                // possible to take it by aiming for the other.
                if (onPlay != null) {
                    Surface(
                        onClick = onPlay,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.format_sheet_play),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // The one filled control on the sheet, in the accent, so the thing the
                // sheet is for is never mistaken for one of the settings around it.
                Surface(
                    onClick = {
                        selected?.let {
                            // Live options only reach a download that can use them.
                            val sent = if (info.isLive || info.isUpcoming) oneOff
                            else oneOff.copy(liveFromStart = false, waitForVideo = false)
                            onDownload(it, audioLanguage, title.trim(), author.trim(), sent)
                        }
                    },
                    enabled = selected != null,
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = if (selected != null) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.height(44.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (confirmAsApply) Icons.Filled.Check else Icons.Filled.Download,
                            null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (confirmAsApply) stringResource(R.string.format_sheet_ok) else stringResource(R.string.format_sheet_download),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Audio / Video tabs ──
            //
            // Two words at the start of the sheet rather than two halves of its width: the
            // tabs are a choice of what to download, read along with the heading above
            // them, and a short bar under the chosen word says which without ruling a line
            // across the sheet. The link and incognito buttons take the room to their right,
            // rather than a row of their own at the end of the sheet.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    // The words, not their touch targets, line up with the heading.
                    modifier = Modifier.offset(x = -SHEET_TAB_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SheetTab(
                        label = stringResource(R.string.format_sheet_tab_audio),
                        selected = !videoTab,
                        enabled = info.audioFormats.isNotEmpty(),
                        onClick = { videoTab = false }
                    )
                    SheetTab(
                        label = stringResource(R.string.format_sheet_tab_video),
                        selected = videoTab,
                        enabled = info.videoFormats.isNotEmpty(),
                        onClick = { videoTab = true }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SheetLinkButton(enabled = info.url.isNotBlank(), onClick = { openDialog = SheetDialog.LINK })
                Spacer(modifier = Modifier.width(10.dp))
                IncognitoButton(onFeedback = { message -> feedback = Feedback(message) })
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Details: what the file is called ──
            SheetSection(
                icon = Icons.Outlined.Description,
                title = stringResource(R.string.format_sheet_section_details),
                summary = listOf(title.trim(), author.trim()).filter { it.isNotBlank() }.joinToString(" · "),
                expanded = detailsOpen,
                onToggle = { toggle(SECTION_DETAILS) }
            ) {
                // Kept above the keyboard as one, so typing in either leaves both in view.
                Column(modifier = Modifier.keptAboveKeyboard(keyboard)) {
                    EditableField(
                        label = stringResource(R.string.format_sheet_label_title),
                        value = title,
                        onValueChange = { title = it },
                        color = fieldColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    EditableField(
                        label = stringResource(R.string.format_sheet_label_author),
                        value = author,
                        onValueChange = { author = it },
                        color = fieldColor
                    )
                }
            }

            // ── Quality: the stream, and its soundtrack where there is a choice ──
            val current = selected
            val noStream = if (videoTab) stringResource(R.string.format_sheet_no_video_stream)
            else stringResource(R.string.format_sheet_no_audio_stream)
            val languageName = audioLanguage?.let(::languageLabel)
                ?.takeIf { info.audioLanguages.size > 1 }
            SheetSection(
                icon = Icons.Outlined.HighQuality,
                title = if (videoTab) stringResource(R.string.format_sheet_video_quality)
                else stringResource(R.string.format_sheet_audio_quality),
                summary = current?.let { qualitySummary(it, languageName) } ?: noStream,
                expanded = qualityOpen,
                onToggle = { toggle(SECTION_QUALITY) }
            ) {
                when {
                    // A sheet opened before the source has reported its formats shows the
                    // generic best row, which is what the download would use if it started
                    // now. It is replaced by the real best the moment the formats land, so
                    // the row always names something that can be downloaded.
                    current == null -> Text(
                        noStream,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    // One row, showing what will be downloaded. Tapping it opens the list.
                    // Until the real list arrives the row stands in for the best: it stays
                    // readable and tappable, and the places the codec and the size will
                    // take are held by pills that keep filling, rather than a line under the
                    // row or a blank placeholder in its place. When they land, the real
                    // badges spring into those places.
                    else -> FormatRow(
                        format = current,
                        selected = true,
                        onClick = { formatSheetVisible = true },
                        showChevron = true,
                        // A video-only stream is muxed with an audio track, so the track
                        // that will be used is named alongside it.
                        mergeAudioId = info.mergeAudioFor(audioLanguage)
                            ?.formatId
                            ?.takeIf { videoTab && current.hasVideo && !current.hasAudio },
                        pendingBadges = formatsPending,
                        popBadges = waitedForFormats
                    )
                }

                // Only where there is a choice. A source with one soundtrack has nothing to
                // ask about, which is nearly all of them.
                if (info.audioLanguages.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LanguageField(
                        value = audioLanguage?.let(::languageLabel) ?: "Source default",
                        onClick = { languageSheetVisible = true },
                        color = fieldColor
                    )
                }
            }

            // ── Save: where it goes and in what container ──
            val containerLabel = container.ifBlank { "Default" }
            SheetSection(
                icon = Icons.Outlined.FolderOpen,
                title = stringResource(R.string.format_sheet_save_dir),
                summary = "${saveDirs.labelOf(videoTab)} · $containerLabel",
                expanded = saveOpen,
                onToggle = { toggle(SECTION_SAVE) }
            ) {
                SaveDirField(
                    label = saveDirs.labelOf(videoTab),
                    onClick = { openDialog = SheetDialog.SAVE_DIR },
                    color = fieldColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                PickerField(
                    label = stringResource(R.string.format_sheet_label_container),
                    value = containerLabel,
                    onClick = { openDialog = SheetDialog.CONTAINER },
                    color = fieldColor
                )
            }

            // ── Adjust: everything done to the file on the way ──
            //
            // Chapters and subtitles only apply to a video download; the audio tab shows
            // the split-by-chapters half of the chapters dialog and no subtitles at all,
            // which is what yt-dlp can actually act on for an audio-only extraction.
            val thumbnailLabel = stringResource(R.string.format_sheet_thumbnail)
            val bitrateLabel = stringResource(R.string.properties_bitrate)
            val chaptersLabel = stringResource(R.string.format_sheet_chapters)
            val subtitlesLabel = stringResource(R.string.format_sheet_subtitles)
            val sponsorBlockLabel = stringResource(R.string.format_sheet_sponsorblock)
            val filenameLabel = stringResource(R.string.format_sheet_filename_template)
            val cutLabel = stringResource(R.string.options_cut_title)
            val liveLabel = stringResource(R.string.options_live_title)
            val cookiesLabel = stringResource(R.string.cookies_title)
            val cookiesOn = rememberUseCookies()

            val thumbnailOn = options.thumbnailBadge > 0
            val bitrateOn = !videoTab && options.audioQuality.isNotBlank()
            val chaptersOn = options.chapterBadge(videoTab) > 0
            val subtitlesOn = videoTab && options.subtitleBadge > 0
            val sponsorBlockOn = options.sponsorBlockBadge > 0
            val cutOn = oneOff.hasSection
            val liveOn = oneOff.liveFromStart || oneOff.waitForVideo
            // A live option only does anything on a stream that is live or scheduled, the
            // only links yt-dlp can use it on, so elsewhere the chip is shown but quiet.
            val liveApplies = info.isLive || info.isUpcoming

            SheetSection(
                icon = Icons.Outlined.Tune,
                title = if (videoTab) stringResource(R.string.format_sheet_adjust_video)
                else stringResource(R.string.format_sheet_adjust_audio),
                summary = listOfNotNull(
                    thumbnailLabel.takeIf { thumbnailOn },
                    bitrateLabel.takeIf { bitrateOn },
                    chaptersLabel.takeIf { chaptersOn },
                    subtitlesLabel.takeIf { subtitlesOn },
                    sponsorBlockLabel.takeIf { sponsorBlockOn },
                    cutLabel.takeIf { cutOn },
                    liveLabel.takeIf { liveOn },
                    cookiesLabel.takeIf { cookiesOn }
                ).joinToString(", "),
                expanded = adjustOpen,
                onToggle = { toggle(SECTION_ADJUST) }
            ) {
                // Wraps rather than sitting in fixed rows, so a long label or a larger font
                // moves a chip to the next line instead of pushing it off the edge.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OptionChip(
                        label = thumbnailLabel,
                        icon = Icons.Filled.Image,
                        active = thumbnailOn,
                        onClick = { openDialog = SheetDialog.THUMBNAIL }
                    )
                    // Named rather than valued: the bitrate belongs to a conversion, not to
                    // the stream picked above, and the chip is lit while one is set.
                    if (!videoTab) {
                        OptionChip(
                            label = bitrateLabel,
                            icon = Icons.Filled.HighQuality,
                            active = bitrateOn,
                            onClick = { openDialog = SheetDialog.AUDIO_QUALITY }
                        )
                    }
                    OptionChip(
                        label = chaptersLabel,
                        icon = Icons.Filled.Book,
                        active = chaptersOn,
                        onClick = { openDialog = SheetDialog.CHAPTERS }
                    )
                    if (videoTab) {
                        OptionChip(
                            label = subtitlesLabel,
                            icon = Icons.Filled.ClosedCaption,
                            active = subtitlesOn,
                            onClick = { openDialog = SheetDialog.SUBTITLES }
                        )
                    }
                    OptionChip(
                        label = sponsorBlockLabel,
                        icon = Icons.Filled.Paid,
                        active = sponsorBlockOn,
                        onClick = { openDialog = SheetDialog.SPONSORBLOCK }
                    )
                    OptionChip(
                        label = filenameLabel,
                        icon = Icons.Filled.Edit,
                        active = options.filenameTemplate.isNotBlank(),
                        onClick = { openDialog = SheetDialog.FILENAME }
                    )
                    OptionChip(
                        label = cookiesLabel,
                        icon = Icons.Filled.Cookie,
                        active = cookiesOn,
                        onClick = { openDialog = SheetDialog.COOKIES }
                    )
                    // For this download alone, so not offered where the sheet only adjusts
                    // a link in a set.
                    if (!confirmAsApply) {
                        OptionChip(
                            label = cutLabel,
                            icon = Icons.Filled.ContentCut,
                            active = cutOn,
                            onClick = { openDialog = SheetDialog.CUT }
                        )
                        OptionChip(
                            label = liveLabel,
                            icon = Icons.Filled.LiveTv,
                            active = liveOn,
                            quiet = !liveApplies,
                            onClick = { openDialog = SheetDialog.LIVE }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // What the link and incognito buttons did, shown over the top of the sheet, where it
        // stays in sight however far the sheet is scrolled.
        androidx.compose.animation.AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp)
        ) {
            FeedbackToast(shownFeedback)
        }
      }
    }

    if (formatSheetVisible) {
        FormatSelectionSheet(
            info = info,
            selected = selected,
            audioFirst = !videoTab,
            // A link still being read shows the ladder with the list's own loading state
            // under it, the same as one whose formats are being read.
            isLoadingFormats = isLoadingFormats || isReadingLink,
            onRefresh = onRefreshFormats,
            canChooseSource = remember(info.url) { NewPipeEngine.handlesStream(info.url) },
            preferredHeight = options.videoQuality,
            onConfirm = { format ->
                if (format.hasVideo) {
                    pickedVideo = format
                    chosenVideo = format
                } else {
                    pickedAudio = format
                    chosenAudio = format
                }
                // Picking an audio stream from the video tab, or the other way round,
                // moves the sheet to the tab that entry belongs to.
                videoTab = format.hasVideo
                formatSheetVisible = false
            },
            onDismiss = { formatSheetVisible = false }
        )
    }

    if (languageSheetVisible) {
        AudioLanguageSheet(
            languages = info.audioLanguages,
            selected = audioLanguage,
            onPick = { code ->
                audioLanguage = code
                // The tab's audio moves to the new soundtrack, since the row above is
                // what the download will use and it would otherwise still name the old one.
                pickedAudio = info.bestAudioFor(code)
                languageSheetVisible = false
            },
            onDismiss = { languageSheetVisible = false }
        )
    }

    when (openDialog) {
        SheetDialog.NONE -> Unit

        SheetDialog.SPONSORBLOCK -> SponsorBlockDialog(
            options = options,
            onConfirm = {
                onOptionsChange(it)
                openDialog = SheetDialog.NONE
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.CUT -> CutSheet(
            url = info.url,
            thumbnail = info.thumbnail,
            durationSeconds = info.durationSeconds,
            current = oneOff,
            onApply = { start, end, precise ->
                oneOff = oneOff.copy(sectionStart = start, sectionEnd = end, preciseCuts = precise)
                openDialog = SheetDialog.NONE
            },
            onClear = {
                oneOff = oneOff.copy(sectionStart = -1.0, sectionEnd = -1.0, preciseCuts = false)
                openDialog = SheetDialog.NONE
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.LIVE -> LiveStreamDialog(
            isUpcoming = info.isUpcoming,
            current = oneOff,
            onChange = { oneOff = it },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.THUMBNAIL -> ThumbnailDialog(
            options = options,
            onChange = onOptionsChange,
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.CHAPTERS -> ChaptersDialog(
            options = options,
            isVideo = videoTab,
            onChange = onOptionsChange,
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.SUBTITLES -> SubtitlesDialog(
            options = options,
            onChange = onOptionsChange,
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.FILENAME -> FilenameTemplateDialog(
            template = options.filenameTemplate,
            onConfirm = {
                onOptionsChange(options.copy(filenameTemplate = it))
                openDialog = SheetDialog.NONE
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.AUDIO_QUALITY -> AudioQualityDialog(
            options = options,
            onConfirm = {
                onOptionsChange(it)
                openDialog = SheetDialog.NONE
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        // A sheet of its own rather than a menu: nine audio containers are more than a menu
        // anchored to a field shows comfortably, and the set-of-links sheet already picks
        // the container this way.
        SheetDialog.CONTAINER -> BatchContainerSheet(
            isVideo = videoTab,
            current = container,
            onSelect = { stored ->
                onOptionsChange(
                    if (videoTab) options.copy(videoContainer = stored)
                    else options.copy(audioContainer = stored)
                )
                openDialog = SheetDialog.NONE
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.COOKIES -> CookiesDialog(
            url = info.url,
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.SAVE_DIR -> SaveDirDialog(
            isVideo = videoTab,
            saveDir = saveDirs.of(videoTab),
            label = saveDirs.labelOf(videoTab),
            onOpen = {
                openDialog = SheetDialog.NONE
                onOpenSaveDir(videoTab)
            },
            onPick = { card ->
                openDialog = SheetDialog.NONE
                onPickSaveDir(videoTab, card)
            },
            onReset = {
                openDialog = SheetDialog.NONE
                onResetSaveDir(videoTab)
            },
            onDismiss = { openDialog = SheetDialog.NONE }
        )

        SheetDialog.LINK -> LinkOptionsDialog(
            links = listOf(info.url),
            onFeedback = { message -> feedback = Feedback(message) },
            onDismiss = { openDialog = SheetDialog.NONE }
        )
    }
}

/**
 * The quality section's line while it is closed: the step, the codec, the size, and the
 * soundtrack where the source has more than one.
 */
private fun qualitySummary(format: MediaFormat, language: String?): String =
    listOf(format.shortLabel.uppercase(), format.codecLabel, format.sizeLabel, language.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/**
 * One of the sheet's sections: a header that says what the section holds and what it is set
 * to, and the controls under it while it is open.
 *
 * Flat, on a step-up tone with no outline. The whole header is the switch, and the chevron
 * turns with it; the turn is applied while drawing, so it repaints the arrow rather than
 * recomposing the header. A closed section composes nothing below its header.
 */
@Composable
internal fun SheetSection(
    icon: ImageVector,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit
) {
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sectionChevron"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = color
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (summary.isNotBlank()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = turn },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(SECTION_FADE_MS)),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(SECTION_FADE_MS))
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}

private const val SECTION_FADE_MS = 150

/** How many sections can be open at once before the oldest closes. */
private const val MAX_OPEN_SECTIONS = 2

private const val SECTION_DETAILS = "details"
private const val SECTION_QUALITY = "quality"
private const val SECTION_SAVE = "save"
private const val SECTION_ADJUST = "adjust"

private val SHEET_TAB_PADDING = 12.dp

/** One of the sheet's Audio / Video tabs: its word, and a short bar under it when chosen. */
@Composable
private fun SheetTab(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val textColor by animateColorAsState(
        targetValue = when {
            selected -> colors.primary
            enabled -> colors.onSurface
            else -> colors.onSurface.copy(alpha = 0.38f)
        },
        label = "sheetTabText"
    )
    val barWidth by animateDpAsState(
        targetValue = if (selected) 32.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "sheetTabBar"
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = SHEET_TAB_PADDING, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.primary)
        )
    }
}

/** Which of the sheet's dialogs is open. Only one can be at a time. */
private enum class SheetDialog { NONE, CUT, LIVE, THUMBNAIL, SPONSORBLOCK, CHAPTERS, SUBTITLES, FILENAME, SAVE_DIR, AUDIO_QUALITY, CONTAINER, COOKIES, LINK }

/** One message from the footer; a new one for every tap, so a repeat starts its time over. */
private class Feedback(val message: String)

/** The fill the sheet's fields rest on outside a section. */
@Composable
internal fun defaultFieldColor(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)

/**
 * Labelled box whose value the user can type into, matching the read-only fields' look.
 *
 * The whole box takes a tap, not only the line of text in it: a tap on the label or the
 * padding puts the cursor in this box's own field and brings the keyboard back if it was
 * put away. A tap on the text itself is left to the field, which places the cursor there.
 */
@Composable
internal fun EditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = defaultFieldColor()
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color
    ) {
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current
                    .merge(MaterialTheme.typography.bodyMedium)
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }
}

/**
 * Labelled box showing a fixed choice, which opens the sheet that changes it. Used for the
 * container, whose choices are a sheet of their own.
 */
@Composable
internal fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = defaultFieldColor()
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = stringResource(R.string.format_sheet_change_field),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** The soundtrack row, shown only for a source that published more than one. */
@Composable
private fun LanguageField(value: String, onClick: () -> Unit, color: Color = defaultFieldColor()) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.format_sheet_audio_language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.Translate,
                contentDescription = stringResource(R.string.format_sheet_change_audio_language),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** The destination row. Tapping it leads to opening or changing the folder. */
@Composable
private fun SaveDirField(label: String, onClick: () -> Unit, color: Color = defaultFieldColor()) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.format_sheet_save_dir),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.Folder,
                contentDescription = stringResource(R.string.format_sheet_save_location),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * One of the adjust options. While it is set it takes the accent, icon and all, so which
 * options are on reads at a glance without a count beside each. [quiet] dims a chip whose
 * option would have no effect on this link, while still letting it be opened.
 */
@Composable
internal fun OptionChip(
    label: String,
    icon: ImageVector,
    active: Boolean = false,
    quiet: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        active -> MaterialTheme.colorScheme.primary
        quiet -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    FlatChip(
        label = label,
        onClick = onClick,
        selected = active,
        leading = { Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 15.dp else 16.dp), tint = tint) },
        compact = compact,
        modifier = Modifier.graphicsLayer { alpha = if (quiet && !active) 0.6f else 1f }
    )
}
