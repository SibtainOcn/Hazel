package com.hazel.android.ui.screens.gallery

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SaveKind
import com.hazel.android.data.SettingsRepository
import com.hazel.android.gallery.GalleryImageSize
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryVideoMode
import com.hazel.android.ui.screens.download.Preset
import com.hazel.android.ui.screens.download.SaveLocationScreen
import com.hazel.android.ui.screens.download.TemplateKind
import com.hazel.android.ui.screens.download.defaultFieldColor
import com.hazel.android.ui.screens.download.fieldGroupShape
import com.hazel.android.util.SdCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val state = remember { mutableStateOf<GalleryOptions?>(null) }
    LaunchedEffect(Unit) {
        if (state.value == null) state.value = SettingsRepository.getGalleryOptions(context).first()
    }
    return state
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

/** Image size rows: the original, and the smaller sizes only X serves. */
@Composable
internal fun ImageSizeChoices(selected: GalleryImageSize, onSelect: (GalleryImageSize) -> Unit) {
    val sizes = GalleryImageSize.entries
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        sizes.forEachIndexed { i, size ->
            ChoiceRow(
                label = imageSizeLabel(size),
                hint = stringResource(
                    if (size == GalleryImageSize.ORIGINAL) R.string.gallery_size_original_hint else R.string.gallery_size_x_only
                ),
                selected = size == selected,
                shape = fieldGroupShape(i, sizes.size),
                onClick = { onSelect(size) }
            )
        }
    }
}

@Composable
internal fun VideoModeChoices(selected: GalleryVideoMode, onSelect: (GalleryVideoMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChoiceRow(
            label = stringResource(R.string.gallery_video_best),
            hint = stringResource(R.string.gallery_video_best_hint),
            selected = selected == GalleryVideoMode.BEST,
            shape = fieldGroupShape(0, 2),
            onClick = { onSelect(GalleryVideoMode.BEST) }
        )
        ChoiceRow(
            label = stringResource(R.string.gallery_video_single),
            hint = stringResource(R.string.gallery_video_single_hint),
            selected = selected == GalleryVideoMode.SINGLE,
            shape = fieldGroupShape(1, 2),
            onClick = { onSelect(GalleryVideoMode.SINGLE) }
        )
    }
}

/** One choice of a group: the pick takes a tonal accent fill and a check, never a stroke. */
@Composable
private fun ChoiceRow(label: String, hint: String, selected: Boolean, shape: Shape, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = if (selected) colors.primary.copy(alpha = 0.16f) else defaultFieldColor()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.primary else colors.onSurface
                )
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** A field that only shows a value, for the details gallery-dl cannot change. */
@Composable
internal fun ReadOnlyField(label: String, value: String, shape: Shape) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = defaultFieldColor()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
    }
}

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
