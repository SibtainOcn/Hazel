package com.hazel.android.ui.screens.download

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.R
import com.hazel.android.data.SaveDir
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.components.AlertDialog
import com.hazel.android.ui.components.SoftIconButton
import com.hazel.android.ui.theme.glassScreen
import com.hazel.android.util.FolderCheck
import com.hazel.android.util.FolderSize
import com.hazel.android.util.FolderUtil
import com.hazel.android.util.SdCard
import com.hazel.android.util.SdCards
import com.hazel.android.util.StoragePaths
import com.hazel.android.util.StorageSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where one kind of download (or the backups) is saved, as a screen of its own: internal
 * storage, each SD card that is in, any other folder, and the folders picked most recently
 * anywhere in the app. Tapping a recent folder saves there at once; holding any location
 * opens it in a file manager.
 *
 * Internal storage and each card show how full they are, refreshed while the screen is open.
 * A recent folder that is gone, or that Hazel can no longer write to, is left off the list,
 * and one that goes missing between the list and the tap is reported and dropped from it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SaveLocationScreen(
    isVideo: Boolean,
    saveDir: SaveDir,
    label: String,
    onOpen: () -> Unit,
    /** Given the card to save to, or null to pick any folder. */
    onPick: (card: SdCard?) -> Unit,
    onReset: () -> Unit,
    /** Saves to a recent folder; the screen closes once it returns. */
    onUseRecent: suspend (SaveDir) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.format_sheet_save_location_title),
    body: String? = null,
    internalPath: String = StoragePaths.downloadsDisplay(isAudio = !isVideo),
    internalFolder: () -> File = { StoragePaths.finalDownloads(isAudio = !isVideo) },
    cardFolder: String = "Hazel/" + SdCards.kindFolderName(isVideo),
    /** Whether a picked folder can still be saved into. Blocking; run off the main thread. */
    usable: (Context, String) -> Boolean = FolderCheck::usable
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var problem by remember { mutableStateOf<Int?>(null) }

    val onCard = remember(saveDir.uri) { SdCards.cardOf(saveDir.uri, context) }
    val folderBytes by produceState<Long?>(null, saveDir.uri, onCard) {
        if (saveDir.isCustom && onCard == null) {
            value = withContext(Dispatchers.IO) { runCatching { FolderSize.of(context, saveDir.uri) }.getOrNull() }
        }
    }
    val firstCards = remember { SdCards.list(context) }
    val volumes by produceState(Volumes(null, firstCards.map { it to null })) {
        while (true) {
            withContext(Dispatchers.IO) { runCatching { Volumes.read(context) }.getOrNull() }?.let { value = it }
            delay(SPACE_REFRESH_MS)
        }
    }
    val stored by remember(context) { SettingsRepository.getRecentFolders(context) }
        .collectAsState(initial = emptyList())
    // Checked off the main thread: each one asks its document provider.
    val recents by produceState(emptyList<Recent>(), stored) {
        value = withContext(Dispatchers.IO) {
            stored.filter { usable(context, it.uri) }
                .map { Recent(it, SdCards.cardOf(it.uri, context) != null) }
        }
    }

    // Checks the folder is still usable before acting on it; when it is not, says so and
    // takes it off the list.
    fun withRecent(dir: SaveDir, action: suspend () -> Unit) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { usable(context, dir.uri) }
            if (ok) {
                action()
            } else {
                runCatching { SettingsRepository.removeRecentFolders(context, setOf(dir.uri)) }
                problem = R.string.save_location_recent_missing
            }
        }
    }
    fun hold(open: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        open()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize().glassScreen(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SoftIconButton(onClick = onDismiss, corner = 22.dp, modifier = Modifier.padding(start = 8.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = stringResource(R.string.settings_back),
                            modifier = Modifier.size(20.dp).padding(start = 4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialShapes.Cookie7Sided.toShape())
                                .background(MaterialTheme.colorScheme.onSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.save_location_hold_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        if (body != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    val places = 2 + volumes.cards.size
                    Column(verticalArrangement = Arrangement.spacedBy(LOCATION_GAP)) {
                        val internal = volumes.internal
                        LocationRow(
                            icon = Icons.Filled.PhoneAndroid,
                            title = stringResource(R.string.save_location_internal),
                            subtitle = internalPath,
                            detail = internal?.let { freeLabel(it) },
                            usedFraction = internal?.usedFraction,
                            selected = !saveDir.isCustom,
                            shape = locationShape(0, places),
                            onClick = onReset,
                            onLongClick = { hold { FolderUtil.open(context, internalFolder()) } }
                        )
                        volumes.cards.forEachIndexed { index, (card, space) ->
                            val selected = onCard?.uuid == card.uuid
                            LocationRow(
                                icon = Icons.Filled.SdCard,
                                title = card.name,
                                subtitle = if (selected) label
                                else stringResource(R.string.save_location_card_hint, cardFolder),
                                detail = space?.let { freeLabel(it) },
                                usedFraction = space?.usedFraction,
                                selected = selected,
                                shape = locationShape(index + 1, places),
                                onClick = { onPick(card) },
                                onLongClick = {
                                    hold {
                                        if (selected) FolderUtil.openTree(context, android.net.Uri.parse(saveDir.uri))
                                        else FolderUtil.openVolume(context, card.uuid)
                                    }
                                }
                            )
                        }
                        val other = saveDir.isCustom && onCard == null
                        LocationRow(
                            icon = Icons.Filled.CreateNewFolder,
                            title = stringResource(R.string.save_location_other),
                            subtitle = if (other) label else stringResource(R.string.save_location_other_hint),
                            detail = folderBytes?.let {
                                stringResource(R.string.save_location_folder_uses, Formatter.formatShortFileSize(context, it))
                            },
                            selected = other,
                            shape = locationShape(places - 1, places),
                            onClick = { onPick(null) },
                            onLongClick = if (other) {
                                { hold { FolderUtil.openTree(context, android.net.Uri.parse(saveDir.uri)) } }
                            } else null
                        )
                    }

                    if (recents.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 20.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.save_location_recent),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { scope.launch { runCatching { SettingsRepository.clearRecentFolders(context) } } }) {
                                Text(stringResource(R.string.save_location_recent_clear), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(LOCATION_GAP)) {
                            recents.forEachIndexed { index, recent ->
                                val dir = recent.dir
                                LocationRow(
                                    icon = Icons.Filled.History,
                                    title = dir.label.ifBlank { dir.uri },
                                    tag = stringResource(
                                        if (recent.onCard) R.string.save_location_tag_card else R.string.save_location_tag_internal
                                    ),
                                    selected = dir.uri == saveDir.uri,
                                    compact = true,
                                    shape = locationShape(index, recents.size),
                                    onClick = {
                                        withRecent(dir) {
                                            runCatching { onUseRecent(dir) }
                                                .onSuccess { onDismiss() }
                                                .onFailure { problem = R.string.save_location_error_access }
                                        }
                                    },
                                    onLongClick = {
                                        hold { withRecent(dir) { FolderUtil.openTree(context, android.net.Uri.parse(dir.uri)) } }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Side by side where both fit; a long language at a large font puts them on
                // lines of their own rather than breaking a word.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.save_location_close), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = onOpen) {
                        Text(
                            stringResource(R.string.format_sheet_open_folder),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        problem?.let { message ->
            AlertDialog(
                onDismissRequest = { problem = null },
                icon = { Icon(Icons.Filled.ErrorOutline, null) },
                title = { Text(stringResource(R.string.save_location_error_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(message)) },
                confirmButton = {
                    TextButton(onClick = { problem = null }) { Text(stringResource(R.string.save_location_close)) }
                }
            )
        }
    }
}

private class Recent(val dir: SaveDir, val onCard: Boolean)

/** Internal storage and the cards that are in right now, each with how much room it has. */
private class Volumes(val internal: StorageSpace?, val cards: List<Pair<SdCard, StorageSpace?>>) {
    companion object {
        fun read(context: Context) = Volumes(
            StorageSpace.internal(context),
            SdCards.list(context).map { it to it.space() }
        )
    }
}

private const val SPACE_REFRESH_MS = 2_000L
private const val NEARLY_FULL = 0.9f
private val LOCATION_GAP = 3.dp
private val LOCATION_OUTER = 22.dp
private val LOCATION_INNER = 8.dp

/** Rows as one connected group: round at the outer ends, tight between. */
private fun locationShape(index: Int, count: Int) = RoundedCornerShape(
    topStart = if (index == 0) LOCATION_OUTER else LOCATION_INNER,
    topEnd = if (index == 0) LOCATION_OUTER else LOCATION_INNER,
    bottomStart = if (index == count - 1) LOCATION_OUTER else LOCATION_INNER,
    bottomEnd = if (index == count - 1) LOCATION_OUTER else LOCATION_INNER
)

@Composable
private fun freeLabel(space: StorageSpace): String = stringResource(
    R.string.save_location_free,
    Formatter.formatShortFileSize(LocalContext.current, space.freeBytes)
)

/**
 * One place to save to, as a flat surface in a connected group. Its icon sits on a soft
 * cookie; the one in use is tinted and swaps its tag for a check on a cookie of its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocationRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    subtitle: String? = null,
    tag: String? = null,
    detail: String? = null,
    usedFraction: Float? = null,
    compact: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    val fill = if (selected) scheme.primary.copy(alpha = PICKED_TINT).compositeOver(base) else base
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 34.dp else 42.dp)
                .clip((if (compact) MaterialShapes.Cookie6Sided else MaterialShapes.Cookie7Sided).toShape())
                .background(scheme.onSurface.copy(alpha = 0.12f).compositeOver(fill)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 18.dp else 20.dp), tint = scheme.onSurface)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                fontWeight = if (compact) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (usedFraction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearWavyProgressIndicator(
                    progress = { usedFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (usedFraction >= NEARLY_FULL) scheme.error else scheme.onSurface,
                    trackColor = scheme.onSurface.copy(alpha = 0.12f),
                    // A long, low wave: it reads as calm rather than as a zigzag.
                    amplitude = { WavyProgressIndicatorDefaults.indicatorAmplitude(it) * 0.45f },
                    wavelength = 56.dp,
                    waveSpeed = 0.dp
                )
            }
            if (detail != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(detail, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface.copy(alpha = 0.55f))
            }
        }
        if (selected) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(scheme.onSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = scheme.inverseOnSurface)
            }
        } else if (tag != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                tag,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = TAG_MAX_WIDTH)
                    .clip(CircleShape)
                    .background(scheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

private const val PICKED_TINT = 0.18f

/** A volume tag never takes more than this from the folder's own name. */
private val TAG_MAX_WIDTH = 80.dp
