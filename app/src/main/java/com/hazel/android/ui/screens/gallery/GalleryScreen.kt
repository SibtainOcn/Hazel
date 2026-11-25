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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.gallery.GalleryOptions
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
import com.hazel.android.ui.screens.queue.FailureLogSheet
import com.hazel.android.util.copyToClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A line under the search bar, kept as a resource so it follows the app's language. */
data class GalleryNotice(@param:StringRes val text: Int, val args: List<Any> = emptyList())

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    var query by mutableStateOf("")
        private set
    var posts by mutableStateOf<List<GalleryPost>>(emptyList())
        private set
    /** The post opened from the list of several; null shows the list. */
    var openPost by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<GalleryNotice?>(null)
        private set
    /** The last failed run, for the log sheet. */
    var failure by mutableStateOf<FailedDownload?>(null)
        private set

    /** The post shown as cards: the only one, or the one opened from the list. */
    val post: GalleryPost? get() = posts.singleOrNull() ?: openPost?.let(posts::getOrNull)
    val items: List<GalleryItem> get() = post?.items.orEmpty()
    val showsList: Boolean get() = posts.size > 1 && post == null

    /** Whether the results came from a search, where each post is a result of its own. */
    var fromSearch by mutableStateOf(false)
        internal set

    /** Download all belongs to one post with several files, never to a page of search results. */
    val offersDownloadAll: Boolean get() = !showsList && items.size > 1 && (openPost != null || !fromSearch)

    private var job: Job? = null
    private var lastRun: (() -> Unit)? = null

    fun handle(request: GalleryRequest) {
        job?.cancel()
        lastRun = { handle(request) }
        posts = emptyList()
        openPost = null
        when (request) {
            is GalleryRequest.Links -> {
                fromSearch = false
                if (request.urls.isEmpty()) return
                query = request.urls.joinToString(" ")
                launchRun(request.urls.first()) { readLinks(request.urls) }
            }
            is GalleryRequest.Search -> {
                fromSearch = true
                query = request.query
                val url = request.site.urlFor(request.query)
                launchRun(url) {
                    val limit = SettingsRepository.getGalleryPostsPerSearch(getApplication()).first()
                    show(GalleryPost.group(url, GalleryDlEngine.list(getApplication(), url, postLimit = limit).items))
                }
            }
        }
    }

    /** Each link in turn, its posts shown as they land. A failed link is skipped while others remain. */
    private suspend fun readLinks(urls: List<String>) {
        var failure: GalleryDlException? = null
        urls.forEach { url ->
            try {
                posts = posts + GalleryPost.group(url, GalleryDlEngine.list(getApplication(), url).items)
            } catch (e: GalleryDlException) {
                if (urls.size == 1) throw e
                failure = e
            }
        }
        if (posts.isEmpty()) failure?.let { throw it }
        show(posts)
    }

    fun open(index: Int) {
        openPost = index.takeIf { it in posts.indices }
    }

    fun closePost() {
        openPost = null
    }

    /** Saves the files of [post] at [indexes] (counted from 1), or the whole post when null. */
    fun download(indexes: List<Int>?, options: GalleryOptions? = null, post: GalleryPost? = this.post) {
        val link = post?.source?.takeIf { it.isNotEmpty() } ?: return
        lastRun = { download(indexes, options, post) }
        launchRun(link) {
            val started = System.currentTimeMillis()
            val saved = GalleryDlEngine.download(getApplication(), link, indexes, options)
            notice = GalleryNotice(R.string.gallery_saved, listOf(saved, secondsSince(started)))
        }
    }

    fun stop() {
        job?.cancel()
    }

    /** Runs the failed request again. */
    fun retry() {
        lastRun?.invoke()
    }

    fun clear() {
        job?.cancel()
        posts = emptyList()
        openPost = null
        notice = null
        failure = null
        lastRun = null
        query = ""
    }

    internal fun show(found: List<GalleryPost>) {
        posts = found
        notice = if (found.isEmpty()) GalleryNotice(R.string.gallery_empty) else null
    }

    private fun launchRun(url: String, block: suspend () -> Unit) {
        busy = true
        notice = null
        failure = null
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(url, e)
            } finally {
                busy = false
            }
        }
    }

    internal fun fail(url: String, e: Exception) {
        val log = (e as? GalleryDlException)?.let { it.log.ifBlank { it.detail } } ?: e.message.orEmpty()
        notice = GalleryNotice((e as? GalleryDlException)?.let { messageFor(it.kind) } ?: R.string.gallery_failed)
        failure = FailedDownload(url = url, title = query, author = "", thumbnail = null, isVideo = false, errorLog = log)
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
    // Post index and file position, so a file of any post on a list of several can be opened.
    var viewing by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    var logOpen by rememberSaveable { mutableStateOf(false) }
    var sheetFor by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    var postSheetFor by rememberSaveable { mutableStateOf<Int?>(null) }

    val currentPost = viewModel.posts.indexOf(viewModel.post)

    LaunchedEffect(Unit) {
        GalleryRequests.next.collect { if (it != null) GalleryRequests.take()?.let(viewModel::handle) }
    }
    BackHandler(enabled = viewModel.posts.size > 1 && viewModel.openPost != null, onBack = viewModel::closePost)

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
                viewModel.busy && viewModel.posts.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HazelLoadingIndicator(size = 48.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(onClick = viewModel::stop) { Text(stringResource(R.string.gallery_stop)) }
                    }
                }
                viewModel.posts.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyState(
                            icon = painterResource(R.drawable.gallery_tab),
                            title = viewModel.notice?.let { stringResource(it.text, *it.args.toTypedArray()) }
                                ?: stringResource(R.string.gallery_empty_title),
                            hint = stringResource(R.string.gallery_empty_hint).takeIf { viewModel.failure == null }
                        )
                        if (viewModel.failure != null) {
                            TextButton(onClick = { logOpen = true }) { Text(stringResource(R.string.gallery_view_log)) }
                        }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    viewModel.notice?.let { notice ->
                        item(key = "notice") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(notice.text, *notice.args.toTypedArray()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (viewModel.failure != null) {
                                    TextButton(onClick = { logOpen = true }) { Text(stringResource(R.string.gallery_view_log)) }
                                }
                            }
                        }
                    }
                    if (viewModel.showsList) {
                        itemsIndexed(viewModel.posts, key = { i, _ -> "post-$i" }) { i, post ->
                            val single = post.items.singleOrNull()
                            if (single != null) {
                                GalleryCard(
                                    item = single,
                                    total = 1,
                                    onOpen = { sheetFor = i to 0 },
                                    onView = { viewing = i to 0 }
                                )
                            } else {
                                GalleryPostCard(
                                    post = post,
                                    onOpen = { viewModel.open(i) },
                                    onDownloadAll = { postSheetFor = i }
                                )
                            }
                        }
                    } else {
                        if (viewModel.posts.size > 1) {
                            item(key = "back") {
                                TextButton(onClick = viewModel::closePost) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.gallery_all_posts))
                                }
                            }
                        }
                        itemsIndexed(viewModel.items, key = { _, it -> it.index }) { position, item ->
                            GalleryCard(
                                item = item,
                                total = viewModel.items.size,
                                onOpen = { sheetFor = currentPost to position },
                                onView = { viewing = currentPost to position }
                            )
                        }
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
            if (viewModel.offersDownloadAll && !viewModel.busy) {
                DownloadAllButton(onClick = { postSheetFor = currentPost.takeIf { it >= 0 } })
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

    viewing?.let { (postIndex, start) ->
        viewModel.posts.getOrNull(postIndex)?.let { post ->
            GalleryViewer(
                items = post.items,
                startIndex = start,
                onDownload = { item -> viewModel.download(listOf(item.index), post = post) },
                onDismiss = { viewing = null }
            )
        }
    }

    viewModel.failure?.takeIf { logOpen }?.let { item ->
        FailureLogSheet(
            item = item,
            onCopy = {
                copyToClipboard(context, item.errorLog)
                Toast.makeText(context, R.string.history_failed_log_copied, Toast.LENGTH_SHORT).show()
            },
            onCopyUrl = {
                copyToClipboard(context, item.url)
                Toast.makeText(context, R.string.sheet_link_copied, Toast.LENGTH_SHORT).show()
            },
            onRetry = {
                logOpen = false
                viewModel.retry()
            },
            onDismiss = { logOpen = false }
        )
    }

    // New results close what was open on the old ones.
    var postsShown by remember { mutableStateOf(viewModel.posts) }
    LaunchedEffect(viewModel.posts) {
        if (postsShown !== viewModel.posts) {
            postsShown = viewModel.posts
            sheetFor = null
            viewing = null
            postSheetFor = null
        }
    }

    sheetFor?.let { (postIndex, position) ->
        val post = viewModel.posts.getOrNull(postIndex) ?: return@let
        val item = post.items.getOrNull(position) ?: return@let
        GalleryItemSheet(
            item = item,
            total = post.items.size,
            postUrl = post.source,
            onDownload = { options ->
                sheetFor = null
                viewModel.download(listOf(item.index), options, post)
            },
            onDismiss = { sheetFor = null }
        )
    }

    postSheetFor?.let(viewModel.posts::getOrNull)?.let { post ->
        GalleryPostSheet(
            items = post.items,
            postUrl = post.source,
            onDownload = { indexes, options ->
                postSheetFor = null
                viewModel.download(indexes, options, post)
            },
            onDismiss = { postSheetFor = null }
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
