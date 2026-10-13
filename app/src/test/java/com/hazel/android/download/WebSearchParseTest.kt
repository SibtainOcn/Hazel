package com.hazel.android.download

import com.hazel.android.download.extractor.WebSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Reading DuckDuckGo's plain results page and a SearXNG answer, and checking addresses. */
class WebSearchParseTest {

    /** Cut down from a real html.duckduckgo.com page: two results, an ad, and the next-page form. */
    private val page = """
        <div class="result results_links results_links_deep web-result ">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fm.youtube.com%2Fwatch%3Fv%3DBCxTQq0UiFs&amp;rut=2189">Lofi Hip Hop &amp; <b>Chill</b> - YouTube</a>
          </h2>
        </div>
        <div class="result results_links result--ad">
          <a rel="nofollow" class="result__a" href="https://duckduckgo.com/y.js?ad_domain=shop.example">Buy now</a>
        </div>
        <div class="result">
          <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.dailymotion.com%2Fvideo%2Fx8u5ftg&amp;rut=3249">Rainy night &#x27;lofi&#x27;</a>
        </div>
        <div class="nav-link">
          <form action="/html/" method="post">
            <input type="submit" class='btn btn--alt' value="Next" />
            <input type="hidden" name="q" value="lofi hip hop" />
            <input type="hidden" name="s" value="10" />
            <input type="hidden" name="nextParams" value="" />
            <input type="hidden" name="dc" value="11" />
            <input type="hidden" name="vqd" value="4-2529" />
            <input name="kl" value="wt-wt" type="hidden" />
          </form>
        </div>
    """.trimIndent()

    @Test
    fun readsResultsAndUnwrapsTheirLinks() {
        val parsed = WebSearch.parseDuckDuckGo(page)
        assertEquals(
            listOf("https://m.youtube.com/watch?v=BCxTQq0UiFs", "https://www.dailymotion.com/video/x8u5ftg"),
            parsed.results.map { it.url }
        )
        assertEquals("Lofi Hip Hop & Chill - YouTube", parsed.results[0].title)
        assertEquals("Rainy night 'lofi'", parsed.results[1].title)
        assertEquals("dailymotion.com", parsed.results[1].uploader)
    }

    @Test
    fun keepsTheFormForTheNextPage() {
        val next = WebSearch.parseDuckDuckGo(page).next
        assertEquals("lofi hip hop", next?.get("q"))
        assertEquals("10", next?.get("s"))
        assertEquals("4-2529", next?.get("vqd"))
        assertEquals("wt-wt", next?.get("kl"))
        assertNull(next?.get("Next"))
    }

    @Test
    fun lastPageHasNoNext() {
        assertNull(WebSearch.parseDuckDuckGo("<html><body>No more results.</body></html>").next)
    }

    @Test
    fun readsSearxngJson() {
        val body = """
            {"results":[
              {"url":"https://vimeo.com/76979871","title":"The New Vimeo Player","author":"Vimeo Staff",
               "thumbnail":"https://i.vimeocdn.com/x.jpg","length":"1:02"},
              {"url":"https://www.youtube.com/watch?v=jNQXAC9IVRw","title":"Me at the zoo","img_src":"https://i.ytimg.com/a.jpg","length":"19:01:05"},
              {"url":"javascript:alert(1)","title":"bad"}
            ]}
        """.trimIndent()
        val results = WebSearch.parseSearxng(body)
        assertEquals(2, results.size)
        assertEquals("Vimeo Staff", results[0].uploader)
        assertEquals(62, results[0].durationSeconds)
        assertEquals("https://i.ytimg.com/a.jpg", results[1].thumbnail)
        assertEquals(19 * 3600 + 65, results[1].durationSeconds)
        assertEquals(emptyList(), WebSearch.parseSearxng("not json"))
    }

    @Test
    fun normalizesServerAddresses() {
        assertEquals("https://searx.example.org", WebSearch.normalizeServer("searx.example.org/"))
        assertEquals("http://192.168.1.5:8080", WebSearch.normalizeServer(" http://192.168.1.5:8080 "))
        assertNull(WebSearch.normalizeServer(""))
        assertNull(WebSearch.normalizeServer("ftp://searx.example.org"))
        assertNull(WebSearch.normalizeServer("https://"))
    }
}
