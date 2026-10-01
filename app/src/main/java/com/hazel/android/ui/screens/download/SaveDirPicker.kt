package com.hazel.android.ui.screens.download

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.hazel.android.R
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SettingsRepository
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.SdCard
import com.hazel.android.util.SdCards
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Changing where one kind of download is saved: to any folder through the document picker,
 * or to an SD card, with the picker opened on the card. Either way the grant is persisted so
 * the folder stays writable on later launches, and the choice is saved for the kind it was
 * made for until it is changed or reset.
 *
 * The launchers live here, in a composable that stays on screen, rather than in the dialog
 * that asks, since the dialog is gone by the time the picker answers. The kind being picked
 * for survives the activity being recreated while the picker is up. Anything that goes wrong
 * along the way is put in a dialog here rather than leaving the old folder quietly in place.
 *
 * Returns the action to call with the kind (true for video) and the card, or null for any
 * folder.
 */
@Composable
fun rememberSaveDirPicker(saveDirs: SaveDirs): (isVideo: Boolean, card: SdCard?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickingVideo by rememberSaveable { mutableStateOf(true) }
    var pickingCard by rememberSaveable { mutableStateOf(false) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }

    val accessRefused = stringResource(R.string.save_location_error_access)
    val noPicker = stringResource(R.string.save_location_error_no_picker)

    val onPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val forVideo = pickingVideo
                val toCard = pickingCard
                val app = context.applicationContext
                scope.launch(Dispatchers.IO) {
                    // A card's root gets the same Hazel/Audio or Hazel/Video folder the
                    // built-in location uses, rather than downloads piling up at its top.
                    // When the card will not make it, the picked folder is used as it is.
                    val kindFolder = if (toCard) SdCards.kindFolderIn(app, uri, forVideo) else uri
                    val folder = kindFolder ?: uri
                    runCatching {
                        SettingsRepository.setSaveDir(
                            app, forVideo, folder.toString(), MediaStoreHelper.describeTree(folder)
                        )
                    }.onFailure { error ->
                        withContext(Dispatchers.Main) { problem = error.message ?: accessRefused }
                        return@launch
                    }
                    if (kindFolder == null) {
                        val message = app.getString(
                            R.string.save_location_error_card_folder,
                            "Hazel/" + SdCards.kindFolderName(forVideo)
                        )
                        withContext(Dispatchers.Main) { problem = message }
                    }
                }
            } catch (_: SecurityException) {
                // The provider refused a lasting grant, so the folder in use stays as it was.
                problem = accessRefused
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(), onPicked
    )
    val cardPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onPicked(result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK })
    }

    problem?.let { message ->
        AlertDialog(
            onDismissRequest = { problem = null },
            icon = { Icon(Icons.Filled.ErrorOutline, null) },
            title = { Text(stringResource(R.string.save_location_error_title), fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { problem = null }) { Text(stringResource(R.string.save_location_close)) }
            }
        )
    }

    return { isVideo, card ->
        pickingVideo = isVideo
        pickingCard = card != null
        val current = saveDirs.of(isVideo).uri.takeIf { it.isNotBlank() }?.let(Uri::parse)
        val openPlain = {
            runCatching { folderPicker.launch(current) }.onFailure { problem = noPicker }
        }
        if (card != null) {
            // A device whose picker does not answer the card's own intent still has the
            // plain picker, where the card is listed among the places to choose from.
            runCatching { cardPicker.launch(SdCards.pickerIntent(context, card)) }
                .onFailure { openPlain() }
        } else {
            openPlain()
        }
    }
}

/**
 * Tells the user that downloads could not go to the folder they were meant for and were
 * saved somewhere else instead, naming the folder, where they went, and which ones.
 */
@Composable
fun SaveFallbackDialog(
    wanted: String,
    savedTo: String,
    titles: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.ErrorOutline, null) },
        title = { Text(stringResource(R.string.save_fallback_title), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                stringResource(R.string.save_fallback_body, wanted, savedTo) +
                    "\n\n" + titles.take(MAX_LISTED).joinToString("\n") { "• $it" } +
                    if (titles.size > MAX_LISTED) "\n…" else ""
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.save_location_close)) }
        }
    )
}

private const val MAX_LISTED = 5
