package com.hazel.android.ui.screens.cookies

import androidx.compose.material3.ButtonDefaults
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.hazel.android.R
import com.hazel.android.data.CookieEntry
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.download.DownloadNotificationHelper
import com.hazel.android.download.NetworkProxy
import com.hazel.android.download.WebViewProxy
import com.hazel.android.ui.components.DropdownMenu
import com.hazel.android.ui.components.DropdownMenuItem
import com.hazel.android.ui.components.HazelLoadingIndicator
import com.hazel.android.ui.components.MenuTone
import com.hazel.android.ui.components.ShapeIconButton
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.ui.theme.sheetButtonColor
import com.hazel.android.ui.theme.tonalFill
import com.hazel.android.util.CookieExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Signs in to a site so its cookies can be handed to yt-dlp.
 *
 * The user logs in exactly as they would in a browser. Save sign-in reads the
 * cookies the session left behind, stores them under the site's address, and returns
 * [Activity.RESULT_OK] so the caller can retry whatever needed them.
 *
 * Cookies are cleared on entry, so each sign-in starts from a clean session and one site's
 * cookies can never end up filed under another.
 *
 * The screen and its loader are drawn first. Clearing the cookies and making the WebView
 * both start the browser engine, which holds the main thread for a moment the first time,
 * so they wait for that first frame rather than keeping the screen from appearing at all.
 */
class CookieWebViewActivity : ComponentActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }
            ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        // Opened from a failure notification's Sign in: that notification is answered.
        if (intent.getBooleanExtra(EXTRA_FROM_FAILURE, false)) DownloadNotificationHelper.cancelFailure(this)

        // A fresh sign-in starts from no cookies; a screen rebuilt after a rotation keeps
        // the session it was in the middle of.
        val clearFirst = savedInstanceState == null

        setContent {
            val savedTheme by remember(this) { SettingsRepository.isDarkTheme(this) }.collectAsState(initial = null)
            val accentName by remember(this) { SettingsRepository.getAccentColor(this) }.collectAsState(initial = null)

            HazelTheme(
                darkTheme = savedTheme ?: true,
                accentName = accentName ?: return@setContent
            ) {
                CookieWebViewScreen(url = url, title = title, clearFirst = clearFirst, openEntry = intent.getLongExtra(EXTRA_OPEN_ENTRY, 0L))
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun CookieWebViewScreen(url: String, title: String, clearFirst: Boolean, openEntry: Long) {
        // An account opened from the Cookies screen starts on its site with its saved cookies.
        val firstPage = if (openEntry != 0L) url else SignInBrowser.startUrl(url)
        val siteKey = remember(url) { CookieRepository.siteOfUrl(url) }
        val siteName = title.ifBlank { SignInBrowser.nameOf(siteKey) ?: url }
        var currentUrl by remember { mutableStateOf(url) }
        var progress by remember { mutableIntStateOf(0) }
        // The browser is started once the screen is up, and the loader stays until the
        // first page has something to show.
        var browserReady by remember { mutableStateOf(false) }
        var firstPaint by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            // With a proxy set, the sign-in page goes through it or does not open: signing in
            // from the device's own address would show the site an address the downloads
            // never come from, and give away the one the proxy is there to hide.
            if (!webViewOnProxy()) {
                Toast.makeText(this@CookieWebViewActivity, R.string.cookies_proxy_unavailable, Toast.LENGTH_LONG).show()
                finish()
                return@LaunchedEffect
            }
            if (clearFirst) {
                CookieExtractor.clearAll()
                if (openEntry != 0L) {
                    CookieRepository.getEntries(this@CookieWebViewActivity).first()
                        .firstOrNull { it.id == openEntry }
                        ?.let { CookieExtractor.load(it.content) }
                }
            }
            browserReady = true
        }
        var desktopMode by remember { mutableStateOf(false) }
        var textZoom by remember { mutableIntStateOf(100) }
        var menuOpen by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        // Solid once the site has set its login cookie; sites not known are always solid.
        var signedIn by remember { mutableStateOf(false) }
        // The main page failed to load (offline, refused): shown with Retry instead of a blank page.
        var loadFailed by remember { mutableStateOf(false) }
        val scheme = MaterialTheme.colorScheme

        BackHandler(enabled = true) {
            val view = webView
            if (view != null && view.canGoBack()) view.goBack() else finish()
        }

        fun setDesktop(on: Boolean) {
            desktopMode = on
            webView?.let {
                applyDesktopMode(it, on)
                it.reload()
            }
        }

        Scaffold(
            containerColor = scheme.background,
            topBar = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    ShapeIconButton(onClick = { finish() }, size = 46.dp, shape = MaterialShapes.Cookie9Sided.toShape()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cookies_back))
                    }
                    Surface(shape = RoundedCornerShape(22.dp), color = tonalFill(0.6f), modifier = Modifier.weight(1f).heightIn(min = 46.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp), verticalArrangement = Arrangement.Center) {
                            Text(siteName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when {
                                    loadFailed -> stringResource(R.string.cookie_webview_not_loaded)
                                    desktopMode -> stringResource(R.string.cookie_webview_desktop) + " · " + hostOf(currentUrl)
                                    else -> hostOf(currentUrl) + pathOf(currentUrl)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    ShapeIconButton(onClick = { loadFailed = false; webView?.reload() }, size = 46.dp, shape = MaterialShapes.Cookie6Sided.toShape()) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cookie_webview_reload))
                    }
                    Box {
                        Surface(
                            onClick = { menuOpen = true },
                            shape = RoundedCornerShape(50),
                            color = if (menuOpen) scheme.primary.copy(alpha = 0.22f) else sheetButtonColor,
                            contentColor = if (menuOpen) scheme.primary else scheme.onSurface,
                            modifier = Modifier.size(width = 34.dp, height = 46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.cookies_menu))
                            }
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cookie_webview_desktop)) },
                                icon = Icons.Filled.DesktopWindows,
                                tone = MenuTone.Accent,
                                selected = desktopMode,
                                trailingIcon = { MenuValue(stringResource(if (desktopMode) R.string.cookie_webview_on else R.string.cookie_webview_off)) },
                                onClick = { menuOpen = false; setDesktop(!desktopMode) }
                            )
                            TextSizeRow(textZoom) { size ->
                                textZoom = size
                                webView?.settings?.textZoom = size
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cookie_webview_go_sign_in)) },
                                icon = Icons.AutoMirrored.Filled.Login,
                                tone = MenuTone.Neutral,
                                onClick = { menuOpen = false; loadFailed = false; webView?.loadUrl(SignInBrowser.startUrl(url)) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cookie_webview_clear_site)) },
                                icon = Icons.Filled.CleaningServices,
                                tone = MenuTone.Danger,
                                onClick = {
                                    menuOpen = false
                                    CookieExtractor.clearSite(currentUrl)
                                    signedIn = false
                                    Toast.makeText(this@CookieWebViewActivity, R.string.cookie_webview_cleared, Toast.LENGTH_SHORT).show()
                                    webView?.loadUrl(SignInBrowser.startUrl(url))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cookie_webview_copy_address)) },
                                icon = Icons.Filled.Link,
                                tone = MenuTone.Neutral,
                                onClick = {
                                    menuOpen = false
                                    (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)
                                        ?.setPrimaryClip(ClipData.newPlainText("Hazel", currentUrl))
                                    Toast.makeText(this@CookieWebViewActivity, R.string.cookies_toast_exported, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val background = scheme.background.toArgb()
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (browserReady) AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            configure(this)
                            // The screen's own tone until the page paints, not a black box.
                            setBackgroundColor(background)
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedHttpAuthRequest(
                                    view: WebView?,
                                    handler: android.webkit.HttpAuthHandler,
                                    host: String?,
                                    realm: String?
                                ) {
                                    if (!WebViewProxy.answerAuth(handler, host)) {
                                        super.onReceivedHttpAuthRequest(view, handler, host, realm)
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val target = request?.url?.toString()
                                    if (SignInBrowser.opensInPage(target)) return false
                                    // An "open the app" link: show the web page it stands for.
                                    SignInBrowser.webFallback(target)?.let { view?.loadUrl(it) }
                                    return true
                                }

                                override fun onPageStarted(view: WebView?, startedUrl: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, startedUrl, favicon)
                                    loadFailed = false
                                    startedUrl?.let { currentUrl = it }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame != true) return
                                    android.util.Log.w("HazelSignIn", "Page failed: ${error?.errorCode} ${error?.description}")
                                    loadFailed = true
                                    firstPaint = true
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: android.webkit.RenderProcessGoneDetail?
                                ): Boolean {
                                    if (view != null) {
                                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                                        view.destroy()
                                    }
                                    if (webView === view) webView = null
                                    // Reclaimed by the system for memory: open again where it was.
                                    // A real crash is not retried, so it cannot loop.
                                    if (Build.VERSION.SDK_INT >= 26 && detail?.didCrash() == false) {
                                        recreate()
                                    } else {
                                        Toast.makeText(this@CookieWebViewActivity, R.string.cookie_webview_crashed, Toast.LENGTH_LONG).show()
                                        finish()
                                    }
                                    return true
                                }

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    firstPaint = true
                                    finishedUrl?.let { currentUrl = it }
                                    signedIn = hasLoginCookie(siteKey)
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                    if (newProgress >= 100) firstPaint = true
                                }
                            }
                            webView = this
                            loadUrl(firstPage)
                        }
                    }
                )
                // Over the page rather than above it, so the page is not resized while it loads.
                if (firstPaint && progress in 1..99 && !loadFailed) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    )
                }
                if (loadFailed) {
                    Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialShapes.Cookie9Sided.toShape())
                                    .background(tonalFill(0.8f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.cookie_webview_load_failed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.cookie_webview_load_failed_why),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { setDesktop(!desktopMode) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = sheetButtonColor, contentColor = MaterialTheme.colorScheme.onSurface)) {
                                    Text(stringResource(if (desktopMode) R.string.cookie_webview_mobile else R.string.cookie_webview_desktop))
                                }
                                Button(onClick = { loadFailed = false; webView?.reload() }, shape = RoundedCornerShape(16.dp)) {
                                    Text(stringResource(R.string.history_failed_retry))
                                }
                            }
                        }
                    }
                }
                if (!firstPaint) {
                    HazelLoadingIndicator(size = 48.dp, modifier = Modifier.align(Alignment.Center))
                }
                if (!loadFailed) {
                    Surface(
                        onClick = {
                            if (!saving) {
                                saving = true
                                saveCookies(url, title, siteName) { saving = false }
                            }
                        },
                        enabled = !saving,
                        shape = RoundedCornerShape(28.dp),
                        color = if (signedIn) scheme.primary else scheme.primary.copy(alpha = 0.45f),
                        contentColor = scheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 16.dp, bottom = 18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.cookie_webview_save), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    /** Text size as a slider inside the menu, so a page can be sized while it stays in view. */
    @Composable
    private fun TextSizeRow(size: Int, onChange: (Int) -> Unit) {
        val scheme = MaterialTheme.colorScheme
        Column(modifier = Modifier.widthIn(min = 240.dp).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FormatSize, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.cookie_webview_text_size), modifier = Modifier.weight(1f))
                MenuValue("$size%")
            }
            Slider(
                value = size.toFloat(),
                onValueChange = { onChange((it / TEXT_STEP).roundToInt() * TEXT_STEP) },
                valueRange = TEXT_MIN.toFloat()..TEXT_MAX.toFloat(),
                steps = (TEXT_MAX - TEXT_MIN) / TEXT_STEP - 1
            )
        }
    }

    @Composable
    private fun MenuValue(text: String) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }

    /** Whether the WebView holds [site]'s login cookie; true for a site whose cookie is not known. */
    private fun hasLoginCookie(site: String?): Boolean {
        val names = site?.let { CookieRepository.LOGIN_COOKIES[it] } ?: return true
        return runCatching {
            val manager = CookieManager.getInstance()
            LOGIN_HOSTS[site].orEmpty().plus(site).any { host ->
                manager.getCookie("https://$host").orEmpty().split(';')
                    .any { it.substringBefore('=').trim() in names }
            }
        }.getOrDefault(true)
    }

    private fun hostOf(address: String): String =
        runCatching { java.net.URI(address).host.orEmpty() }.getOrDefault("").removePrefix("www.")

    private fun pathOf(address: String): String =
        runCatching { java.net.URI(address).path.orEmpty() }.getOrDefault("").trimEnd('/')

    /**
     * Whether web views go through the proxy, or there is none. The setting is read off the
     * main thread first, in case storage has not been read yet; a proxy set a moment ago is
     * given a short while to take effect.
     */
    private suspend fun webViewOnProxy(): Boolean {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { NetworkProxy.current() }
        repeat(PROXY_WAIT_STEPS) {
            if (WebViewProxy.inStep()) return true
            kotlinx.coroutines.delay(PROXY_WAIT_STEP_MS)
        }
        return WebViewProxy.inStep()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }
        applyIdentity(view, desktop = false)
        // Without this every request names the app in X-Requested-With, marking it as a WebView.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(view.settings, emptySet())
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
    }

    /**
     * Some sites only offer the sign-in form to a desktop browser, so the user agent can be
     * switched to one. The wide page is zoomed out to fit the screen and can be pinched.
     */
    private fun applyDesktopMode(view: WebView, desktop: Boolean) {
        applyIdentity(view, desktop)
        view.settings.apply {
            useWideViewPort = desktop
            loadWithOverviewMode = desktop
        }
    }

    /** The user agent and its matching Client Hints, as a mobile or desktop Chrome. */
    private fun applyIdentity(view: WebView, desktop: Boolean) {
        val base = WebSettings.getDefaultUserAgent(view.context)
        view.settings.userAgentString =
            if (desktop) SignInBrowser.desktopUserAgent(base) else SignInBrowser.mobileUserAgent(base)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            runCatching { WebSettingsCompat.setUserAgentMetadata(view.settings, SignInBrowser.metadata(base, desktop)) }
        }
    }

    override fun onDestroy() {
        webView?.let {
            webView = null
            it.stopLoading()
            (it.parent as? android.view.ViewGroup)?.removeView(it)
            it.destroy()
        }
        super.onDestroy()
    }

    private fun saveCookies(url: String, title: String, fallbackName: String, onFailed: () -> Unit) {
        lifecycleScope.launch {
            val result = CookieExtractor.extract(this@CookieWebViewActivity, url)
            val content = result.getOrNull()

            if (content.isNullOrBlank()) {
                Toast.makeText(
                    this@CookieWebViewActivity,
                    result.exceptionOrNull()?.message
                        ?: getString(R.string.cookie_webview_no_cookies),
                    Toast.LENGTH_LONG
                ).show()
                onFailed()
                return@launch
            }

            val existing = CookieRepository.getEntries(this@CookieWebViewActivity).first()
                .firstOrNull { it.url == url && it.title == title }
            // A new sign-in with no name is named after its site, so no account reads as a bare address.
            val name = if (existing == null && title.isBlank()) fallbackName else title

            CookieRepository.upsert(
                this@CookieWebViewActivity,
                CookieEntry(
                    id = existing?.id ?: System.currentTimeMillis(),
                    url = url,
                    title = name,
                    content = content,
                    enabled = true
                )
            )
            // Kept with the cookies, because they were issued to this identity and are
            // only honoured when they come back under it.
            CookieRepository.setUserAgent(
                this@CookieWebViewActivity,
                webView?.settings?.userAgentString.orEmpty()
            )

            // Saving cookies is meaningless while the master switch is off, so confirming
            // here turns it on.
            CookieRepository.setUseCookies(this@CookieWebViewActivity, true)

            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_FROM_FAILURE = "from_failure"

        /** How long a proxy set a moment ago is given to reach web views: 20 x 100 ms. */
        private const val PROXY_WAIT_STEPS = 20
        private const val PROXY_WAIT_STEP_MS = 100L

        private const val TEXT_MIN = 50
        private const val TEXT_MAX = 200
        private const val TEXT_STEP = 10
        const val EXTRA_OPEN_ENTRY = "open_entry"

        /** Hosts a site keeps its login cookie on, besides its own key. */
        private val LOGIN_HOSTS = mapOf(
            "twitter.com" to listOf("x.com"),
            "youtube.com" to listOf("www.youtube.com", "google.com"),
            "instagram.com" to listOf("www.instagram.com"),
            "facebook.com" to listOf("www.facebook.com"),
            "tiktok.com" to listOf("www.tiktok.com"),
            "reddit.com" to listOf("www.reddit.com")
        )

        /** Intent that opens the sign-in page for [url]. */
        /** Intent that opens a saved account's site with its cookies, to browse or refresh it. */
        fun openIntent(context: Context, entry: CookieEntry): Intent =
            intent(context, entry.url, entry.title).putExtra(EXTRA_OPEN_ENTRY, entry.id)

        fun intent(context: Context, url: String, title: String = ""): Intent =
            Intent(context, CookieWebViewActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }
}
