package com.hazel.android.ui.screens.gallery

import android.app.Application
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryRequest
import com.hazel.android.gallery.GalleryRequests
import com.hazel.android.ui.components.EmptyState
import com.hazel.android.ui.components.HazelLoadingIndicator
import com.hazel.android.ui.screens.download.DownloadAllButton
import com.hazel.android.ui.screens.download.PasteButton
import com.hazel.android.ui.screens.download.SearchOptionsSheet
import com.hazel.android.ui.screens.download.SearchScreen
import com.hazel.android.ui.screens.download.UrlSearchBar
import com.hazel.android.ui.screens.download.rememberClipStamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A line under the search bar, kept as a resource so it follows the app's language. */
data class GalleryNotice(@param:StringRes val text: Int, val args: List<Any> = emptyList())

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    var query by mutableStateOf("")
        private set
    var items by mutableStateOf<List<GalleryItem>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<GalleryNotice?>(null)
        private set

    private var job: Job? = null
    private var source = ""

    fun handle(request: GalleryRequest) {
        job?.cancel()
        when (request) {
            is GalleryRequest.Links -> {
                val link = request.urls.firstOrNull() ?: return
                query = link
                launchRun {
                    show(link, GalleryDlEngine.list(getApplication(), link).items)
                }
            }
            is GalleryRequest.Search -> {
                query = request.query
                launchRun {
                    val url = request.site.urlFor(request.query)
                    val limit = SettingsRepository.getGalleryPostsPerSearch(getApplication()).first()
                    show(url, GalleryDlEngine.list(getApplication(), url, postLimit = limit).items)
                }
            }
        }
    }

    fun download(index: Int?) {
        val link = source.takeIf { it.isNotEmpty() } ?: return
        launchRun {
            val started = System.currentTimeMillis()
            val saved = GalleryDlEngine.download(getApplication(), link, index?.let(::listOf))
            notice = GalleryNotice(R.string.gallery_saved, listOf(saved, secondsSince(started)))
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        job?.cancel()
        items = emptyList()
        notice = null
        query = ""
        source = ""
    }

    private fun show(url: String, found: List<GalleryItem>) {
        source = url
        items = found
        notice = if (found.isEmpty()) GalleryNotice(R.string.gallery_empty) else null
    }

    private fun launchRun(block: suspend () -> Unit) {
        busy = true
        notice = null
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GalleryDlException) {
                notice = GalleryNotice(messageFor(e.kind))
            } catch (e: Exception) {
                notice = GalleryNotice(R.string.gallery_failed)
            } finally {
                busy = false
            }
        }
    }

    private fun secondsSince(start: Long): String = "%.1f".format((System.currentTimeMillis() - start) / 1000f)

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
fun GalleryScreen(
    onVideoLinks: (List<String>) -> Unit,
    onVideoSearch: (String, SearchSource) -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        GalleryRequests.next.collect { if (it != null) GalleryRequests.take()?.let(viewModel::handle) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(8.dp))
            UrlSearchBar(
                url = viewModel.query,
                onOpenSearch = { searchOpen = true },
                onMenuOpen = { menuOpen = true },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                viewModel.busy && viewModel.items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HazelLoadingIndicator(size = 48.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = viewModel::stop) { Text(stringResource(R.string.gallery_stop)) }
                    }
                }
                viewModel.items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = painterResource(R.drawable.gallery_tab),
                        title = viewModel.notice?.let { stringResource(it.text, *it.args.toTypedArray()) }
                            ?: stringResource(R.string.gallery_empty_title),
                        hint = stringResource(R.string.gallery_empty_hint)
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    viewModel.notice?.let { notice ->
                        item(key = "notice") {
                            Text(
                                stringResource(notice.text, *notice.args.toTypedArray()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    itemsIndexed(viewModel.items, key = { _, it -> it.index }) { position, item ->
                        GalleryCard(
                            item = item,
                            total = viewModel.items.size,
                            onOpen = { viewing = position },
                            onView = { viewing = position }
                        )
                    }
                }
            }
        }

        val clipStamp = rememberClipStamp()
        var usedClipStamp by rememberSaveable { mutableStateOf<Long?>(null) }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = clipStamp != null && clipStamp != usedClipStamp && !viewModel.busy,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                PasteButton(
                    stamp = clipStamp,
                    onClick = {
                        usedClipStamp = clipStamp
                        scope.launch {
                            val links = clipboard.getClipEntry()?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                .split(Regex("""\s+""")).filter { it.startsWith("http") }.distinct()
                            if (links.isEmpty()) {
                                Toast.makeText(context, R.string.search_nothing_to_paste, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.handle(GalleryRequest.Links(links))
                            }
                        }
                    }
                )
            }
            if (viewModel.items.size > 1 && !viewModel.busy) {
                DownloadAllButton(onClick = { viewModel.download(null) })
            }
        }
    }

    if (searchOpen) {
        SearchScreen(
            initialQuery = "",
            imagesFirst = true,
            onSearch = { links ->
                searchOpen = false
                onVideoLinks(links)
            },
            onSearchWords = { query, source ->
                searchOpen = false
                onVideoSearch(query, source)
            },
            onSearchImages = { request ->
                searchOpen = false
                viewModel.handle(request)
            },
            onClearResults = viewModel::clear,
            onDismiss = { searchOpen = false }
        )
    }

    viewing?.let { start ->
        GalleryViewer(
            items = viewModel.items,
            startIndex = start,
            onDownload = { item -> viewModel.download(item.index) },
            onDismiss = { viewing = null }
        )
    }

    if (menuOpen) {
        SearchOptionsSheet(
            onClearResults = viewModel::clear,
            onDismiss = { menuOpen = false },
            imagesFirst = true
        )
    }
}
