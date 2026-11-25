package com.hazel.android.ui.screens.more

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.YtDlpEngine
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryImageSize
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GallerySearchSite
import com.hazel.android.gallery.GalleryShow
import com.hazel.android.gallery.GalleryVideoMode
import com.hazel.android.ui.components.AlertDialog
import com.hazel.android.ui.components.ExpressiveTextField
import com.hazel.android.ui.screens.download.SaveLocationScreen
import com.hazel.android.ui.screens.download.rememberSaveKindPicker
import com.hazel.android.ui.theme.tonalFill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class GalleryChoice { IMAGE_SIZE, VIDEO, FILENAME, SITE, POSTS, SHOW, CLEAR_RECORD }

/** Defaults for gallery-dl downloads and image search. */
@Composable
fun GallerySettingsScreen(onBack: () -> Unit, onOpenCookies: () -> Unit, onOpenProxy: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val options by remember(context) { SettingsRepository.getGalleryOptions(context) }.collectAsState(initial = GalleryOptions())
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }.collectAsState(initial = SaveDirs())
    val site by remember(context) { SettingsRepository.getGallerySearchSite(context) }.collectAsState(initial = GallerySearchSite.DEFAULT)
    val posts by remember(context) { SettingsRepository.getGalleryPostsPerSearch(context) }.collectAsState(initial = SettingsRepository.DEFAULT_GALLERY_POSTS)
    val show by remember(context) { SettingsRepository.getGalleryShow(context) }.collectAsState(initial = GalleryShow.ALL)
    val skipReposts by remember(context) { SettingsRepository.getGallerySkipReposts(context) }.collectAsState(initial = true)

    var open by rememberSaveable { mutableStateOf<GalleryChoice?>(null) }
    var folderOpen by rememberSaveable { mutableStateOf(false) }
    val pickSaveDir = rememberSaveKindPicker(saveDirs)
    val close = { open = null }

    val maintenance = stringResource(R.string.search_under_maintenance)
    val later = { Toast.makeText(context, maintenance, Toast.LENGTH_SHORT).show() }
    fun save(change: (GalleryOptions) -> GalleryOptions) {
        scope.launch { SettingsRepository.setGalleryOptions(context, change(options)) }
    }

    SettingsScreen(
        title = stringResource(R.string.more_image_settings),
        onBack = onBack,
        description = stringResource(R.string.gallery_settings_description)
    ) {
        SettingsSection(
            title = stringResource(R.string.more_downloads),
            rows = listOf(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Folder,
                        title = stringResource(R.string.gallery_settings_save_folder),
                        value = saveDirs.labelOf(SaveKind.GALLERY),
                        onClick = { folderOpen = true }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.PhotoSizeSelectLarge,
                        title = stringResource(R.string.gallery_settings_image_size),
                        value = stringResource(imageSizeLabel(options.imageSize)),
                        onClick = { open = GalleryChoice.IMAGE_SIZE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.HighQuality,
                        title = stringResource(R.string.gallery_settings_video_quality),
                        value = stringResource(videoLabel(options.videoMode)),
                        onClick = { open = GalleryChoice.VIDEO }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.DriveFileRenameOutline,
                        title = stringResource(R.string.options_filename_title),
                        value = options.safeTemplate ?: stringResource(R.string.gallery_settings_filename_default),
                        onClick = { open = GalleryChoice.FILENAME }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.DataObject,
                        title = stringResource(R.string.gallery_settings_metadata),
                        checked = options.writeMetadata,
                        onCheckedChange = { on -> save { it.copy(writeMetadata = on) } }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Event,
                        title = stringResource(R.string.gallery_settings_post_date),
                        checked = options.keepPostDate,
                        onCheckedChange = { on -> save { it.copy(keepPostDate = on) } }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.FolderZip,
                        title = stringResource(R.string.gallery_settings_zip),
                        checked = options.packZip,
                        onCheckedChange = { on -> save { it.copy(packZip = on) } }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.DoneAll,
                        title = stringResource(R.string.gallery_settings_skip_downloaded),
                        summary = stringResource(R.string.gallery_settings_skip_downloaded_hint),
                        checked = options.skipDownloaded,
                        onCheckedChange = { on -> save { it.copy(skipDownloaded = on) } }
                    )
                },
                {
                    ActionSettingRow(
                        icon = Icons.Filled.DeleteSweep,
                        title = stringResource(R.string.gallery_settings_clear_record),
                        summary = stringResource(R.string.gallery_settings_clear_record_hint),
                        onClick = { open = GalleryChoice.CLEAR_RECORD }
                    )
                }
            )
        )

        SettingsSection(
            title = stringResource(R.string.fetch_settings_search),
            rows = listOf(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.TravelExplore,
                        title = stringResource(R.string.fetch_settings_search_on),
                        value = site.label,
                        onClick = { open = GalleryChoice.SITE }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.FormatListNumbered,
                        title = stringResource(R.string.gallery_posts_per_search),
                        value = posts.toString(),
                        onClick = { open = GalleryChoice.POSTS }
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.PhotoLibrary,
                        title = stringResource(R.string.gallery_show),
                        value = stringResource(showLabel(show)),
                        onClick = { open = GalleryChoice.SHOW }
                    )
                },
                {
                    SwitchSettingRow(
                        icon = Icons.Filled.Repeat,
                        title = stringResource(R.string.gallery_skip_reposts),
                        checked = skipReposts,
                        onCheckedChange = { on -> scope.launch { SettingsRepository.setGallerySkipReposts(context, on) } }
                    )
                }
            )
        )

        SettingsSection(
            title = stringResource(R.string.gallery_settings_profiles),
            rows = listOf(
                {
                    ValueSettingRow(
                        icon = Icons.Filled.AccountCircle,
                        title = stringResource(R.string.gallery_settings_instagram_sections),
                        value = stringResource(R.string.gallery_settings_instagram_sections_value),
                        onClick = later
                    )
                },
                {
                    ValueSettingRow(
                        icon = Icons.Filled.Forum,
                        title = stringResource(R.string.gallery_settings_x_posts),
                        value = stringResource(R.string.gallery_settings_x_posts_value),
                        onClick = later
                    )
                }
            )
        )

        SettingsSection(
            title = stringResource(R.string.gallery_settings_network),
            rows = listOf(
                { ActionSettingRow(icon = Icons.Filled.Timer, title = stringResource(R.string.advanced_sleep_requests), onClick = later) },
                { ActionSettingRow(icon = Icons.Filled.Speed, title = stringResource(R.string.storage_locations_speed_limit), onClick = later) },
                { ActionSettingRow(icon = Icons.Filled.Replay, title = stringResource(R.string.gallery_settings_retries), onClick = later) },
                { ActionSettingRow(icon = Icons.Filled.Cookie, title = stringResource(R.string.more_cookies), onClick = onOpenCookies) },
                { ActionSettingRow(icon = Icons.Filled.Lan, title = stringResource(R.string.advanced_proxy), onClick = onOpenProxy) }
            )
        )

        SettingsSection(
            title = stringResource(R.string.gallery_settings_extras),
            rows = listOf(
                { ActionSettingRow(icon = Icons.Filled.Subtitles, title = stringResource(R.string.gallery_settings_captions), onClick = later) },
                { ActionSettingRow(icon = Icons.Filled.Animation, title = stringResource(R.string.gallery_settings_ugoira), onClick = later) }
            )
        )
    }

    if (folderOpen) {
        val kind = SaveKind.GALLERY
        SaveLocationScreen(
            isVideo = false,
            saveDir = saveDirs.of(kind),
            label = saveDirs.labelOf(kind),
            onPick = { card ->
                folderOpen = false
                pickSaveDir(kind, card)
            },
            onReset = {
                folderOpen = false
                scope.launch { SettingsRepository.resetSaveDir(context, kind) }
            },
            onUseRecent = { SettingsRepository.setSaveDir(context, kind, it.uri, it.label) },
            onDismiss = { folderOpen = false },
            internalPath = kind.display,
            internalFolder = { kind.builtInFolder() },
            cardFolder = "Hazel/" + kind.folderName
        )
    }

    val cleared = stringResource(R.string.gallery_settings_clear_record_done)
    val clearFailed = stringResource(R.string.gallery_settings_clear_record_failed)
    val clearBusy = stringResource(R.string.gallery_settings_clear_record_busy)
    when (open) {
        GalleryChoice.IMAGE_SIZE -> SingleChoiceDialog(
            title = stringResource(R.string.gallery_settings_image_size),
            choices = GalleryImageSize.entries.map { it to stringResource(imageSizeLabel(it)) },
            selected = options.imageSize,
            onSelect = { size -> save { it.copy(imageSize = size) }; close() },
            onDismiss = close,
            describe = { if (it == GalleryImageSize.ORIGINAL) null else stringResource(R.string.gallery_settings_image_size_hint) }
        )
        GalleryChoice.VIDEO -> SingleChoiceDialog(
            title = stringResource(R.string.gallery_settings_video_quality),
            choices = GalleryVideoMode.entries.map { it to stringResource(videoLabel(it)) },
            selected = options.videoMode,
            onSelect = { mode -> save { it.copy(videoMode = mode) }; close() },
            onDismiss = close
        )
        GalleryChoice.FILENAME -> GalleryTemplateDialog(
            current = options.filenameTemplate,
            onSave = { template -> save { it.copy(filenameTemplate = template) }; close() },
            onDismiss = close
        )
        GalleryChoice.SITE -> SingleChoiceDialog(
            title = stringResource(R.string.fetch_settings_search_on),
            choices = GallerySearchSite.entries.map { it to it.label },
            selected = site,
            onSelect = { scope.launch { SettingsRepository.setGallerySearchSite(context, it) }; close() },
            onDismiss = close
        )
        GalleryChoice.POSTS -> SingleChoiceDialog(
            title = stringResource(R.string.gallery_posts_per_search),
            choices = SettingsRepository.GALLERY_POST_COUNTS.map { it to it.toString() },
            selected = posts,
            onSelect = { scope.launch { SettingsRepository.setGalleryPostsPerSearch(context, it) }; close() },
            onDismiss = close
        )
        GalleryChoice.SHOW -> SingleChoiceDialog(
            title = stringResource(R.string.gallery_show),
            choices = GalleryShow.entries.map { it to stringResource(showLabel(it)) },
            selected = show,
            onSelect = { scope.launch { SettingsRepository.setGalleryShow(context, it) }; close() },
            onDismiss = close
        )
        GalleryChoice.CLEAR_RECORD -> ConfirmSettingDialog(
            title = stringResource(R.string.gallery_settings_clear_record_confirm_title),
            body = stringResource(R.string.gallery_settings_clear_record_confirm_body),
            confirm = stringResource(R.string.download_clear),
            onConfirm = {
                close()
                val app = context.applicationContext
                scope.launch {
                    // Gallery runs hold the engine's shared lock, so a busy engine means the archive is in use.
                    val ok = withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { YtDlpEngine.whenIdle { GalleryDlEngine.clearArchive(app) } }.getOrDefault(false)
                    }
                    val message = when (ok) {
                        null -> clearBusy
                        true -> cleared
                        false -> clearFailed
                    }
                    Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = close
        )
        null -> Unit
    }
}

private val TEMPLATE_FIELDS = listOf("{username}", "{id}", "{num}", "{date}", "{extension}")

/** A gallery-dl filename: blank keeps the site's default; a path is refused. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryTemplateDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(current) }
    val valid = draft.isBlank() || GalleryOptions(filenameTemplate = draft).safeTemplate != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.options_filename_title), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                ExpressiveTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    isError = !valid,
                    placeholder = { Text("{username}_{num}.{extension}") },
                    supportingText = if (valid) null else {
                        { Text(stringResource(R.string.gallery_settings_filename_invalid)) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.options_filename_fields),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TEMPLATE_FIELDS.forEach { field ->
                        Surface(
                            onClick = { draft += field },
                            shape = RoundedCornerShape(12.dp),
                            color = tonalFill(0.55f)
                        ) {
                            Text(
                                field,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.trim()) }, enabled = valid) {
                Text(stringResource(R.string.options_text_input_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { draft = "" }, enabled = draft.isNotEmpty()) {
                    Text(stringResource(R.string.options_filename_clear))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_cancel)) }
            }
        }
    )
}

private fun imageSizeLabel(size: GalleryImageSize): Int = when (size) {
    GalleryImageSize.ORIGINAL -> R.string.gallery_settings_size_original
    GalleryImageSize.LARGE -> R.string.gallery_settings_size_large
    GalleryImageSize.MEDIUM -> R.string.gallery_settings_size_medium
}

private fun videoLabel(mode: GalleryVideoMode): Int = when (mode) {
    GalleryVideoMode.BEST -> R.string.gallery_settings_video_best
    GalleryVideoMode.SINGLE -> R.string.gallery_settings_video_single
}

private fun showLabel(show: GalleryShow): Int = when (show) {
    GalleryShow.ALL -> R.string.gallery_show_all
    GalleryShow.IMAGES -> R.string.gallery_show_images
    GalleryShow.VIDEOS -> R.string.gallery_show_videos
}
