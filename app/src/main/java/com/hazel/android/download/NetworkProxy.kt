package com.hazel.android.download

import com.hazel.android.R
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLDecoder

/**
 * The proxy every connection the app makes goes through, when the user has set one.
 *
 * The app reaches the network four ways, and each is pointed at the proxy here:
 * - yt-dlp, a separate process, is given `--proxy` with every read and download
 *   ([applyAdvanced]).
 * - OkHttp clients (the NewPipe extractor, the in-app player, search suggestions, thumbnails,
 *   documentation pages, update checks) are built through [throughProxy].
 * - Anything else in the process, such as a library's own HttpURLConnection, asks the
 *   process-wide [ProxySelector], which [install] replaces with one that reads this setting.
 * - Web views (sign-in pages, PO tokens) are routed by [WebViewProxy].
 *
 * With a proxy set, a connection that cannot reach it fails rather than going out directly:
 * the selector offers the proxy alone, with nothing to fall back to. With none set, the
 * selector hands every question to the one the system had, so a proxy set for the Wi-Fi
 * network still applies as it did before.
 */
object NetworkProxy {

    /** A proxy address that passed [parse]. */
    data class Spec(
        /** One of [SCHEMES], lower case. */
        val scheme: String,
        val host: String,
        val port: Int,
        val user: String?,
        val password: String?,
        /** The address as yt-dlp takes it, scheme included, credentials still encoded. */
        val url: String
    ) {
        val isSocks: Boolean get() = scheme.startsWith("socks")

        /** The proxy as Java's networking takes it. The host is left unresolved, so the proxy looks it up. */
        fun javaProxy(): Proxy = Proxy(
            if (isSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP,
            InetSocketAddress.createUnresolved(host.removeSurrounding("[", "]"), port)
        )

        /** The address for showing on screen: the user name stays, the password does not. */
        val display: String
            get() = "$scheme://" + (user?.let { "$it@" } ?: "") + "$host:$port"
    }

    /**
     * The kinds of proxy offered. Every one is taken by yt-dlp (checked against its
     * networking handlers) and by Java's sockets; socks5h asks the proxy to look up names,
     * which is what keeps them off the local network.
     */
    val SCHEMES = listOf("http", "https", "socks5", "socks5h")

    /**
     * The proxy written in [text], or null when it is blank or not a proxy address. An
     * address with no scheme is taken as http, as yt-dlp takes it. A port is required: each
     * kind of proxy has its own usual port, and guessing the wrong one fails with nothing to
     * say why.
     */
    fun parse(text: String): Spec? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val full = if ("://" in trimmed) trimmed else "http://$trimmed"
        val uri = runCatching { URI(full) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in SCHEMES) return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        val info = uri.rawUserInfo?.takeIf { it.isNotEmpty() }
        val user = info?.substringBefore(':')?.let(::decode)?.takeIf { it.isNotEmpty() }
        val password = info?.takeIf { ':' in it }?.substringAfter(':')?.let(::decode)
        val rest = full.substringAfter("://").trimEnd('/')
        return Spec(scheme, host, port, user, password, "$scheme://$rest")
    }

    /**
     * [text] with any password taken out, for a copy that leaves the device, such as a
     * backup. Anything that is not a proxy address comes back as it was.
     */
    fun withoutPassword(text: String): String {
        val spec = parse(text) ?: return text
        if (spec.password == null) return spec.url
        val rest = spec.url.substringAfter("://")
        val info = rest.substringBeforeLast('@')
        return "${spec.scheme}://${info.substringBefore(':')}@${rest.substringAfterLast('@')}"
    }

    /**
     * The address made from the parts the proxy screen asks for, or null when they do not
     * make one. The user name and password are encoded, so any character can be used in them.
     */
    fun build(scheme: String, host: String, port: String, user: String, password: String): String? {
        val name = host.trim().removeSurrounding("[", "]")
        if (name.isEmpty()) return null
        val where = if (':' in name) "[$name]" else name
        val login = user.takeIf { it.isNotEmpty() }?.let { u ->
            encode(u) + (password.takeIf { it.isNotEmpty() }?.let { ":" + encode(it) } ?: "")
        }
        val text = "$scheme://" + (login?.let { "$it@" } ?: "") + "$where:${port.trim()}"
        return text.takeIf { parse(it)?.scheme == scheme }
    }

    private fun encode(part: String): String =
        java.net.URLEncoder.encode(part, "UTF-8").replace("+", "%20")

    /** Whether [text] is something the setting can hold: blank, for none, or a proxy address. */
    fun isValid(text: String): Boolean = text.isBlank() || parse(text) != null

    private fun decode(part: String): String =
        runCatching { URLDecoder.decode(part.replace("+", "%2B"), "UTF-8") }.getOrDefault(part)

    /** The last address parsed and what it came to, so a connection does not parse it again. */
    @Volatile
    private var parsed: Pair<String, Spec?> = "" to null

    /**
     * The proxy in force, or null for a direct connection. Waits for the stored setting the
     * first time it is asked, so nothing at start-up goes out directly just because storage
     * had not been read yet.
     */
    fun current(): Spec? {
        val text = AdvancedSettingsStore.ready().activeProxy
        val last = parsed
        if (last.first == text) return last.second
        return parse(text).also { parsed = text to it }
    }

    /** The `--proxy` value for yt-dlp, or null to leave the option off. */
    fun forEngine(settings: AdvancedSettings): String? = parse(settings.activeProxy)?.url

    /**
     * The selector every connection outside yt-dlp asks. Created around the selector the
     * system had, which answers whenever no proxy is set.
     */
    val selector: ProxySelector by lazy { Selector(ProxySelector.getDefault()) }

    /**
     * Makes the proxy apply to every connection in the process, and its user name and
     * password answer the proxy's request for them. Called once, before anything connects.
     */
    fun install() {
        ProxySelector.setDefault(selector)
        Authenticator.setDefault(Credentials)
    }

    /**
     * Points an OkHttp client at the proxy. The selector is read for every connection, so a
     * client built before the setting changed follows the change. OkHttp leaves a proxy's
     * request for a password unanswered unless told how; [ProxyLogin] answers it.
     */
    fun OkHttpClient.Builder.throughProxy(): OkHttpClient.Builder =
        proxySelector(selector).proxyAuthenticator(ProxyLogin)

    /**
     * Why a run failed, in the user's words, when the proxy is the likely reason, or null.
     * yt-dlp reports a proxy that refuses the login as a ProxyError (or the SOCKS error under
     * it), and one that is not running only as a connection that failed, without naming the
     * proxy; with a proxy set, every connection goes to it first, so that is pointed at too.
     * ffmpeg, which yt-dlp hands cut parts and some live streams to, cannot use SOCKS at all.
     */
    fun hintFor(message: String?): Int? {
        val text = message?.lowercase() ?: return null
        if (current() == null) return null
        return when {
            "does not support socks proxies" in text -> R.string.proxy_error_ffmpeg_socks
            PROXY_FAILURE_SIGNS.any { it in text } -> R.string.proxy_error_unreachable
            else -> null
        }
    }

    private val PROXY_FAILURE_SIGNS = listOf(
        "proxyerror", "unable to connect to proxy", "socks5error", "socks4error",
        "407 proxy authentication", "connection refused", "remote end closed connection",
        "errno 111", "network is unreachable"
    )

    /**
     * Hosts on this device itself, which no proxy could reach. Only the addresses written out:
     * a name that merely starts with "127." is any site's to choose, and would go around the proxy.
     */
    private fun isLoopback(host: String?): Boolean {
        val name = host?.removeSurrounding("[", "]")?.lowercase() ?: return false
        return name == "localhost" || name == "::1" || LOOPBACK_V4.matches(name)
    }

    private val LOOPBACK_V4 = Regex("""127(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}""")

    private class Selector(private val system: ProxySelector?) : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            requireNotNull(uri) { "uri" }
            val spec = current()
            if (spec == null) return system?.select(uri) ?: listOf(Proxy.NO_PROXY)
            if (isLoopback(uri.host)) return listOf(Proxy.NO_PROXY)
            return listOf(spec.javaProxy())
        }

        override fun connectFailed(uri: URI?, address: SocketAddress?, error: IOException?) {
            if (current() == null && uri != null && address != null && error != null) {
                system?.connectFailed(uri, address, error)
            }
        }
    }

    /**
     * Gives an HTTP proxy its user name and password when it asks, through OkHttp. Only to
     * the proxy in the setting, and only once per request: a second refusal means the password
     * is wrong, and asking again would loop.
     */
    private object ProxyLogin : okhttp3.Authenticator {
        override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? {
            val spec = current() ?: return null
            val user = spec.user ?: return null
            if (response.code != 407 || response.request.header("Proxy-Authorization") != null) return null
            val address = route?.proxy?.takeIf { it.type() == Proxy.Type.HTTP }?.address() as? InetSocketAddress
                ?: return null
            if (!address.hostString.equals(spec.host.removeSurrounding("[", "]"), ignoreCase = true) || address.port != spec.port) {
                return null
            }
            return response.request.newBuilder()
                .header("Proxy-Authorization", okhttp3.Credentials.basic(user, spec.password.orEmpty(), Charsets.UTF_8))
                .build()
        }
    }

    /** Answers a password request from the proxy, and only from the proxy. */
    private object Credentials : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            val spec = current() ?: return null
            val user = spec.user ?: return null
            val host = spec.host.removeSurrounding("[", "]")
            if (!requestingHost.equals(host, ignoreCase = true) || requestingPort != spec.port) return null
            // A site asking for a password over the proxy is not the proxy asking.
            if (requestorType == RequestorType.SERVER && !requestingProtocol.orEmpty().startsWith("SOCKS")) return null
            return PasswordAuthentication(user, spec.password.orEmpty().toCharArray())
        }
    }
}
