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
    fun `plain text keeps its layout`() {
        assertEquals("<pre>a &amp; b\n  c</pre>", MarkdownLite.plainText("a & b\n  c"))
    }
}
