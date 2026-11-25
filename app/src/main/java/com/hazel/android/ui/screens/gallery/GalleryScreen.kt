package com.hazel.android.ui.screens.gallery

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.ui.components.ExpressiveTextField
import com.hazel.android.ui.components.HazelLoadingIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    var url by mutableStateOf("")
    var items by mutableStateOf<List<GalleryItem>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private var job: Job? = null
    private var postUrl = ""

    fun fetch() {
        val link = url.trim().takeIf { it.isNotEmpty() } ?: return
        launchRun {
            items = emptyList()
            val started = System.currentTimeMillis()
            val found = GalleryDlEngine.list(getApplication(), link).items
            postUrl = link
            items = found
            status = if (found.isEmpty()) app().getString(R.string.gallery_empty)
            else app().getString(R.string.gallery_found, found.size, elapsedSince(started))
        }
    }

    fun download(index: Int?) {
        val link = postUrl.takeIf { it.isNotEmpty() } ?: return
        launchRun {
            val started = System.currentTimeMillis()
            val saved = GalleryDlEngine.download(getApplication(), link, index?.let(::listOf))
            status = app().getString(R.string.gallery_saved, saved, elapsedSince(started))
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun launchRun(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = null
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GalleryDlException) {
                status = app().getString(messageFor(e.kind))
            } catch (e: Exception) {
                status = app().getString(R.string.gallery_failed)
            } finally {
                busy = false
            }
        }
    }

    private fun app(): Application = getApplication()

    private fun elapsedSince(start: Long): String =
        "%.1f".format((System.currentTimeMillis() - start) / 1000f)

    private fun messageFor(kind: GalleryDlException.Kind): Int = when (kind) {
        GalleryDlException.Kind.LOGIN_REQUIRED -> R.string.gallery_error_login
        GalleryDlException.Kind.UNSUPPORTED -> R.string.gallery_error_unsupported
        GalleryDlException.Kind.NOT_FOUND -> R.string.gallery_error_not_found
        GalleryDlException.Kind.NETWORK -> R.string.gallery_error_network
        GalleryDlException.Kind.SETUP -> R.string.gallery_error_setup
        GalleryDlException.Kind.OTHER -> R.string.gallery_failed
    }
}

@Composable
fun GalleryScreen(viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveTextField(
            value = viewModel.url,
            onValueChange = { viewModel.url = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            placeholder = { Text(stringResource(R.string.gallery_paste_hint)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { pasted(context)?.let { viewModel.url = it } }) {
                    Icon(painterResource(R.drawable.ic_paste), contentDescription = null)
                }
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (viewModel.busy) {
                FilledTonalButton(onClick = viewModel::stop) { Text(stringResource(R.string.gallery_stop)) }
            } else {
                Button(onClick = viewModel::fetch) { Text(stringResource(R.string.gallery_get)) }
                if (viewModel.items.isNotEmpty()) {
                    FilledTonalButton(onClick = { viewModel.download(null) }) {
                        Text(stringResource(R.string.gallery_download_all))
                    }
                }
            }
        }
        if (viewModel.busy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { HazelLoadingIndicator() }
        }
        viewModel.status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.items, key = { it.index }) { item ->
                GalleryRow(item = item, enabled = !viewModel.busy, onDownload = { viewModel.download(item.index) })
            }
        }
    }
}

@Composable
private fun GalleryRow(item: GalleryItem, enabled: Boolean, onDownload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Column(Modifier.weight(1f)) {
            Text("#${item.index}", style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (item.isVideo) R.string.gallery_video else R.string.gallery_image) +
                    " · " + item.extension.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(onClick = onDownload, enabled = enabled) { Text(stringResource(R.string.gallery_download)) }
    }
}

private fun pasted(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}
