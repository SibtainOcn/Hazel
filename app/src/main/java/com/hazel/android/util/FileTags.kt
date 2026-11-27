package com.hazel.android.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * The tags yt-dlp's `--embed-metadata` writes into a download: title, artist, album, genre,
 * date, description and the page link (`purl`, or `comment` where the container has no
 * `purl`). Android's own reader leaves description and link out, so they are read here.
 */
data class FileTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val date: String = "",
    val description: String = "",
    val link: String = "",
    val comment: String = ""
) {
    val isEmpty: Boolean
        get() = listOf(title, artist, album, genre, date, description, link, comment).all { it.isBlank() }
}

/** Random access to a file's bytes. */
interface ByteSource {
    val size: Long
    /** Reads up to [len] bytes at [pos]; fewer only at the end. */
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int
}

class ArraySource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (pos >= bytes.size) return 0
        val n = minOf(len.toLong(), bytes.size - pos).toInt()
        System.arraycopy(bytes, pos.toInt(), buf, off, n)
        return n
    }
}

private class ChannelSource(private val channel: FileChannel) : ByteSource {
    override val size: Long = channel.size()
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
        var done = 0
        while (done < len) {
            val n = channel.read(ByteBuffer.wrap(buf, off + done, len - done), pos + done)
            if (n <= 0) break
            done += n
        }
        return done
    }
}

object FileTagReader {

    /** A tag longer than this is art or something else that is not text, and is skipped. */
    private const val TEXT_MAX = 256 * 1024
    private const val MAX_BOXES = 4096

    /** Reads [fileUri]'s tags off the main thread; empty for anything unreadable. */
    suspend fun read(context: Context, fileUri: String): FileTags = withContext(Dispatchers.IO) {
        if (fileUri.isBlank()) return@withContext FileTags()
        runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(fileUri), "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { read(ChannelSource(it.channel)) }
            }
        }.getOrNull() ?: FileTags()
    }

    fun read(source: ByteSource): FileTags {
        val head = source.bytes(0, 12) ?: return FileTags()
        val raw = runCatching {
            when {
                head.ascii(4, 4) == "ftyp" -> Mp4.read(source)
                head.u32be(0) == 0x1A45DFA3L -> Matroska.read(source)
                head.ascii(0, 4) == "OggS" -> Ogg.read(source)
                head.ascii(0, 4) == "fLaC" -> Flac.read(source)
                head.ascii(0, 3) == "ID3" -> Id3.read(source)
                else -> emptyMap()
            }
        }.getOrDefault(emptyMap())
        return tagsOf(raw)
    }

    /** Joins the keys each container uses into one answer; keys are lower case. */
    internal fun tagsOf(raw: Map<String, String>): FileTags {
        fun get(vararg keys: String) = keys.firstNotNullOfOrNull { k -> raw[k]?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()
        val comment = get("comment")
        val link = get("purl").ifEmpty { comment.takeIf(::isLink).orEmpty() }
        // Some files carry the link as the description, with the text itself as the synopsis.
        val description = listOf("description", "synopsis")
            .firstNotNullOfOrNull { k -> raw[k]?.trim()?.takeIf { it.isNotEmpty() && !isLink(it) } }.orEmpty()
        return FileTags(
            title = get("title"),
            artist = get("artist"),
            album = get("album"),
            genre = get("genre"),
            date = get("date"),
            description = description,
            link = link,
            comment = comment.takeUnless { it == link || it == description }.orEmpty()
        )
    }

    private fun isLink(text: String) = text.startsWith("https://") || text.startsWith("http://")

    // ── MP4 / M4A: moov > udta > meta > ilst, one atom per tag with its text in a data atom ──

    private object Mp4 {
        private val KEYS = mapOf(
            "©nam" to "title", "©ART" to "artist", "©alb" to "album", "©gen" to "genre",
            "©day" to "date", "©cmt" to "comment", "desc" to "description", "ldes" to "synopsis"
        )

        fun read(s: ByteSource): Map<String, String> {
            val moov = child(s, 0, s.size, "moov") ?: return emptyMap()
            val udta = child(s, moov.first, moov.second, "udta") ?: return emptyMap()
            val meta = child(s, udta.first, udta.second, "meta") ?: return emptyMap()
            // meta is a full box: a version and flags come before its children.
            val ilst = child(s, meta.first + 4, meta.second, "ilst")
                ?: child(s, meta.first, meta.second, "ilst") ?: return emptyMap()
            val out = HashMap<String, String>()
            boxes(s, ilst.first, ilst.second) { type, start, end ->
                val key = KEYS[type] ?: return@boxes
                val data = child(s, start, end, "data") ?: return@boxes
                val length = data.second - data.first - 8
                if (length in 1..TEXT_MAX) s.bytes(data.first + 8, length.toInt())?.let { out[key] = String(it, Charsets.UTF_8) }
            }
            return out
        }

        /** The first child box named [name] between [from] and [to], as its payload range. */
        private fun child(s: ByteSource, from: Long, to: Long, name: String): Pair<Long, Long>? {
            var found: Pair<Long, Long>? = null
            boxes(s, from, to) { type, start, end -> if (found == null && type == name) found = start to end }
            return found
        }

        private inline fun boxes(s: ByteSource, from: Long, to: Long, each: (String, Long, Long) -> Unit) {
            var pos = from
            var count = 0
            while (pos + 8 <= to && count++ < MAX_BOXES) {
                val header = s.bytes(pos, 16) ?: s.bytes(pos, 8) ?: return
                var size = header.u32be(0)
                var headerSize = 8L
                if (size == 1L && header.size >= 16) {
                    size = header.u64be(8)
                    headerSize = 16
                } else if (size == 0L) {
                    size = to - pos
                }
                if (size < headerSize || pos + size > to) return
                each(String(header, 4, 4, Charsets.ISO_8859_1), pos + headerSize, pos + size)
                pos += size
            }
        }
    }

    // ── Matroska / WebM: Segment > Tags > Tag > SimpleTag (TagName, TagString) ──

    private object Matroska {
        private const val SEGMENT = 0x18538067L
        private const val SEEK_HEAD = 0x114D9B74L
        private const val SEEK = 0x4DBBL
        private const val SEEK_ID = 0x53ABL
        private const val SEEK_POSITION = 0x53ACL
        private const val TAGS = 0x1254C367L
        private const val TAG = 0x7373L
        private const val TARGETS = 0x63C0L
        private const val TRACK_UID = 0x63C5L
        private const val SIMPLE_TAG = 0x67C8L
        private const val TAG_NAME = 0x45A3L
        private const val TAG_STRING = 0x4487L
        private const val CLUSTER = 0x1F43B675L
        private const val INFO = 0x1549A966L
        private const val INFO_TITLE = 0x7BA9L
        private const val UNKNOWN = -1L

        private class Element(val id: Long, val dataStart: Long, val size: Long)

        fun read(s: ByteSource): Map<String, String> {
            val ebml = element(s, 0) ?: return emptyMap()
            val segment = element(s, ebml.dataStart + ebml.size)?.takeIf { it.id == SEGMENT } ?: return emptyMap()
            val segmentEnd = if (segment.size == UNKNOWN) s.size else minOf(s.size, segment.dataStart + segment.size)
            val tagPlaces = LinkedHashSet<Long>()
            var title = ""
            var pos = segment.dataStart
            var count = 0
            while (pos < segmentEnd && count++ < MAX_BOXES) {
                val e = element(s, pos) ?: break
                when (e.id) {
                    SEEK_HEAD -> seekTags(s, e, segment.dataStart, tagPlaces)
                    TAGS -> tagPlaces += pos
                    INFO -> children(s, e) { if (it.id == INFO_TITLE) title = text(s, it) }
                }
                if (e.size == UNKNOWN) break
                pos = e.dataStart + e.size
                // Media data follows; what is left to find is pointed to by the seek head.
                if (e.id == CLUSTER && tagPlaces.isNotEmpty()) break
            }
            val out = HashMap<String, String>()
            tagPlaces.forEach { place -> element(s, place)?.takeIf { it.id == TAGS }?.let { tags(s, it, out) } }
            if (title.isNotEmpty()) out.putIfAbsent("title", title)
            return out
        }

        private fun seekTags(s: ByteSource, head: Element, segmentData: Long, into: MutableSet<Long>) {
            children(s, head) { seek ->
                if (seek.id != SEEK) return@children
                var id = 0L
                var position = -1L
                children(s, seek) { field ->
                    when (field.id) {
                        SEEK_ID -> id = uint(s, field)
                        SEEK_POSITION -> position = uint(s, field)
                    }
                }
                if (id == TAGS && position >= 0) into += segmentData + position
            }
        }

        private fun tags(s: ByteSource, tags: Element, out: MutableMap<String, String>) {
            children(s, tags) { tag ->
                if (tag.id != TAG) return@children
                var perTrack = false
                val found = HashMap<String, String>()
                children(s, tag) { part ->
                    when (part.id) {
                        TARGETS -> children(s, part) { if (it.id == TRACK_UID) perTrack = true }
                        SIMPLE_TAG -> {
                            var name = ""
                            var value = ""
                            children(s, part) { field ->
                                when (field.id) {
                                    TAG_NAME -> name = text(s, field)
                                    TAG_STRING -> value = text(s, field)
                                }
                            }
                            if (name.isNotEmpty() && value.isNotEmpty()) found[name.lowercase()] = value
                        }
                    }
                }
                // Per-track tags hold encoder, language and duration, never the video's own.
                if (!perTrack) found.forEach { (k, v) -> out.putIfAbsent(k, v) }
            }
        }

        private inline fun children(s: ByteSource, parent: Element, each: (Element) -> Unit) {
            if (parent.size == UNKNOWN) return
            val end = parent.dataStart + parent.size
            var pos = parent.dataStart
            var count = 0
            while (pos < end && count++ < MAX_BOXES) {
                val e = element(s, pos) ?: return
                if (e.size == UNKNOWN || e.dataStart + e.size > end) return
                each(e)
                pos = e.dataStart + e.size
            }
        }

        private fun text(s: ByteSource, e: Element): String {
            if (e.size !in 1..TEXT_MAX.toLong()) return ""
            return s.bytes(e.dataStart, e.size.toInt())?.let { String(it, Charsets.UTF_8).trimEnd('\u0000') }.orEmpty()
        }

        private fun uint(s: ByteSource, e: Element): Long {
            if (e.size !in 1..8) return 0
            val b = s.bytes(e.dataStart, e.size.toInt()) ?: return 0
            return b.fold(0L) { acc, x -> (acc shl 8) or (x.toLong() and 0xFF) }
        }

        private fun element(s: ByteSource, pos: Long): Element? {
            val b = s.bytes(pos, 12) ?: s.bytes(pos, (s.size - pos).toInt().coerceIn(0, 12)) ?: return null
            if (b.isEmpty()) return null
            val idLength = vintLength(b[0]) ?: return null
            if (idLength > 4 || b.size < idLength + 1) return null
            val id = (0 until idLength).fold(0L) { acc, i -> (acc shl 8) or (b[i].toLong() and 0xFF) }
            val sizeLength = vintLength(b[idLength]) ?: return null
            if (b.size < idLength + sizeLength) return null
            var size = (b[idLength].toLong() and (0xFFL shr sizeLength))
            var allOnes = size == (0xFFL shr sizeLength)
            for (i in 1 until sizeLength) {
                val x = b[idLength + i].toLong() and 0xFF
                if (x != 0xFFL) allOnes = false
                size = (size shl 8) or x
            }
            return Element(id, pos + idLength + sizeLength, if (allOnes) UNKNOWN else size)
        }

        private fun vintLength(first: Byte): Int? {
            val v = first.toInt() and 0xFF
            for (i in 0 until 8) if (v and (0x80 shr i) != 0) return i + 1
            return null
        }
    }

    // ── Vorbis comments: Opus and Vorbis in Ogg, and FLAC ──

    /** `KEY=value` entries after a vendor string, little endian lengths; pictures are skipped. */
    private fun vorbisComments(next: (Int) -> ByteArray?, skip: (Long) -> Boolean): Map<String, String> {
        val out = HashMap<String, String>()
        val vendor = next(4)?.u32le(0) ?: return out
        if (!skip(vendor)) return out
        val count = next(4)?.u32le(0) ?: return out
        for (i in 0 until minOf(count, MAX_BOXES.toLong())) {
            val length = next(4)?.u32le(0) ?: break
            if (length > TEXT_MAX) {
                if (!skip(length)) break
                continue
            }
            val entry = next(length.toInt())?.let { String(it, Charsets.UTF_8) } ?: break
            val eq = entry.indexOf('=')
            if (eq > 0) out.putIfAbsent(entry.substring(0, eq).lowercase(), entry.substring(eq + 1))
        }
        return out
    }

    private object Flac {
        fun read(s: ByteSource): Map<String, String> {
            var pos = 4L
            var count = 0
            while (pos + 4 <= s.size && count++ < 64) {
                val h = s.bytes(pos, 4) ?: return emptyMap()
                val last = h[0].toInt() and 0x80 != 0
                val type = h[0].toInt() and 0x7F
                val length = ((h[1].toLong() and 0xFF) shl 16) or ((h[2].toLong() and 0xFF) shl 8) or (h[3].toLong() and 0xFF)
                if (type == 4) {
                    var at = pos + 4
                    val end = at + length
                    return vorbisComments(
                        next = { n -> if (at + n > end) null else s.bytes(at, n)?.also { at += n } },
                        skip = { n -> (at + n <= end).also { if (it) at += n } }
                    )
                }
                if (last) break
                pos += 4 + length
            }
            return emptyMap()
        }
    }

    /** The second packet of an Ogg stream, read page by page as a byte stream. */
    private object Ogg {
        fun read(s: ByteSource): Map<String, String> {
            val packet = PacketReader(s)
            if (!packet.skipFirst()) return emptyMap()
            val magic = packet.take(7) ?: return emptyMap()
            val opus = String(magic, Charsets.ISO_8859_1) == "OpusTag" && packet.take(1)?.get(0) == 's'.code.toByte()
            val vorbis = magic[0].toInt() == 3 && String(magic, 1, 6, Charsets.ISO_8859_1) == "vorbis"
            if (!opus && !vorbis) return emptyMap()
            return vorbisComments(next = packet::take, skip = packet::skip)
        }

        private class PacketReader(private val s: ByteSource) {
            private var page = 0L
            private var lacing = ByteArray(0)
            private var segment = 0
            private var bodyPos = 0L
            /** Bytes left of the current segment. */
            private var left = 0
            private var packetEnded = false
            private var pages = 0

            private fun nextPage(): Boolean {
                if (pages++ > 512) return false
                val h = s.bytes(page, 27) ?: return false
                if (String(h, 0, 4, Charsets.ISO_8859_1) != "OggS") return false
                val segments = h[26].toInt() and 0xFF
                lacing = s.bytes(page + 27, segments) ?: return false
                bodyPos = page + 27 + segments
                page = bodyPos + lacing.sumOf { it.toInt() and 0xFF }
                segment = 0
                return true
            }

            /** Moves to the next segment; false at the end of the packet or the stream. */
            private fun advance(): Boolean {
                while (left == 0) {
                    if (packetEnded) return false
                    if (segment >= lacing.size && !nextPage()) return false
                    if (segment >= lacing.size) continue
                    val length = lacing[segment++].toInt() and 0xFF
                    left = length
                    if (length < 255) packetEnded = true
                    if (length == 0) return false
                }
                return true
            }

            fun skipFirst(): Boolean {
                while (advance()) { bodyPos += left; left = 0 }
                packetEnded = false
                return true
            }

            fun take(n: Int): ByteArray? {
                val out = ByteArray(n)
                var done = 0
                while (done < n) {
                    if (!advance()) return null
                    val k = minOf(left, n - done)
                    if (s.read(bodyPos, out, done, k) < k) return null
                    bodyPos += k; left -= k; done += k
                }
                return out
            }

            fun skip(n: Long): Boolean {
                var rest = n
                while (rest > 0) {
                    if (!advance()) return false
                    val k = minOf(left.toLong(), rest).toInt()
                    bodyPos += k; left -= k; rest -= k
                }
                return true
            }
        }
    }

    // ── MP3: ID3v2.3 / 2.4 text, comment and user frames ──

    private object Id3 {
        private val TEXT = mapOf(
            "TIT2" to "title", "TPE1" to "artist", "TALB" to "album", "TCON" to "genre", "TDRC" to "date", "TYER" to "date"
        )

        fun read(s: ByteSource): Map<String, String> {
            val h = s.bytes(0, 10) ?: return emptyMap()
            val version = h[3].toInt()
            if (version != 3 && version != 4) return emptyMap()
            val end = 10 + syncsafe(h, 6)
            val out = HashMap<String, String>()
            var pos = 10L
            var count = 0
            while (pos + 10 <= end && count++ < MAX_BOXES) {
                val f = s.bytes(pos, 10) ?: break
                if (f[0].toInt() == 0) break
                val id = String(f, 0, 4, Charsets.ISO_8859_1)
                val size = if (version == 4) syncsafe(f, 4) else f.u32be(4)
                if (size <= 0 || pos + 10 + size > end) break
                if (size <= TEXT_MAX) {
                    val body = s.bytes(pos + 10, size.toInt())
                    if (body != null) frame(id, body, out)
                }
                pos += 10 + size
            }
            return out
        }

        private fun frame(id: String, body: ByteArray, out: MutableMap<String, String>) {
            if (body.isEmpty()) return
            val encoding = body[0].toInt()
            TEXT[id]?.let { key -> out.putIfAbsent(key, decode(body, 1, body.size, encoding).substringBefore('\u0000')); return }
            when (id) {
                "COMM" -> if (body.size > 4) {
                    val (_, rest) = terminated(body, 4, encoding)
                    out.putIfAbsent("comment", decode(body, rest, body.size, encoding).trimEnd('\u0000'))
                }
                "TXXX" -> {
                    val (name, rest) = terminated(body, 1, encoding)
                    val key = decode(body, 1, name, encoding).lowercase()
                    if (key in setOf("description", "synopsis", "purl", "comment")) {
                        out.putIfAbsent(key, decode(body, rest, body.size, encoding).trimEnd('\u0000'))
                    }
                }
            }
        }

        /** Where the text starting at [from] ends, and where what follows it starts. */
        private fun terminated(b: ByteArray, from: Int, encoding: Int): Pair<Int, Int> {
            val wide = encoding == 1 || encoding == 2
            var i = from
            while (i < b.size) {
                if (!wide && b[i].toInt() == 0) return i to i + 1
                if (wide && i + 1 < b.size && b[i].toInt() == 0 && b[i + 1].toInt() == 0) return i to i + 2
                i += if (wide) 2 else 1
            }
            return b.size to b.size
        }

        private fun decode(b: ByteArray, from: Int, to: Int, encoding: Int): String {
            if (to <= from) return ""
            val charset = when (encoding) {
                1 -> Charsets.UTF_16
                2 -> Charsets.UTF_16BE
                3 -> Charsets.UTF_8
                else -> Charsets.ISO_8859_1
            }
            return String(b, from, to - from, charset)
        }

        private fun syncsafe(b: ByteArray, at: Int): Long =
            (0 until 4).fold(0L) { acc, i -> (acc shl 7) or (b[at + i].toLong() and 0x7F) }
    }

    // ── helpers ──

    private fun ByteSource.bytes(pos: Long, n: Int): ByteArray? {
        if (n < 0 || pos < 0 || pos + n > size) return null
        val out = ByteArray(n)
        return if (read(pos, out, 0, n) == n) out else null
    }

    private fun ByteArray.ascii(at: Int, n: Int) = if (size >= at + n) String(this, at, n, Charsets.ISO_8859_1) else ""
    private fun ByteArray.u32be(at: Int): Long = (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (this[at + i].toLong() and 0xFF) }
    private fun ByteArray.u64be(at: Int): Long = (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (this[at + i].toLong() and 0xFF) }
    private fun ByteArray.u32le(at: Int): Long = (3 downTo 0).fold(0L) { acc, i -> (acc shl 8) or (this[at + i].toLong() and 0xFF) }
}
