package com.hazel.android.ui.screens.more

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The changelog and licence render as readable HTML, and nothing in them runs as markup. */
class MarkdownLiteTest {

    @Test
    fun `headings bullets bold code and links`() {
        val html = MarkdownLite.toHtml(
            "## [1.0]\n\n### Added\n- **Bold** and `a <b>` with [site](https://example.com)\n- second\n"
        )
        assertEquals(
            "<h2>[1.0]</h2><h3>Added</h3><ul><li><strong>Bold</strong> and <code>a &lt;b&gt;</code> " +
                "with <a href=\"https://example.com\">site</a></li><li>second</li></ul>",
            html
        )
    }

    @Test
    fun `nested bullets and continuation lines stay in their item`() {
        val html = MarkdownLite.toHtml("- one\n  more of one\n  - inner\n- two")
        assertEquals("<ul><li>one more of one<ul><li>inner</li></ul></li><li>two</li></ul>", html)
    }

    @Test
    fun `markup in the file is escaped, never run`() {
        val html = MarkdownLite.toHtml("<script>alert(1)</script>\n- [x](javascript:alert(1))")
        assertFalse("<script>" in html)
        assertFalse("href=\"javascript" in html)
        assertTrue("&lt;script&gt;" in html)
    }

    @Test
    fun `a hand wrapped text file is reflowed into paragraphs and headings`() {
        val text = "                    GNU LICENSE\n                 Version 3\n\n" +
            "  The licence is\nwrapped by hand & here.\n\n  0. Definitions.\n\n  TERMS AND CONDITIONS\n"
        assertEquals(
            "<p class=\"c\"><strong>GNU LICENSE Version 3</strong></p>" +
                "<p>The licence is wrapped by hand &amp; here.</p><h3>0. Definitions.</h3><h3>TERMS AND CONDITIONS</h3>",
            MarkdownLite.plainText(text)
        )
    }

    @Test
    fun `the changelog opens on its first section`() {
        val md = "# Changelog\n\nAll notable changes.\n\n## [Unreleased]\n### Added\n- one"
        val html = MarkdownLite.toHtml(MarkdownLite.fromFirstSection(md))
        assertTrue(html.startsWith("<h2>[Unreleased]</h2>"))
        assertTrue("notable" !in html)
        assertEquals("no sections", MarkdownLite.fromFirstSection("no sections"))
    }
}
