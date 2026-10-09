package com.hazel.android.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.hazel.android.data.DownloadHistoryRepository
import com.hazel.android.data.FailedDownload
import com.hazel.android.data.HistoryEntry
import com.hazel.android.data.SaveDirs
import com.hazel.android.download.DownloadOptions
import com.hazel.android.download.MediaInfo
import com.hazel.android.ui.components.MediaCard
import com.hazel.android.ui.components.gridColumns
import com.hazel.android.ui.components.gridItems
import com.hazel.android.ui.screens.download.batch.BatchDownloadSheet
import com.hazel.android.ui.screens.history.HistoryScreen
import com.hazel.android.ui.screens.queue.FailedCard
import com.hazel.android.ui.theme.HazelTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the lists of 16:9 cards on screens from a phone held upright to a wide tablet, so the
 * grid they turn into on a wide screen can be looked at beside the phone's single column.
 *
 * Each case checks the number of columns the width should give and writes a screenshot to
 * app/build/outputs/roborazzi/screens/<case>_<size>.png.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenSizeLayoutTest(private val size: String, private val qualifiers: String, private val columns: Int) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf("phone", "w360dp-h780dp-port-xxhdpi", 1),
            arrayOf("phone_land", "w780dp-h360dp-land-xxhdpi", 2),
            arrayOf("foldable", "w673dp-h841dp-port-xhdpi", 2),
            arrayOf("tablet", "w800dp-h1280dp-port-xhdpi", 2),
            arrayOf("tablet_land", "w1280dp-h800dp-land-xhdpi", 3),
            arrayOf("large_land", "w1600dp-h1000dp-land-mdpi", 4)
        )

        private const val URL = "https://www.youtube.com/watch?v=jNQXAC9IVRw"

        private val TITLES = listOf(
            "Me at the zoo", "Big Buck Bunny", "Sintel, the full film in 4K", "Tears of Steel",
            "Cosmos Laundromat", "Spring", "Agent 327: Operation Barbershop", "Coffee Run", "Sprite Fright"
        )
    }

    private val device = object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers(qualifiers)
        }
    }

    private val compose = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(device).around(compose)

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val results = TITLES.mapIndexed { i, title ->
        MediaInfo("$URL&i=$i", title, "Blender Studio", null, 60 + i * 37, emptyList(), emptyList())
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            HazelTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        compose.waitForIdle()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/screens/${name}_$size.png")
    }

    /** The list the home screen and the queue lay their cards out in, on the screen's width. */
    @Composable
    private fun <T> CardList(items: List<T>, spacing: androidx.compose.ui.unit.Dp, key: (T) -> Any, card: @Composable (T) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = gridColumns(maxWidth - 40.dp)
            check(columns == this@ScreenSizeLayoutTest.columns) { "$size laid out $columns columns" }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                gridItems(items, columns, spacing, key = key) { card(it) }
            }
        }
    }

    @Test
    fun homeResults() {
        show {
            CardList(results, 20.dp, key = { it.url }) { info ->
                MediaCard(info = info, isDownloading = false, alreadyDownloaded = info.durationSeconds % 2 == 0)
            }
        }
        capture("home_results")
    }

    @Test
    fun queueFailed() {
        val failed = results.mapIndexed { i, info ->
            FailedDownload(
                url = info.url, title = info.title, author = info.uploader, thumbnail = null, isVideo = i % 3 != 0,
                errorLog = "ERROR: [youtube] jNQXAC9IVRw: Video unavailable"
            )
        }
        show {
            CardList(failed, 12.dp, key = { it.url }) { item ->
                FailedCard(item = item, onOpen = {}, onViewLog = {}, onRetry = {}, onDismiss = {})
            }
        }
        capture("queue_failed")
    }

    @Test
    fun history() {
        runBlocking {
            DownloadHistoryRepository.clear(context)
            results.forEachIndexed { i, info ->
                DownloadHistoryRepository.record(
                    context,
                    HistoryEntry(
                        id = i + 1L, url = info.url, title = info.title, author = info.uploader,
                        thumbnail = null, durationSeconds = info.durationSeconds, fileName = "video$i.mp4",
                        fileUri = "", savedPath = "Download/Hazel/Video", isVideo = i % 3 != 0,
                        sizeBytes = 3_700_000L * (i + 1), completedAt = System.currentTimeMillis() - i * 7_200_000L
                    )
                )
            }
        }
        show { HistoryScreen() }
        capture("history")
    }

    @Test
    fun downloadAllSheet() {
        show {
            BatchDownloadSheet(
                results = results,
                options = DownloadOptions(),
                onOptionsChange = {},
                saveDirs = SaveDirs(),
                onOpenSaveDir = {},
                onPickSaveDir = { _, _ -> },
                onResetSaveDir = {},
                onResolveFormats = {},
                onRemove = {},
                onDownload = {},
                onDismiss = {}
            )
        }
        capture("download_all")
    }
}
