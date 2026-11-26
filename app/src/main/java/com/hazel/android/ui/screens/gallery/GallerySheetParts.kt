package com.hazel.android.ui.screens.gallery

import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryImageSize
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.ui.screens.download.Preset
import com.hazel.android.ui.screens.download.SaveLocationScreen
import com.hazel.android.ui.screens.download.TemplateKind
import com.hazel.android.util.SdCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.hazel.android.HazelApp
import com.hazel.android.ui.components.ModalBottomSheet
import com.hazel.android.ui.theme.sheetBlackColor

/** gallery-dl's own filename fields, written as `{name}`. */
internal val GalleryTemplates = TemplateKind(
    presets = listOf(
        Preset("{username}_{num}.{extension}", R.string.gallery_filename_preset_user_num, Icons.Filled.Person),
        Preset("{id}_{num}.{extension}", R.string.gallery_filename_preset_id_num, Icons.Filled.Tag),
        Preset("{filename}.{extension}", R.string.gallery_filename_preset_original, Icons.Filled.Description)
    ),
    fields = listOf("username", "id", "num", "date", "extension", "filename"),
    field = { "{$it}" },
    normalize = { it.trim().replace('/', '_').replace('\\', '_').replace("..", ".") }
)

/** The saved gallery defaults, read once; null until they land. Changes stay with this sheet. */
@Composable
internal fun rememberGalleryOptions(): MutableState<GalleryOptions?> {
    val context = LocalContext.current
    val cached = SavedGalleryOptions.value
    val state = remember { mutableStateOf(cached) }
    LaunchedEffect(Unit) {
        val saved = SettingsRepository.getGalleryOptions(context).first()
        SavedGalleryOptions.value = saved
        // Settings may have changed since the copy was kept; an edit made in the sheet stays.
        if (state.value == cached) state.value = saved
    }
    return state
}

/** The saved options, kept once read so a sheet has them on its first frame instead of popping them in. */
private object SavedGalleryOptions {
    @Volatile var value: GalleryOptions? = null
}

/** Reads the saved options ahead of the first sheet. */
@Composable
internal fun WarmGalleryOptions() {
    val context = LocalContext.current
    LaunchedEffect(context) {
        if (SavedGalleryOptions.value == null) {
            SavedGalleryOptions.value = SettingsRepository.getGalleryOptions(context).first()
        }
    }
}

internal fun showUnderMaintenance(context: Context) {
    Toast.makeText(context, R.string.gallery_under_maintenance, Toast.LENGTH_SHORT).show()
}

/** `JPG · 1080P`, or the type alone when the site gave no size. */
internal fun galleryTag(item: GalleryItem): String {
    val type = item.extension.uppercase()
    return if (item.height > 0) "$type · ${item.height}P" else type
}

@Composable
internal fun imageSizeLabel(size: GalleryImageSize): String = stringResource(
    when (size) {
        GalleryImageSize.ORIGINAL -> R.string.gallery_size_original
        GalleryImageSize.LARGE -> R.string.gallery_size_large
        GalleryImageSize.MEDIUM -> R.string.gallery_size_medium
    }
)

/** The title a file's sheet starts with: the site's, else the file's own name. */
internal fun defaultTitle(item: GalleryItem): String = item.title.ifBlank {
    runCatching { java.net.URI(item.url).path }.getOrNull().orEmpty()
        .substringAfterLast('/').substringBeforeLast('.')
}

/** The author a file's sheet starts with: the site's, else the site itself. */
internal fun defaultAuthor(item: GalleryItem): String = item.author.ifBlank { item.site }

/** The gallery folder's location screen, the same one More > Storage locations opens. */
@Composable
internal fun GallerySaveLocation(
    saveDirs: SaveDirs,
    onPick: (SaveKind, SdCard?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kind = SaveKind.GALLERY
    SaveLocationScreen(
        isVideo = false,
        saveDir = saveDirs.of(kind),
        label = saveDirs.labelOf(kind),
        onPick = { card ->
            onDismiss()
            onPick(kind, card)
        },
        onReset = {
            onDismiss()
            scope.launch { SettingsRepository.resetSaveDir(context, kind) }
        },
        onUseRecent = { SettingsRepository.setSaveDir(context, kind, it.uri, it.label) },
        onDismiss = onDismiss,
        internalPath = kind.display,
        internalFolder = { kind.builtInFolder() },
        cardFolder = "Hazel/" + kind.folderName
    )
}

/** Keeps a change made in a sheet as the default for the next one, as the video sheet does. */
internal fun saveGalleryOptions(context: Context, options: GalleryOptions) {
    SavedGalleryOptions.value = options
    val app = context.applicationContext
    HazelApp.instance.applicationScope.launch { SettingsRepository.setGalleryOptions(app, options) }
}

/** True inside a sheet a host already shows, so a gallery sheet fills it instead of opening its own. */
internal val LocalInHostSheet = staticCompositionLocalOf { false }

/**
 * A gallery sheet's frame: its own bottom sheet, or, inside a host's ([LocalInHostSheet]), a
 * plain column there. Sheets opened from inside it get frames of their own again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GallerySheetFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalInHostSheet.current) {
        Column(modifier) {
            CompositionLocalProvider(LocalInHostSheet provides false) { content() }
        }
        return
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = sheetBlackColor,
        sheetMaxWidth = sheetMaxWidth,
        content = content
    )
}
