package com.hazel.android.ui.screens.download

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.hazel.android.R
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SearchHistoryRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.BatchItem
import com.hazel.android.download.BatchState
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.DownloadViewModel
import com.hazel.android.download.MediaInfo
import com.hazel.android.download.formatDuration
import com.hazel.android.download.formatFileSize
import com.hazel.android.ui.components.MediaCardShimmer
import com.hazel.android.ui.components.rememberPresence
import com.hazel.android.ui.components.ProcessingShimmer
import com.hazel.android.ui.motion.M3Motion
import com.hazel.android.ui.screens.cookies.CookieWebViewActivity
import com.hazel.android.ui.screens.download.batch.BatchDownloadSheet
import com.hazel.android.util.FolderUtil
import com.hazel.android.util.LinkKey
import com.hazel.android.util.MediaOpener
import com.hazel.android.util.MediaStoreHelper
import com.hazel.android.util.StoragePaths
import kotlinx.coroutines.launch

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
    downloadViewModel: DownloadViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by downloadViewModel.state.collectAsState()

    val options by SettingsRepository.getDownloadOptions(context)
        .collectAsState(initial = DownloadOptions())
    val treeUri by SettingsRepository.getDownloadTreeUri(context).collectAsState(initial = "")
    val treeLabel by SettingsRepository.getDownloadTreeLabel(context).collectAsState(initial = "")

    // Large artwork or a tight list, for a set of links long enough that the difference
    // matters. A playlist can resolve to dozens of cards, and at one screen each the list
    // stops being something that can be looked over.
    val compact by SettingsRepository.getResultsCompact(context)
        .collectAsState(initial = false)

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
    val orderedResults = remember(state.results, state.info?.url, state.isDownloading) {
        val active = state.info?.url?.takeIf { state.isDownloading }
        if (active == null) state.results
        else state.results.sortedByDescending { it.url == active }
    }

    val listState = rememberLazyListState()

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
    var sheetVisible by remember { mutableStateOf(false) }
    var batchSheetVisible by remember { mutableStateOf(false) }

    // Picking a destination goes through the system document picker so the folder can sit
    // anywhere, including on removable storage. The grant is persisted so the same folder
    // stays writable on later launches.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                scope.launch {
                    SettingsRepository.setDownloadTree(
                        context, uri.toString(), MediaStoreHelper.describeTree(uri)
                    )
                }
            } catch (_: SecurityException) {
                // The provider refused a lasting grant, so the built-in folder stays in use.
            }
        }
    }

    // Coming back from a successful sign-in, the link is read again: the cookies that were
    // just saved are picked up by the new attempt.
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            downloadViewModel.fetchInfo()
        }
    }

    // Reading links clears the resolved media, which would otherwise pull a sheet out of
    // the tree mid-animation and show as a box flashing at the bottom of the screen.
    LaunchedEffect(state.isFetching) {
        if (state.isFetching) {
            sheetVisible = false
            batchSheetVisible = false
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

    var alreadyHave by remember { mutableStateOf<HistoryEntry?>(null) }

    // Marks a link that arrived from another app's share sheet. That is the one route into
    // the app that does not pass through the search screen, so it is the one whose repeat
    // warning is still raised here rather than there.
    var cameFromShare by remember { mutableStateOf(false) }

    // Set while a link shared to the direct target is being resolved. It suppresses the
    // sheet and the repeat warning, both of which are questions, and the point of that
    // target is that nothing is asked.
    var directPending by remember { mutableStateOf(false) }

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

            resolved != downloadViewModel.autoOpenedUrl && !state.isMultiple &&
                    !state.isDownloading && !state.isComplete -> {
                downloadViewModel.markAutoOpened(resolved)

                // A repeat is raised in the search screen, where the link is entered and
                // the answer is still cheap. A link shared in from another app never goes
                // through that screen, so it is the one case still checked here.
                val existing = if (cameFromShare && !directPending) {
                    history.firstOrNull { LinkKey.sameMedia(it.url, resolved) }
                        ?.takeIf { DownloadHistoryRepository.fileExists(context, it) }
                } else null

                when {
                    existing != null -> alreadyHave = existing
                    // The direct target downloads instead of opening the sheet. The effect
                    // below does that once the formats are in.
                    directPending -> Unit
                    else -> sheetVisible = true
                }
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
        //
        // The view model takes the direct ones rather than this screen reading them here.
        // Shares arrive faster than a link can be read, and a queue that lives on a screen
        // is one the next share arrives too early to join.
        val direct = shares.filter { it.direct }
        val asked = shares.filterNot { it.direct }

        direct.forEach { downloadViewModel.startDirect(context, it.url, it.source) }

        // The ordinary target opens the sheet, so those go through the usual read, which
        // already takes several links at once.
        if (asked.isNotEmpty()) {
            downloadViewModel.onUrlChange(asked.first().url)
            downloadViewModel.fetchAll(asked.map { it.url })
        }

        // Remembered the same way a typed link is. A link shared in is a link used, and the
        // entry screen offering back only what was typed there made the history look like it
        // had forgotten half of what the app had downloaded. Incognito is the one case that
        // is not recorded, which is its whole point.
        if (!incognito) shares.forEach { SearchHistoryRepository.record(context, it.url) }
    }

    // A link shared to the instant target reads and downloads itself, at the quality saved
    // in settings, without a sheet or a question. That runs in the view model rather than
    // here, so it survives this screen and so several shares in a row can queue up.

    if (guideSeen == false) {
        UserGuideDialog(
            onOpenBatterySettings = {
                scope.launch { SettingsRepository.setGuideSeen(context) }
                openBatterySettings(context)
            },
            onDismiss = { scope.launch { SettingsRepository.setGuideSeen(context) } }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // The field stays where it is while the results move under it. It is what the
            // screen is for, and a set of a hundred links used to carry it off the top of
            // the screen on the first flick.
            Spacer(modifier = Modifier.height(8.dp))
            UrlSearchBar(
                url = state.url,
                onOpenSearch = {
                    cameFromShare = false
                    searchOpen = true
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Kept out of the list with the field above it. It says how much the list
            // holds and switches how it is drawn, and both of those are worth reaching
            // without scrolling back to the top of a hundred links first.
            //
            // Shown from the first link. It was held back until there were two, on the
            // grounds that a single card is not a list, but that made the layout switch a
            // control which comes and goes, so nobody learns it is there and a single card
            // cannot be read as a line. The count says "1 link" for one of them.
            if (state.results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.download_links,
                            state.results.size,
                            state.results.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    // The list is kept for as long as the app runs, so there has to be a
                    // way of putting it down. Plain text rather than another icon: it
                    // throws away work, and that is worth spelling out.
                    Text(
                        stringResource(R.string.download_clear),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(enabled = !state.isDownloading) {
                                downloadViewModel.clearResults()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    IconButton(
                        onClick = {
                            scope.launch {
                                SettingsRepository.setResultsCompact(context, !compact)
                            }
                        }
                    ) {
                        Icon(
                            if (compact) Icons.Filled.GridView
                            else Icons.AutoMirrored.Filled.List,
                            contentDescription =
                                stringResource(if (compact) R.string.download_show_large_artwork else R.string.download_show_list),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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

            // A lazy list rather than a scrolling column: a column composes every card it
            // holds, artwork and all, so a playlist of a hundred built a hundred full width
            // images at once and ran the app out of memory on the way back from the compact
            // layout. This builds only what is on screen, whatever the list is holding.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    // Room for the action that floats over the list, on the same terms as
                    // the action itself.
                    bottom = if (pendingResults.size > 1) 96.dp else 32.dp
                )
            ) {
                // An instant share reads with nothing on screen to show for it, so the
                // same skeleton stands in, named after where the link came from.
                item(key = "instant") {
                    AnimatedVisibility(
                        visible = state.instantSource.isNotBlank(),
                        enter = M3Motion.contentEnter(),
                        exit = M3Motion.contentExit()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.download_instant_source, state.instantSource),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            MediaCardShimmer()
                        }
                    }
                }

                // While links are being read, a skeleton of the card stands in for them.
                item(key = "fetching") {
                    AnimatedVisibility(
                        visible = state.isFetching,
                        enter = M3Motion.contentEnter(),
                        exit = M3Motion.contentExit()
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
                            // One placeholder per link being read, so a set of links looks
                            // like the list it is about to become rather than like a single
                            // card. Capped, because a hundred placeholders say nothing more
                            // than a screenful of them does.
                            repeat(state.fetchCount.coerceIn(1, SHIMMER_CARD_LIMIT)) {
                                Spacer(modifier = Modifier.height(20.dp))
                                MediaCardShimmer()
                            }
                        }
                    }
                }

                items(orderedResults, key = { it.url }) { info ->
                    Spacer(modifier = Modifier.height(if (compact) 8.dp else 20.dp))

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
                    val isActive = state.isDownloading && state.info?.url == info.url

                    // A card that came from a listing carries no formats yet. Reading them
                    // starts with the sheet, so the wait happens against an open sheet
                    // rather than against a card that looks unresponsive.
                    val openSheet = {
                        downloadViewModel.selectResult(info)
                        downloadViewModel.resolveFormats(info)
                        sheetVisible = true
                    }
                    val remove = if (state.isMultiple && !runInHand) {
                        { downloadViewModel.removeResult(info) }
                    } else null

                    Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = entrance
                            translationY = (1f - entrance) * 28f
                        }
                    ) {
                    if (compact) {
                        MediaRow(
                            info = info,
                            isDownloading = isActive,
                            isProcessing = isActive && state.isProcessing,
                            progress = state.progress,
                            totalBytes = state.totalBytes,
                            isComplete = batchItem?.state == BatchState.DONE ||
                                    (!state.isMultiple && state.isComplete),
                            batchItem = batchItem,
                            waitingForWifi = state.waitingForWifi,
                            alreadyDownloaded = info.url in savedUrls,
                            onOpenSheet = openSheet,
                            onCancel = downloadViewModel::cancelDownload,
                            onPause = downloadViewModel::pauseDownload,
                            onResume = downloadViewModel::resumeDownload,
                            onRemove = remove
                        )
                    } else {
                        MediaCard(
                            info = info,
                            isDownloading = isActive,
                            isProcessing = isActive && state.isProcessing,
                            progress = state.progress,
                            totalBytes = state.totalBytes,
                            isComplete = batchItem?.state == BatchState.DONE ||
                                    (!state.isMultiple && state.isComplete),
                            batchItem = batchItem,
                            waitingForWifi = state.waitingForWifi,
                            alreadyDownloaded = info.url in savedUrls,
                            onOpenSheet = openSheet,
                            onCancel = downloadViewModel::cancelDownload,
                            onPause = downloadViewModel::pauseDownload,
                            onResume = downloadViewModel::resumeDownload,
                            onRemove = remove
                        )
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

        // One action for the whole set, which is the point of collecting links together.
        //
        // Offered on what is actually left to fetch rather than on how long the list is. A
        // list of two where one is already saved is one download, and a set action that
        // opens a sheet holding a single card is a set action that should not have been
        // there at all.
        if (pendingResults.size > 1 && !runInHand) {
            DownloadAllButton(
                onClick = { batchSheetVisible = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            )
        }
    }

    if (searchOpen) {
        SearchScreen(
            // Opened empty rather than prefilled: a prefilled field would filter the
            // history down to that one link, hiding every other link already used.
            initialQuery = "",
            onSearch = { queries ->
                searchOpen = false
                downloadViewModel.fetchAll(queries)
            },
            onClearResults = downloadViewModel::clearResults,
            onDismiss = { searchOpen = false }
        )
    }

    alreadyHave?.let { existing ->
        AlreadyDownloadedDialog(
            entry = existing,
            onPlay = { MediaOpener.play(context, existing.fileUri, existing.isVideo) },
            onOpenLocation = { MediaOpener.openLocation(context, treeUri) },
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

    val saveDirLabel = treeLabel.ifBlank { StoragePaths.DOWNLOADS_DISPLAY }

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
                saveDirLabel = saveDirLabel,
                isCustomSaveDir = treeUri.isNotBlank(),
                isLoadingFormats = state.isFetching || !info.hasResolvedFormats,
                onOpenSaveDir = { openSaveDir(context, treeUri) },
                onPickSaveDir = {
                    folderPicker.launch(treeUri.takeIf { it.isNotBlank() }?.let(Uri::parse))
                },
                onResetSaveDir = {
                    scope.launch { SettingsRepository.clearDownloadTree(context) }
                },
                onDownload = { format, audioLanguage, title, author ->
                    sheetVisible = false
                    downloadViewModel.startDownload(
                        context = context,
                        format = format,
                        options = options,
                        title = title,
                        author = author,
                        audioLanguage = audioLanguage,
                        treeUri = treeUri
                    )
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
            saveDirLabel = saveDirLabel,
            isCustomSaveDir = treeUri.isNotBlank(),
            onOpenSaveDir = { openSaveDir(context, treeUri) },
            onPickSaveDir = {
                folderPicker.launch(treeUri.takeIf { it.isNotBlank() }?.let(Uri::parse))
            },
            onResetSaveDir = {
                scope.launch { SettingsRepository.clearDownloadTree(context) }
            },
            onResolveFormats = downloadViewModel::resolveFormats,
            onRemove = downloadViewModel::removeResult,
            onDownload = { plans ->
                batchSheetVisible = false
                downloadViewModel.startBatch(context, plans, options, treeUri)
            },
            onDismiss = { batchSheetVisible = false }
        )
    }
}

private fun openSaveDir(context: android.content.Context, treeUri: String) {
    if (treeUri.isNotBlank()) FolderUtil.openTree(context, Uri.parse(treeUri))
    else FolderUtil.open(context, StoragePaths.finalDownloads)
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
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
 * carries the progress readout, and its centre becomes the cancel control.
 *
 * Once the transfer finishes and the streams are being merged and tagged, the cancel
 * control goes: there is no transfer left to stop, and offering to stop one would only
 * invite a tap that cannot do what it says. A sweep across the artwork takes its place, so
 * the card still reads as busy while that work runs.
 */
@Composable
private fun MediaCard(
    info: MediaInfo,
    isDownloading: Boolean,
    isProcessing: Boolean = false,
    progress: Float,
    totalBytes: Long,
    isComplete: Boolean,
    batchItem: BatchItem?,
    waitingForWifi: Boolean = false,
    alreadyDownloaded: Boolean = false,
    onOpenSheet: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onRemove: (() -> Unit)? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = M3Motion.emphasized(300),
        label = "cardProgress"
    )

    var menuOpen by remember { mutableStateOf(false) }
    val isPaused = batchItem?.state == BatchState.PAUSED

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = if (isDownloading) Modifier
            else Modifier.clickable(onClick = onOpenSheet)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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

                // Offered while the download is in hand, and while it is sitting paused.
                // It is the only way back from a pause, so it cannot go away with the
                // thing it undoes.
                if (isDownloading || isPaused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .zIndex(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { menuOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.download_options),
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            if (isPaused) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.download_resume)) },
                                    onClick = {
                                        menuOpen = false
                                        onResume()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.download_pause)) },
                                    // Nothing to pause once the transfer is done and the
                                    // engine has moved on to merging or tagging.
                                    enabled = !isProcessing,
                                    onClick = {
                                        menuOpen = false
                                        onPause()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_cancel)) },
                                onClick = {
                                    menuOpen = false
                                    onCancel()
                                }
                            )
                        }
                    }
                }

                // A paused download is still a download in hand, so the artwork keeps the
                // treatment that says so. Only the control in the middle changes: there is
                // nothing to stop any more, and the thing to do is start it again.
                if (isDownloading || isPaused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )

                    // Percentage readout, with the transferred size beside it once
                    // yt-dlp has reported a total. Both are about the transfer, so once
                    // that is done the corner just names the stage that follows.
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPaused) {
                            OverlayChip(text = stringResource(R.string.download_paused), bold = true)
                            if (totalBytes > 0) {
                                val done = (totalBytes * animatedProgress).toLong()
                                OverlayChip(
                                    text = "${formatFileSize(done)} / ${formatFileSize(totalBytes)}"
                                )
                            }
                        } else if (isProcessing) {
                            OverlayChip(text = stringResource(R.string.download_processing), bold = true)
                        } else {
                            OverlayChip(
                                text = "%.1f %%".format(animatedProgress * 100),
                                bold = true
                            )
                            if (totalBytes > 0) {
                                val done = (totalBytes * animatedProgress).toLong()
                                OverlayChip(
                                    text = "${formatFileSize(done)} / ${formatFileSize(totalBytes)}"
                                )
                            }
                        }
                    }

                    if (isProcessing && !isPaused) {
                        ProcessingShimmer(modifier = Modifier.fillMaxSize())
                    } else {
                        // A progress ring wrapping the cancel control. It is the only
                        // tappable area while a download runs, so a stray tap cannot
                        // reopen the sheet.
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable(onClick = if (isPaused) onResume else onCancel),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(60.dp),
                                color = Color.White,
                                trackColor = Color.Transparent,
                                strokeWidth = 3.dp
                            )
                            Icon(
                                if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Close,
                                contentDescription =
                                    stringResource(if (isPaused) R.string.download_resume_action else R.string.download_cancel_action),
                                modifier = Modifier.size(22.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                // Held back for want of Wi-Fi. The artwork is darkened exactly as a
                // running download darkens it, because the card is in hand either way, and
                // the middle says what it is waiting for. Said here rather than as a line of
                // red text above the list: nothing failed, and the wait belongs to this item
                // rather than to the screen.
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

                // Removing a link from the set, offered only while the set is idle. A
                // paused item counts as busy: the run as a whole has stopped, so the set is
                // idle by the only measure this screen has, and the remove control was
                // being drawn straight on top of the menu that resumes it.
                if (onRemove != null && !isDownloading && !isPaused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable(onClick = onRemove),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.download_remove_link),
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }

                // Bottom right corner carries the duration, and whatever this link's state
                // is worth saying: queued, failed, or saved.
                if (!isDownloading) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val duration = formatDuration(info.durationSeconds)
                        if (duration.isNotBlank()) {
                            CornerTag(text = duration)
                        }
                        when {
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
                            // Marks a link in a set that has been downloaded before, where
                            // there is no dialog to raise it.
                            alreadyDownloaded -> CornerTag(text = stringResource(R.string.download_downloaded))
                        }
                    }
                }

                // Filled line along the bottom edge of the thumbnail. Processing has no
                // figure to fill it with, so the line runs on its own there.
                if (isDownloading) {
                    val lineModifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)

                    if (isProcessing) {
                        LinearProgressIndicator(
                            modifier = lineModifier,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = lineModifier,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            drawStopIndicator = {}
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (info.uploader.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        info.uploader,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


/**
 * One resolved link as a single line, for a set too long to browse as artwork.
 *
 * It carries the same controls as the card, in the same order, so switching layout changes
 * how much fits on screen and nothing else. While this item downloads, its progress runs
 * along the bottom edge and the artwork holds the cancel control, exactly as the card does.
 */
@Composable
private fun MediaRow(
    info: MediaInfo,
    isDownloading: Boolean,
    isProcessing: Boolean,
    progress: Float,
    totalBytes: Long,
    isComplete: Boolean,
    batchItem: BatchItem?,
    waitingForWifi: Boolean = false,
    alreadyDownloaded: Boolean,
    onOpenSheet: () -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onRemove: (() -> Unit)?
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = M3Motion.emphasized(300),
        label = "rowProgress"
    )

    var menuOpen by remember { mutableStateOf(false) }
    val isPaused = batchItem?.state == BatchState.PAUSED
    val inHand = isDownloading || isPaused

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = if (inHand) Modifier else Modifier.clickable(onClick = onOpenSheet)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 104.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
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
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    }

                    // A paused item keeps the treatment that says it is in hand, exactly as
                    // the card does. Only the control in the middle changes: there is
                    // nothing to stop any more, and the thing to do is start it again.
                    if (inHand) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        )
                        if (isProcessing && !isPaused) {
                            ProcessingShimmer(modifier = Modifier.fillMaxSize())
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable(onClick = if (isPaused) onResume else onCancel),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Close,
                                    contentDescription = stringResource(
                                        if (isPaused) R.string.download_resume_action
                                        else R.string.download_cancel_action
                                    ),
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        val duration = formatDuration(info.durationSeconds)
                        if (duration.isNotBlank()) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    duration,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        info.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info.uploader.isNotBlank()) {
                        Text(
                            info.uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val pausedLabel = stringResource(R.string.download_paused)
                    val state = when {
                        isPaused -> buildString {
                            append(pausedLabel)
                            if (totalBytes > 0) {
                                val done = (totalBytes * animatedProgress).toLong()
                                append("  ")
                                append(formatFileSize(done))
                                append(" / ")
                                append(formatFileSize(totalBytes))
                            }
                        }
                        waitingForWifi && !isDownloading ->
                            stringResource(R.string.download_waiting_wifi)
                        isProcessing -> stringResource(R.string.download_processing)
                        // The same line the card shows: how far along, and how far there is
                        // to go. A percentage on its own says nothing about whether the
                        // wait is thirty seconds or ten minutes.
                        isDownloading -> buildString {
                            append("%.1f %%".format(animatedProgress * 100))
                            if (totalBytes > 0) {
                                val done = (totalBytes * animatedProgress).toLong()
                                append("  ")
                                append(formatFileSize(done))
                                append(" / ")
                                append(formatFileSize(totalBytes))
                            }
                        }
                        batchItem?.state == BatchState.FAILED -> batchItem.error ?: stringResource(R.string.download_failed)
                        isComplete -> stringResource(R.string.download_saved)
                        batchItem?.state == BatchState.QUEUED -> stringResource(R.string.download_queued)
                        alreadyDownloaded -> stringResource(R.string.download_downloaded)
                        else -> ""
                    }
                    if (state.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            state,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (batchItem?.state == BatchState.FAILED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // The same options the card offers, in the same order. They used to be on
                // the card alone, so switching to this layout mid-download took away every
                // way of pausing or resuming the thing being watched. They sit at the end
                // of the line rather than over the artwork, which at this size is too small
                // to hold a control on top of the one already in the middle of it.
                when {
                    inHand -> Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.download_options),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            if (isPaused) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.download_resume)) },
                                    onClick = {
                                        menuOpen = false
                                        onResume()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.download_pause)) },
                                    // Nothing to pause once the transfer is done and the
                                    // engine has moved on to merging or tagging.
                                    enabled = !isProcessing,
                                    onClick = {
                                        menuOpen = false
                                        onPause()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.download_cancel)) },
                                onClick = {
                                    menuOpen = false
                                    onCancel()
                                }
                            )
                        }
                    }

                    onRemove != null -> IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.download_remove_link),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Drawn while the item is paused as well, because a bar that vanishes on a
            // pause takes with it the only sign of how much of the file is already down.
            if (inHand) {
                if (isProcessing && !isPaused) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = if (isPaused) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        trackColor = Color.Transparent,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

/**
 * How many links there have to be before the layout toggle is offered. Below this the two
 * layouts read much the same, and the control is one more thing on screen for no gain.
 */
/**
 * How many stand-in cards a read shows at most. A long playlist reports its whole
 * length, and a placeholder for every entry of it is a screenful of the same shape
 * repeated, which says nothing the first few do not.
 */
private const val SHIMMER_CARD_LIMIT = 6

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
private fun siteRootOf(url: String): String = runCatching {
    val parsed = java.net.URL(url)
    "${parsed.protocol}://${parsed.host}"
}.getOrDefault(url)

private fun copyToClipboard(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Hazel log", text))
}
