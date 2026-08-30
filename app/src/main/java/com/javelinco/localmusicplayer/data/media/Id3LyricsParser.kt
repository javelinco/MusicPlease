package com.javelinco.localmusicplayer.data.media

import java.nio.charset.Charset

object Id3LyricsParser {
    private const val HEADER_SIZE = 10
    private const val MAX_TAG_BYTES = 4 * 1024 * 1024

    fun parse(bytes: ByteArray): EmbeddedLyrics = runCatching { parseTag(bytes) }
        .getOrDefault(EmbeddedLyrics())

    private fun parseTag(bytes: ByteArray): EmbeddedLyrics {
        if (bytes.size < HEADER_SIZE || bytes.copyOfRange(0, 3).toString(Charsets.ISO_8859_1) != "ID3") {
            return EmbeddedLyrics()
        }
        val version = bytes[3].toInt() and 0xff
        if (version !in 3..4) return EmbeddedLyrics()
        val declaredSize = synchsafe(bytes, 6) ?: return EmbeddedLyrics()
        if (declaredSize > MAX_TAG_BYTES || declaredSize > bytes.size - HEADER_SIZE) return EmbeddedLyrics()

        val flags = bytes[5].toInt() and 0xff
        var body = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + declaredSize)
        if (flags and 0x80 != 0) body = removeUnsynchronization(body)
        var cursor = extendedHeaderSize(body, version, flags) ?: return EmbeddedLyrics()
        var synchronizedLyrics: LyricsDocument? = null
        var plainLyrics: LyricsDocument? = null

        while (cursor + HEADER_SIZE <= body.size) {
            val frameId = body.copyOfRange(cursor, cursor + 4).toString(Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break
            if (!frameId.all { it in 'A'..'Z' || it in '0'..'9' }) break
            val frameSize = if (version == 4) synchsafe(body, cursor + 4) else int32(body, cursor + 4)
            if (frameSize == null || frameSize < 0) break
            val frameStart = cursor + HEADER_SIZE
            val frameEnd = frameStart + frameSize
            if (frameEnd > body.size) break
            val formatFlags = body[cursor + 9].toInt() and 0xff
            val unsupported = if (version == 3) {
                formatFlags and 0xc0 != 0
            } else {
                formatFlags and 0x0c != 0
            }
            if (!unsupported) {
                var payload = body.copyOfRange(frameStart, frameEnd)
                if (version == 4 && formatFlags and 0x02 != 0) {
                    payload = removeUnsynchronization(payload)
                }
                when (frameId) {
                    "USLT" -> if (plainLyrics == null) plainLyrics = parseUslt(payload)
                    "SYLT" -> if (synchronizedLyrics == null) synchronizedLyrics = parseSylt(payload)
                }
            }
            cursor = frameEnd
        }
        return EmbeddedLyrics(synchronizedLyrics, plainLyrics)
    }

    private fun extendedHeaderSize(body: ByteArray, version: Int, flags: Int): Int? {
        if (flags and 0x40 == 0) return 0
        if (body.size < 4) return null
        val declared = if (version == 4) synchsafe(body, 0) else int32(body, 0)
        if (declared == null || declared < 0) return null
        val skipped = if (version == 3) declared + 4 else declared
        return skipped.takeIf { it in 4..body.size }
    }

    private fun parseUslt(payload: ByteArray): LyricsDocument? {
        if (payload.size < 5) return null
        val encoding = payload[0].toInt() and 0xff
        val textStart = skipTerminated(payload, 4, encoding) ?: return null
        val text = decodeText(payload, textStart, payload.size, encoding).trim().trim('\u0000')
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty)
            .map { LyricLine(null, it) }.toList()
        return lines.takeIf(List<*>::isNotEmpty)?.let { LyricsDocument(it, synchronized = false) }
    }

    private fun parseSylt(payload: ByteArray): LyricsDocument? {
        if (payload.size < 7) return null
        val encoding = payload[0].toInt() and 0xff
        if ((payload[4].toInt() and 0xff) != 2) return null
        var cursor = skipTerminated(payload, 6, encoding) ?: return null
        val lines = mutableListOf<LyricLine>()
        while (cursor < payload.size) {
            val terminator = findTerminator(payload, cursor, encoding) ?: break
            val words = decodeText(payload, cursor, terminator, encoding).trim().trim('\u0000')
            cursor = terminator + terminatorWidth(encoding)
            if (cursor + 4 > payload.size) break
            val timestamp = int32(payload, cursor)?.toLong()?.and(0xffff_ffffL) ?: break
            cursor += 4
            if (words.isNotEmpty()) lines += LyricLine(timestamp, words)
        }
        return lines.takeIf(List<*>::isNotEmpty)?.let {
            LyricsDocument(it.sortedBy(LyricLine::timeMs), synchronized = true)
        }
    }

    private fun skipTerminated(bytes: ByteArray, start: Int, encoding: Int): Int? {
        val terminator = findTerminator(bytes, start, encoding) ?: return null
        return terminator + terminatorWidth(encoding)
    }

    private fun findTerminator(bytes: ByteArray, start: Int, encoding: Int): Int? {
        val width = terminatorWidth(encoding)
        if (start !in 0..bytes.size) return null
        if (width == 1) {
            for (index in start until bytes.size) if (bytes[index] == 0.toByte()) return index
        } else {
            var index = start
            while (index + 1 < bytes.size) {
                if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) return index
                index += 2
            }
        }
        return null
    }

    private fun terminatorWidth(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun decodeText(bytes: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start !in 0..end || end > bytes.size) return ""
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return ""
        }
        return bytes.copyOfRange(start, end).toString(charset)
    }

    private fun int32(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        return (bytes[offset].toInt() and 0xff shl 24) or
            (bytes[offset + 1].toInt() and 0xff shl 16) or
            (bytes[offset + 2].toInt() and 0xff shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        val values = IntArray(4) { bytes[offset + it].toInt() and 0xff }
        if (values.any { it and 0x80 != 0 }) return null
        return (values[0] shl 21) or (values[1] shl 14) or (values[2] shl 7) or values[3]
    }

    private fun removeUnsynchronization(bytes: ByteArray): ByteArray {
        val output = ByteArray(bytes.size)
        var read = 0
        var written = 0
        while (read < bytes.size) {
            output[written++] = bytes[read]
            if (
                bytes[read] == 0xff.toByte() &&
                read + 1 < bytes.size &&
                bytes[read + 1] == 0.toByte()
            ) {
                read += 1
            }
            read += 1
        }
        return output.copyOf(written)
    }
}
