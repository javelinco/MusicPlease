package com.javelinco.localmusicplayer.data.media

import kotlinx.serialization.Serializable

@Serializable
data class LyricLine(
    val timeMs: Long?,
    val text: String,
)

@Serializable
data class LyricsDocument(
    val lines: List<LyricLine>,
    val synchronized: Boolean,
)

data class EmbeddedLyrics(
    val synchronized: LyricsDocument? = null,
    val plain: LyricsDocument? = null,
)

fun LyricsDocument.activeLineIndex(positionMs: Long): Int? {
    if (!synchronized || lines.isEmpty()) return null
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
        val middle = (low + high) ushr 1
        val time = lines[middle].timeMs ?: return null
        if (time <= positionMs) {
            result = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return result.takeIf { it >= 0 }
}
