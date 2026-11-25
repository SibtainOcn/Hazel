package com.hazel.android.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32

/**
 * A post's caption kept inside the image file, as XMP `dc:description` in UTF-8: an APP1
 * segment in a JPEG, an iTXt chunk in a PNG. Exif's own description field is ASCII only,
 * so captions with emoji or other scripts would not survive it. Other formats are left alone.
 */
object CaptionMetadata {

    private const val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val PNG_KEYWORD = "XML:com.adobe.xmp"
    /** A JPEG segment holds at most 65533 bytes; the caption is cut to leave room for the packet. */
    private const val JPEG_CAPTION_MAX_BYTES = 60_000
    /** The caption sits near the start of the file; no more than this is read looking for it. */
    private const val READ_LIMIT = 2 * 1024 * 1024
    private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private val DESCRIPTION = Regex("""<dc:description>.*?<rdf:li[^>]*>(.*?)</rdf:li>""", RegexOption.DOT_MATCHES_ALL)

    fun canEmbed(file: File): Boolean = file.extension.lowercase() in setOf("jpg", "jpeg", "png")

    /** Writes [caption] into [file] in place. False when the format is not supported or the file is not what its name says. */
    fun embed(file: File, caption: String): Boolean {
        if (caption.isBlank() || !canEmbed(file)) return false
        val bytes = file.readBytes()
        val out = when {
            isJpeg(bytes) -> jpegWith(bytes, caption)
            isPng(bytes) -> pngWith(bytes, caption)
            else -> null
        } ?: return false
        val temp = File(file.parentFile, file.name + ".caption")
        return try {
            temp.writeBytes(out)
            temp.renameTo(file) || run { file.writeBytes(out); true }
        } finally {
            temp.delete()
        }
    }

    /** The caption kept in the image read from [input], or null when there is none. */
    fun read(input: InputStream): String? {
        val bytes = input.readUpTo(READ_LIMIT)
        val packet = when {
            isJpeg(bytes) -> jpegXmp(bytes)
            isPng(bytes) -> pngXmp(bytes)
            else -> null
        } ?: return null
        return DESCRIPTION.find(packet)?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.isNotBlank() }
    }

    internal fun packet(caption: String): String =
        "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">" +
            "<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
            "<dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">${escape(caption)}</rdf:li></rdf:Alt></dc:description>" +
            "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>"

    private fun isJpeg(b: ByteArray) = b.size > 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()
    private fun isPng(b: ByteArray) = b.size > 8 && b.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or
            ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    /** Leading APPn segments as (start, end) offsets; stops at the first other marker. */
    private fun jpegAppSegments(b: ByteArray): List<IntRange> {
        val found = mutableListOf<IntRange>()
        var i = 2
        while (i + 4 <= b.size && b[i] == 0xFF.toByte()) {
            val marker = b[i + 1].toInt() and 0xFF
            if (marker !in 0xE0..0xEF) break
            val end = i + 2 + u16(b, i + 2)
            if (end > b.size) break
            found += i until end
            i = end
        }
        return found
    }

    private fun isXmpSegment(b: ByteArray, range: IntRange): Boolean {
        val header = XMP_HEADER.toByteArray(Charsets.US_ASCII)
        val start = range.first + 4
        return (b[range.first + 1].toInt() and 0xFF) == 0xE1 && start + header.size <= range.last + 1 &&
            b.copyOfRange(start, start + header.size).contentEquals(header)
    }

    private fun jpegXmp(b: ByteArray): String? {
        val header = XMP_HEADER.length
        return jpegAppSegments(b).firstOrNull { isXmpSegment(b, it) }
            ?.let { String(b, it.first + 4 + header, it.last + 1 - (it.first + 4 + header), Charsets.UTF_8) }
    }

    private fun jpegWith(b: ByteArray, caption: String): ByteArray {
        val text = caption.toByteArray(Charsets.UTF_8).let { bytes ->
            if (bytes.size <= JPEG_CAPTION_MAX_BYTES) caption
            else String(bytes, 0, JPEG_CAPTION_MAX_BYTES, Charsets.UTF_8).trimEnd('�')
        }
        val payload = XMP_HEADER.toByteArray(Charsets.US_ASCII) + packet(text).toByteArray(Charsets.UTF_8)
        val segments = jpegAppSegments(b)
        val headEnd = segments.lastOrNull()?.let { it.last + 1 } ?: 2
        return ByteArrayOutputStream(b.size + payload.size + 4).apply {
            write(b, 0, 2)
            segments.filterNot { isXmpSegment(b, it) }.forEach { write(b, it.first, it.last + 1 - it.first) }
            write(0xFF); write(0xE1)
            val length = payload.size + 2
            write(length shr 8); write(length and 0xFF)
            write(payload)
            write(b, headEnd, b.size - headEnd)
        }.toByteArray()
    }

    private fun pngChunks(b: ByteArray): List<Pair<String, IntRange>> {
        val found = mutableListOf<Pair<String, IntRange>>()
        var i = 8
        while (i + 12 <= b.size) {
            val length = u32(b, i)
            if (length < 0 || i + 12 + length > b.size) break
            found += String(b, i + 4, 4, Charsets.US_ASCII) to (i until i + 12 + length)
            i += 12 + length
        }
        return found
    }

    private fun isXmpChunk(b: ByteArray, type: String, range: IntRange): Boolean {
        val keyword = PNG_KEYWORD.toByteArray(Charsets.US_ASCII)
        val start = range.first + 8
        return type == "iTXt" && start + keyword.size <= range.last + 1 &&
            b.copyOfRange(start, start + keyword.size).contentEquals(keyword)
    }

    private fun pngXmp(b: ByteArray): String? {
        val (_, range) = pngChunks(b).firstOrNull { (type, r) -> isXmpChunk(b, type, r) } ?: return null
        // Keyword, NUL, compression flag and method, then empty language and translated keyword.
        val textStart = range.first + 8 + PNG_KEYWORD.length + 5
        val end = range.last + 1 - 4
        return if (textStart <= end) String(b, textStart, end - textStart, Charsets.UTF_8) else null
    }

    private fun pngWith(b: ByteArray, caption: String): ByteArray? {
        val chunks = pngChunks(b)
        if (chunks.none { it.first == "IDAT" }) return null
        val data = PNG_KEYWORD.toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, 0, 0) + packet(caption).toByteArray(Charsets.UTF_8)
        val type = "iTXt".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(type); update(data) }.value
        return ByteArrayOutputStream(b.size + data.size + 12).apply {
            write(b, 0, 8)
            var placed = false
            chunks.forEach { (name, range) ->
                if (isXmpChunk(b, name, range)) return@forEach
                if (name == "IDAT" && !placed) {
                    writeInt(data.size); write(type); write(data); writeInt(crc.toInt())
                    placed = true
                }
                write(b, range.first, range.last + 1 - range.first)
            }
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeInt(v: Int) {
        write(v ushr 24 and 0xFF); write(v ushr 16 and 0xFF); write(v ushr 8 and 0xFF); write(v and 0xFF)
    }

    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun escape(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun unescape(text: String) =
        text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#xA;", "\n").replace("&amp;", "&")
}
