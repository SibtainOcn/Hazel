package com.hazel.android.gallery

/**
 * Splits the `-j` output of one gallery-dl run over several links back into each link's part.
 * Every link's output follows a [MARK] line naming it ([RUN_PREFIX] prints them), and a link
 * gallery-dl can't read gets its mark with nothing after it. Fed from the reading thread.
 */
internal class GalleryLinkOutput(private val urls: List<String>, private val maxChars: Int) {

    private val parts = arrayOfNulls<StringBuilder>(urls.size)
    private val loose = StringBuilder()
    private var current = -1
    private var marked = false
    private var total = 0

    /** Takes one line of output; returns the index of the link whose part this line closed, if any. */
    @Synchronized
    fun feed(line: String): Int? {
        total += line.length + 1
        if (total > maxChars) throw GalleryDlException(GalleryDlException.Kind.OTHER, "listing too large")
        if (line.startsWith(MARK)) {
            val closed = current.takeIf { it >= 0 }
            val named = urls.indexOf(line.substring(MARK.length))
            current = if (named >= 0) named else (current + 1).coerceAtMost(urls.lastIndex)
            parts[current] = StringBuilder()
            marked = true
            return closed.takeIf { it != current }
        }
        (if (current >= 0) parts[current]!! else loose).append(line).append('\n')
        return null
    }

    /**
     * The output of the link at [index], or null when gallery-dl gave none. Without marks (an
     * older runner), the whole output belongs to a lone link, and to several only when it holds
     * exactly one listing for each.
     */
    @Synchronized
    fun part(index: Int): String? {
        if (marked) return parts[index]?.toString()?.takeIf { it.isNotBlank() }
        if (urls.size == 1) return loose.toString().takeIf { it.isNotBlank() }
        return listings().takeIf { it.size == urls.size }?.get(index)
    }

    /** The top-level listings in unmarked output: gallery-dl starts and ends each at column 0. */
    private fun listings(): List<String> {
        val found = mutableListOf<String>()
        val open = StringBuilder()
        loose.lineSequence().forEach { line ->
            if (line.isEmpty() && open.isEmpty()) return@forEach
            open.append(line).append('\n')
            if (line == "]" || line == "[]") {
                found += open.toString()
                open.clear()
            }
        }
        return found
    }

    companion object {
        const val MARK = "\u001e"

        /**
         * Python run before gallery-dl's main: each top-level `-j` read prints [MARK] and its
         * link first. Reads started for a post a listing leads to have a parent and stay unmarked.
         */
        const val RUN_PREFIX = "import sys, gallery_dl, gallery_dl.extractor, gallery_dl.job\n" +
            "try:\n" +
            "    _hazel_init = gallery_dl.job.DataJob.__init__\n" +
            "    def _hazel_mark(self, url, parent=None, *a, **k):\n" +
            "        if parent is None:\n" +
            "            sys.stdout.write('\\x1e' + str(url) + '\\n')\n" +
            "            sys.stdout.flush()\n" +
            "        _hazel_init(self, url, parent, *a, **k)\n" +
            "    gallery_dl.job.DataJob.__init__ = _hazel_mark\n" +
            "except Exception:\n" +
            "    pass\n"
    }
}
