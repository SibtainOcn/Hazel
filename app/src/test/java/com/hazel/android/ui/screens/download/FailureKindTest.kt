package com.hazel.android.ui.screens.download

import org.junit.Assert.assertEquals
import org.junit.Test

/** The read-failure dialog offers only what can fix the error the engine reported. */
class FailureKindTest {

    private fun assertKind(expected: FailureKind, message: String) =
        assertEquals(message, expected, failureKind(message))

    @Test
    fun `media that is gone offers no fix`() {
        assertKind(FailureKind.GONE, "ERROR: [youtube] BaW_jenozKc: This video is unavailable")
        assertKind(FailureKind.GONE, "ERROR: [youtube] abc: Video unavailable. This video has been removed by the uploader")
        assertKind(
            FailureKind.GONE,
            "ERROR: [youtube] abc: Video unavailable. This video is no longer available because the YouTube " +
                "account associated with this video has been terminated."
        )
        assertKind(FailureKind.GONE, "ERROR: [generic] Unable to download webpage: HTTP Error 404: Not Found")
        assertKind(FailureKind.GONE, "ERROR: [dailymotion] x00000000: Not found.")
        assertKind(FailureKind.GONE, "ERROR: [twitter] 1: No video could be found in this tweet")
        assertKind(FailureKind.GONE, "ERROR: [twitch:vod] 1: Video 1 does not exist")
    }

    @Test
    fun `a sign-in is asked for where the source wants an account`() {
        assertKind(FailureKind.SIGN_IN, "ERROR: [youtube] abc: Sign in to confirm you're not a bot. Use --cookies-from-browser")
        assertKind(FailureKind.SIGN_IN, "ERROR: [youtube] abc: Sign in to confirm your age. This video may be inappropriate")
        assertKind(FailureKind.SIGN_IN, "ERROR: [youtube] abc: Private video. Sign in if you've been granted access to this video")
        assertKind(FailureKind.SIGN_IN, "ERROR: [instagram] abc: Requested content is not available, rate-limit reached or login required")
        assertKind(FailureKind.SIGN_IN, "ERROR: [vimeo] 1: The web client only works when logged-in. Use --cookies")
        assertKind(FailureKind.SIGN_IN, "ERROR: [Instagram] C0: Check if this post is accessible in your browser without being logged-in.")
    }

    @Test
    fun `a refused request offers cookies`() {
        assertKind(FailureKind.REFUSED, "ERROR: [generic] Unable to download webpage: HTTP Error 403: Forbidden")
        assertKind(FailureKind.REFUSED, "ERROR: [vimeo] abc: HTTP Error 429: Too Many Requests")
        assertKind(
            FailureKind.REFUSED,
            "ERROR: [youtube] abc: The uploader has not made this video available in your country"
        )
        // YouTube's rate limit says the video is unavailable when it is there.
        assertKind(FailureKind.REFUSED, "ERROR: [youtube] abc: Video unavailable. This content isn't available, try again later.")
    }

    @Test
    fun `an unsupported site and anything else`() {
        assertKind(FailureKind.UNSUPPORTED, "ERROR: Unsupported URL: https://example.com/page")
        assertKind(FailureKind.OTHER, "ERROR: [generic] Unable to download webpage: <urlopen error timed out>")
        assertKind(FailureKind.OTHER, "ERROR: [youtube] abc: Requested format is not available")
    }
}
