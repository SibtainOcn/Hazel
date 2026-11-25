package com.hazel.android.ui.screens.gallery

import android.app.Application
import kotlinx.coroutines.Dispatchers
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.HazelApp
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import com.hazel.android.ui.components.gridItems
import com.hazel.android.ui.components.gridColumns
import androidx.compose.foundation.layout.BoxWithConstraints
import com.hazel.android.ui.components.rememberScrollShrink
import com.hazel.android.ui.components.SkeletonSpacing
import com.hazel.android.ui.components.ArrivingCard
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.gallery.GalleryRequest
import com.hazel.android.gallery.GalleryRequests
import com.hazel.android.gallery.queue.request
import com.hazel.android.gallery.queue.GalleryQueue
import com.hazel.android.ui.components.BetaTag
import com.hazel.android.ui.components.EmptyState
import com.hazel.android.ui.components.ResultsSkeleton
import com.hazel.android.ui.screens.download.DownloadAllButton
import com.hazel.android.ui.screens.download.FailureKind
import com.hazel.android.ui.screens.download.NoResultsDialog
import com.hazel.android.ui.screens.download.PasteButton
import com.hazel.android.ui.screens.download.SearchOptionsSheet
import com.hazel.android.ui.screens.download.SearchScreen
import com.hazel.android.ui.screens.download.UrlSearchBar
import com.hazel.android.ui.screens.download.rememberClipStamp
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.screens.queue.FailureLogSheet
import com.hazel.android.util.copyToClipboard
import com.hazel.android.util.siteRootOf
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
    var busy by mutableStateOf(false)
        internal set
    var notice by mutableStateOf<GalleryNotice?>(null)
        private set
    /** The last failed run, for the log sheet. */
    var failure by mutableStateOf<FailedDownload?>(null)
        private set
    /** What the last failure was, so its dialog offers the right way out (such as signing in). */
    var failureKind by mutableStateOf<GalleryDlException.Kind?>(null)
        private set
    /** The failure dialog, shown once per failure, as Home shows a link it could not read. */
    var failureDialog by mutableStateOf(false)
        private set

    fun dismissFailureDialog() {
        failureDialog = false
    }

    /** The post shown as cards: the only one, or the one opened from the list. */
    val post: GalleryPost? get() = posts.singleOrNull()
    val items: List<GalleryItem> get() = post?.items.orEmpty()
    val showsList: Boolean get() = posts.size > 1

    /** Whether the results came from a search, where each post is a result of its own. */
    var fromSearch by mutableStateOf(false)
        internal set

    /** Download all belongs to one post with several files, never to a page of search results. */
    val offersDownloadAll: Boolean get() = !showsList && items.size > 1 && !fromSearch

    private var job: Job? = null
    private var lastRun: (() -> Unit)? = null

    fun handle(request: GalleryRequest) {
        job?.cancel()
        lastRun = { handle(request) }
        posts = emptyList()
        recordInHistory(request)
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
                    val found = GalleryPost.group(url, GalleryDlEngine.list(getApplication(), url, postLimit = limit).items, feed = true)
                    val byAuthor = SettingsRepository.getGalleryGroupByAuthor(getApplication()).first()
                    show(if (byAuthor) GalleryPost.byAuthor(found) else found)
                }
            }
        }
    }

    /** Each link in turn, its posts shown as they land. A failed link is skipped while others remain. */
    private suspend fun readLinks(urls: List<String>) {
        var failure: GalleryDlException? = null
        val limit = SettingsRepository.getGalleryPostsPerSearch(getApplication()).first()
        urls.forEach { url ->
            try {
                posts = posts + GalleryPost.group(url, GalleryDlEngine.list(getApplication(), url, postLimit = limit).items)
            } catch (e: GalleryDlException) {
                if (urls.size == 1) throw e
                failure = e
            }
        }
        if (posts.isEmpty()) failure?.let { throw it }
        show(posts)
    }

    /** Queues the files of [post] at [indexes] (counted from 1), or the whole post when null. */
    fun download(
        indexes: List<Int>?,
        options: GalleryOptions? = null,
        post: GalleryPost? = this.post,
        title: String? = null,
        author: String? = null,
        save: com.hazel.android.gallery.GallerySave = com.hazel.android.gallery.GallerySave.MEDIA
    ) {
        if (post == null || post.source.isEmpty()) return
        val request = post.request(indexes, options, title, author, save)
        viewModelScope.launch { GalleryQueue.add(getApplication(), request) }
    }


    /** Saves what was asked to the shared search history up front, so a read that fails is still there. */
    private fun recordInHistory(request: GalleryRequest) {
        val app = getApplication<Application>()
        val entries = when (request) {
            is GalleryRequest.Links -> request.urls
            is GalleryRequest.Search -> listOf(request.query)
        }
        HazelApp.instance.applicationScope.launch(Dispatchers.IO) {
            if (!SettingsRepository.getIncognito(app).first()) entries.forEach { SearchHistoryRepository.record(app, it) }
        }
    }

    /** Runs the failed request again. */
    fun retry() {
        lastRun?.invoke()
    }

    fun clear() {
        job?.cancel()
        posts = emptyList()
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
        notice = GalleryNotice((e as? GalleryDlException)?.let { it.kind.message } ?: R.string.gallery_failed)
        failure = FailedDownload(url = url, title = query, author = "", thumbnail = null, isVideo = false, errorLog = log)
        failureKind = (e as? GalleryDlException)?.kind ?: GalleryDlException.Kind.OTHER
        failureDialog = true
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
    val galleryJobs by GalleryQueue.jobs.collectAsState()
    val galleryFinished by GalleryQueue.finished.collectAsState()
    var logOpen by rememberSaveable { mutableStateOf(false) }
    var sheetFor by rememberSaveable { mutableStateOf<Pair<Int, Int>?>(null) }
    var postSheetFor by rememberSaveable { mutableStateOf<Int?>(null) }

    val currentPost = viewModel.posts.indexOf(viewModel.post)

    val searchBarShine = remember { Animatable(0f) }
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.busy) {
        if (viewModel.busy) {
            wasBusy = true
        } else if (wasBusy) {
            wasBusy = false
            searchBarShine.snapTo(0f)
            searchBarShine.animateTo(1f, tween(durationMillis = 850, easing = FastOutSlowInEasing))
        }
    }
    // A video in a post is read by yt-dlp and opens the video sheet on Home.
    val openItem: (Int, Int) -> Unit = { postIndex, position ->
        val post = viewModel.posts.getOrNull(postIndex)
        val item = post?.items?.getOrNull(position)
        if (item != null && item.isVideo) onVideoLinks(listOf(item.postUrl ?: post.source))
        else sheetFor = postIndex to position
    }

    LaunchedEffect(Unit) {
        com.hazel.android.gallery.GallerySpare.warm(context)
        GalleryRequests.next.collect { if (it != null) GalleryRequests.take()?.let(viewModel::handle) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(8.dp))
            UrlSearchBar(
                url = viewModel.query,
                onOpenSearch = { searchOpen = true },
                shineProgress = searchBarShine.value,
                onMenuOpen = { menuOpen = true },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            val shrink = rememberScrollShrink()
            when {
                viewModel.busy && viewModel.posts.isEmpty() ->
                    ResultsSkeleton(modifier = Modifier.fillMaxWidth().weight(1f))
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
                            hint = stringResource(R.string.gallery_empty_hint).takeIf { viewModel.failure == null },
                            badge = { BetaTag() }
                        )
                        if (viewModel.failure != null) {
                            TextButton(onClick = { logOpen = true }) { Text(stringResource(R.string.gallery_view_log)) }
                        }
                    }
                }
                // A wide screen lays the cards side by side, as Home and History do.
                else -> BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val columns = gridColumns(maxWidth - 40.dp)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(shrink),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(SkeletonSpacing)
                ) {
                    if (viewModel.showsList) {
                        gridItems(viewModel.posts.indices.toList(), columns, SkeletonSpacing, key = { "post-$it" }) { i ->
                            val post = viewModel.posts[i]
                            val single = post.items.singleOrNull()
                            ArrivingCard(post.source to i, shrink) {
                                if (single != null) {
                                    GalleryCard(
                                        item = single,
                                        total = 1,
                                        status = GalleryQueue.statusOf(galleryJobs, galleryFinished, post, post.items.first()),
                                        onOpen = { openItem(i, 0) },
                                        onView = { viewing = i to 0 }
                                    )
                                } else {
                                    GalleryPostCard(
                                        post = post,
                                        onOpen = { position -> viewing = i to position },
                                        onDownloadAll = { postSheetFor = i }
                                    )
                                }
                            }
                        }
                    } else {
                        gridItems(viewModel.items.indices.toList(), columns, SkeletonSpacing, key = { viewModel.items[it].index }) { position ->
                            val item = viewModel.items[position]
                            ArrivingCard(item.url, shrink) {
                                GalleryCard(
                                    item = item,
                                    total = viewModel.items.size,
                                    status = viewModel.post?.let { GalleryQueue.statusOf(galleryJobs, galleryFinished, it, item) },
                                    onOpen = { openItem(currentPost, position) },
                                    onView = { viewing = currentPost to position }
                                )
                            }
                        }
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
            // A post with several files is browsed on its own; a single result swipes through
            // every result, so the strip and Download reach the rest of the list too.
            val places = remember(viewModel.posts, postIndex) {
                if (post.items.size == 1 && viewModel.posts.size > 1) {
                    viewModel.posts.flatMapIndexed { p, each -> each.items.indices.map { p to it } }
                } else {
                    post.items.indices.map { postIndex to it }
                }
            }
            val items = remember(places) { places.map { (p, i) -> viewModel.posts[p].items[i] } }
            GalleryViewer(
                items = items,
                startIndex = places.indexOf(postIndex to start).coerceAtLeast(0),
                onOpenSheet = { item ->
                    val (p, i) = places.getOrNull(items.indexOfFirst { it === item }) ?: return@GalleryViewer
                    // A video goes to the video sheet on Home, so the viewer closes first.
                    if (item.isVideo) viewing = null
                    openItem(p, i)
                },
                onDismiss = { viewing = null }
            )
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.retry()
    }
    viewModel.failure?.takeIf { viewModel.failureDialog }?.let { item ->
        NoResultsDialog(
            message = item.errorLog,
            canFetchCookies = true,
            canContinue = false,
            canAddCookies = true,
            link = item.url,
            knownKind = viewModel.failureKind?.let(::dialogKind),
            onCopyLog = {
                copyToClipboard(context, item.errorLog)
                viewModel.dismissFailureDialog()
            },
            onGetCookies = {
                viewModel.dismissFailureDialog()
                signInLauncher.launch(CookieWebViewActivity.intent(context, siteRootOf(item.url)))
            },
            onContinueAnyway = {},
            onDismiss = viewModel::dismissFailureDialog
        )
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
            postUrl = post.source,
            onDownload = { options, title, author, save ->
                sheetFor = null
                viewModel.download(listOf(item.index), options, post, title, author, save)
            },
            onDismiss = { sheetFor = null }
        )
    }

    postSheetFor?.let(viewModel.posts::getOrNull)?.let { post ->
        GalleryPostSheet(
            items = post.items,
            postUrl = post.source,
            onDownload = { picks ->
                postSheetFor = null
                picks.forEach { viewModel.download(it.indexes, it.options, post, it.title, it.author) }
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

/** gallery-dl's own reading of a failure, in the terms of the shared failure dialog. */
internal fun dialogKind(kind: GalleryDlException.Kind): FailureKind = when (kind) {
    GalleryDlException.Kind.LOGIN_REQUIRED -> FailureKind.SIGN_IN
    GalleryDlException.Kind.UNSUPPORTED -> FailureKind.UNSUPPORTED
    GalleryDlException.Kind.NOT_FOUND -> FailureKind.GONE
    GalleryDlException.Kind.NETWORK, GalleryDlException.Kind.SETUP, GalleryDlException.Kind.OTHER -> FailureKind.OTHER
}
