package com.hazel.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeResultsRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun card(n: Int) =
        """{"url":"https://example.com/v$n","title":"Video $n","uploader":"Someone","thumbnail":"","duration":60}"""

    @Test
    fun searchWordsComeBackWithTheirCards() {
        HomeResultsRepository.save(
            context,
            HomeResultsRepository.parse("[${card(1)},${card(2)}]").results,
            searchQuery = "mr beast"
        )
        val saved = HomeResultsRepository.load(context)
        assertEquals(2, saved.results.size)
        assertEquals("mr beast", saved.searchQuery)
    }

    @Test
    fun aSetOfLinksComesBackWithoutSearchWords() {
        HomeResultsRepository.save(context, HomeResultsRepository.parse("[${card(1)},${card(2)}]").results)
        assertEquals("", HomeResultsRepository.load(context).searchQuery)
    }

    @Test
    fun theOlderBareListStillLoads() {
        val saved = HomeResultsRepository.parse("[${card(1)},${card(2)},${card(3)}]")
        assertEquals(3, saved.results.size)
        assertEquals("", saved.searchQuery)
        assertEquals("Video 2", saved.results[1].title)
    }

    @Test
    fun nothingSavedLoadsEmpty() {
        HomeResultsRepository.clear(context)
        val saved = HomeResultsRepository.load(context)
        assertTrue(saved.results.isEmpty())
        assertEquals("", saved.searchQuery)
    }
}
