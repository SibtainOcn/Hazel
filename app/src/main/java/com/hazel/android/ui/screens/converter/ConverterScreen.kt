package com.hazel.android.ui.screens.converter

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.R
import com.hazel.android.converter.AudioFormat
import com.hazel.android.converter.AudioFormats
import com.hazel.android.converter.AudioTag
import com.hazel.android.converter.ConverterViewModel
import com.hazel.android.converter.LogLevel
import com.hazel.android.ui.theme.ErrorRed
import com.hazel.android.ui.theme.SuccessGreen
import com.hazel.android.ui.theme.WarningAmber
import com.hazel.android.util.FolderUtil
import com.hazel.android.util.PermissionHelper
import com.hazel.android.util.StoragePaths

/**
 * Turns a video already on the phone into an audio file, with no network involved.
 *
 * The screen is three decisions in a column, in the order they are made: what to convert,
 * what to turn it into, and where it lands. Everything else on the screen is a consequence
 * of one of those three, so nothing appears until it has something to say.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    onBack: () -> Unit = {},
    converterViewModel: ConverterViewModel = viewModel()
) {
    val state by converterViewModel.state.collectAsState()
    val context = LocalContext.current

    var formatSheetOpen by remember { mutableStateOf(false) }
    var detailsSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Whether a finished file will reach the user's own Music folder. Only ever false up to
    // Android 10, where that is a direct file write and the permission can be refused.
    var canPublish by remember { mutableStateOf(PermissionHelper.canWriteSharedStorage(context)) }

    // Asked for once the details are settled, so the run starts the moment it is answered
    // either way. A refusal is not a reason to stop: it only changes where the file lands.
    val storageRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        canPublish = granted || !PermissionHelper.needsLegacyStorageWrite()
        converterViewModel.convert(context)
    }

    fun beginConversion() {
        detailsSheetOpen = false
        if (PermissionHelper.needsLegacyStorageWrite() && !canPublish) {
            storageRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            converterViewModel.convert(context)
        }
    }

    // ACTION_OPEN_DOCUMENT, which every version this app runs on has. The list is wider than
    // just video because a provider decides the type of what it holds, and plenty of them
    // hand back a generic type for a container they do not recognise. Those files would
    // otherwise be shown greyed out and unpickable, which reads as the app refusing them.
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { picked ->
            // Held for as long as the picker's own grant allows, which covers the run. Some
            // providers refuse to make it persistable, so this is attempted and not relied
            // upon: the file is copied into the cache before any work starts either way.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            converterViewModel.selectFile(context, picked)
        }
    }

    LaunchedEffect(Unit) {
        canPublish = PermissionHelper.canWriteSharedStorage(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Header(onBack = onBack)

        Spacer(Modifier.height(18.dp))

        SourceCard(
            fileName = state.inputFileName,
            sizeBytes = state.inputSizeBytes,
            enabled = !state.isConverting,
            onPick = { filePicker.launch(PICKER_TYPES) }
        )

        Spacer(Modifier.height(10.dp))

        SettingRow(
            icon = { tint ->
                Icon(Icons.Filled.GraphicEq, null, Modifier.size(20.dp), tint = tint)
            },
            label = "Convert to",
            value = state.format.name,
            trailing = { TagStrip(state.format.tags) },
            enabled = !state.isConverting,
            onClick = { formatSheetOpen = true }
        )

        Spacer(Modifier.height(8.dp))

        SettingRow(
            icon = { tint ->
                Icon(Icons.Filled.FolderOpen, null, Modifier.size(20.dp), tint = tint)
            },
            label = "Saved to",
            value = if (canPublish) StoragePaths.CONVERTED_DISPLAY else "Hazel's own storage",
            enabled = true,
            onClick = { FolderUtil.open(context, StoragePaths.finalConverted) }
        )

        // Only ever shown on the versions that can refuse. Said before the conversion runs
        // rather than after it, because it is the sort of thing worth knowing while it can
        // still be changed.
        if (!canPublish && PermissionHelper.needsLegacyStorageWrite()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Without storage access the file stays inside Hazel, where other apps cannot see it.",
                style = MaterialTheme.typography.labelSmall,
                color = WarningAmber,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(Modifier.height(18.dp))

        ConvertButton(
            isConverting = state.isConverting,
            enabled = state.inputFileUri != null && !state.isConverting,
            onClick = { detailsSheetOpen = true }
        )

        AnimatedVisibility(
            visible = state.isConverting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ProgressPanel(
                progress = state.progress,
                line = state.statusLine,
                level = state.statusLevel
            )
        }

        AnimatedVisibility(visible = state.isComplete, enter = fadeIn(), exit = fadeOut()) {
            ResultCard(
                fileName = state.outputFileName,
                sizeBytes = state.outputSizeBytes,
                where = state.outputPath,
                inAppStorage = state.savedToAppStorage,
                onOpen = { FolderUtil.open(context, StoragePaths.finalConverted) },
                onAgain = converterViewModel::resetState
            )
        }

        AnimatedVisibility(visible = state.error != null, enter = fadeIn(), exit = fadeOut()) {
            ErrorCard(
                message = state.error.orEmpty(),
                onDismiss = converterViewModel::dismissError
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (detailsSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { detailsSheetOpen = false },
            sheetState = detailsSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DetailsSheetContent(
                outputName = state.outputName,
                extension = state.format.extension,
                onName = converterViewModel::setOutputName,
                onConfirm = { beginConversion() }
            )
        }
    }

    if (formatSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { formatSheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            FormatSheetContent(
                selected = state.format,
                onPick = {
                    converterViewModel.setFormat(it)
                    formatSheetOpen = false
                }
            )
        }
    }
}

// ── Pieces ──

@Composable
private fun Header(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(R.drawable.back),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Offline Converter",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Video to audio, nothing leaves the phone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The file to convert.
 *
 * Empty it is the one thing to do on the screen and is sized accordingly. Filled it steps
 * back into a row, because by then the decision worth looking at is the next one down.
 */
@Composable
private fun SourceCard(
    fileName: String,
    sizeBytes: Long,
    enabled: Boolean,
    onPick: () -> Unit
) {
    val chosen = fileName.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (chosen) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onPick)
                .padding(horizontal = 16.dp, vertical = if (chosen) 14.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.MovieCreation,
                contentDescription = null,
                modifier = Modifier.size(if (chosen) 22.dp else 26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (chosen) fileName else "Choose a video file",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (chosen) {
                        if (sizeBytes > 0) formatSize(sizeBytes) else "Ready"
                    } else {
                        "Anything on the phone or an SD card"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (chosen && enabled) {
                Text(
                    "Change",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** One tappable line: an icon, what it is, what it is set to. */
@Composable
private fun SettingRow(
    icon: @Composable (Color) -> Unit,
    label: String,
    value: String,
    enabled: Boolean,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon(MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    trailing?.let {
                        Spacer(Modifier.width(8.dp))
                        it()
                    }
                }
            }
            if (enabled) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConvertButton(isConverting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // Two different reasons for a button not to be tappable, and they must not look alike.
    // Nothing chosen yet is a button waiting to be used and should recede. A conversion
    // already running is the most active thing on the screen, and Material's disabled
    // treatment drained it until the word and the spinner were barely there at all.
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = if (isConverting) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            },
            disabledContentColor = if (isConverting) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
    ) {
        if (isConverting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Converting", style = MaterialTheme.typography.titleSmall)
        } else {
            Icon(Icons.Filled.Transform, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Convert", style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * The bar, and the one line under it.
 *
 * The line is replaced in place as the engine reports the next thing it is doing, rather
 * than added to a list that grows down the screen. What the engine said four seconds ago is
 * of no use to anybody watching it work, and a panel of it pushes everything else off the
 * bottom of the screen.
 */
@Composable
private fun ProgressPanel(progress: Float, line: String, level: LogLevel) {
    val animated by animateFloatAsState(targetValue = progress, label = "convertProgress")

    Column(modifier = Modifier.padding(top = 16.dp)) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when (level) {
                            LogLevel.ERROR -> ErrorRed
                            LogLevel.WARN -> WarningAmber
                            LogLevel.SUCCESS -> SuccessGreen
                            LogLevel.INFO -> MaterialTheme.colorScheme.primary
                        }
                    )
            )
            Spacer(Modifier.width(10.dp))
            // Crossfaded rather than swapped, so a line replaced several times a second
            // reads as one line changing instead of as flicker.
            AnimatedContent(
                targetState = line,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "convertStatus",
                modifier = Modifier.weight(1f)
            ) { current ->
                Text(
                    current,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            // On the row it belongs to. Underneath it read as a second status below the
            // first, which is one status too many for one progress bar.
            Text(
                "${(animated * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ResultCard(
    fileName: String,
    sizeBytes: Long,
    where: String,
    inAppStorage: Boolean,
    onOpen: () -> Unit,
    onAgain: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(SuccessGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.small_right_tick),
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Saved",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SuccessGreen
                    )
                    Text(
                        fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            if (sizeBytes > 0) {
                                append(formatSize(sizeBytes))
                                append("  ·  ")
                            }
                            append(where)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (inAppStorage) WarningAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Two words, side by side, separated by a hairline rather than by two filled
            // shapes. The work is already done: the card is reporting, not asking, and a
            // pair of solid buttons under a finished job reads as another decision to make.
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Row(modifier = Modifier.height(46.dp)) {
                FlatAction("Open folder", Modifier.weight(1f), onOpen)
                VerticalDivider(
                    modifier = Modifier.padding(vertical = 9.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                FlatAction("Convert another", Modifier.weight(1f), onAgain)
            }
        }
    }
}

/** One word, the whole width of its half, with nothing drawn around it. */
@Composable
private fun FlatAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "That did not convert",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onDismiss, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                Text("Dismiss", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── The details sheet ──

/**
 * What the saved file will be called and who it says made it, asked once, just before the
 * conversion starts.
 *
 * Asked here rather than on the screen behind it because neither field is a decision until
 * the moment of converting: the name arrives filled in from the video, and most of the time
 * the right answer is to leave it and press the button.
 */
@Composable
private fun DetailsSheetContent(
    outputName: String,
    extension: String,
    onName: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 28.dp)) {
        Text(
            "Save as",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Names the file, and is written into its title tag.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        FlatField(
            value = outputName,
            onValueChange = onName,
            label = "Name",
            // The extension is the format's to decide, so it is shown and not typed.
            suffix = {
                Text(
                    ".$extension",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            // Not "Convert" a second time. The button behind this sheet says that, and two
            // of them in a row reads as the first one not having worked.
            Text("Start", style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * A text field drawn as one of the screen's own surfaces.
 *
 * No outline and no indicator line. Material's bordered fields put a notch in their own
 * border to hold the label, which is a lot of drawing for two words and sits oddly next to
 * the flat rows the rest of the screen is made of. The label goes above the text instead,
 * where it reads the same way the rows do.
 */
@Composable
private fun FlatField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    suffix: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
                suffix?.let {
                    Spacer(Modifier.width(8.dp))
                    it()
                }
            }
        }
    }
}

// ── The format sheet ──

@Composable
private fun FormatSheetContent(selected: AudioFormat, onPick: (AudioFormat) -> Unit) {
    val (common, rest) = AudioFormats.all.partition { it in COMMON }

    Column(modifier = Modifier.padding(bottom = 28.dp)) {
        Text(
            "Convert to",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 12.dp)
        )

        SheetHeading("The three worth using")
        common.forEach { FormatRow(it, it.id == selected.id, onPick) }

        Spacer(Modifier.height(10.dp))
        SheetHeading("Everything else")
        rest.forEach { FormatRow(it, it.id == selected.id, onPick) }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 6.dp)
    )
}

@Composable
private fun FormatRow(format: AudioFormat, isSelected: Boolean, onPick: (AudioFormat) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(format) }
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    format.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    ".${format.extension}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                TagStrip(format.tags)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                format.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * The words beside a format.
 *
 * Quality tags carry the accent colour and size tags do not, so the two are told apart at a
 * glance without anybody reading them.
 */
@Composable
private fun TagStrip(tags: List<AudioTag>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { tag ->
            val loud = tag in LOUD_TAGS
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = if (loud) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            ) {
                Text(
                    tag.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (loud) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ── Helpers ──

private val COMMON = listOf(AudioFormats.OPUS, AudioFormats.AAC, AudioFormats.MP3)

private val LOUD_TAGS = setOf(AudioTag.BEST, AudioTag.RECOMMENDED, AudioTag.LOSSLESS)

/**
 * Every video type, plus the generic one.
 *
 * A document provider decides what type it reports for what it holds, and a container it
 * does not recognise, which regularly means Matroska or a transport stream, comes back as
 * a generic stream of bytes. Filtering on video alone leaves those greyed out in the
 * picker, so the file the user came to convert is the one they cannot select.
 */
private val PICKER_TYPES = arrayOf("video/*", "application/octet-stream")

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
