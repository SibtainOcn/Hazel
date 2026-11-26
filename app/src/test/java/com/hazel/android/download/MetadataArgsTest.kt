package com.hazel.android.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An edited title and author become `--parse-metadata` values yt-dlp reads as plain text. */
class MetadataArgsTest {





    @Test
    fun colonsAreEscapedSoTheSplitPassesThem() {
        assertEquals(listOf("""Part 1\: The Start :%(title)s """), metadataParseArgs("Part 1: The Start", ""))
    }

    @Test
    fun percentStaysLiteral() {
        assertEquals(listOf("100%% %%(title)s :%(title)s "), metadataParseArgs("100% %(title)s", ""))
    }

    @Test
    fun theFromEndsInTheSpaceTheToMatchesOff() {
        val (from, to) = metadataParseArgs("Song", "").single().split(":", limit = 2)
        assertTrue(from.endsWith(" ") && to.endsWith(" "))
    }

    @Test
    fun blankTitleAndAuthorWriteNothing() {
        assertEquals(emptyList(), metadataParseArgs("  ", ""))
    }


}
