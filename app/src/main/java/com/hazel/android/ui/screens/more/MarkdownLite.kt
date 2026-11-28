package com.hazel.android.ui.screens.more

/**
 * Turns Markdown files (the project's own, and the engines' supported-site lists) into a light
 * HTML page, so they open in a moment instead of inside the whole GitHub page around them.
 *
 * Understood: headings, bullet and numbered lists (nested by indent), paragraphs, bold,
 * italics, inline code, fenced code, rules, links, bare web addresses, pipe tables and the
 * plain HTML tables GitHub files use (cells keep only bold and web links). Anything else is
 * shown as its text, escaped, so a file that grows a new construct still reads; it never runs
 * as markup.
 */
object MarkdownLite {

    /**
     * The page for [markdown], styled to the given colours (CSS colour strings). Titles and
     * links take the site's teal rather than the app's accent, a shade per [dark] tone so
     * they read on either background. The file's own preface (its title and the note on its
     * format) is left out, so the page opens on the first section.
     */
    fun page(markdown: String, background: String, text: String, muted: String, dark: Boolean, plain: Boolean = false): String {
        val body = if (plain) plainText(markdown) else toHtml(fromFirstSection(markdown))
        val accent = if (dark) TEAL_ON_DARK else TEAL_ON_LIGHT
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{margin:0;padding:8px 20px 40px;background:$background;color:$text;font:15px/1.6 -apple-system,Roboto,sans-serif;word-wrap:break-word}
h1{font-size:22px;margin:18px 0 8px}
h2{font-size:22px;font-weight:800;letter-spacing:-.01em;margin:32px 0 10px;padding-top:28px;border-top:1px solid rgba(127,127,127,.22);color:$accent}
h2:first-child{margin-top:12px;padding-top:0;border-top:0}
h3{font-size:12px;font-weight:700;margin:20px 0 6px;text-transform:uppercase;letter-spacing:.08em;color:$muted}
ul,ol{padding-left:18px;margin:4px 0}li{margin:8px 0}p{margin:8px 0}
code{font:13px monospace;background:rgba(127,127,127,.18);padding:1px 5px;border-radius:5px}
a{color:$accent}.c{text-align:center}pre{white-space:pre-wrap;font:13px/1.5 monospace;margin:0}
pre.code{background:rgba(127,127,127,.12);padding:10px 12px;border-radius:10px;margin:10px 0}
hr{height:1px;background:rgba(127,127,127,.22);border:0;margin:20px 0}
.tag{font-size:12px;color:$muted;background:rgba(127,127,127,.14);padding:1px 7px;border-radius:99px;white-space:nowrap}
.t{overflow-x:auto;overflow-y:hidden;-webkit-overflow-scrolling:touch;overscroll-behavior-x:contain;touch-action:pan-x pan-y;margin:12px -20px;padding:0 20px}
table{border-collapse:collapse;font-size:13px;line-height:1.45;min-width:100%}
th{text-align:left;color:$muted;font-size:12px;text-transform:uppercase;letter-spacing:.06em;padding:8px 10px}
td{padding:9px 10px;vertical-align:top;min-width:120px;overflow-wrap:anywhere}td a{white-space:nowrap}
tbody tr:nth-child(odd) td{background:rgba(127,127,127,.07)}
td.g{background:none!important;color:$accent;padding-top:22px;font-size:14px}
</style></head><body>$body</body></html>"""
    }

    /** [markdown] from its first "## " section on; all of it when it has none. */
    fun fromFirstSection(markdown: String): String {
        val lines = markdown.lines()
        val first = lines.indexOfFirst { it.startsWith("## ") }
        return if (first <= 0) markdown else lines.drop(first).joinToString("\n")
    }

    /** The site's teal: deep on a light page, bright on a dark one. */
    private const val TEAL_ON_LIGHT = "#0E7C86"
    private const val TEAL_ON_DARK = "#5CD0D6"

    // Built once: files such as the supported-site lists run to thousands of lines.
    private val HEADING = Regex("^(#{1,6})\\s+(.*)")
    private val BULLET = Regex("^[-*+]\\s+(.*)")
    private val NUMBERED = Regex("^\\d{1,9}[.)]\\s+(.*)")
    private val RULE = Regex("^([-*_])(\\s*\\1){2,}$")
    private val TABLE_DIVIDER = Regex("^\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?$")
    private val BOLD = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*")
    private val ITALIC_STAR = Regex("(?<![*\\w])\\*(?=[^\\s*])([^*]+?)(?<=[^\\s*])\\*(?![*\\w])")
    private val ITALIC_UNDERSCORE = Regex("(?<![\\w&])_(?=\\S)([^_]+?)(?<=\\S)_(?!\\w)")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)\\s]+)(?:\\s+&quot;([^&]*)&quot;)?\\)")
    private val WHITESPACE = Regex("\\s+")
    private val HELD_BOLD = Regex("\u0001(\\d+)\u0001")
    private val BARE_URL = Regex("https?://[^\\s<>\"']+")
    private val HTML_ROW = Regex("<tr\\b[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val HTML_CELL = Regex("<(t[hd])\\b([^>]*)>(.*?)</t[hd]>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val COLSPAN = Regex("colspan\\s*=\\s*\"?(\\d+)", RegexOption.IGNORE_CASE)
    private val CELL_LINK = Regex("<a\\s[^>]*href\\s*=\\s*\"(https?://[^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val CELL_BOLD = Regex("<(strong|b)>(.*?)</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val ANY_TAG = Regex("<[^>]*>")

    fun toHtml(markdown: String): String {
        val out = StringBuilder()
        // Open lists, innermost last: the indent each started at and whether it is numbered.
        val lists = ArrayDeque<Pair<Int, Boolean>>()
        val paragraph = StringBuilder()
        val lines = markdown.replace("\r", "").lines()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) out.append("<p>").append(inline(paragraph.toString().trim())).append("</p>")
            paragraph.clear()
        }
        fun closeLists(toIndent: Int = -1) {
            while (lists.isNotEmpty() && lists.last().first > toIndent) {
                out.append(if (lists.removeLast().second) "</li></ol>" else "</li></ul>")
            }
        }
        fun listItem(indent: Int, numbered: Boolean, body: String) {
            flushParagraph()
            if (lists.isEmpty() || indent > lists.last().first) {
                out.append(if (numbered) "<ol><li>" else "<ul><li>")
                lists.addLast(indent to numbered)
            } else {
                closeLists(indent)
                if (lists.isNotEmpty() && lists.last().second != numbered) {
                    closeLists(indent - 1)
                    out.append(if (numbered) "<ol><li>" else "<ul><li>")
                    lists.addLast(indent to numbered)
                } else {
                    out.append("</li><li>")
                }
            }
            out.append(inline(body))
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val indent = line.length - line.trimStart().length
            val trimmed = line.trimStart()
            when {
                line.isBlank() -> flushParagraph()
                // HTML comments say nothing to a reader.
                trimmed.startsWith("<!--") -> {
                    while (i < lines.size && !lines[i].contains("-->")) i++
                }
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    flushParagraph(); closeLists()
                    val fence = trimmed.take(3)
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                        code.append(escape(lines[i])).append('\n'); i++
                    }
                    out.append("<pre class=\"code\">").append(code.toString().trimEnd()).append("</pre>")
                }
                trimmed.startsWith("<table", ignoreCase = true) -> {
                    flushParagraph(); closeLists()
                    val block = StringBuilder()
                    while (i < lines.size) {
                        block.append(lines[i]).append('\n')
                        if (lines[i].contains("</table>", ignoreCase = true)) break
                        i++
                    }
                    out.append(htmlTable(block.toString()))
                }
                trimmed.startsWith("|") && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1].trim()) -> {
                    flushParagraph(); closeLists()
                    val rows = mutableListOf(trimmed)
                    i += 2
                    while (i < lines.size && lines[i].trimStart().startsWith("|")) { rows += lines[i].trim(); i++ }
                    i--
                    out.append(pipeTable(rows))
                }
                indent < 4 && RULE.matches(trimmed) -> {
                    flushParagraph(); closeLists(); out.append("<hr>")
                }
                else -> {
                    val heading = if (indent == 0) HEADING.find(trimmed) else null
                    val bullet = BULLET.find(trimmed)
                    val numbered = if (bullet == null) NUMBERED.find(trimmed) else null
                    when {
                        heading != null -> {
                            flushParagraph(); closeLists()
                            val level = heading.groupValues[1].length
                            out.append("<h$level>").append(inline(heading.groupValues[2])).append("</h$level>")
                        }
                        bullet != null -> listItem(indent, false, bullet.groupValues[1])
                        numbered != null -> listItem(indent, true, numbered.groupValues[1])
                        lists.isNotEmpty() && indent > 0 -> out.append(' ').append(inline(trimmed))
                        else -> {
                            closeLists()
                            paragraph.append(trimmed).append(' ')
                        }
                    }
                }
            }
            i++
        }
        flushParagraph(); closeLists()
        return out.toString()
    }

    /** A Markdown pipe table: the first row is the header. */
    private fun pipeTable(rows: List<String>): String {
        fun cells(row: String) = row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
        val head = cells(rows.first())
        val sb = StringBuilder("<div class=\"t\"><table><thead><tr>")
        head.forEach { sb.append("<th>").append(inline(it)).append("</th>") }
        sb.append("</tr></thead><tbody>")
        rows.drop(1).forEach { row ->
            sb.append("<tr>")
            cells(row).forEach { sb.append("<td>").append(inline(it)).append("</td>") }
            sb.append("</tr>")
        }
        return sb.append("</tbody></table></div>").toString()
    }

    /**
     * An HTML table as GitHub files write them. Only rows and cells are read; a cell keeps
     * its bold and its web links, the rest of it is text. A cell spanning the whole row
     * (a group title) is drawn as one.
     */
    private fun htmlTable(html: String): String {
        val sb = StringBuilder("<div class=\"t\"><table>")
        var inBody = false
        for (row in HTML_ROW.findAll(html)) {
            val cells = HTML_CELL.findAll(row.groupValues[1]).toList()
            if (cells.isEmpty()) continue
            val header = cells.all { it.groupValues[1].equals("th", ignoreCase = true) }
            if (header && !inBody) sb.append("<thead>")
            if (!header && !inBody) { sb.append("<tbody>"); inBody = true }
            sb.append("<tr>")
            for (cell in cells) {
                val span = COLSPAN.find(cell.groupValues[2])?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 20)
                when {
                    header -> sb.append("<th>")
                    span != null && span > 1 -> sb.append("<td colspan=\"").append(span).append("\" class=\"g\">")
                    else -> sb.append("<td>")
                }
                sb.append(cellText(cell.groupValues[3])).append(if (header) "</th>" else "</td>")
            }
            sb.append("</tr>")
            if (header && !inBody) sb.append("</thead>")
        }
        if (inBody) sb.append("</tbody>")
        return sb.append("</table></div>").toString()
    }

    /** A table cell's own markup reduced to bold and web links; everything else escaped. */
    private fun cellText(raw: String): String {
        val links = mutableListOf<String>()
        var s = CELL_LINK.replace(raw) { m ->
            links += "<a href=\"${escape(m.groupValues[1])}\">${escape(unentity(ANY_TAG.replace(m.groupValues[2], "")).trim())}</a>"
            "\u0000${links.size - 1}\u0000"
        }
        val bolds = mutableListOf<String>()
        s = CELL_BOLD.replace(s) { m ->
            bolds += "<strong>${escape(unentity(ANY_TAG.replace(m.groupValues[2], "")).trim())}</strong>"
            "\u0001${bolds.size - 1}\u0001"
        }
        s = linkUrls(escape(unentity(ANY_TAG.replace(s, " ")).replace(WHITESPACE, " ").trim()), short = true)
        // Bold first: a bold cell may hold a link.
        s = HELD_BOLD.replace(s) { bolds[it.groupValues[1].toInt()] }
        return HELD.replace(s) { links[it.groupValues[1].toInt()] }
    }

    /** The few entities these files use, turned back into characters before escaping again. */
    private fun unentity(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&")

    /**
     * A plain text file such as the licence, reflowed for a phone. Such files are wrapped
     * by hand at about 75 characters, which breaks badly on a narrow screen, so each block
     * between blank lines becomes one paragraph. A block indented far in (the centred title
     * lines) is centred, and a short block ending without a full stop, or a numbered one
     * like "0. Definitions.", reads as a heading.
     */
    fun plainText(text: String): String = text.replace("\r", "")
        .split(Regex("\\n\\s*\\n"))
        .filter { it.isNotBlank() }
        .joinToString("") { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            val joined = escape(lines.joinToString(" ") { it.trim() }.replace(Regex("\\s{2,}"), " "))
            val centred = lines.all { it.length - it.trimStart().length >= 8 }
            val numberedTitle = lines.size == 1 && Regex("^\\s*\\d+\\.\\s+\\S.{0,60}$").matches(lines[0])
            when {
                centred -> "<p class=\"c\"><strong>$joined</strong></p>"
                numberedTitle || (lines.size == 1 && joined.length < 40 && !joined.endsWith(".")) -> "<h3>$joined</h3>"
                else -> "<p>$joined</p>"
            }
        }

    internal fun inline(text: String): String {
        // Code first, so nothing inside backticks is read as bold or a link.
        val parts = text.split('`')
        return parts.mapIndexed { i, part ->
            if (i % 2 == 1 && i < parts.size - 1) "<code>${escape(part)}</code>"
            else {
                // Links and bare addresses are set aside first, so an underscore or star inside
                // an address is never read as emphasis.
                val held = mutableListOf<String>()
                fun hold(html: String) = "\u0000${held.size}\u0000".also { held += html }
                var s = escape(if (i % 2 == 1) "`$part" else part)
                // A link to a web page opens it; any other target (such as the "## " notes in
                // the yt-dlp list) is a small label, its title written in front (phones have no hover).
                s = LINK.replace(s) { m ->
                    val target = m.groupValues[2]
                    val title = m.groupValues[3]
                    val label = emphasis(m.groupValues[1])
                    hold(
                        if (target.startsWith("http://") || target.startsWith("https://")) "<a href=\"$target\">$label</a>"
                        else "<span class=\"tag\">" + (if (title.isNotEmpty()) "$title: " else "") + "$label</span>"
                    )
                }
                s = BARE_URL.replace(s) { m -> urlAndRest(m.value).let { (url, rest) -> hold("<a href=\"$url\">$url</a>") + rest } }
                s = emphasis(s)
                HELD.replace(s) { held[it.groupValues[1].toInt()] }
            }
        }.joinToString("")
    }

    private val HELD = Regex("\u0000(\\d+)\u0000")

    private fun emphasis(s: String): String {
        var r = BOLD.replace(s, "<strong>$1</strong>")
        r = ITALIC_STAR.replace(r, "<em>$1</em>")
        return ITALIC_UNDERSCORE.replace(r, "<em>$1</em>")
    }

    /**
     * A matched address split from what follows it: an escaped quote or bracket ends it, and
     * trailing punctuation belongs to the sentence, not the address.
     */
    private fun urlAndRest(match: String): Pair<String, String> {
        val stop = listOf("&quot;", "&lt;", "&gt;").map { match.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: match.length
        val url = match.substring(0, stop).trimEnd('.', ',', ';', ':', ')', '!', '?')
        return url to match.substring(url.length)
    }

    /**
     * Bare web addresses in escaped [text] become links. In a table cell the address shows as
     * its site alone (2ch.org), so a narrow column doesn't break it letter by letter.
     */
    private fun linkUrls(text: String, short: Boolean = false): String = BARE_URL.replace(text) { m ->
        urlAndRest(m.value).let { (url, rest) ->
            val shown = if (short) url.substringAfter("://").removePrefix("www.").trimEnd('/') else url
            "<a href=\"$url\">$shown</a>$rest"
        }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
