package com.hazel.android.update

import com.hazel.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * The yt-dlp update used to say "Update failed" whatever had gone wrong, so a full phone, a
 * dead connection and a build that would not start could not be told apart. These cases pin
 * each cause to its own message, and pin which of them are worth another try. The words
 * themselves live in strings.xml, so what is checked here is which sentence is chosen and
 * that every sentence can be filled in.
 */
class UpdateFailureTest {

    private fun kind(error: Throwable) = UpdateFailure.from(error).kind



    @Test
    fun `timeouts are told apart from an unreachable host`() {
        assertEquals(UpdateFailure.Kind.TIMEOUT, kind(SocketTimeoutException("timeout")))
        // OkHttp reports a call that ran past its deadline as a bare InterruptedIOException.
        assertEquals(UpdateFailure.Kind.TIMEOUT, kind(InterruptedIOException("timeout")))
        assertEquals(UpdateFailure.Kind.UNREACHABLE, kind(UnknownHostException("github.com")))
        assertEquals(UpdateFailure.Kind.UNREACHABLE, kind(ConnectException("Connection refused")))
    }

    @Test
    fun `a certificate problem points at the phone's clock`() {
        val untrusted = SSLHandshakeException(
            "java.security.cert.CertPathValidatorException: Trust anchor for certification path not found."
        )
        assertEquals(UpdateFailure.Kind.CLOCK_OR_TLS, kind(untrusted))
        assertEquals(
            UpdateFailure.Kind.CLOCK_OR_TLS,
            kind(SSLException("handshake failed", CertificateException("not yet valid")))
        )
        // An expired or not-yet-valid chain on a phone with the wrong date surfaces this way.
        assertEquals(
            UpdateFailure.Kind.CLOCK_OR_TLS,
            kind(SSLException("Chain validation failed", CertPathValidatorException("timestamp check failed")))
        )
    }







    @Test
    fun `only failures a second try could change are retried`() {
        assertFalse(UpdateFailure.from(IOException("ENOSPC")).retryable)
        assertFalse(UpdateFailure.from(SSLException("x", CertificateException("expired"))).retryable)
        assertTrue(UpdateFailure.from(SocketTimeoutException()).retryable)
        assertTrue(UpdateFailure.from(SocketException("Connection reset")).retryable)
        assertTrue(UpdateFailure.from(IOException("something odd")).retryable)
    }

    @Test
    fun `a missing release is told apart and not retried`() {
        for (code in listOf(404, 410)) {
            val failure = UpdateFailure.http(code)
            assertEquals(UpdateFailure.Kind.RELEASE_GONE, failure.kind)
            assertFalse(failure.retryable)
        }
    }

    @Test
    fun `a busy GitHub is retried but a rate limit is left alone`() {
        // A 429 or 403 is GitHub limiting the caller; asking again seconds later prolongs it.
        for (code in listOf(403, 429)) {
            assertEquals(UpdateFailure.Kind.HTTP, UpdateFailure.http(code).kind)
            assertFalse("HTTP $code", UpdateFailure.http(code).retryable)
        }
        for (code in listOf(408, 500, 502, 503, 504)) {
            assertTrue("HTTP $code", UpdateFailure.http(code).retryable)
        }
    }













    @Test
    fun `the placeholders in every language match what the code supplies`() {
        // A placeholder with no argument shows the raw token, and a missing one would crash the
        // screen, so each language's sentence is held to the argument count of its failure.
        val keyOf = R.string::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }
        val files = File("src/main/res").listFiles { f -> f.name.startsWith("values") }
            .orEmpty().map { File(it, "strings.xml") }.filter { it.isFile }
        assertTrue("no strings.xml found", files.size >= 2)

        for (kind in UpdateFailure.Kind.entries) {
            val key = keyOf.getValue(kind.message)
            val slots = UpdateFailure.of(kind).messageArgs.size
            for (file in files) {
                val text = Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                    .find(file.readText())?.groupValues?.get(1) ?: continue
                val found = Regex("""%(\d+)\$[sd]""").findAll(text).map { it.groupValues[1] }.toSet()
                assertEquals("$key in ${file.parentFile.name}", (1..slots).map { it.toString() }.toSet(), found)
            }
        }
    }
}
