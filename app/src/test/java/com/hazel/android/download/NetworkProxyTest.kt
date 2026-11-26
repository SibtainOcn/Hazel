package com.hazel.android.download

import com.hazel.android.R
import com.hazel.android.download.NetworkProxy.throughProxy
import com.yausername.youtubedl_android.YoutubeDLRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Authenticator
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The proxy setting reaches every way the app connects, and never lets a connection go out
 * directly while it is set.
 *
 * The routing tests run real sockets against an HTTP proxy and a SOCKS 5 proxy started here,
 * each asking for a password. The sites asked for do not exist, so an answer can only have
 * come through the proxy.
 */
@RunWith(RobolectricTestRunner::class)
// A bare application: the app's own start-up would read the stored settings over the ones
// each test sets.
@Config(sdk = [35], application = android.app.Application::class)
class NetworkProxyTest {

    private val servers = mutableListOf<ServerSocket>()

    @Before
    fun setUp() {
        NetworkProxy.install()
    }

    @After
    fun tearDown() {
        AdvancedSettingsStore.current = AdvancedSettings()
        AdvancedSettingsStore.loader = null
        servers.forEach { runCatching { it.close() } }
    }

    private fun useProxy(text: String) {
        AdvancedSettingsStore.current = AdvancedSettings(proxyEnabled = true, proxy = text)
    }

    // ── The address ──





    @Test
    fun credentialsAreReadAndNeverShown() {
        val spec = assertNotNull(NetworkProxy.parse("http://me:p%40ss%3Aword@p.example:8080"))
        assertEquals("me", spec.user)
        assertEquals("p@ss:word", spec.password)
        // yt-dlp is handed the address as written, still encoded.
        assertEquals("http://me:p%40ss%3Aword@p.example:8080", spec.url)
        assertEquals("http://me@p.example:8080", spec.display)
        assertFalse("word" in spec.display)
    }

    @Test
    fun aBackupKeepsTheProxyButNotItsPassword() {
        assertEquals("socks5h://me@127.0.0.1:9050", NetworkProxy.withoutPassword("socks5h://me:pw@127.0.0.1:9050"))
        assertEquals("http://p.example:8080", NetworkProxy.withoutPassword("http://p.example:8080"))
        assertEquals("", NetworkProxy.withoutPassword(""))
        val prefs = androidx.datastore.preferences.core.mutablePreferencesOf(
            androidx.datastore.preferences.core.stringPreferencesKey("adv_proxy") to "http://me:s3cret@p.example:8080"
        )
        val exported = com.hazel.android.data.BackupRepository
            .export(prefs, com.hazel.android.data.BackupCategory.entries.toSet())
        assertEquals("http://me@p.example:8080", exported.getJSONObject("values").getJSONObject("adv_proxy").getString("v"))
        assertFalse("s3cret" in exported.toString())
    }





    // ── yt-dlp ──

    @Test
    fun everyEngineRunIsGivenTheProxy() {
        val settings = AdvancedSettings(proxyEnabled = true, proxy = "socks5h://u:p@127.0.0.1:9050")
        val read = YoutubeDLRequest("https://vimeo.com/1").apply { applyAdvanced("https://vimeo.com/1", settings = settings) }
        assertEquals("socks5h://u:p@127.0.0.1:9050", read.getOption("--proxy"))
        val youtube = YoutubeDLRequest("https://www.youtube.com/watch?v=jNQXAC9IVRw")
            .apply { applyAdvanced("https://www.youtube.com/watch?v=jNQXAC9IVRw", settings = settings) }
        assertEquals("socks5h://u:p@127.0.0.1:9050", youtube.getOption("--proxy"))
    }



    @Test
    fun aReadFromAnotherAddressIsNotServedBack() {
        val url = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val direct = ReadProfile.stampFor(url, AdvancedSettings())
        val proxied = ReadProfile.stampFor(url, AdvancedSettings(proxyEnabled = true, proxy = "socks5h://127.0.0.1:9050"))
        val other = ReadProfile.stampFor(url, AdvancedSettings(proxyEnabled = true, proxy = "http://10.0.0.2:3128"))
        assertEquals("", direct)
        assertNotEquals(direct, proxied)
        assertNotEquals(proxied, other)
        // The password is not part of where the read came from.
        assertEquals(proxied, ReadProfile.stampFor(url, AdvancedSettings(proxyEnabled = true, proxy = "socks5h://a:b@127.0.0.1:9050")))
    }



    // ── Start-up ──



    // ── The selector ──

    @Test
    fun withAProxySetNothingFallsBackToDirect() {
        useProxy("http://10.0.0.2:3128")
        val route = ProxySelector.getDefault().select(URI("https://rr1---sn.googlevideo.com/videoplayback"))
        assertEquals(1, route.size)
        assertEquals(Proxy.Type.HTTP, route[0].type())
        // The device's own servers are never sent to a proxy.
        assertEquals(listOf(Proxy.NO_PROXY), ProxySelector.getDefault().select(URI("http://127.0.0.1:8080/")))
    }

    @Test
    fun aSiteNamedLikeALoopbackAddressStillGoesThroughTheProxy() {
        useProxy("http://10.0.0.2:3128")
        for (host in listOf("127.example.com", "127.0.0.1.example.com", "127.0.0.256")) {
            val route = ProxySelector.getDefault().select(URI("https://$host/thumb.jpg"))
            assertEquals(listOf(Proxy.Type.HTTP), route.map { it.type() }, host)
        }
        assertEquals(listOf(Proxy.NO_PROXY), ProxySelector.getDefault().select(URI("http://127.10.20.30/")))
        assertEquals(listOf(Proxy.NO_PROXY), ProxySelector.getDefault().select(URI("http://[::1]:8080/")))
        assertEquals(listOf(Proxy.NO_PROXY), ProxySelector.getDefault().select(URI("http://localhost/")))
    }



    @Test
    fun thePasswordGoesToTheProxyAndNowhereElse() {
        useProxy("http://me:secret@proxy.example:3128")
        val toProxy = Authenticator.requestPasswordAuthentication(
            "proxy.example", null, 3128, "https", "", "basic", null, Authenticator.RequestorType.PROXY
        )
        assertEquals("me", toProxy?.userName)
        assertEquals("secret", toProxy?.password?.concatToString())
        val toSite = Authenticator.requestPasswordAuthentication(
            "www.youtube.com", null, 443, "https", "", "basic", null, Authenticator.RequestorType.SERVER
        )
        assertNull(toSite)
        val siteOnProxyHost = Authenticator.requestPasswordAuthentication(
            "proxy.example", null, 3128, "https", "", "basic", null, Authenticator.RequestorType.SERVER
        )
        assertNull(siteOnProxyHost, "a site asking is not the proxy asking")
    }

    // ── Real connections ──







    @Test
    fun aWrongPasswordFailsRatherThanGoingAround() {
        val proxy = socksProxy(user = "me", password = "s3cret")
        useProxy("socks5h://me:wrong@127.0.0.1:${proxy.localPort}")
        assertFailsWith<java.io.IOException> {
            client().newCall(Request.Builder().url("http://search.invalid/q").build()).execute().close()
        }
    }

    @Test
    fun aProxyThatIsDownFailsRatherThanGoingAround() {
        val closed = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        useProxy("http://127.0.0.1:$closed")
        assertFailsWith<java.io.IOException> {
            client().newCall(Request.Builder().url("http://media.invalid/x").build()).execute().close()
        }
    }



    // ── Helpers ──

    private fun client() = OkHttpClient.Builder()
        .throughProxy()
        // A fresh connection per call, so each one asks the selector again.
        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun server(handle: (java.net.Socket) -> Unit): ServerSocket {
        val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        servers += server
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { socket.use { runCatching { handle(it) } } }
            }
        }
        return server
    }

    /** A SOCKS 5 proxy (RFC 1928, password per RFC 1929) that answers the HTTP request itself. */
    private fun socksProxy(user: String, password: String): ServerSocket = server { socket ->
        val input = DataInputStream(socket.getInputStream())
        val out = socket.getOutputStream()
        check(input.readUnsignedByte() == 5)
        val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        check(2.toByte() in methods) { "client offered no password" }
        out.write(byteArrayOf(5, 2)); out.flush()
        check(input.readUnsignedByte() == 1)
        val givenUser = String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) })
        val givenPassword = String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) })
        if (givenUser != user || givenPassword != password) {
            out.write(byteArrayOf(1, 1)); out.flush()
            return@server
        }
        out.write(byteArrayOf(1, 0)); out.flush()
        check(input.readUnsignedByte() == 5)
        check(input.readUnsignedByte() == 1) // connect
        input.readUnsignedByte()
        val host = when (input.readUnsignedByte()) {
            3 -> String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) })
            1 -> ByteArray(4).also { input.readFully(it) }.joinToString(".") { (it.toInt() and 0xff).toString() }
            else -> error("address type")
        }
        val port = input.readUnsignedShort()
        out.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); out.flush()
        readHead(input)
        respond(out, "via-socks $host:$port")
    }

    private fun readHead(input: InputStream): List<String> {
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) break
            if (c == '\n'.code) {
                val text = line.toString().trimEnd('\r')
                if (text.isEmpty()) break
                lines += text
                line.clear()
            } else {
                line.append(c.toChar())
            }
        }
        return lines
    }

    private fun respond(out: OutputStream, body: String) {
        val bytes = body.toByteArray()
        out.write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }
}
