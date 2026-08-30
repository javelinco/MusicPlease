package com.javelinco.localmusicplayer.data.media

object LrcParser {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val offsetPattern = Regex("^\\[offset:([+-]?\\d+)]$", RegexOption.IGNORE_CASE)
    private val metadataPattern = Regex(
        "^\\[(ar|al|ti|au|by|re|ve|length):.*]$",
        RegexOption.IGNORE_CASE,
    )
    private val timestampPattern = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(bytes: ByteArray): LyricsDocument? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        var offsetMs = 0L
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            offsetPattern.matchEntire(line)?.let { match ->
                offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                return@forEach
            }
            if (metadataPattern.matches(line)) return@forEach

            var cursor = 0
            val timestamps = mutableListOf<Long>()
            while (cursor < line.length) {
                val match = timestampPattern.find(line, cursor) ?: break
                if (match.range.first != cursor) break
                parseTimestamp(match)?.let(timestamps::add)
                cursor = match.range.last + 1
            }
            val words = line.substring(cursor).trim()
            if (timestamps.isNotEmpty()) {
                if (words.isNotEmpty()) timestamps.forEach { timed += LyricLine(it, words) }
            } else {
                plain += LyricLine(null, line)
            }
        }

        if (timed.isNotEmpty()) {
            val adjusted = timed.map { line ->
                line.copy(timeMs = ((line.timeMs ?: 0L) + offsetMs).coerceAtLeast(0L))
            }.sortedBy { it.timeMs }
            return LyricsDocument(adjusted, synchronized = true)
        }
        return plain.takeIf(List<*>::isNotEmpty)?.let {
            LyricsDocument(it, synchronized = false)
        }
    }

    private fun parseTimestamp(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull()?.takeIf { it in 0..59 } ?: return null
        val fraction = match.groupValues[3]
        val milliseconds = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return minutes * 60_000 + seconds * 1_000 + milliseconds
    }
}
