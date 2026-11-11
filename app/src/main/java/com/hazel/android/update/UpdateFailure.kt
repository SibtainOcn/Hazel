package com.hazel.android.update

import androidx.annotation.StringRes
import com.hazel.android.R
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * Why a yt-dlp update did not finish, in words the person can act on.
 *
 * The update used to report every failure as "Update failed. Please try again.", which left
 * a full phone, a dead connection, a wrong clock and a build that would not start all
 * looking the same, both to the person and to anyone reading their bug report. Each is told
 * apart here.
 *
 * What the person reads is a string resource, so it arrives in their language; the technical
 * cause stays in [detail] and [summary], in English, for the log and the bug report. Only
 * Android resource ids are used, no framework classes, so the classification is still tested
 * on the JVM.
 */
data class UpdateFailure(val kind: Kind, val detail: String, val status: Int = 0) {

    enum class Kind(
        /** The sentence shown to the person: what happened, and what to try. */
        @StringRes val message: Int,
        /** Whether a second attempt at the download could end differently. */
        val retryable: Boolean
    ) {
        NO_SPACE(R.string.upd_failure_no_space, false),
        TIMEOUT(R.string.upd_failure_timeout, true),
        UNREACHABLE(R.string.upd_failure_unreachable, true),
        CONNECTION_LOST(R.string.upd_failure_connection_lost, true),
        CLOCK_OR_TLS(R.string.upd_failure_clock_or_tls, false),
        HTTP(R.string.upd_failure_http, false),
        RELEASE_GONE(R.string.upd_failure_release_gone, false),
        INCOMPLETE(R.string.upd_failure_incomplete, true),
        COULD_NOT_INSTALL(R.string.upd_failure_could_not_install, false),
        WOULD_NOT_START(R.string.upd_failure_would_not_start, false),
        STOPPED_WHILE_STARTING(R.string.upd_failure_stopped_while_starting, false),
        UNKNOWN(R.string.upd_failure_unknown, true)
    }

    /**
     * Whether another try at the download could end differently. GitHub being down or slow
     * (408, 5xx) is worth another go. A 429 or 403 is a rate limit, and asking again a few
     * seconds later only prolongs it, so those are left for the person to retry later.
     */
    val retryable: Boolean
        get() = kind.retryable || (kind == Kind.HTTP && (status == 408 || status >= 500))

    /** The string resource that words this failure for the person. */
    @get:StringRes
    val messageRes: Int
        get() = kind.message

    /**
     * What fills the placeholder in [messageRes]: the HTTP status, or for a failure nothing
     * else could name, the technical cause. The other sentences have no placeholder.
     */
    val messageArgs: Array<Any>
        get() = when (kind) {
            Kind.HTTP -> arrayOf<Any>(status)
            Kind.UNKNOWN -> arrayOf<Any>(detail.ifBlank { "?" })
            else -> emptyArray()
        }

    /** One technical line for the log, an exception and the bug report. Never shown as the reason. */
    val summary: String
        get() = if (detail.isBlank()) kind.name else "${kind.name} ($detail)"

    companion object {

        /** Longest cause text kept, so a Python traceback does not fill the screen. */
        private const val DETAIL_LIMIT = 110

        fun of(kind: Kind, detail: String = ""): UpdateFailure =
            UpdateFailure(kind, shorten(detail))

        /** GitHub answered the download with [code]. A release that is gone is told apart. */
        fun http(code: Int): UpdateFailure =
            UpdateFailure(
                if (code == 404 || code == 410) Kind.RELEASE_GONE else Kind.HTTP,
                "HTTP $code",
                code
            )

        /** The failure behind [error], looking through whatever wrapped it. */
        fun from(error: Throwable): UpdateFailure {
            val chain = generateSequence(error) { it.cause }.take(8).toList()

            chain.filterIsInstance<UpdateFailureException>().firstOrNull()?.let { return it.failure }

            if (chain.any { isOutOfSpace(it) }) return of(Kind.NO_SPACE, "ENOSPC")

            // A certificate problem is the one connection error the person can fix themselves.
            val certificate = chain.firstOrNull {
                it is CertificateException || it is CertPathValidatorException ||
                    (it is SSLException && mentionsCertificate(it.message))
            }
            if (certificate != null) return of(Kind.CLOCK_OR_TLS, describe(certificate))

            chain.firstOrNull { it is SocketTimeoutException }?.let { return of(Kind.TIMEOUT, describe(it)) }
            chain.firstOrNull { it is UnknownHostException }?.let { return of(Kind.UNREACHABLE, describe(it)) }
            chain.firstOrNull { it is ConnectException || it is NoRouteToHostException }
                ?.let { return of(Kind.UNREACHABLE, describe(it)) }
            // OkHttp reports a call that ran past its deadline as a bare "timeout".
            chain.firstOrNull { it is InterruptedIOException }?.let { return of(Kind.TIMEOUT, describe(it)) }
            // OkHttp reports a body that ends before its stated length as a ProtocolException.
            chain.firstOrNull { it is SSLException || it is SocketException || it is EOFException || it is ProtocolException }
                ?.let { return of(Kind.CONNECTION_LOST, describe(it)) }

            return of(Kind.UNKNOWN, describe(error))
        }

        /**
         * Whether a failed `yt-dlp --version` looks like the process being stopped rather than
         * yt-dlp refusing to run: no error text at all, or the system's words for lacking
         * memory. A refusal by Python itself names a module or a syntax and is not transient.
         */
        fun isTransientStartFailure(message: String?): Boolean {
            val text = message?.trim()?.lowercase().orEmpty()
            if (text.isEmpty()) return true
            return TRANSIENT_MARKERS.any { it in text }
        }

        /** The failure to report for a build that would not start, given what it printed. */
        fun forStartFailure(message: String?): UpdateFailure =
            if (isTransientStartFailure(message)) of(Kind.STOPPED_WHILE_STARTING, lastLine(message))
            else of(Kind.WOULD_NOT_START, lastLine(message))

        private val TRANSIENT_MARKERS = listOf(
            "memoryerror",
            "cannot allocate memory",
            "out of memory",
            "killed",
            "resource temporarily unavailable",
            "cannot fork"
        )

        private fun isOutOfSpace(error: Throwable): Boolean {
            val text = error.message?.lowercase() ?: return false
            return "enospc" in text || "no space left" in text
        }

        private fun mentionsCertificate(message: String?): Boolean {
            val text = message?.lowercase() ?: return false
            return "trust anchor" in text || "certificate" in text || "not yet valid" in text ||
                "chain validation" in text
        }

        private fun describe(error: Throwable): String {
            val name = error.javaClass.simpleName.ifBlank { "Error" }
            val text = error.message?.trim().orEmpty()
            return if (text.isEmpty()) name else "$name: $text"
        }

        /** Last non-empty line: a Python traceback ends with the error that matters. */
        private fun lastLine(message: String?): String =
            message?.lineSequence()?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }.orEmpty()

        private fun shorten(text: String): String {
            val oneLine = text.trim().replace(Regex("\\s+"), " ")
            return if (oneLine.length <= DETAIL_LIMIT) oneLine else oneLine.take(DETAIL_LIMIT - 1) + "…"
        }
    }
}

/** An update failure whose cause is already known, thrown so callers can report it as is. */
internal class UpdateFailureException(val failure: UpdateFailure, cause: Throwable? = null) :
    IOException(failure.summary, cause) {

    constructor(kind: UpdateFailure.Kind, detail: String = "", cause: Throwable? = null) :
        this(UpdateFailure.of(kind, detail), cause)
}
