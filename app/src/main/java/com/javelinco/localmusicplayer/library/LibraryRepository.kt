package com.javelinco.localmusicplayer.library

import com.javelinco.localmusicplayer.data.db.LibraryDao
import com.javelinco.localmusicplayer.data.db.TrackEntity

enum class SearchFilter(val ftsColumn: String?) {
    ALL(null),
    TITLE("title"),
    ARTIST("artist"),
    ALBUM("album"),
    GENRE("genre"),
    FILE_NAME("fileName"),
}

class LibraryRepository(
    private val libraryDao: LibraryDao,
) {
    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.ALL,
        limit: Int = 200,
    ): List<TrackEntity> {
        val expression = query.toFtsPrefixExpression(filter.ftsColumn) ?: return emptyList()
        return libraryDao.searchTracks(expression, limit.coerceIn(1, 1_000))
    }
}

private fun String.toFtsPrefixExpression(column: String?): String? {
    val terms = trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (terms.isEmpty()) return null
    // Whitespace is implicit AND in both FTS4 query syntaxes. A literal AND is
    // treated as a searchable term when SQLite uses the standard parser.
    return terms.joinToString(" ") { term ->
        val escaped = term.replace("\"", "\"\"")
        val prefix = "\"$escaped\"*"
        if (column == null) prefix else "$column:$prefix"
    }
}
