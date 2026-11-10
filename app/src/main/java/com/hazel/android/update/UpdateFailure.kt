package com.hazel.android.update

import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/**
 * Why a yt-dlp update did not finish, in words the person can act on.
 *
 * The update used to report every failure as "Update failed. Please try again.", which left
 * a full phone, a dead connection, a wrong clock and a build that would not start all
 * looking the same, both to the person and to anyone reading their bug report. Each is told
 * apart here, with the technical cause kept in brackets for the report.
 *
 * Kept free of Android classes so the classification can be tested on the JVM.
 */
internal data class UpdateFailure(val kind: Kind, val detail: String) {

    enum class Kind(
        /** What to tell the person, ending in what to try. */
        val advice: String,
        /** Whether a second attempt at the download could end differently. */
        val retryable: Boolean
    ) {
        NO_SPACE("Not enough free storage on this phone. Free up some space, then try again.", false),
        TIMEOUT("The connection to GitHub timed out. Try again on a stronger network.", true),
        UNREACHABLE("Couldn't reach GitHub. Check the connection, and any VPN or Private DNS setting.", true),
        CONNECTION_LOST("The connection dropped during the download. Try again on a steadier network.", true),
        CLOCK_OR_TLS("A secure connection to GitHub couldn't be made. Check the phone's date and time.", false),
        HTTP("GitHub refused the download.", false),
        INCOMPLETE("The download came through incomplete. Try again on a steadier network.", true),
        COULD_NOT_INSTALL("The new engine couldn't be moved into place. Try again.", false),
        WOULD_NOT_START(
            "The new yt-dlp didn't start on this phone, so the current one was kept. Downloads still work.",
            false
        ),
        STOPPED_WHILE_STARTING(
            "The new yt-dlp was stopped while starting, likely for lack of memory. " +
                "The current one was kept. Close other apps and try again.",
            false
        ),
        UNKNOWN("Update failed.", true)
    }

    /**
     * Whether another try at the download could end differently. A refusal is retried only
     * when GitHub says it is busy (429) or failing (5xx), not when the file is simply gone.
     */
    val retryable: Boolean
        get() = kind.retryable || (kind == Kind.HTTP && detail.removePrefix("HTTP ").trim().toIntOrNull()
            ?.let { it == 408 || it == 429 || it >= 500 } == true)

    /** The line shown to the person: the advice, then the cause for anyone reporting it. */
    val message: String
        get() = if (detail.isBlank()) kind.advice else "${kind.advice} ($detail)"

    companion object {

        /** Longest cause text kept, so a Python traceback does not fill the screen. */
        private const val DETAIL_LIMIT = 110

        fun of(kind: Kind, detail: String = ""): UpdateFailure =
            UpdateFailure(kind, shorten(detail))

        /** The failure behind [error], looking through whatever wrapped it. */
        fun from(error: Throwable): UpdateFailure {
            val chain = generateSequence(error) { it.cause }.take(8).toList()

            chain.filterIsInstance<UpdateFailureException>().firstOrNull()?.let { return it.failure }

            if (chain.any { isOutOfSpace(it) }) return of(Kind.NO_SPACE, "ENOSPC")

            // A certificate problem is the one connection error the person can fix themselves.
            if (chain.any { it is CertificateException } ||
                chain.any { it is SSLException && mentionsCertificate(it.message) }
            ) {
                return of(Kind.CLOCK_OR_TLS, describe(chain.first { it is CertificateException || it is SSLException }))
            }

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
            return "trust anchor" in text || "certificate" in text || "not yet valid" in text
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
    IOException(failure.message, cause) {

    constructor(kind: UpdateFailure.Kind, detail: String = "", cause: Throwable? = null) :
        this(UpdateFailure.of(kind, detail), cause)
}
