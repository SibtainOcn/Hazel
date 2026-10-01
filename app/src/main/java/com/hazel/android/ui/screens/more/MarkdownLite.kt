package com.hazel.android.ui.screens.more

/**
 * Turns the project's own Markdown files into a light HTML page, so the changelog and the
 * licence open in a moment instead of inside the whole GitHub page around them.
 *
 * Only what those files use is understood: headings, bullet lists (nested by indent),
 * paragraphs, bold, inline code and links. Anything else is shown as its text, escaped, so
 * a file that grows a new construct still reads; it never runs as markup.
 */
object MarkdownLite {

    /** The page for [markdown], styled to the given colours (CSS colour strings). */
    fun page(markdown: String, background: String, text: String, muted: String, accent: String, plain: Boolean = false): String {
        val body = if (plain) plainText(markdown) else toHtml(markdown)
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{margin:0;padding:8px 20px 40px;background:$background;color:$text;font:15px/1.55 -apple-system,Roboto,sans-serif;word-wrap:break-word}
h1{font-size:22px;margin:18px 0 8px}h2{font-size:19px;margin:26px 0 6px;color:$accent}h3{font-size:15px;margin:16px 0 4px;text-transform:uppercase;letter-spacing:.04em;color:$muted}
ul{padding-left:18px;margin:4px 0}li{margin:4px 0}p{margin:8px 0}
code{font:13px monospace;background:rgba(127,127,127,.18);padding:1px 5px;border-radius:5px}
a{color:$accent}pre{white-space:pre-wrap;font:13px/1.5 monospace;margin:0}
</style></head><body>$body</body></html>"""
    }

    fun toHtml(markdown: String): String {
        val out = StringBuilder()
        val listIndents = ArrayDeque<Int>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) out.append("<p>").append(inline(paragraph.toString().trim())).append("</p>")
            paragraph.clear()
        }
        fun closeLists(toIndent: Int = -1) {
            while (listIndents.isNotEmpty() && listIndents.last() > toIndent) {
                out.append("</li></ul>")
                listIndents.removeLast()
            }
        }

        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            val indent = line.length - line.trimStart().length
            val trimmed = line.trimStart()
            val heading = Regex("^(#{1,6})\\s+(.*)").find(trimmed)
            val bullet = Regex("^[-*]\\s+(.*)").find(trimmed)
            when {
                line.isBlank() -> flushParagraph()
                heading != null && indent == 0 -> {
                    flushParagraph(); closeLists()
                    val level = heading.groupValues[1].length
                    out.append("<h$level>").append(inline(heading.groupValues[2])).append("</h$level>")
                }
                bullet != null -> {
                    flushParagraph()
                    if (listIndents.isEmpty() || indent > listIndents.last()) {
                        out.append("<ul><li>")
                        listIndents.addLast(indent)
                    } else {
                        closeLists(indent)
                        out.append("</li><li>")
                    }
                    out.append(inline(bullet.groupValues[1]))
                }
                listIndents.isNotEmpty() && indent > 0 -> out.append(' ').append(inline(trimmed))
                else -> {
                    closeLists()
                    paragraph.append(trimmed).append(' ')
                }
            }
        }
        flushParagraph(); closeLists()
        return out.toString()
    }

    /** A plain text file, such as the licence, kept as it is laid out. */
    fun plainText(text: String): String = "<pre>${escape(text)}</pre>"

    private fun inline(text: String): String {
        // Code first, so nothing inside backticks is read as bold or a link.
        val parts = text.split('`')
        return parts.mapIndexed { i, part ->
            if (i % 2 == 1 && i < parts.size - 1) "<code>${escape(part)}</code>"
            else {
                var s = escape(if (i % 2 == 1) "`$part" else part)
                s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
                s = s.replace(Regex("\\[([^\\]]+)]\\((https?://[^)\\s\"]+)\\)"), "<a href=\"$2\">$1</a>")
                s
            }
        }.joinToString("")
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
