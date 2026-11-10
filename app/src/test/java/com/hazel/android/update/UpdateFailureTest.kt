package com.hazel.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * The yt-dlp update used to say "Update failed" whatever had gone wrong, so a full phone, a
 * dead connection and a build that would not start could not be told apart. These cases pin
 * each cause to its own message, and pin which of them are worth another try.
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
        val message = UpdateFailure.from(IllegalStateException("boom")).message
        assertTrue(message, message.contains("IllegalStateException: boom"))
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
    fun `a missing release is not retried but a busy GitHub is`() {
        assertFalse(UpdateFailure.of(UpdateFailure.Kind.HTTP, "HTTP 404").retryable)
        assertFalse(UpdateFailure.of(UpdateFailure.Kind.HTTP, "HTTP 403").retryable)
        assertTrue(UpdateFailure.of(UpdateFailure.Kind.HTTP, "HTTP 429").retryable)
        assertTrue(UpdateFailure.of(UpdateFailure.Kind.HTTP, "HTTP 503").retryable)
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
    fun `the message is the advice followed by the cause`() {
        val failure = UpdateFailure.of(UpdateFailure.Kind.HTTP, "HTTP 404")
        assertEquals("${UpdateFailure.Kind.HTTP.advice} (HTTP 404)", failure.message)
        assertEquals(UpdateFailure.Kind.NO_SPACE.advice, UpdateFailure.of(UpdateFailure.Kind.NO_SPACE).message)
    }
}
