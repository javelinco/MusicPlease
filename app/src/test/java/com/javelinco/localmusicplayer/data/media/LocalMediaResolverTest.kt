package com.javelinco.localmusicplayer.data.media

import com.javelinco.localmusicplayer.core.model.SourceId
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.source.MediaStoreSource
import com.javelinco.localmusicplayer.data.source.SafTreeSource
import com.javelinco.localmusicplayer.data.source.SourceEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalMediaResolverTest {
    @Test
    fun selectedFolderUsesSidecarThenSyltThenUsltAndEmbeddedArtFirst() = runTest {
        val reader = FakeCompanions(
            listOf(
                CompanionFile("SONG.LRC", "lrc", 20, 2),
                CompanionFile("cover.jpg", "cover", 30, 3),
            ),
            mapOf("lrc" to "[00:01.00]Sidecar".encodeToByteArray(), "cover" to FOLDER_ART),
        )
        val resolver = LocalMediaResolver(reader, FakeEmbedded(EMBEDDED_ART))

        val result = resolver.resolve(SOURCE, entry(parent = "album"), track())

        assertEquals("Sidecar", result.lyrics!!.lines.single().text)
        assertArrayEquals(EMBEDDED_ART, result.artwork)
        assertEquals(1, reader.listCalls)
    }

    @Test
    fun fallsBackToEmbeddedTimedThenPlainAndFolderArtwork() = runTest {
        val embedded = EmbeddedLyrics(
            synchronized = LyricsDocument(listOf(LyricLine(1_000, "Timed")), true),
            plain = LyricsDocument(listOf(LyricLine(null, "Plain")), false),
        )
        val reader = FakeCompanions(
            listOf(CompanionFile("folder.jpg", "folder", 30, 4)),
            mapOf("folder" to FOLDER_ART),
        )
        val resolver = LocalMediaResolver(reader, FakeEmbedded(null, embedded))

        val result = resolver.resolve(SOURCE, entry(parent = "album"), track())

        assertEquals("Timed", result.lyrics!!.lines.single().text)
        assertArrayEquals(FOLDER_ART, result.artwork)
    }

    @Test
    fun mediaStoreNeverRequestsAdjacentFiles() = runTest {
        val reader = FakeCompanions(emptyList(), emptyMap())
        val resolver = LocalMediaResolver(reader, FakeEmbedded(null))

        resolver.resolve(MediaStoreSource(SourceId("media"), "Device"), entry(parent = null), track())

        assertEquals(0, reader.listCalls)
    }

    private class FakeCompanions(
        private val files: List<CompanionFile>,
        private val bytes: Map<String, ByteArray>,
    ) : CompanionFileReader {
        var listCalls = 0
        override suspend fun list(source: SafTreeSource, parentDocumentId: String): List<CompanionFile> {
            listCalls++
            return files
        }
        override suspend fun read(file: CompanionFile, maxBytes: Int): ByteArray? =
            bytes[file.uri]?.takeIf { it.size <= maxBytes }
    }

    private class FakeEmbedded(
        private val art: ByteArray?,
        private val lyrics: EmbeddedLyrics = EmbeddedLyrics(),
    ) : EmbeddedMediaReader {
        override suspend fun read(entry: SourceEntry): EmbeddedTrackMedia = EmbeddedTrackMedia(art, lyrics)
    }

    private fun entry(parent: String?) = SourceEntry(SourceId("source"), "stable", "content://song", "Song.mp3", "audio/mpeg", 10, 1, parent)
    private fun track() = TrackEntity("track", "source", "content://song", "Song.mp3", "Song", "Artist", "Album", null, null, "song", "artist", "album", "artist", "", null, null, 1_000, 1, 10, true, "album")

    companion object {
        private val SOURCE = SafTreeSource(SourceId("source"), "content://tree", "Music")
        private val EMBEDDED_ART = byteArrayOf(1, 2, 3)
        private val FOLDER_ART = byteArrayOf(4, 5, 6)
    }
}
