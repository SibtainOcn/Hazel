package com.hazel.android.download

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hazel.android.HazelApp
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Makes YouTube proof-of-origin tokens on the device, so the web clients' streams can be
 * reached without pasting tokens made elsewhere.
 *
 * The work is done by `assets/po_token.html` in a WebView nobody sees: YouTube's own
 * challenge has to run in a browser's JavaScript engine, and the WebView is the one every
 * device has. The page keeps what the challenge gave it for as long as it lasts, so only the
 * first token in a while takes a second or so and the rest take milliseconds. The WebView is
 * let go after [IDLE_MS] without use.
 */
object PoTokenGenerator {

    /** Tokens for one request: the visitor they belong to, and one per video ID asked for. */
    data class Minted(
        val visitorData: String,
        val sessionToken: String,
        val videoTokens: Map<String, String>
    )

    /** The outcome of the last attempt, for the settings screen. */
    data class Status(val ok: Boolean, val message: String, val at: Long)

    @Volatile
    var lastStatus: Status? = null
        private set

    private const val TAG = "HazelPoToken"
    private const val PAGE_ADDRESS = "https://www.youtube.com/"
    private const val IDLE_MS = 10 * 60 * 1000L
    private const val TIMEOUT_MS = 20_000L

    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<Int, (String?) -> Unit>()
    private val nextId = AtomicInteger()

    // Touched on the main thread only.
    private var webView: WebView? = null
    private var pageReady = false
    private val waitingForPage = mutableListOf<() -> Unit>()
    private val release = Runnable { destroy() }

    /**
     * Tokens for [videoIds], or null when none could be made in time. Blocks, so it is for
     * the worker threads that build requests; on the main thread it returns null at once.
     */
    fun mintBlocking(videoIds: List<String>, timeoutMs: Long = TIMEOUT_MS): Minted? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        val latch = CountDownLatch(1)
        var answer: String? = null
        val id = nextId.incrementAndGet()
        pending[id] = { text -> answer = text; latch.countDown() }
        main.post { run(id, videoIds) }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            pending.remove(id)
            record(false, "timed out")
            return null
        }
        return parse(answer, videoIds)
    }

    private fun parse(text: String?, videoIds: List<String>): Minted? {
        val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (json == null) {
            record(false, "no answer")
            return null
        }
        val error = json.optString("error")
        if (error.isNotBlank()) {
            record(false, error)
            return null
        }
        val tokens = json.optJSONObject("tokens")
        val minted = Minted(
            visitorData = json.optString("visitorData"),
            sessionToken = json.optString("poToken"),
            videoTokens = videoIds.mapNotNull { id ->
                tokens?.optString(id)?.takeIf { it.isNotBlank() }?.let { id to it }
            }.toMap()
        )
        if (minted.visitorData.isBlank() || minted.sessionToken.isBlank()) {
            record(false, "incomplete answer")
            return null
        }
        record(true, "")
        return minted
    }

    private fun record(ok: Boolean, message: String) {
        if (!ok) Log.w(TAG, "PO token not made: $message")
        lastStatus = Status(ok, message, System.currentTimeMillis())
    }

    /** Main thread. */
    private fun run(id: Int, videoIds: List<String>) {
        main.removeCallbacks(release)
        main.postDelayed(release, IDLE_MS)
        val view = webView ?: create() ?: run {
            pending.remove(id)?.invoke(null)
            return
        }
        val call = {
            val ids = JSONArray(videoIds).toString()
            view.evaluateJavascript(
                "hazelMint($ids).then(function (t) { HazelPoToken.onResult($id, t); });",
                null
            )
        }
        if (pageReady) call() else waitingForPage += call
    }

    /** Main thread. Null when the device has no working WebView. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView? = try {
        val context = HazelApp.instance
        val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
        WebView(context).apply {
            settings.javaScriptEnabled = true
            addJavascriptInterface(Bridge, "HazelPoToken")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (pageReady) return
                    pageReady = true
                    waitingForPage.toList().forEach { it() }
                    waitingForPage.clear()
                }
            }
            // Given YouTube's address so the page's requests to it are same-origin.
            loadDataWithBaseURL(PAGE_ADDRESS, html, "text/html", "utf-8", null)
        }.also { webView = it }
    } catch (e: Exception) {
        // A device without a WebView provider, or one being updated right now.
        record(false, e.message ?: e.javaClass.simpleName)
        null
    }

    /** Main thread. */
    private fun destroy() {
        webView?.destroy()
        webView = null
        pageReady = false
        waitingForPage.clear()
    }

    private object Bridge {
        @JavascriptInterface
        fun onResult(id: Int, text: String?) {
            pending.remove(id)?.invoke(text)
        }
    }
}
