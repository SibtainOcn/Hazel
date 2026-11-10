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
    fun `a full phone is named as such, however the system words it`() {
        assertEquals(
            UpdateFailure.Kind.NO_SPACE,
            kind(IOException("write failed: ENOSPC (No space left on device)"))
        )
        assertEquals(
            UpdateFailure.Kind.NO_SPACE,
            kind(RuntimeException("could not stage", IOException("No space left on device")))
        )
    }

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
    fun `a dropped connection is not mistaken for a certificate problem`() {
        assertEquals(UpdateFailure.Kind.CONNECTION_LOST, kind(SSLException("Connection reset by peer")))
        assertEquals(UpdateFailure.Kind.CONNECTION_LOST, kind(SocketException("Software caused connection abort")))
        assertEquals(UpdateFailure.Kind.CONNECTION_LOST, kind(EOFException()))
        assertEquals(UpdateFailure.Kind.CONNECTION_LOST, kind(ProtocolException("unexpected end of stream")))
    }

    @Test
    fun `an unknown error keeps its class and text for the bug report`() {
        val failure = UpdateFailure.from(IllegalStateException("boom"))
        assertEquals(UpdateFailure.Kind.UNKNOWN, failure.kind)
        assertTrue(failure.summary, failure.summary.contains("IllegalStateException: boom"))
        // It is also what the unknown sentence shows, since nothing else names the cause.
        assertEquals(listOf<Any>("IllegalStateException: boom"), failure.messageArgs.toList())
    }

    @Test
    fun `a failure that is already known passes through, even when wrapped`() {
        val known = UpdateFailureException(UpdateFailure.Kind.WOULD_NOT_START, "ImportError: x")
        assertEquals(UpdateFailure.Kind.WOULD_NOT_START, kind(RuntimeException("outer", known)))
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
    fun `a start with no output or a memory complaint is transient`() {
        assertTrue(UpdateFailure.isTransientStartFailure(null))
        assertTrue(UpdateFailure.isTransientStartFailure("  \n"))
        assertTrue(UpdateFailure.isTransientStartFailure("Traceback (most recent call last):\nMemoryError"))
        assertTrue(UpdateFailure.isTransientStartFailure("Killed"))
    }

    @Test
    fun `a refusal by Python itself is not transient`() {
        assertFalse(
            UpdateFailure.isTransientStartFailure(
                "ImportError: You are using an unsupported version of Python."
            )
        )
        assertFalse(UpdateFailure.isTransientStartFailure("zipimport.ZipImportError: bad local file header"))
    }

    @Test
    fun `a refused build reports the last line of what it printed`() {
        val failure = UpdateFailure.forStartFailure(
            "Traceback (most recent call last):\n  File \"__main__.py\", line 1\nImportError: unsupported Python\n"
        )
        assertEquals(UpdateFailure.Kind.WOULD_NOT_START, failure.kind)
        assertEquals("ImportError: unsupported Python", failure.detail)
        assertEquals(UpdateFailure.Kind.STOPPED_WHILE_STARTING, UpdateFailure.forStartFailure("").kind)
    }

    @Test
    fun `the cause is kept short and on one line`() {
        val detail = UpdateFailure.of(UpdateFailure.Kind.UNKNOWN, "line one\n" + "x".repeat(500)).detail
        assertTrue(detail.length <= 110)
        assertFalse(detail.contains('\n'))
    }

    @Test
    fun `every failure has its own sentence`() {
        val sentences = UpdateFailure.Kind.entries.map { it.message }
        assertEquals(UpdateFailure.Kind.entries.size, sentences.toSet().size)
        assertEquals(R.string.upd_failure_no_space, UpdateFailure.of(UpdateFailure.Kind.NO_SPACE).messageRes)
        assertEquals(R.string.upd_failure_release_gone, UpdateFailure.http(404).messageRes)
        assertEquals(R.string.upd_failure_http, UpdateFailure.http(429).messageRes)
    }

    @Test
    fun `a sentence is only ever given the arguments it has room for`() {
        // The status fills the HTTP sentence, the cause fills the unknown one, nothing else has a slot.
        assertEquals(listOf<Any>(429), UpdateFailure.http(429).messageArgs.toList())
        assertEquals(listOf<Any>("?"), UpdateFailure.of(UpdateFailure.Kind.UNKNOWN).messageArgs.toList())
        for (kind in UpdateFailure.Kind.entries) {
            val expected = if (kind == UpdateFailure.Kind.HTTP || kind == UpdateFailure.Kind.UNKNOWN) 1 else 0
            assertEquals(kind.name, expected, UpdateFailure.of(kind).messageArgs.size)
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
