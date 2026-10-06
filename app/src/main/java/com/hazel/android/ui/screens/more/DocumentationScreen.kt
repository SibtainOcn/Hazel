package com.hazel.android.ui.screens.more

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hazel.android.R
import kotlinx.coroutines.launch

/**
 * One page of the documentation: what the list calls it, and where it lives.
 *
 * The pages are the project's own website and repository, read in the app rather than
 * copied into it, so they are always the current version and say the same as the site.
 */
enum class DocPage(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val url: String,
    /**
     * The file itself, for the pages that are a file in the repository. Read directly and
     * drawn by [MarkdownLite], it opens in a moment rather than inside the whole GitHub page.
     */
    val rawUrl: String? = null,
    /** True for a plain text file, kept as it is laid out rather than read as Markdown. */
    val plain: Boolean = false
) {
    /** The site's front page; the pages below each open one part of it. */
    SITE_HOME(R.string.docs_site, Icons.Filled.Language, "${SITE}index.html"),
    GUIDE(R.string.docs_guide, Icons.AutoMirrored.Filled.MenuBook, "${SITE}guide.html"),
    FEATURES(R.string.docs_features, Icons.Filled.AutoAwesome, "${SITE}index.html#features"),
    FAQ(R.string.docs_faq, Icons.Filled.QuestionAnswer, "${SITE}faq.html"),
    CHANGELOG(R.string.docs_changelog, Icons.Filled.History, "$REPO/blob/main/CHANGELOG.md", "$RAW/CHANGELOG.md"),
    LICENSE(R.string.docs_license, Icons.Filled.Gavel, "$REPO/blob/main/LICENSE", "$RAW/LICENSE", plain = true);

    /** [url], a link to this file on the main branch, pointed at release [tag] instead. */
    fun atTag(url: String, tag: String): String = url.replace("/main/", "/$tag/")

    companion object {
        fun fromName(name: String?): DocPage = entries.firstOrNull { it.name == name } ?: GUIDE
    }
}

private const val SITE = "https://sibtainocn.github.io/Hazel/hazel/"
private const val REPO = "https://github.com/SibtainOcn/Hazel"
private const val RAW = "https://raw.githubusercontent.com/SibtainOcn/Hazel/main"

/** Files read this session, so opening one again draws at once. */
private val fetched = java.util.concurrent.ConcurrentHashMap<String, String>()

/** The text of [url], or null when it could not be read. */
private suspend fun fetchText(url: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    fetched[url]?.let { return@withContext it }
    runCatching {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        try {
            if (connection.responseCode != 200) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()?.also { fetched[url] = it }
}

private fun css(color: Color): String =
    "rgba(${(color.red * 255).toInt()},${(color.green * 255).toInt()},${(color.blue * 255).toInt()},${color.alpha})"

/** The list of documentation pages, under More. */
@Composable
fun DocumentationScreen(
    onBack: () -> Unit,
    onOpen: (DocPage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        DocsHeader(title = stringResource(R.string.docs_title), onBack = onBack)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            DocPage.entries.forEachIndexed { index, page ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(page.labelRes)) },
                    leadingContent = { Icon(page.icon, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOpen(page) }
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * One documentation page, read in the app.
 *
 * Pages of the project's site and repository stay here, so following a link from the guide
 * to the FAQ does not leave the app; anything else (a store page, another project) opens in
 * the user's browser, where it belongs. The page is drawn in the app's light or dark tone,
 * Back walks back through the pages read before leaving, and the browser button opens the
 * current page outside the app, for saving or sharing it. A page that cannot load says so
 * with a way to try again, rather than leaving a blank screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocumentationPageScreen(
    page: DocPage,
    onBack: () -> Unit,
    /**
     * A release whose copy of the page to show, such as "1.1.12", for What's new opened from
     * the update screen. Its tagged copy is read first; one with no release of its own (a
     * nightly build) falls back to the current page.
     */
    version: String? = null
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val background = scheme.background
    val dark = background.luminance() < 0.5f
    val scope = rememberCoroutineScope()

    // A repository file is read directly and drawn here; a site page, or a file that could
    // not be read, is loaded as the page itself.
    val tag = version?.trim()?.removePrefix("v")?.removePrefix("V")?.takeIf { it.isNotBlank() }?.let { "v$it" }
    fun load(view: WebView) {
        val raw = page.rawUrl ?: return view.loadUrl(page.url)
        scope.launch {
            val tagged = tag?.let { fetchText(page.atTag(raw, it))?.let { text -> text to page.atTag(page.url, it) } }
            val (text, url) = tagged ?: (fetchText(raw) to page.url)
            if (text == null) {
                view.loadUrl(url)
            } else {
                val html = MarkdownLite.page(
                    text, css(background), css(scheme.onBackground), css(scheme.onSurfaceVariant), dark,
                    plain = page.plain
                )
                view.loadDataWithBaseURL(url, html, "text/html", "utf-8", null)
            }
        }
    }

    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(page.url) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val scroll = remember { com.hazel.android.ui.components.WebScrollState() }

    fun leave() {
        val view = webView
        if (view != null && view.canGoBack() && !failed) view.goBack() else onBack()
    }
    BackHandler { leave() }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
            webView = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
            DocsHeader(
                title = stringResource(page.labelRes),
                onBack = { leave() },
                trailing = {
                    IconButton(onClick = { openOutside(context, currentUrl) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.docs_open_in_browser),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    com.hazel.android.ui.components.TrackedWebView(viewContext, scroll).apply {
                        setBackgroundColor(background.toArgb())
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url.toString()
                                if (isOurs(url) && page.rawUrl == null) return false
                                openOutside(view.context, url)
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                loading = true
                                if (url.startsWith("http")) currentUrl = url
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                // The site keeps its own theme choice; it is set to the app's
                                // tone so the page reads as part of the app.
                                val theme = if (dark) "dark" else "light"
                                view.evaluateJavascript(
                                    "try{localStorage.setItem('hazel-theme','$theme');" +
                                        "document.documentElement.removeAttribute('data-theme-auto');" +
                                        "document.documentElement.setAttribute('data-theme','$theme')}catch(e){}",
                                    null
                                )
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    failed = true
                                    loading = false
                                }
                            }
                        }
                        load(this)
                        webView = this
                    }
                }
            )

            if (!loading && !failed) {
                com.hazel.android.ui.components.FastScrollbar(
                    scroll,
                    Modifier.align(Alignment.TopEnd),
                    androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp)
                )
            }

            // Until the page has drawn, the shape loader stands over it, so the screen is
            // never a blank sheet while the page or file is fetched.
            if (loading && !failed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(background),
                    contentAlignment = Alignment.Center
                ) {
                    com.hazel.android.ui.components.HazelLoadingIndicator(size = 56.dp)
                }
            }

            if (failed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Text(
                                stringResource(R.string.docs_error_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.docs_error_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = {
                                failed = false
                                loading = true
                                webView?.let { load(it) }
                            }) { Text(stringResource(R.string.docs_retry)) }
                        }
                    }
                }
            }
        }
    }
}

/** The header every documentation screen shares, in the style of the other More screens. */
@Composable
private fun DocsHeader(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(R.drawable.back),
                contentDescription = stringResource(R.string.docs_back),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/** The project's own pages, which are read in the app; everything else goes to the browser. */
private fun isOurs(url: String): Boolean =
    url.startsWith(SITE) || url.startsWith("$REPO/") || url == REPO

private fun openOutside(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
