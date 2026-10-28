package com.hazel.android.ui.screens.download

import com.hazel.android.ui.components.player.miniWidth
import com.hazel.android.ui.components.player.LargePlayer
import com.hazel.android.ui.components.player.MiniPlayer
import com.hazel.android.ui.components.player.PlayerPlace
import com.hazel.android.ui.components.player.PlayerDock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.hazel.android.ui.theme.tonalFill
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.hazel.android.ui.components.AmbientStarField
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hazel.android.ui.screens.history.DownloadPropertiesSheet
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
import androidx.compose.foundation.layout.fillMaxHeight
import com.hazel.android.ui.components.pressScale
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.ui.components.FastScrollbar
import com.hazel.android.ui.components.gridColumns
import com.hazel.android.ui.components.gridItems
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
    pendingFailure: com.hazel.android.download.FailureNotice? = null,
    onPendingFailureConsumed: () -> Unit = {},
    onSharesConsumed: () -> Unit = {},
    downloadViewModel: DownloadViewModel = viewModel(),
    /** Opens the queue screen, where a download in hand is paused, resumed or cancelled. */
    onOpenQueue: () -> Unit = {}
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val state by downloadViewModel.state.collectAsState()
    val formatsReading by downloadViewModel.formatsReading.collectAsState()
    val formatReadErrors by downloadViewModel.formatReadErrors.collectAsState()

    val options by remember(context) { SettingsRepository.getDownloadOptions(context) }
        .collectAsState(initial = DownloadOptions())
    val saveDirs by remember(context) { SettingsRepository.getSaveDirs(context) }.collectAsState(initial = SaveDirs())

    // The kind whose folder the picker is choosing, set as it opens.


    // Collected as null until the stored value arrives, so the dialog cannot flash up for
    // a frame on every launch before the real answer loads and dismisses it again.
    val guideSeen by remember(context) { SettingsRepository.getGuideSeen(context) }
        .collectAsState(initial = null as Boolean?)

    val incognito by remember(context) { SettingsRepository.getIncognito(context) }.collectAsState(initial = false)

    // The link being downloaded is shown first, and the rest keep the order they arrived
    // in. What is being worked on now is what the user opened the app to see, and hunting
    // for it down a list of queued links is the opposite of that. The list is reordered
    // rather than animated into place: a card sliding around under a moving progress bar
    // is harder to read than one that is simply where it belongs.
    val listState = rememberLazyListState()
    val orderedResults = remember(state.results, state.active?.url, state.isDownloading) {
        val active = state.active?.url?.takeIf { state.isDownloading }
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
    // as one surface the moment they start overlapping. Asked of the scroll itself rather
    // than of the first visible row: the empty screen leads with a row of no height, which
    // made its empty-screen hint the first visible row and drew the separator over a blank screen.
    //
    // Only with cards in the list. With none, the error row that leads it has no height, so
    // the list reports itself as past that row, and able to scroll back to it, without
    // anything having moved; that drew the separator and the scrollbar over an empty screen.
    val listScrolled by remember {
        derivedStateOf { listState.canScrollBackward && state.results.isNotEmpty() }
    }

    var searchOpen by remember { mutableStateOf(false) }
    // The one card playing, if any. Starting another stops it. The player itself is held
    // here rather than in the card, so full screen cannot drop it along with the card when
    // the list lays itself out again.
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var playerFullscreen by remember { mutableStateOf(false) }
    val playback = playingUrl?.let { rememberPlaybackController(it) }
    // Moves the video between its card, the mini player and the large view, so a scroll or
    // a download carries on playing instead of stopping it.
    val dock = remember { PlayerDock() }
    val stopPlaying = {
        playingUrl = null
        playerFullscreen = false
        dock.start(null)
    }
    var sheetVisible by remember { mutableStateOf(false) }
    // A card held down, for its action dialog, and the saved copy whose properties are open.
    var heldInfo by remember { mutableStateOf<MediaInfo?>(null) }
    var propertiesEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    // Starts a link straight in the large player, from the sheet or a held card.
    val playLarge: (MediaInfo) -> Unit = { info ->
        sheetVisible = false
        playerFullscreen = false
        playingUrl = info.url
        dock.startLarge(info.url)
    }
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
    val activeUrl = state.active?.url
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
    val history by remember(context) { DownloadHistoryRepository.getHistory(context) }
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
    // Shown for a set the whole time it is on screen, while it downloads and after it is
    // saved. It offers what is not already in the queue, the whole set again once all of
    // it is saved, and the queue itself when every link is already in hand.
    val showDownloadAll = state.results.size > 1 && state.searchQuery.isBlank()
    val inHandUrls = state.batch.filter {
        it.state == BatchState.QUEUED || it.state == BatchState.DOWNLOADING || it.state == BatchState.PAUSED
    }.mapTo(mutableSetOf()) { it.url }
    val notInHand = pendingResults.filterNot { it.url in inHandUrls }
    val downloadAllResults = when {
        notInHand.isNotEmpty() -> notInHand
        runInHand -> emptyList()
        else -> state.results
    }

    var alreadyHave by remember { mutableStateOf<HistoryEntry?>(null) }

    // Marks a link that arrived from another app's share sheet. That is the one route into
    // the app that does not pass through the search screen, so it is the one whose repeat
    // warning is still raised here rather than there.
    var cameFromShare by remember { mutableStateOf(false) }


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
            onDismiss = { scope.launch { SettingsRepository.setGuideSeen(context, WhatsNew.RELEASE_ID) } }
        )
    }

    // Decided once, after both values load. The id is saved as the card first shows, so it
    // cannot come back after a crash, a kill or a rotation, only with the next RELEASE_ID.
    val whatsNewSeen by remember(context) { SettingsRepository.getWhatsNewSeen(context) }
        .collectAsState(initial = null as String?)
    var whatsNewDecided by rememberSaveable { mutableStateOf(false) }
    var whatsNewOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(guideSeen, whatsNewSeen) {
        if (whatsNewDecided) return@LaunchedEffect
        val show = WhatsNew.shouldShow(guideSeen, whatsNewSeen) ?: return@LaunchedEffect
        whatsNewDecided = true
        if (show) {
            whatsNewOpen = true
            runCatching { SettingsRepository.setWhatsNewSeen(context, WhatsNew.RELEASE_ID) }
        }
    }
    if (whatsNewOpen) {
        WhatsNewDialog(onDismiss = { whatsNewOpen = false })
    }

    val statusToast = rememberStatusToastState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // The field stays where it is while the results move under it. It is what the
            // screen is for, and a set of a hundred links used to carry it off the top of
            // the screen on the first flick.
            Spacer(modifier = Modifier.height(8.dp))

            // Home screen search bar. Its 3-dot opens the same search options sheet the full
            // SearchScreen opens, so the two entry points behave identically.
            var homeMenuOpen by remember { mutableStateOf(false) }
            if (homeMenuOpen) {
                SearchOptionsSheet(
                    onClearResults = downloadViewModel::clearResults,
                    onDismiss = { homeMenuOpen = false },
                    onCleared = statusToast::show
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
                onMenuOpen = { homeMenuOpen = true },
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
                        Spacer(modifier = Modifier.height(16.dp))
                        // As many cards as the screen has room for, each at its real size, so
                        // the last one runs off the bottom rather than being squeezed. A tall
                        // screen shows more of them and a short one fewer.
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = gridColumns(maxWidth)
                            val cardWidth = (maxWidth - SKELETON_SPACING * (columns - 1)) / columns
                            val cardHeight = cardWidth * 9f / 16f + SKELETON_SPACING
                            val count = (maxHeight / cardHeight).toInt().plus(1)
                                .coerceIn(1, MAX_VIEWPORT_SKELETONS)
                            ShimmerHost(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(SKELETON_SPACING)) {
                                    repeat(count) { ShimmerRow(columns) }
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
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // A wide screen lays the cards out side by side, inside the list's padding.
                    val columns = gridColumns(maxWidth - 40.dp)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { dock.viewport = it.boundsInWindow() }
                            .nestedScroll(shrink),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            // Room for the action that floats over the list, on the same terms as
                            // the action itself.
                            // Kept only while there are cards: on an empty screen it made the
                            // list taller than its space, and the hint sat that much above the
                            // middle.
                            bottom = if (orderedResults.isEmpty()) 0.dp else LIST_BOTTOM_ROOM
                        )
                    ) {
                        gridItems(orderedResults, columns, SKELETON_SPACING, key = { it.url }) { info ->
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
                            // The item's own state says which card is running; the shared pointer
                            // only stands in for a link the run has no list entry for.
                            val isActive = state.isDownloading && when (batchItem) {
                                null -> state.active?.url == info.url
                                else -> batchItem.state == BatchState.DOWNLOADING
                            }

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
                                    waitingForWifi = state.waitingForWifi && batchItem?.state == BatchState.QUEUED,
                                    alreadyDownloaded = info.url in savedUrls,
                                    player = playback?.takeIf { playingUrl == info.url },
                                    playerFullscreen = playerFullscreen,
                                    playerInCard = dock.place == PlayerPlace.CARD && !dock.docking,
                                    onPlay = {
                                        playingUrl = info.url
                                        dock.start(info.url)
                                    },
                                    onFullscreen = { playerFullscreen = true },
                                    onStopPlaying = { if (playingUrl == info.url) stopPlaying() },
                                    onPlayerMoved = { bounds, canDock ->
                                        dock.onCardMoved(info.url, bounds, canDock, playerFullscreen)
                                    },
                                    onPlayerGone = { dock.onCardGone(info.url, playerFullscreen) },
                                    onPlayerTakenOver = { dock.toMini(info.url) },
                                    onOpenSheet = openSheet,
                                    onOpenQueue = onOpenQueue,
                                    onHold = { heldInfo = info }
                                )
                            }
                        }

                        // While playlist/multi links continue reading remaining items, 2 skeleton cards
                        // stand in below the loaded results to smoothly indicate incoming entries.
                        if (state.isFetching && orderedResults.isNotEmpty()) {
                            item(key = "fetching") {
                                Column {
                                    ShimmerHost(modifier = Modifier.fillMaxWidth()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            // One row of them on a wide screen: two would
                                            // stand in for more cards than are coming.
                                            repeat(if (columns > 1) 1 else INCREMENTAL_SKELETON_COUNT) {
                                                ShimmerRow(columns)
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
                            androidx.compose.animation.AnimatedVisibility(
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

                        // Nothing read yet: a faint mark and one line over quiet drifting stars
                        // that answers a touch, and nothing to tap. The search bar above and
                        // the paste button already offer every way to start.
                        if (!incognito && state.results.isEmpty() && !state.isFetching) {
                            item(key = "empty") {
                                // Fills the space between the search bar and the bottom bar,
                                // so the line sits in its middle.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillParentMaxHeight()
                                ) {
                                    AmbientStarField(Modifier.matchParentSize(), paused = searchOpen)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_download_minimal),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            stringResource(R.string.home_empty_hint),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                            textAlign = TextAlign.Center
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
                    // Nothing to move through on an empty screen, so no bar over it.
                    if (orderedResults.isNotEmpty()) {
                        FastScrollbar(
                            listState,
                            Modifier.align(Alignment.TopEnd),
                            trackPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = LIST_BOTTOM_ROOM)
                        )
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
                            Toast.makeText(context, resources.getString(R.string.search_nothing_to_paste), Toast.LENGTH_SHORT).show()
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

        // The paste button shows while the clipboard holds something to paste, the way a
        // copied link is nearly always what brings someone here. Its label shows for a
        // moment when a new clip arrives and then it folds to an icon. A clip that has
        // been pasted is not offered again, and the button stands aside while a read is
        // running. It sits above the set action when both are there.
        val clipStamp = rememberClipStamp()
        var usedClipStamp by rememberSaveable { mutableStateOf<Long?>(null) }
        val showPaste = clipStamp != null && clipStamp != usedClipStamp && !state.isFetching

        val miniShown = playback != null && (dock.place != PlayerPlace.CARD || dock.docking)
        if (playback != null) {
            MiniPlayer(
                dock = dock,
                controller = playback,
                thumbnail = state.results.firstOrNull { it.url == playingUrl }?.thumbnail,
                fullscreen = playerFullscreen,
                onClose = stopPlaying
            )
        }
        // The actions stand above the mini player when the screen is too narrow for both.
        var screenWidth by remember { mutableIntStateOf(0) }
        var actionsWidth by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        // The actions' measured width includes their 20dp side padding, which matches the
        // mini player's 12dp margin plus an 8dp gap, so they touch once wider than the rest.
        val crowded = with(density) {
            miniShown && actionsWidth > 0 && actionsWidth > screenWidth - miniWidth(screenWidth.toDp()).toPx()
        }
        val lift by animateDpAsState(
            if (crowded) with(density) { (miniWidth(screenWidth.toDp()) * 9f / 16f) + 12.dp } else 0.dp,
            label = "actionsLift"
        )
        Box(Modifier.matchParentSize().onSizeChanged { screenWidth = it.width })
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .onSizeChanged { actionsWidth = it.width }
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + lift),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showPaste,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
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
            // Search results are left out: see [showDownloadAll].
            if (showDownloadAll) {
                DownloadAllButton(
                    onClick = {
                        if (downloadAllResults.isEmpty()) onOpenQueue() else batchSheetVisible = true
                    }
                )
            }
        }
        // Above the paste button, so a long message never runs under it.
        StatusToastHost(
            statusToast,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = 104.dp)
        )
    }

    // A card that has gone from the results takes its player with it.
    LaunchedEffect(state.results, playingUrl) {
        if (playingUrl != null && state.results.none { it.url == playingUrl }) stopPlaying()
    }

    if (dock.place == PlayerPlace.LARGE && playback != null) {
        val playingInfo = state.results.firstOrNull { it.url == playingUrl }
        LargePlayer(
            dock = dock,
            controller = playback,
            title = playingInfo?.title,
            author = playingInfo?.uploader,
            thumbnail = playingInfo?.thumbnail,
            fullscreen = playerFullscreen,
            onFullscreen = { playerFullscreen = true },
            onClose = stopPlaying,
            onDownload = playingInfo?.let { info ->
                {
                    dock.collapse()
                    downloadViewModel.selectResult(info)
                    downloadViewModel.resolveFormats(info)
                    sheetVisible = true
                }
            }
        )
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

    heldInfo?.let { info ->
        // The saved copy, if its file is still there; presence is already read off the main thread.
        val entry = listedEntries.firstOrNull { LinkKey.sameMedia(it.url, info.url) }
        val onDevice = entry?.takeIf { info.url in savedUrls }
        MediaHoldDialog(
            info = info,
            onDismiss = { heldInfo = null },
            onPlay = if (info.canStream) { { heldInfo = null; playLarge(info) } } else null,
            onDownload = {
                heldInfo = null
                downloadViewModel.selectResult(info)
                downloadViewModel.resolveFormats(info)
                sheetVisible = true
            },
            onOpen = onDevice?.let { saved -> { heldInfo = null; MediaOpener.play(context, saved.fileUri, saved.isVideo) } },
            onProperties = entry?.let { saved -> { heldInfo = null; propertiesEntry = saved } }
        )
    }

    propertiesEntry?.let { entry ->
        DownloadPropertiesSheet(
            entry = entry,
            present = presence[entry.id] ?: false,
            onDismiss = { propertiesEntry = null },
            onRemove = {
                propertiesEntry = null
                scope.launch { DownloadHistoryRepository.remove(context, entry.id) }
            },
            onDelete = null
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

    // A failure notification that was tapped opens on its dialog.
    LaunchedEffect(pendingFailure) {
        pendingFailure?.let {
            downloadViewModel.showFailure(it.message, it.signInSite)
            onPendingFailureConsumed()
        }
    }

    state.errorLog?.let { log ->
        // A failure from a notification names its own site, and is not about the link in
        // the search bar.
        val notice = state.errorSignInSite
        NoResultsDialog(
            message = log,
            canFetchCookies = isCookieRelated(log) && (notice != null || state.url.isNotBlank()),
            canContinue = notice == null && state.url.isNotBlank(),
            canAddCookies = notice != null || state.url.isNotBlank(),
            link = if (notice == null) state.url else null,
            onCopyLog = {
                copyToClipboard(context, log)
                downloadViewModel.clearErrorLog()
            },
            onGetCookies = {
                downloadViewModel.clearErrorLog()
                if (notice != null) context.startActivity(CookieWebViewActivity.intent(context, notice))
                else signInLauncher.launch(CookieWebViewActivity.intent(context, siteRootOf(state.url)))
            },
            onContinueAnyway = downloadViewModel::continueWithoutMetadata,
            onDismiss = downloadViewModel::clearErrorLog
        )
    }

    SignInPrompt(downloadViewModel)

    val openSaveDirOf: (Boolean) -> Unit = { isVideo ->
        MediaOpener.openLocation(context, saveDirs.of(isVideo).uri, isVideo)
    }
    val resetSaveDir: (Boolean) -> Unit = { isVideo ->
        scope.launch { SettingsRepository.resetSaveDir(context, isVideo) }
    }

    if (sheetVisible) {
        state.info?.let { info ->
            FormatSheet(
                info = info,
                onPlay = if (info.canStream) { { playLarge(info) } } else null,
                options = options,
                onOptionsChange = {
                    scope.launch { SettingsRepository.setDownloadOptions(context, it) }
                },
                saveDirs = saveDirs,
                isLoadingFormats = state.isFetching || info.url in formatsReading,
                // A search or playlist card opens before its details are read.
                isReadingLink = !info.hasResolvedFormats && info.url in formatsReading,
                formatsError = formatReadErrors[info.url],
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
                        // The sheet's own link, named rather than left to whatever the
                        // state points at by the time the button is pressed.
                        downloadViewModel.startDownload(
                            context = context,
                            format = format,
                            options = options.with(oneOff),
                            title = title,
                            author = author,
                            audioLanguage = audioLanguage,
                            info = info,
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
            results = downloadAllResults,
            sourceUrl = state.url,
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
}

/**
 * The resting search field. Tapping it opens the full screen entry, which is where links
 * are actually typed, so this stays a button rather than an input.
 *
 * Its 3-dot opens the search options sheet, the same one the search screen opens.
 *
 * It is drawn on the highest surface tone, with no outline: the tone alone gives it its edge
 * against the near-black background.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UrlSearchBar(
    url: String,
    onOpenSearch: () -> Unit,
    shineProgress: Float = 0f,
    onMenuOpen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // The field and its menu as one connected group: round on the outside, tight where they meet.
    val fieldShape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 8.dp, bottomEnd = 8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
    Surface(
        onClick = onOpenSearch,
        modifier = Modifier
            .refreshShine(shineProgress, fieldShape)
            .weight(1f)
            .fillMaxHeight(),
        shape = fieldShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialShapes.Cookie7Sided.toShape())
                    // The accent eased a quarter toward the bar, so it reads tonal beside it.
                    .background(androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        0.25f
                    )),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
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
        }
    }

    // Opens the same search options sheet the search screen's own 3-dot does. Pressed, its
    // inner corners round out and it squeezes, the connected group's press.
    val menuPress = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val inner by com.hazel.android.ui.components.pressCorner(menuPress, resting = 8.dp, pressed = 18.dp)
    Surface(
        onClick = onMenuOpen,
        interactionSource = menuPress,
        modifier = Modifier
            .pressScale(menuPress, pressed = 0.92f)
            .width(50.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = 26.dp, bottomEnd = 26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.search_more),
                modifier = Modifier.size(22.dp)
            )
        }
    }
    }
}

/**
 * Download all: an accent pill led by a filled seven-sided cookie in the inverted colour, the
 * download arrow cut into it in the pill's own colour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = fill
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, top = 5.dp, bottom = 5.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(MaterialShapes.Cookie7Sided.toShape())
                    .background(ink),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = fill
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.download_download_all),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    /** Whether the video shows in this card, rather than in the mini player or large view. */
    playerInCard: Boolean = true,
    /** Where the playing card's picture is in the window, and whether it can hold the video. */
    onPlayerMoved: (Rect, Boolean) -> Unit = { _, _ -> },
    onPlayerGone: () -> Unit = {},
    onPlayerTakenOver: () -> Unit = {},
    onOpenSheet: () -> Unit,
    onOpenQueue: () -> Unit,
    onHold: () -> Unit = {}
) {
    val isPaused = batchItem?.state == BatchState.PAUSED
    val isQueued = batchItem?.state == BatchState.QUEUED
    // A download in hand is managed from the queue, so the card leads there instead of back
    // to the sheet that started it.
    val inHand = isDownloading || isPaused || isQueued
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = tonalFill(0.35f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(
                    if (player != null) {
                        Modifier.onGloballyPositioned { onPlayerMoved(it.boundsInWindow(), !inHand) }
                    } else Modifier
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    onClickLabel = if (inHand) stringResource(R.string.download_open_queue) else null,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHold()
                    },
                    onClick = if (inHand) onOpenQueue else onOpenSheet
                )
        ) {
            // Under the picture, so it shows while it loads, when it fails and when there is none.
            com.hazel.android.ui.components.ArtworkPlaceholder(Modifier.matchParentSize())
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
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

            // Waiting its turn: the same glyph, empty and still.
            if (isQueued && !isDownloading && !waitingForWifi) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
                FillingDownloadIcon(
                    progress = 0f,
                    flowing = false,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                )
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

            // Bottom right corner plays the link, for one not in hand, on a flat accent cookie.
            if (!inHand) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(44.dp)
                        .clip(MaterialShapes.Cookie6Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            onClickLabel = stringResource(R.string.player_play),
                            onClick = onPlay
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.player_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Playing covers the artwork with the player while it is here. Scrolled away or
            // taken over by a download, the video moves to the mini player and carries on.
            if (player != null) {
                DisposableEffect(info.url) { onDispose { onPlayerGone() } }
                LaunchedEffect(inHand) { if (inHand) onPlayerTakenOver() }
            }
            if (player != null && !inHand && playerInCard) {
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

/** Room at the foot of the results list for the action that floats over it. */
private val LIST_BOTTOM_ROOM = 96.dp

/** A row of [columns] skeleton cards, the width the real ones will take. */
@Composable
private fun ShimmerRow(columns: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SKELETON_SPACING)) {
        repeat(columns) { MediaCardShimmer(modifier = Modifier.weight(1f)) }
    }
}

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

/** A video the player can stream: not a premiere yet to start, and not read as audio only. */
private val MediaInfo.canStream: Boolean
    get() = !isUpcoming && (!hasResolvedFormats || videoFormats.isNotEmpty())

/**
 * The site's front page, which is where a sign-in starts. Cookies are stored per site, so
 * the individual media address is trimmed away.
 */
