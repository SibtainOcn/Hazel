package com.hazel.android.ui.screens.gallery

import android.util.Log
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hazel.android.R
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.extractor.SearchSource
import com.hazel.android.gallery.FileLinks
import com.hazel.android.gallery.GalleryDlEngine
import com.hazel.android.gallery.GalleryDlException
import com.hazel.android.gallery.GalleryItem
import com.hazel.android.gallery.GalleryOptions
import com.hazel.android.gallery.GalleryPost
import com.hazel.android.gallery.GalleryReading
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
import com.hazel.android.ui.screens.download.StatusToastHost
import com.hazel.android.ui.screens.download.rememberStatusToastState
import com.hazel.android.ui.screens.download.SearchScreen
import com.hazel.android.ui.screens.download.UrlSearchBar
import com.hazel.android.ui.screens.download.rememberClipStamp
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.screens.queue.FailureLogSheet
import com.hazel.android.util.copyToClipboard
import com.hazel.android.util.siteRootOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import com.hazel.android.gallery.GalleryResultsStore
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
    /** How many links were pasted; with several, results stay a list of posts from the first one in. */
    val linkCount: Int get() = if (fromSearch) 0 else query.split(' ').count { it.startsWith("http") }
    val showsList: Boolean get() = posts.size > 1 || (linkCount > 1 && posts.isNotEmpty())

    /** Whether the results came from a search, where each post is a result of its own. */
    var fromSearch by mutableStateOf(false)
        internal set

    /** Download all belongs to one post with several files, never to a page of search results. */
    val offersDownloadAll: Boolean get() = !showsList && items.size > 1 && !fromSearch

    private var job: Job? = null
    private var lastRun: (() -> Unit)? = null

    /** Until the saved results are looked up, so the empty state doesn't flash before them. */
    var restoring by mutableStateOf(true)
        private set
    /** The results on screen came back from the last session; they appear without arriving. */
    var restored by mutableStateOf(false)
        private set

    init {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                if (SettingsRepository.getIncognito(app).first()) null else GalleryResultsStore.load(app)
            }
            if (saved != null && posts.isEmpty() && !busy) {
                query = saved.query
                fromSearch = saved.fromSearch
                posts = saved.posts
                restored = true
            }
            restoring = false
            snapshotFlow { GalleryResultsStore.Saved(query, fromSearch, posts) }
                .distinctUntilChangedBy { it.posts }
                .drop(1)
                .collect { current ->
                    withContext(Dispatchers.IO) {
                        if (SettingsRepository.getIncognito(app).first()) GalleryResultsStore.clear(app)
                        else GalleryResultsStore.save(app, current)
                    }
                }
        }
    }

    fun handle(request: GalleryRequest) {
        job?.cancel()
        lastRun = { handle(request) }
        posts = emptyList()
        restored = false
        restoring = false
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
                    val listing = GalleryDlEngine.list(getApplication(), url, postLimit = limit)
                    val byAuthor = SettingsRepository.getGalleryGroupByAuthor(getApplication()).first()
                    show(withContext(Dispatchers.Default) {
                        GalleryPost.group(url, listing.items, feed = true).let { if (byAuthor) GalleryPost.byAuthor(it) else it }
                    })
                }
            }
        }
    }

    /**
     * Every link in one read per site, each link's posts shown in paste order as they land. A
     * failed link is skipped while others remain, and [skippedLinks] says so.
     */
    private suspend fun readLinks(urls: List<String>) {
        val app = getApplication<Application>()
        val limit = SettingsRepository.getGalleryPostsPerSearch(app).first()
        val byAuthor = SettingsRepository.getGalleryGroupByAuthor(app).first()
        val links = urls.distinct()
        val read = arrayOfNulls<List<GalleryPost>>(links.size)
        val failures = mutableListOf<Throwable>()
        var handed = 0
        val counted = links.size > 1
        if (counted) GalleryReading.set(GalleryReading.Progress(0, links.size))
        try {
            GalleryDlEngine.listEach(app, links, postLimit = limit) { url, result ->
                result.onSuccess { listing ->
                    val found = withContext(Dispatchers.Default) {
                        read[links.indexOf(url)] = GalleryPost.group(url, listing.items)
                        read.filterNotNull().flatten().let { if (byAuthor) GalleryPost.byAuthor(it) else it }
                    }
                    posts = found
                }.onFailure { failures += it }
                if (counted) GalleryReading.set(GalleryReading.Progress(++handed, links.size))
            }
        } finally {
            if (counted) GalleryReading.set(null)
        }
        if (posts.isEmpty()) failures.firstOrNull()?.let { throw it }
        if (failures.isNotEmpty() && posts.isNotEmpty()) skippedLinks = failures.size
        show(posts)
    }

    /** How many pasted links could not be read while others were; the screen says so once. */
    var skippedLinks by mutableStateOf(0)
        private set

    fun skippedLinksShown() {
        skippedLinks = 0
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

    /** Fresh video links by the file's listed url, kept while the app runs; the saved results keep theirs. */
    private val freshLinks = mutableMapOf<String, String>()
    private val linkReads = mutableMapOf<String, Deferred<Map<String, String>>>()

    /**
     * A playable link for [item] that has not expired: the one it was listed with, or one from
     * reading its post again (saved results can predate the link, and the sites' links expire).
     * [failed] is a link that just stopped working. A post is read once for all its files, and
     * once at a time. Null when none can be had.
     */
    suspend fun playableUrl(item: GalleryItem, failed: String? = null): String? {
        (freshLinks[item.url] ?: item.playUrl)?.takeUnless { FileLinks.isStale(it) || it == failed }?.let { return it }
        val post = posts.firstOrNull { item in it.items } ?: return null
        // Kept in the view model's scope, so a viewer page left mid-read still gets the links.
        val read = linkReads[post.source]?.takeIf { it.isActive } ?: viewModelScope.async {
            val fresh = try {
                withContext(Dispatchers.IO) {
                    FileLinks.freshLinks(post.items, GalleryDlEngine.list(getApplication(), post.source).items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Hazel", "Gallery link refresh failed: ${e.javaClass.simpleName}")
                emptyMap()
            }
            freshLinks.putAll(fresh)
            linkReads.remove(post.source)
            fresh
        }.also { linkReads[post.source] = it }
        return read.await()[item.url]
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
    WarmGalleryOptions()
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

    val statusToast = rememberStatusToastState()
    val skippedText = stringResource(R.string.gallery_links_skipped)
    LaunchedEffect(viewModel.skippedLinks) {
        if (viewModel.skippedLinks > 0) {
            statusToast.show(skippedText, ok = false)
            viewModel.skippedLinksShown()
        }
    }
    val links = viewModel.linkCount
    val reading by GalleryReading.progress.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(8.dp))
            UrlSearchBar(
                url = if (links > 1) pluralStringResource(R.plurals.batch_links, links, links) else viewModel.query,
                fetching = reading?.let { it.read to it.total },
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
                viewModel.restoring -> Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
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
                            ArrivingCard(post.source to i, shrink, animate = !viewModel.restored) {
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
                            ArrivingCard(item.url, shrink, animate = !viewModel.restored) {
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
        StatusToastHost(
            statusToast,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 104.dp)
        )
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
                onRefresh = viewModel::playableUrl,
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
            imagesFirst = true,
            onCleared = statusToast::show
        )
    }
}

/** gallery-dl's own reading of a failure, in the terms of the shared failure dialog. */
internal fun dialogKind(kind: GalleryDlException.Kind): FailureKind = when (kind) {
    GalleryDlException.Kind.LOGIN_REQUIRED -> FailureKind.SIGN_IN
    GalleryDlException.Kind.SIGN_IN_REFUSED -> FailureKind.SIGN_IN_AGAIN
    GalleryDlException.Kind.UNSUPPORTED -> FailureKind.UNSUPPORTED
    GalleryDlException.Kind.NOT_FOUND -> FailureKind.GONE
    GalleryDlException.Kind.NETWORK, GalleryDlException.Kind.SETUP, GalleryDlException.Kind.OTHER -> FailureKind.OTHER
}
