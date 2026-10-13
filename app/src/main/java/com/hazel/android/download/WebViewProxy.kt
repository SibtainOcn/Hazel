package com.hazel.android.download

import android.webkit.HttpAuthHandler
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

/**
 * Keeps the app's web views on the proxy in [NetworkProxy].
 *
 * Web views do their own networking, outside the process's proxy selector, so they are given
 * the proxy through the WebView library's override, which applies to every web view in the
 * app. The override is only touched once a proxy has been set, so a user who never sets one
 * never loads the WebView engine for it.
 *
 * A web view that cannot be put on the proxy must not load anything while one is set: a
 * device whose WebView has no override, or a SOCKS proxy that needs a password, which
 * WebView cannot give. [inStep] says whether pages may load.
 */
object WebViewProxy {

    /** The address last asked for; "" for none. Main thread. */
    private var requested = ""

    /** The address web views are known to be using; "" for none, null while unknown. */
    @Volatile
    private var applied: String? = ""

    /** Points web views at [spec], or back to a direct connection. Main thread. */
    fun apply(spec: NetworkProxy.Spec?) {
        val wanted = spec?.url.orEmpty()
        if (wanted == requested) return
        requested = wanted
        applied = null
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            // Nothing was ever overridden, so with no proxy web views connect as they always did.
            if (spec == null) applied = ""
            return
        }
        val controller = ProxyController.getInstance()
        val done = Runnable { if (requested == wanted) applied = wanted }
        runCatching {
            if (spec == null || !reachable(spec)) {
                controller.clearProxyOverride(Runnable::run, if (spec == null) done else Runnable {})
            } else {
                val config = ProxyConfig.Builder().addProxyRule(rule(spec)).build()
                controller.setProxyOverride(config, Runnable::run, done)
            }
        }
    }

    /** Whether web views may load now: they go through the proxy, or there is none. */
    fun inStep(): Boolean = applied == NetworkProxy.current()?.url.orEmpty()

    /**
     * Answers a web view's request for a password when it comes from the proxy. Returns
     * whether it was the proxy's, so the caller passes any other request on as before.
     */
    fun answerAuth(handler: HttpAuthHandler, host: String?): Boolean {
        val spec = NetworkProxy.current() ?: return false
        val user = spec.user ?: return false
        val proxyHost = spec.host.removeSurrounding("[", "]")
        val asked = host.orEmpty().removeSurrounding("[", "]")
        if (!asked.equals(proxyHost, ignoreCase = true) && !asked.equals("$proxyHost:${spec.port}", ignoreCase = true)) {
            return false
        }
        handler.proceed(user, spec.password.orEmpty())
        return true
    }

    /** WebView can answer an HTTP proxy's password request, never a SOCKS proxy's. */
    private fun reachable(spec: NetworkProxy.Spec): Boolean = !(spec.isSocks && spec.user != null)

    /** The rule as WebView reads it: no credentials, and its own name for SOCKS 5. */
    private fun rule(spec: NetworkProxy.Spec): String {
        val scheme = if (spec.isSocks) "socks5" else spec.scheme
        return "$scheme://${spec.host}:${spec.port}"
    }
}
