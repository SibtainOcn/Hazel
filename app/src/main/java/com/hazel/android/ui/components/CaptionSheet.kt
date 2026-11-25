package com.hazel.android.ui.components

import com.hazel.android.ui.theme.sheetButtonColor
import androidx.compose.material3.ButtonDefaults
import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hazel.android.R
import com.hazel.android.gallery.GalleryDlEngine
import kotlinx.coroutines.launch

/**
 * A post's full caption: opens at its natural height, drags up to full, scrolls when long and
 * can be selected. Copy puts it on the clipboard; Save as text writes it to the gallery folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionSheet(caption: String, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(
                stringResource(R.string.gallery_caption),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
            ) {
                SelectionContainer {
                    Text(
                        caption,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        scope.launch { clipboard.setClipEntry(ClipData.newPlainText(title, caption).toClipEntry()) }
                        Toast.makeText(context, R.string.properties_copied, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = sheetButtonColor, contentColor = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.caption_copy), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        scope.launch {
                            val saved = GalleryDlEngine.saveCaptionText(context.applicationContext, title.ifBlank { caption.take(40) }, caption)
                            Toast.makeText(context, if (saved) R.string.caption_saved else R.string.caption_save_failed, Toast.LENGTH_SHORT).show()
                            saving = false
                        }
                    },
                    enabled = !saving,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.caption_save_text), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
