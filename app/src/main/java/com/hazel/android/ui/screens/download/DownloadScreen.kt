package com.hazel.android.ui.screens.download

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.hazel.android.ui.components.rememberScrollShrink
import com.hazel.android.ui.components.scrollShrink
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.ui.components.FillingDownloadIcon
import com.hazel.android.ui.components.ProcessingTracker
import com.hazel.android.download.ProcessingStep
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SaveDirs
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.BatchItem
import com.hazel.android.download.BatchState
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadViewModel
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatDuration
import com.hazel.android.ui.components.MediaCardShimmer
import com.hazel.android.ui.components.ShimmerHost
import com.hazel.android.ui.components.refreshShine
import com.hazel.android.ui.components.rememberPresence
import com.hazel.android.ui.motion.M3Motion
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.screens.download.batch.BatchDownloadSheet
import com.hazel.android.util.FolderUtil
import com.hazel.android.util.LinkKey
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.StoragePaths
import com.hazel.android.util.copyToClipboard
import com.hazel.android.util.siteRootOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.hazel.android.ui.components.player.FullscreenPlayer
import com.hazel.android.ui.components.player.InlinePlayer
import com.hazel.android.ui.components.player.rememberPlaybackController
import com.hazel.android.download.playback.PlaybackController
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.PlayArrow

/**
 * Paste one link or several, read what the sources offer, pick formats, download.
 *
 * A single link goes straight to its download sheet. Several links become a list, each card
 * openable on its own, with one action that downloads the whole set.
 */
@Composable
fun DownloadScreen(
    pendingShares: List<com.hazel.android.MainActivity.SharedLink> = emptyList(),
    pendingFailure: String? = null,
    onPendingFailureConsumed: () -> Unit = {},
    onSharesConsumed: () -> Unit = {},
    downloadViewModel: DownloadViewModel = viewModel(),
    /** Opens the queue screen, where a download in hand is paused, resumed or cancelled. */
    onOpenQueue: () -> Unit = {},
    /** Opens the downloads list, from the starters on an empty home screen. */
    onOpenDownloads: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by downloadViewModel.state.collectAsState()
    val formatsReading by downloadViewModel.formatsReading.collectAsState()

    val options by SettingsRepository.getDownloadOptions(context)
        .collectAsState(initial = DownloadOptions())
    val saveDirs by SettingsRepository.getSaveDirs(context).collectAsState(initial = SaveDirs())

    // The kind whose folder the picker is choosing, set as it opens.


    // Collected as null until the stored value arrives, so the dialog cannot flash up for
    // a frame on every launch before the real answer loads and dismisses it again.
    val guideSeen by SettingsRepository.getGuideSeen(context)
        .collectAsState(initial = null as Boolean?)

    val incognito by SettingsRepository.getIncognito(context).collectAsState(initial = false)

    // The link being downloaded is shown first, and the rest keep the order they arrived
    // in. What is being worked on now is what the user opened the app to see, and hunting
    // for it down a list of queued links is the opposite of that. The list is reordered
    // rather than animated into place: a card sliding around under a moving progress bar
    // is harder to read than one that is simply where it belongs.
    val listState = rememberLazyListState()
    val orderedResults = remember(state.results, state.info?.url, state.isDownloading) {
        val active = state.info?.url?.takeIf { state.isDownloading }
        // The list anchors its scroll to the first visible card's key, so when a finished
        // download dropped back from the top to its own place the viewport followed it,
        // often to the end of the list. Pinning the position by index keeps the user where
        // they were; read without observation so scrolling does not recompose this.
        Snapshot.withoutReadObservation {
            listState.requestScrollToItem(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
        if (active == null) state.results
        else state.results.sortedByDescending { it.url == active }
    }

    // A new read empties the list and the skeleton takes its place, but the list's scroll
    // position outlives it: the next results opened where the last ones were left, which
    // after scrolling a playlist meant landing on its final card. Whenever the list is
    // emptied it goes back to the top, so what arrives next is read from the start.
    val hasResults = state.results.isNotEmpty()
    LaunchedEffect(hasResults) {
        if (!hasResults) listState.requestScrollToItem(0)
    }

    // True once anything has moved under the pinned header. The header does not slide away
    // on scroll, which is the usual trick, because the field and the layout switch are what
    // the screen is for; it separates itself from the list instead, so the two stop reading
    // as one surface the moment they start overlapping.
    val listScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    var searchOpen by remember { mutableStateOf(false) }
    // Counts taps on the empty screen's Paste starter; the paste itself is set up further
    // down, beside the paste button it shares its work with.
    var pasteRequests by remember { mutableStateOf(0) }
    // The one card playing, if any. Starting another stops it. The player itself is held
    // here rather than in the card, so full screen cannot drop it along with the card when
    // the list lays itself out again.
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var playerFullscreen by remember { mutableStateOf(false) }
    val playback = playingUrl?.let { rememberPlaybackController(it) }
    val stopPlaying = {
        playingUrl = null
        playerFullscreen = false
    }
    var sheetVisible by remember { mutableStateOf(false) }
    var batchSheetVisible by remember { mutableStateOf(false) }

    // Picking a destination, any folder or an SD card, with the grant persisted so the
    // same folder stays writable on later launches.
    val pickSaveDir = rememberSaveDirPicker(saveDirs)

    // Coming back from a successful sign-in, the link is read again: the cookies that were
    // just saved are picked up by the new attempt.
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            downloadViewModel.fetchInfo()
        }
    }

    val searchBarShine = remember { Animatable(0f) }
    var wasFetching by remember { mutableStateOf(false) }

    // Reading links clears the resolved media, which would otherwise pull a sheet out of
    // the tree mid-animation and show as a box flashing at the bottom of the screen.
    // When fetching finishes, trigger 1 last refresh shine sweep across the searchbar.
    LaunchedEffect(state.isFetching) {
        if (state.isFetching) {
            sheetVisible = false
            batchSheetVisible = false
            wasFetching = true
        } else if (wasFetching) {
            wasFetching = false
            searchBarShine.snapTo(0f)
            searchBarShine.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
            )
        }
    }

    // When the active download switches (e.g. current item finishes, next starts),
    // scroll the list so the new active card is visible at the top. Without this the
    // viewport stays anchored on the old completed card and the user has to scroll
    // manually to find the one that is running now.
    val activeUrl = state.info?.url
    val isDownloading = state.isDownloading
    LaunchedEffect(activeUrl, isDownloading) {
        if (isDownloading && activeUrl != null && (state.isMultiple || state.batch.size > 1)) {
            // The results come first in the list, before the loading and footer items.
            // orderedResults places the active URL first, so scrolling to index 0
            // brings the currently-downloading card into view without jumping past
            // the header or controls.
            listState.animateScrollToItem(0)
        }
    }

    // Links already downloaded, so a repeat can be pointed out before it is started again.
    val history by DownloadHistoryRepository.getHistory(context)
        .collectAsState(initial = emptyList())

    // The records behind the links on screen, so each one can be asked whether the file it
    // produced is still on the device. Only the links being shown are looked up, rather
    // than the whole history, which can run to hundreds of rows.
    val listedEntries = remember(state.results, history) {
        state.results.mapNotNull { info ->
            history.firstOrNull { LinkKey.sameMedia(it.url, info.url) }
        }
    }
    val presence = rememberPresence(listedEntries)

    /**
     * Links in the list that were downloaded before and whose file is still on the device.
     *
     * The record on its own was taken as proof, so a link whose file the user had since
     * deleted came back marked as downloaded and was left out of the set action. The record
     * is a record of what happened, not of what is there, and a file that has gone is a
     * link worth fetching again.
     */
    val savedUrls by remember(state.results, listedEntries) {
        derivedStateOf {
            state.results.filter { info ->
                listedEntries.firstOrNull { LinkKey.sameMedia(it.url, info.url) }
                    ?.let { presence[it.id] == true } == true
            }.map { it.url }.toSet()
        }
    }

    /**
     * The links the set action still owes a download on.
     *
     * Both records are consulted. The batch says what finished in this run, and the history
     * says what finished in any run, which is what a link read after a restart turns on.
     */
    // Whether a run is going on, counting one that is sitting paused. The run's own flag
    // goes false the moment a pause stops the queue, which is how a paused set came to offer
    // the action that would start it over, alongside controls for dropping links out of a
    // queue that is still holding them.
    val runInHand = state.isDownloading || state.batch.any {
        it.state == BatchState.DOWNLOADING || it.state == BatchState.PAUSED
    }

    val pendingResults = remember(state.results, state.batch, savedUrls) {
        state.results.filterNot { info ->
            state.batch.any { item -> item.url == info.url && item.state == BatchState.DONE } ||
                info.url in savedUrls
        }
    }

    // The set action belongs to a set the user put together: several links pasted at once,
    // or a playlist or channel read from one link. A keyword search lands several cards too,
    // but those are a list to choose from rather than a set to take whole, so it stays away.
    val showDownloadAll = pendingResults.size > 1 && !runInHand && state.searchQuery.isBlank()

    var alreadyHave by remember { mutableStateOf<HistoryEntry?>(null) }

    // Marks a link that arrived from another app's share sheet. That is the one route into
    // the app that does not pass through the search screen, so it is the one whose repeat
    // warning is still raised here rather than there.
    var cameFromShare by remember { mutableStateOf(false) }

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // A single link goes straight to its sheet. A set of links does not, because the list
    // itself is the thing to look at first.
    //
    // Which link has already had its sheet opened is remembered by the view model rather
    // than here. This screen is rebuilt every time the user comes back to it from the
    // downloads list or the settings, and a memory that lives here is blank each time, so
    // the sheet for a link read minutes ago opened again on every return.
    LaunchedEffect(state.info?.url, state.isDownloading, state.isComplete) {
        val resolved = state.info?.url
        when {
            resolved == null -> downloadViewModel.clearAutoOpened()

            // Read by the share overlay, which showed its own sheet for it. Opening the app
            // later leaves the result on the list without asking about it a second time.
            downloadViewModel.readShownElsewhere -> downloadViewModel.markAutoOpened(resolved)

            resolved != downloadViewModel.autoOpenedUrl && !state.isMultiple &&
                    !state.isDownloading && !state.isComplete -> {
                downloadViewModel.markAutoOpened(resolved)

                // A repeat is raised in the search screen, where the link is entered and
                // the answer is still cheap. A link shared in from another app never goes
                // through that screen, so it is the one case still checked here.
                val existing = if (cameFromShare) {
                    history.firstOrNull { LinkKey.sameMedia(it.url, resolved) }
                        ?.takeIf { DownloadHistoryRepository.fileExists(context, it) }
                } else null

                if (existing != null) alreadyHave = existing else sheetVisible = true
            }
        }
    }

    LaunchedEffect(pendingShares.size) {
        if (pendingShares.isEmpty()) return@LaunchedEffect

        // Taken as a batch, in the order they arrived. Several shares in a row are several
        // asks, and answering only the last of them is what made two of three links vanish.
        val shares = pendingShares.toList()
        onSharesConsumed()

        cameFromShare = true

        // Handed on before anything that suspends. A share arriving mid-drain restarts this
        // effect, and anything left waiting behind a suspension point at that moment would
        // be dropped: the links have already been taken off the pending list, so nothing
        // would bring them back.
        downloadViewModel.onUrlChange(shares.first().url)
        downloadViewModel.fetchAll(shares.map { it.url })

        // Remembered the same way a typed link is. A link shared in is a link used, and the
        // entry screen offering back only what was typed there made the history look like it
        // had forgotten half of what the app had downloaded. Incognito is the one case that
        // is not recorded, which is its whole point.
        if (!incognito) shares.forEach { SearchHistoryRepository.record(context, it.url) }
    }

    if (guideSeen == false) {
        GettingStartedDialog(
            onOpenBatterySettings = { openBatterySettings(context) },
            onDismiss = { scope.launch { SettingsRepository.setGuideSeen(context) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // The field stays where it is while the results move under it. It is what the
            // screen is for, and a set of a hundred links used to carry it off the top of
            // the screen on the first flick.
            Spacer(modifier = Modifier.height(8.dp))

            // Home screen search bar with overflow 3-dot menu (clear results / clear history).
            // The menu reuses the same actions the full SearchScreen exposes in its own
            // 3-dot, so the two entry points behave identically.
            var homeMenuOpen by remember { mutableStateOf(false) }
            var showClearHistoryConfirm by remember { mutableStateOf(false) }
            val homeScope = rememberCoroutineScope()

            if (showClearHistoryConfirm) {
                AlertDialog(
                    onDismissRequest = { showClearHistoryConfirm = false },
                    title = { Text(stringResource(R.string.search_clear_history_confirm_title), fontWeight = FontWeight.Bold) },
                    text = { Text(stringResource(R.string.search_clear_history_confirm_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showClearHistoryConfirm = false
                            homeScope.launch(Dispatchers.IO) {
                                SearchHistoryRepository.clear(context.applicationContext)
                            }
                        }) { Text(stringResource(R.string.search_clear_history), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearHistoryConfirm = false }) {
                            Text(stringResource(R.string.search_cancel))
                        }
                    }
                )
            }

            UrlSearchBar(
                // Words searched for stay in the bar, as a link read does.
                url = state.searchQuery.ifBlank { state.url },
                shineProgress = searchBarShine.value,
                onOpenSearch = {
                    cameFromShare = false
                    searchOpen = true
                },
                onClearResults = { showClearConfirmDialog = true },
                onClearHistory = { showClearHistoryConfirm = true },
                menuOpen = homeMenuOpen,
                onMenuOpen = { homeMenuOpen = true },
                onMenuDismiss = { homeMenuOpen = false },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Drawn only once there is something underneath it to separate from, so a
            // short list keeps the plain unbroken background it looks better on.
            val separator by animateFloatAsState(
                targetValue = if (listScrolled) 1f else 0f,
                animationSpec = M3Motion.emphasized(200),
                label = "headerSeparator"
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f * separator),
                                Color.Transparent
                            )
                        )
                    )
            )

            if (orderedResults.isEmpty() && state.isFetching) {
                // Fixed viewport skeleton placeholders during initial link fetch.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .clipToBounds()
                ) {
                    Column {
                        if (state.fetchProgress.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                state.fetchProgress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // As many cards as the screen has room for, each at its real size, so
                        // the last one runs off the bottom rather than being squeezed. A tall
                        // screen shows more of them and a short one fewer.
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val cardHeight = maxWidth * 9f / 16f + SKELETON_SPACING
                            val count = (maxHeight / cardHeight).toInt().plus(1)
                                .coerceIn(1, MAX_VIEWPORT_SKELETONS)
                            ShimmerHost(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(SKELETON_SPACING)) {
                                    repeat(count) { MediaCardShimmer() }
                                }
                            }
                        }
                    }
                }
            } else {
                // A lazy list rather than a scrolling column: a column composes every card it
                // holds, artwork and all, so a playlist of a hundred built a hundred full width
                // images at once and ran the app out of memory on the way back from the compact
                // layout. This builds only what is on screen, whatever the list is holding.
                val shrink = rememberScrollShrink()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(shrink),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        // Room for the action that floats over the list, on the same terms as
                        // the action itself.
                        bottom = 96.dp
                    )
                ) {
                    items(orderedResults, key = { it.url }) { info ->
                        Spacer(modifier = Modifier.height(20.dp))

                        // Each card arrives rather than appearing: it fades up from slightly
                        // below where it belongs, once, the first time it is composed. A long
                        // playlist scrolls past as a series of cards settling into place
                        // instead of a wall that redraws itself under the finger.
                        var shown by remember(info.url) { mutableStateOf(false) }
                        LaunchedEffect(info.url) { shown = true }
                        val entrance by animateFloatAsState(
                            targetValue = if (shown) 1f else 0f,
                            animationSpec = M3Motion.emphasized(320),
                            label = "cardEntrance"
                        )

                        val batchItem = state.batch.firstOrNull { it.url == info.url }
                        val isActive = state.isDownloading && state.active?.url == info.url

                        // A card that came from a listing carries no formats yet. Reading them
                        // starts with the sheet, so the wait happens against an open sheet
                        // rather than against a card that looks unresponsive.
                        val openSheet = {
                            downloadViewModel.selectResult(info)
                            downloadViewModel.resolveFormats(info)
                            sheetVisible = true
                        }

                        Box(
                            modifier = Modifier
                                .scrollShrink(shrink)
                                .graphicsLayer {
                                    alpha = entrance
                                    translationY = (1f - entrance) * 28f
                                }
                        ) {
                            MediaCard(
                                info = info,
                                isDownloading = isActive,
                                isProcessing = isActive && state.isProcessing,
                                processingSteps = if (isActive) state.processingSteps else emptyList(),
                                processingStep = state.processingStep,
                                progress = state.progress,
                                isComplete = batchItem?.state == BatchState.DONE ||
                                        (!state.isMultiple && state.isComplete),
                                batchItem = batchItem,
                                waitingForWifi = state.waitingForWifi,
                                alreadyDownloaded = info.url in savedUrls,
                                player = playback?.takeIf { playingUrl == info.url },
                                playerFullscreen = playerFullscreen,
                                onPlay = { playingUrl = info.url },
                                onFullscreen = { playerFullscreen = true },
                                onStopPlaying = {
                                    // Full screen takes the card off screen; that is no reason to stop.
                                    if (playingUrl == info.url && !playerFullscreen) stopPlaying()
                                },
                                onOpenSheet = openSheet,
                                onOpenQueue = onOpenQueue
                            )
                        }
                    }

                    // While playlist/multi links continue reading remaining items, 2 skeleton cards
                    // stand in below the loaded results to smoothly indicate incoming entries.
                    if (state.isFetching && orderedResults.isNotEmpty()) {
                        item(key = "fetching") {
                            Column {
                                if (state.fetchProgress.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        state.fetchProgress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ShimmerHost(modifier = Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        repeat(INCREMENTAL_SKELETON_COUNT) {
                                            MediaCardShimmer()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // How the run as a whole went, under the list rather than over it. It
                    // reports on what the cards above say one by one, so it belongs after them:
                    // above the list it was the first thing read, before there was anything for
                    // it to be about.
                    item(key = "error") {
                        AnimatedVisibility(
                            visible = state.error != null,
                            enter = M3Motion.contentEnter(),
                            exit = M3Motion.contentExit()
                        ) {
                            state.error?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 16.dp, start = 4.dp)
                                )
                            }
                        }
                    }

                    // Says where the downloads that used to sit here have gone. Reading a new
                    // link takes what has finished off the list, so without this the cards a
                    // user watched arrive would simply be absent the next time they pasted
                    // something, which reads as the app having lost them.
                    if (state.savedAside && !state.isFetching) {
                        item(key = "savedAside") {
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.download_saved_aside),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Nothing read yet: three ways to start, as plain chips rather than a
                    // picture, so the empty screen offers something to do.
                    if (!incognito && state.results.isEmpty() && !state.isFetching) {
                        item(key = "starters") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 160.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.home_start_with),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                ) {
                                    com.hazel.android.ui.components.FlatChip(
                                        label = stringResource(R.string.home_start_paste),
                                        onClick = { pasteRequests++ }
                                    )
                                    com.hazel.android.ui.components.FlatChip(
                                        label = stringResource(R.string.home_start_search),
                                        onClick = {
                                            cameFromShare = false
                                            searchOpen = true
                                        }
                                    )
                                    com.hazel.android.ui.components.FlatChip(
                                        label = stringResource(R.string.home_start_downloads),
                                        onClick = onOpenDownloads
                                    )
                                }
                            }
                        }
                    }

                    if (incognito && state.results.isEmpty() && !state.isFetching) {
                        item(key = "incognito") {
                            Spacer(modifier = Modifier.height(72.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.incognito),
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    stringResource(R.string.download_incognito_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.download_incognito_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Paste ──
        //
        // A link is nearly always copied elsewhere first, so a paste is typically the first
        // thing done on this screen. Having it one tap away without opening the full search
        // screen saves a step.
        val homePasteClipboard = LocalClipboard.current
        var homePastePendingDupe by remember { mutableStateOf<Pair<List<String>, HistoryEntry>?>(null) }

        homePastePendingDupe?.let { (links, existing) ->
            AlreadyDownloadedDialog(
                entry = existing,
                onPlay = { MediaOpener.play(context, existing.fileUri, existing.isVideo) },
                onOpenLocation = {
                    MediaOpener.openLocation(context, saveDirs.of(existing.isVideo).uri, existing.isVideo)
                },
                onDownloadAgain = {
                    homePastePendingDupe = null
                    downloadViewModel.fetchAll(links)
                },
                onDismiss = { homePastePendingDupe = null }
            )
        }


        // What a tap on the paste button does: the clip read as one link or several, with
        // the repeat warning raised first for anything already saved.
        val pasteFromClipboard: () -> Unit = {
                    scope.launch {
                        val pasted = homePasteClipboard.getClipEntry()?.clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(context)?.toString()
                            .orEmpty().trim()
                        if (pasted.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.search_nothing_to_paste), Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val links = pasted.split(Regex("""\s+""")).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        if (links.isEmpty()) return@launch
                        val existing = links.firstNotNullOfOrNull { link ->
                            history
                                .firstOrNull { LinkKey.sameMedia(it.url, link) }
                                ?.takeIf { DownloadHistoryRepository.fileExists(context, it) }
                        }
                        if (existing != null) {
                            homePastePendingDupe = links to existing
                        } else {
                            cameFromShare = false
                            downloadViewModel.fetchAll(links)
                        }
                    }
        }

        LaunchedEffect(pasteRequests) { if (pasteRequests > 0) pasteFromClipboard() }

        // The paste button shows while the clipboard holds something to paste, the way a
        // copied link is nearly always what brings someone here. Its label shows for a
        // moment when a new clip arrives and then it folds to an icon. A clip that has
        // been pasted is not offered again, and the button stands aside while a read is
        // running or while the set action holds the corner.
        val clipStamp = rememberClipStamp()
        var usedClipStamp by rememberSaveable { mutableStateOf<Long?>(null) }
        val showPaste = clipStamp != null && clipStamp != usedClipStamp &&
            !state.isFetching && !showDownloadAll
        androidx.compose.animation.AnimatedVisibility(
            visible = showPaste,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            PasteButton(
                stamp = clipStamp,
                onClick = {
                    usedClipStamp = clipStamp
                    pasteFromClipboard()
                }
            )
        }

        // One action for the whole set, which is the point of collecting links together.
        //
        // Offered on what is actually left to fetch rather than on how long the list is. A
        // list of two where one is already saved is one download, and a set action that
        // opens a sheet holding a single card is a set action that should not have been
        // there at all. Search results are left out: see [showDownloadAll].
        if (showDownloadAll) {
            DownloadAllButton(
                onClick = { batchSheetVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }

    // A card that has gone from the results takes its player with it.
    LaunchedEffect(state.results, playingUrl) {
        if (playingUrl != null && state.results.none { it.url == playingUrl }) stopPlaying()
    }

    if (playerFullscreen && playback != null) {
        val playingInfo = state.results.firstOrNull { it.url == playingUrl }
        FullscreenPlayer(
            controller = playback,
            thumbnail = playingInfo?.thumbnail,
            onExit = { playerFullscreen = false },
            onDownload = playingInfo?.let { info ->
                {
                    playerFullscreen = false
                    downloadViewModel.selectResult(info)
                    downloadViewModel.resolveFormats(info)
                    sheetVisible = true
                }
            }
        )
    }

    if (searchOpen) {
        SearchScreen(
            // Opened empty rather than prefilled: a prefilled field would filter the
            // history down to that one link, hiding every other link already used.
            initialQuery = "",
            onSearch = { queries ->
                searchOpen = false
                stopPlaying()
                downloadViewModel.fetchAll(queries)
            },
            onSearchWords = { query, source ->
                searchOpen = false
                stopPlaying()
                downloadViewModel.search(query, source)
            },
            onClearResults = downloadViewModel::clearResults,
            onDismiss = { searchOpen = false }
        )
    }

    alreadyHave?.let { existing ->
        AlreadyDownloadedDialog(
            entry = existing,
            onPlay = { MediaOpener.play(context, existing.fileUri, existing.isVideo) },
            onOpenLocation = {
                MediaOpener.openLocation(context, saveDirs.of(existing.isVideo).uri, existing.isVideo)
            },
            onDownloadAgain = {
                alreadyHave = null
                sheetVisible = true
            },
            onDismiss = { alreadyHave = null }
        )
    }

    state.errorLog?.let { log ->
        NoResultsDialog(
            message = log,
            canFetchCookies = isCookieRelated(log) && state.url.isNotBlank(),
            canContinue = state.url.isNotBlank(),
            canAddCookies = state.url.isNotBlank(),
            onCopyLog = {
                copyToClipboard(context, log)
                downloadViewModel.clearErrorLog()
            },
            onGetCookies = {
                downloadViewModel.clearErrorLog()
                signInLauncher.launch(CookieWebViewActivity.intent(context, siteRootOf(state.url)))
            },
            onContinueAnyway = downloadViewModel::continueWithoutMetadata,
            onDismiss = downloadViewModel::clearErrorLog
        )
    }

    val openSaveDirOf: (Boolean) -> Unit = { isVideo ->
        MediaOpener.openLocation(context, saveDirs.of(isVideo).uri, isVideo)
    }
    val resetSaveDir: (Boolean) -> Unit = { isVideo ->
        scope.launch { SettingsRepository.resetSaveDir(context, isVideo) }
    }

    if (sheetVisible) {
        state.info?.let { info ->
            // A link already downloaded and still on the device is one the user may have
            // come back to in order to watch rather than to fetch again. The sheet says so
            // by offering to play it, next to the action that would download it a second
            // time. Checked only while the sheet is open, since it touches the filesystem.
            var onDevice by remember(info.url) { mutableStateOf<HistoryEntry?>(null) }
            LaunchedEffect(info.url, history) {
                val entry = history.firstOrNull { LinkKey.sameMedia(it.url, info.url) }
                onDevice = entry?.takeIf { DownloadHistoryRepository.fileExists(context, it) }
            }

            FormatSheet(
                info = info,
                onPlay = onDevice?.let { entry ->
                    { MediaOpener.play(context, entry.fileUri, entry.isVideo) }
                },
                options = options,
                onOptionsChange = {
                    scope.launch { SettingsRepository.setDownloadOptions(context, it) }
                },
                saveDirs = saveDirs,
                isLoadingFormats = state.isFetching || info.url in formatsReading,
                // A search or playlist card opens before its details are read.
                isReadingLink = !info.hasResolvedFormats && info.url in formatsReading,
                onRefreshFormats = { source, fresh -> downloadViewModel.refreshFormats(listOf(info), source, fresh) },
                onOpenSaveDir = openSaveDirOf,
                onPickSaveDir = pickSaveDir,
                onResetSaveDir = resetSaveDir,
                onDownload = { format, audioLanguage, title, author, oneOff ->
                    sheetVisible = false
                    if (!info.hasResolvedFormats && info.url in formatsReading) {
                        // Chosen before the read finished: it starts the moment the
                        // formats land, applied to what the link turned out to hold.
                        downloadViewModel.downloadOnceFormatsRead(
                            context = context,
                            info = info,
                            format = format,
                            options = options.with(oneOff),
                            title = title,
                            author = author,
                            audioLanguage = audioLanguage,
                            saveDirs = saveDirs
                        )
                    } else {
                        downloadViewModel.startDownload(
                            context = context,
                            format = format,
                            options = options.with(oneOff),
                            title = title,
                            author = author,
                            audioLanguage = audioLanguage,
                            saveDirs = saveDirs
                        )
                    }
                },
                onDismiss = { sheetVisible = false }
            )
        }
    }

    if (batchSheetVisible) {
        BatchDownloadSheet(
            results = pendingResults,
            options = options,
            onOptionsChange = {
                scope.launch { SettingsRepository.setDownloadOptions(context, it) }
            },
            saveDirs = saveDirs,
            onOpenSaveDir = openSaveDirOf,
            onPickSaveDir = pickSaveDir,
            onResetSaveDir = resetSaveDir,
            onResolveFormats = downloadViewModel::resolveFormats,
            readingUrls = formatsReading,
            onRefreshFormats = downloadViewModel::refreshFormats,
            onRemove = downloadViewModel::removeResult,
            onDownload = { plans ->
                batchSheetVisible = false
                downloadViewModel.startBatch(context, plans, options, saveDirs = saveDirs)
            },
            onDismiss = { batchSheetVisible = false }
        )
    }


    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.download_clear_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.download_clear_confirm_body)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        downloadViewModel.clearResults()
                    }
                ) {
                    Text(
                        stringResource(R.string.download_clear),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false }
                ) {
                    Text(stringResource(R.string.download_cancel))
                }
            }
        )
    }
}

/**
 * The resting search field. Tapping it opens the full screen entry, which is where links
 * are actually typed, so this stays a button rather than an input.
 *
 * It carries nothing else. The menu that used to sit on it acts on the search history and
 * the resolved results, which are both things the entry screen is already about, so it
 * lives there now and this is a single control that does one thing.
 *
 * It is drawn on the highest surface tone with an outline, because on the near-black
 * background a low-alpha fill has almost no edge and the field reads as empty space.
 */
@Composable
private fun UrlSearchBar(
    url: String,
    onOpenSearch: () -> Unit,
    onClearResults: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    shineProgress: Float = 0f,
    menuOpen: Boolean = false,
    onMenuOpen: () -> Unit = {},
    onMenuDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpenSearch,
        modifier = modifier
            .refreshShine(shineProgress, RoundedCornerShape(26.dp))
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                url.ifBlank { stringResource(R.string.download_search_hint) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (url.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Right: 3-dot overflow menu mirroring the full SearchScreen's menu
            Box {
                IconButton(onClick = onMenuOpen, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.search_more),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onMenuDismiss
                ) {

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_clear_results)) },
                        onClick = {
                            onMenuDismiss()
                            onClearResults()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_clear_history)) },
                        onClick = {
                            onMenuDismiss()
                            onClearHistory()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                stringResource(R.string.download_download_all),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/**
 * Thumbnail, title and author for a resolved link.
 *
 * The whole card is the download control: tapping anywhere on it opens the format sheet,
 * so there is no separate button competing with it. While this card's download runs it
 * carries the progress readout, and its centre a download glyph that fills along with it.
 *
 * Once the transfer finishes and the streams are being merged and tagged, the filling glyph
 * goes and the stage track takes the artwork over, so the card still reads as busy and says
 * which stage the file is at.
 */
@Composable
private fun MediaCard(
    info: MediaInfo,
    isDownloading: Boolean,
    isProcessing: Boolean = false,
    processingSteps: List<ProcessingStep> = emptyList(),
    processingStep: Int = 0,
    progress: Float,
    isComplete: Boolean,
    batchItem: BatchItem?,
    waitingForWifi: Boolean = false,
    alreadyDownloaded: Boolean = false,
    /** The player, when this card is the one playing. */
    player: PlaybackController? = null,
    playerFullscreen: Boolean = false,
    onPlay: () -> Unit = {},
    onFullscreen: () -> Unit = {},
    onStopPlaying: () -> Unit = {},
    onOpenSheet: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val isPaused = batchItem?.state == BatchState.PAUSED
    val isQueued = batchItem?.state == BatchState.QUEUED
    // A download in hand is managed from the queue, so the card leads there instead of back
    // to the sheet that started it.
    val inHand = isDownloading || isPaused || isQueued

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(
                    onClickLabel = if (inHand) stringResource(R.string.download_open_queue) else null,
                    onClick = if (inHand) onOpenQueue else onOpenSheet
                )
        ) {
            // Sources without artwork simply show the placeholder glyph.
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                )
            }

            // Dark gradient overlay from top and bottom so text is always readable over thumbnail
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            0.4f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
            )

            if (isDownloading || isPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }

            // The stage track goes under the title and the readouts, so its shade dims the
            // artwork and not the words laid over it.
            if (isProcessing && !isPaused && processingSteps.isNotEmpty()) {
                ProcessingTracker(
                    steps = processingSteps,
                    current = processingStep,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Title and author directly overlaid on top of the thumbnail
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        start = 14.dp,
                        top = 12.dp,
                        end = 14.dp
                    )
            ) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (info.uploader.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        info.uploader,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // A paused download is still a download in hand, so the artwork keeps the
            // treatment that says so. The glyph in the middle holds where it stopped.
            if (isDownloading || isPaused) {
                if (!isProcessing || isPaused) {
                    // The download glyph fills as the download does, straight on the artwork.
                    // Stopping or pausing it is done from the queue, which a tap on the card
                    // opens.
                    FillingDownloadIcon(
                        progress = progress,
                        flowing = !isPaused,
                        tint = if (isPaused) Color.White.copy(alpha = 0.7f) else Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(40.dp)
                    )
                }
            }

            // Held back for want of Wi-Fi. The artwork is darkened exactly as a
            // running download darkens it, because the card is in hand either way, and
            // the middle says what it is waiting for.
            if (waitingForWifi && !isDownloading && !isPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )

                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = Color.White
                        )
                        Text(
                            stringResource(R.string.download_waiting_wifi),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            // Bottom left corner carries the duration with this link's state beside it:
            // paused, failed, saved, queued or downloaded. It stays while the download runs.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val duration = formatDuration(info.durationSeconds)
                if (duration.isNotBlank()) CornerTag(text = duration)
                when {
                    isPaused -> CornerTag(text = stringResource(R.string.download_paused))
                    isDownloading -> Unit
                    batchItem?.state == BatchState.FAILED -> CornerTag(
                        text = batchItem.error ?: stringResource(R.string.download_failed),
                        background = MaterialTheme.colorScheme.error,
                        foreground = MaterialTheme.colorScheme.onError
                    )
                    isComplete -> CornerTag(
                        text = stringResource(R.string.download_saved),
                        background = MaterialTheme.colorScheme.primary,
                        foreground = MaterialTheme.colorScheme.onPrimary
                    )
                    batchItem?.state == BatchState.QUEUED -> CornerTag(text = stringResource(R.string.download_queued))
                    alreadyDownloaded -> CornerTag(text = stringResource(R.string.download_downloaded))
                }
            }

            // Bottom right corner plays the link, for one not in hand.
            if (!inHand) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            onClickLabel = stringResource(R.string.player_play),
                            onClick = onPlay
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.player_play),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Playing covers the artwork with the player until it is closed or scrolled away.
            if (player != null && !inHand) {
                DisposableEffect(info.url) { onDispose { onStopPlaying() } }
                InlinePlayer(
                    controller = player,
                    thumbnail = info.thumbnail,
                    fullscreen = playerFullscreen,
                    onFullscreen = onFullscreen,
                    onClose = onStopPlaying,
                    onDownload = onOpenSheet,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Gap between skeleton cards, the same as between the cards they stand in for. */
private val SKELETON_SPACING = 20.dp

/** An upper bound on skeleton cards, for a screen taller than any phone's. */
private const val MAX_VIEWPORT_SKELETONS = 8

/**
 * 2 cards stand in below already-loaded results while remaining playlist items continue loading.
 */
private const val INCREMENTAL_SKELETON_COUNT = 2

/** Dark pill drawn over the thumbnail. */
@Composable
private fun OverlayChip(text: String, bold: Boolean = false) {
    Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.6f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Flat tag in the thumbnail's corner, for the duration and the state marker. */
@Composable
private fun CornerTag(
    text: String,
    background: Color = Color.Black.copy(alpha = 0.7f),
    foreground: Color = Color.White
) {
    Surface(shape = RoundedCornerShape(4.dp), color = background) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * The site's front page, which is where a sign-in starts. Cookies are stored per site, so
 * the individual media address is trimmed away.
 */
