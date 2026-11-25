package com.hazel.android.ui.screens.gallery

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import com.hazel.android.ui.components.DropdownMenuItem
import com.hazel.android.ui.components.MenuTone
import androidx.compose.material3.Icon
import com.hazel.android.ui.components.DropdownMenu
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.hazel.android.ui.components.CaptionSheet
import com.hazel.android.gallery.GallerySave
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryImageSize
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryVideoMode
import com.hazel.android.ui.components.KeyboardOverSheet
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.components.SheetHeaderLayout
import com.hazel.android.ui.components.keepFlingInSheet
import com.hazel.android.ui.components.keptAboveKeyboard
import com.hazel.android.ui.components.liftedOverKeyboard
import com.hazel.android.ui.components.rememberSheetKeyboard
import com.hazel.android.ui.screens.download.BadgeTone
import com.hazel.android.ui.screens.download.CookiesDialog
import com.hazel.android.ui.screens.download.EditableField
import com.hazel.android.ui.screens.download.FIELD_GROUP_GAP
import com.hazel.android.ui.screens.download.Feedback
import com.hazel.android.ui.screens.download.FeedbackToast
import com.hazel.android.ui.screens.download.FEEDBACK_MS
import com.hazel.android.ui.screens.download.FilenameTemplateSheet
import com.hazel.android.ui.screens.download.FormatRowFrame
import com.hazel.android.ui.screens.download.IncognitoButton
import com.hazel.android.ui.screens.download.LinkOptionsDialog
import com.hazel.android.ui.screens.download.OptionChip
import com.hazel.android.ui.screens.download.SHEET_TAB_PADDING
import com.hazel.android.ui.screens.download.SaveDirField
import com.hazel.android.ui.screens.download.SheetDownloadPill
import com.hazel.android.ui.screens.download.SheetLinkButton
import com.hazel.android.ui.screens.download.SheetSection
import com.hazel.android.ui.screens.download.SheetTab
import com.hazel.android.ui.screens.download.fieldGroupShape
import com.hazel.android.ui.screens.download.rememberSaveKindPicker
import com.hazel.android.ui.screens.download.rememberUseCookies
import com.hazel.android.ui.screens.download.withSectionToggled
import com.hazel.android.ui.theme.sheetBlackColor
import com.hazel.android.ui.theme.sheetButtonColor
import kotlinx.coroutines.delay

private enum class ItemDialog { NONE, FILENAME, COOKIES, SAVE_DIR, LINK, QUALITY }

private const val SECTION_DETAILS = "details"
private const val SECTION_QUALITY = "quality"
private const val SECTION_SAVE = "save"
private const val SECTION_ADJUST = "adjust"

/**
 * One file of a post, in the video sheet's shell: the same header, link and incognito pair,
 * sections (two open at most), quality row with its own picker, save folder and the options
 * gallery-dl supports. The choices apply to this download only.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GalleryItemSheet(
    item: GalleryItem,
    /** The post's link, for the link dialog and the cookies dialog's site. */
    postUrl: String,
    onDownload: (options: GalleryOptions, title: String, author: String, save: GallerySave) -> Unit,
    onDismiss: () -> Unit,
    /** Opened from a set: the action reads OK and hands the choice back instead of downloading. */
    confirmAsApply: Boolean = false
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
    val current = options ?: GalleryOptions()
    val update: (GalleryOptions) -> Unit = { options = it }

    var title by rememberSaveable(item.url) { mutableStateOf(defaultTitle(item)) }
    var captionOpen by remember { mutableStateOf(false) }
    var saveMenu by remember { mutableStateOf(false) }
    val hasCaption = item.caption.isNotBlank()
    var author by rememberSaveable(item.url) { mutableStateOf(defaultAuthor(item)) }

    // Only Quality starts open; past two open sections the oldest closes, as in the video sheet.
    var openSections by rememberSaveable(item.url) { mutableStateOf(SECTION_QUALITY) }
    val openList = openSections.split(',').filter { it.isNotBlank() }
    val focusManager = LocalFocusManager.current
    fun toggle(section: String) {
        val next = withSectionToggled(openList, section)
        if (SECTION_DETAILS in openList && SECTION_DETAILS !in next) focusManager.clearFocus()
        openSections = next.joinToString(",")
    }

    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var shownFeedback by remember { mutableStateOf("") }
    LaunchedEffect(feedback) {
        val shown = feedback ?: return@LaunchedEffect
        shownFeedback = shown.message
        delay(FEEDBACK_MS)
        feedback = null
    }

    val keyboard = rememberSheetKeyboard()
    val fieldColor = sheetButtonColor

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.liftedOverKeyboard(keyboard),
        sheetState = sheetState,
        containerColor = sheetBlackColor
    ) {
      KeyboardOverSheet(keyboard)
      Box {
        Column(
            modifier = Modifier
                .keepFlingInSheet()
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
                        stringResource(R.string.format_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SheetDownloadPill(
                        label = stringResource(if (confirmAsApply) R.string.format_sheet_ok else R.string.format_sheet_download),
                        icon = if (confirmAsApply) Icons.Filled.Check else Icons.Filled.Download,
                        enabled = options != null,
                        onClick = { options?.let { onDownload(it, title.trim(), author.trim(), GallerySave.MEDIA) } },
                        joinedEnd = 8.dp.takeIf { hasCaption && !confirmAsApply }
                    )
                    // With a caption, the arrow beside Download chooses what is saved: one split button.
                    if (hasCaption && !confirmAsApply) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Box {
                            Surface(
                                onClick = { saveMenu = true },
                                enabled = options != null,
                                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(width = 40.dp, height = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.caption_save_choices))
                                }
                            }
                            DropdownMenu(expanded = saveMenu, onDismissRequest = { saveMenu = false }) {
                                listOf(
                                    Triple(GallerySave.MEDIA, if (item.isVideo) R.string.format_sheet_tab_video else R.string.gallery_image, Icons.Filled.Image),
                                    Triple(GallerySave.CAPTION, R.string.caption_only, Icons.Filled.FormatQuote),
                                    Triple(GallerySave.BOTH, R.string.caption_with_file, Icons.Filled.LibraryAdd)
                                ).forEachIndexed { index, (save, label, icon) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        icon = icon,
                                        tone = listOf(MenuTone.Accent, MenuTone.Info, MenuTone.Neutral)[index],
                                        onClick = {
                                            saveMenu = false
                                            options?.let { onDownload(it, title.trim(), author.trim(), save) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // layout-safe: one tab word and two icon buttons; nothing here holds a long label
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.offset(x = -SHEET_TAB_PADDING)) {
                    SheetTab(
                        label = stringResource(if (item.isVideo) R.string.format_sheet_tab_video else R.string.gallery_image),
                        selected = true,
                        enabled = true,
                        onClick = {}
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SheetLinkButton(
                    enabled = postUrl.isNotBlank(),
                    onClick = { dialog = ItemDialog.LINK },
                    shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                IncognitoButton(
                    onFeedback = { message -> feedback = Feedback(message) },
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SheetSection(
                icon = Icons.Outlined.Description,
                iconShape = MaterialShapes.Cookie6Sided,
                title = stringResource(R.string.format_sheet_section_details),
                summary = listOf(title.trim(), author.trim()).filter { it.isNotBlank() }.joinToString(" · "),
                expanded = SECTION_DETAILS in openList,
                onToggle = { toggle(SECTION_DETAILS) }
            ) {
                Column(
                    modifier = Modifier.keptAboveKeyboard(keyboard),
                    verticalArrangement = Arrangement.spacedBy(FIELD_GROUP_GAP)
                ) {
                    EditableField(
                        label = stringResource(R.string.format_sheet_label_title),
                        value = title,
                        onValueChange = { title = it },
                        color = fieldColor,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = fieldGroupShape(0, if (hasCaption) 3 else 2)
                    )
                    EditableField(
                        label = stringResource(R.string.format_sheet_label_author),
                        value = author,
                        onValueChange = { author = it },
                        color = fieldColor,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        shape = fieldGroupShape(1, if (hasCaption) 3 else 2)
                    )
                    if (hasCaption) {
                        CaptionField(item.caption, fieldColor, fieldGroupShape(2, 3)) { captionOpen = true }
                    }
                }
            }

            val quality = qualityRow(item, current)
            SheetSection(
                icon = if (item.isVideo) Icons.Outlined.HighQuality else Icons.Outlined.PhotoSizeSelectLarge,
                iconShape = MaterialShapes.Cookie7Sided,
                title = stringResource(if (item.isVideo) R.string.format_sheet_video_quality else R.string.gallery_image_size),
                summary = quality.headlineText,
                expanded = SECTION_QUALITY in openList,
                onToggle = { toggle(SECTION_QUALITY) }
            ) {
                FormatRowFrame(
                    badge = quality.choice.badge,
                    headline = quality.choice.headline,
                    corner = null,
                    selected = true,
                    onClick = { dialog = ItemDialog.QUALITY },
                    showChevron = true,
                    pickedShape = false
                ) {
                    QualityTags(quality.choice.tags)
                }
            }

            SheetSection(
                icon = Icons.Outlined.FolderOpen,
                iconShape = MaterialShapes.Pentagon,
                title = stringResource(R.string.format_sheet_save_dir),
                summary = saveDirs.labelOf(SaveKind.GALLERY),
                expanded = SECTION_SAVE in openList,
                onToggle = { toggle(SECTION_SAVE) }
            ) {
                SaveDirField(
                    label = saveDirs.labelOf(SaveKind.GALLERY),
                    onClick = { dialog = ItemDialog.SAVE_DIR },
                    color = fieldColor
                )
            }

            val filenameLabel = stringResource(R.string.format_sheet_filename_template)
            val metadataLabel = stringResource(R.string.gallery_metadata)
            val postDateLabel = stringResource(R.string.gallery_post_date)
            val captionLabel = stringResource(R.string.gallery_caption)
            val cookiesLabel = stringResource(R.string.cookies_title)
            val filenameOn = current.safeTemplate != null
            SheetSection(
                icon = Icons.Outlined.Tune,
                iconPill = true,
                title = stringResource(if (item.isVideo) R.string.format_sheet_adjust_video else R.string.gallery_adjust_image),
                summary = listOfNotNull(
                    filenameLabel.takeIf { filenameOn },
                    metadataLabel.takeIf { current.writeMetadata },
                    captionLabel.takeIf { current.embedCaption && !item.isVideo },
                    postDateLabel.takeIf { current.keepPostDate },
                    cookiesLabel.takeIf { cookiesOn }
                ).joinToString(", "),
                expanded = SECTION_ADJUST in openList,
                onToggle = { toggle(SECTION_ADJUST) }
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
                        // Writes the caption into each JPEG or PNG saved.
                        OptionChip(
                            label = captionLabel,
                            icon = Icons.Filled.FormatQuote,
                            active = current.embedCaption,
                            onClick = { update(current.copy(embedCaption = !current.embedCaption)) }
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
        if (captionOpen) CaptionSheet(item.caption, title.trim(), onDismiss = { captionOpen = false })

        // What the link and incognito buttons did, over the sheet, wherever it is scrolled.
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

    when (dialog) {
        ItemDialog.NONE -> Unit
        ItemDialog.QUALITY -> if (item.isVideo) {
            GalleryQualitySheet(
                choices = videoChoices(item),
                selected = current.videoMode,
                onConfirm = {
                    update(current.copy(videoMode = it))
                    dialog = ItemDialog.NONE
                },
                onDismiss = { dialog = ItemDialog.NONE }
            )
        } else {
            GalleryQualitySheet(
                choices = sizeChoices(item),
                selected = current.imageSize,
                onConfirm = {
                    update(current.copy(imageSize = it))
                    dialog = ItemDialog.NONE
                },
                onDismiss = { dialog = ItemDialog.NONE }
            )
        }
        ItemDialog.LINK -> LinkOptionsDialog(
            links = listOf(postUrl),
            onFeedback = { message -> feedback = Feedback(message) },
            onDismiss = { dialog = ItemDialog.NONE }
        )
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

/** The quality row's choice as it stands, and the words its closed section shows. */
private class QualityRow(val choice: QualityChoice<*>, val headlineText: String)

@Composable
private fun qualityRow(item: GalleryItem, options: GalleryOptions): QualityRow =
    if (item.isVideo) {
        val choice = videoChoices(item).first { it.value == options.videoMode }
        QualityRow(choice, videoModeLabel(options.videoMode))
    } else {
        val choice = sizeChoices(item).firstOrNull { it.value == options.imageSize }
            ?: sizeChoice(item, options.imageSize)
        QualityRow(choice, imageSizeLabel(options.imageSize))
    }

/** The sizes this file can come in: the original, and on X its smaller ones. */
@Composable
internal fun sizeChoices(item: GalleryItem): List<QualityChoice<GalleryImageSize>> =
    GalleryImageSize.entries
        .filter { it == GalleryImageSize.ORIGINAL || item.site == X_SITE }
        .map { sizeChoice(item, it) }

@Composable
private fun sizeChoice(item: GalleryItem, size: GalleryImageSize): QualityChoice<GalleryImageSize> {
    val dimensions = "${item.width} × ${item.height}".takeIf { item.width > 0 && item.height > 0 }
    val tags = if (size == GalleryImageSize.ORIGINAL) {
        listOfNotNull(
            dimensions?.let { it to BadgeTone.SIZE },
            stringResource(R.string.gallery_size_original_hint) to BadgeTone.NEUTRAL
        )
    } else {
        listOf(stringResource(R.string.gallery_size_x_only) to BadgeTone.NEUTRAL)
    }
    return QualityChoice(size, item.extension.uppercase().ifBlank { "IMG" }, imageSizeLabel(size).uppercase(), tags)
}

@Composable
private fun videoChoices(item: GalleryItem): List<QualityChoice<GalleryVideoMode>> = listOf(
    QualityChoice(
        GalleryVideoMode.BEST, "BEST", videoModeLabel(GalleryVideoMode.BEST).uppercase(),
        listOf(stringResource(R.string.gallery_video_best_hint) to BadgeTone.NEUTRAL)
    ),
    QualityChoice(
        GalleryVideoMode.SINGLE, item.extension.uppercase().ifBlank { "MP4" }, videoModeLabel(GalleryVideoMode.SINGLE).uppercase(),
        listOf(stringResource(R.string.gallery_video_single_hint) to BadgeTone.NEUTRAL)
    )
)

@Composable
private fun videoModeLabel(mode: GalleryVideoMode): String = stringResource(
    if (mode == GalleryVideoMode.BEST) R.string.gallery_video_best else R.string.gallery_video_single
)

/** gallery-dl's name for X, the one site whose files come in smaller sizes. */
private const val X_SITE = "twitter"

/** The caption's start in the Details group, with a button that opens all of it. */
@Composable
private fun CaptionField(caption: String, color: androidx.compose.ui.graphics.Color, shape: androidx.compose.ui.graphics.Shape, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = shape, color = color, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.gallery_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    caption.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            androidx.compose.material3.IconButton(onClick = onOpen) {
                Icon(painterResource(R.drawable.ic_expand_all), contentDescription = stringResource(R.string.caption_view_full))
            }
        }
    }
}
