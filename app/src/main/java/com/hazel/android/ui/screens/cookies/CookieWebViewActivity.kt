package com.hazel.android.ui.screens.cookies

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.hazel.android.data.CookieEntry
import com.hazel.android.data.CookieRepository
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.theme.HazelTheme
import com.hazel.android.util.CookieExtractor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Signs in to a site so its cookies can be handed to yt-dlp.
 *
 * The user logs in exactly as they would in a browser. Confirming with the tick reads the
 * cookies the session left behind, stores them under the site's address, and returns
 * [Activity.RESULT_OK] so the caller can retry whatever needed them.
 *
 * Cookies are cleared on entry, so each sign-in starts from a clean session and one site's
 * cookies can never end up filed under another.
 */
class CookieWebViewActivity : ComponentActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }
            ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        if (savedInstanceState == null) CookieExtractor.clearAll()

        setContent {
            val savedTheme by SettingsRepository.isDarkTheme(this).collectAsState(initial = null)
            val accentName by SettingsRepository.getAccentColor(this).collectAsState(initial = null)

            HazelTheme(
                darkTheme = savedTheme ?: true,
                accentName = accentName ?: return@setContent
            ) {
                CookieWebViewScreen(url = url, title = title)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun CookieWebViewScreen(url: String, title: String) {
        var pageTitle by remember { mutableStateOf(url) }
        var progress by remember { mutableStateOf(0) }
        var desktopMode by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }

        BackHandler(enabled = true) {
            val view = webView
            if (view != null && view.canGoBack()) view.goBack() else finish()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    },
                    title = {
                        Text(
                            pageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                desktopMode = !desktopMode
                                webView?.let {
                                    applyDesktopMode(it, desktopMode)
                                    it.reload()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.DesktopWindows,
                                contentDescription = "Desktop site",
                                tint = if (desktopMode) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            onClick = {
                                if (!saving) {
                                    saving = true
                                    saveCookies(url, title) { saving = false }
                                }
                            },
                            enabled = !saving,
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    androidx.compose.foundation.layout.Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "  OK",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            configure(this)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    super.onPageFinished(view, finishedUrl)
                                    pageTitle = view?.title?.takeIf { it.isNotBlank() }
                                        ?: finishedUrl.orEmpty()
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            webView = this
                            loadUrl(url)
                        }
                    }
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
    }

    /**
     * Some sites only offer the sign-in form to a desktop browser, so the user agent can be
     * switched to one.
     */
    private fun applyDesktopMode(view: WebView, desktop: Boolean) {
        view.settings.apply {
            if (desktop) {
                userAgentString = DESKTOP_USER_AGENT
                useWideViewPort = true
                loadWithOverviewMode = true
            } else {
                userAgentString = WebSettings.getDefaultUserAgent(view.context)
                useWideViewPort = false
                loadWithOverviewMode = false
            }
        }
    }

    private fun saveCookies(url: String, title: String, onFailed: () -> Unit) {
        lifecycleScope.launch {
            val result = CookieExtractor.extract(this@CookieWebViewActivity, url)
            val content = result.getOrNull()

            if (content.isNullOrBlank()) {
                Toast.makeText(
                    this@CookieWebViewActivity,
                    result.exceptionOrNull()?.message ?: "No cookies were collected",
                    Toast.LENGTH_LONG
                ).show()
                onFailed()
                return@launch
            }

            val existing = CookieRepository.getEntries(this@CookieWebViewActivity).first()
                .firstOrNull { it.url == url && it.title == title }

            CookieRepository.upsert(
                this@CookieWebViewActivity,
                CookieEntry(
                    id = existing?.id ?: System.currentTimeMillis(),
                    url = url,
                    title = title,
                    content = content,
                    enabled = true
                )
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

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** Intent that opens the sign-in page for [url]. */
        fun intent(context: Context, url: String, title: String = ""): Intent =
            Intent(context, CookieWebViewActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }
}
