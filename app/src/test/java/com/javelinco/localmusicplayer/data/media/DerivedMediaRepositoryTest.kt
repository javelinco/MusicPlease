package com.javelinco.localmusicplayer.data.media

import com.javelinco.localmusicplayer.core.model.SourceId
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.source.SafTreeSource
import com.javelinco.localmusicplayer.data.source.SourceEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class DerivedMediaRepositoryTest {
    @Test
    fun unchangedTrackUsesCacheConcurrentRequestsDeduplicateAndChangesResolve() = runTest {
        val resolver = CountingResolver()
        val source = SafTreeSource(SourceId("source"), "content://tree", "Music")
        val repository = DerivedMediaRepository(
            cache = DerivedMediaCache(Files.createTempDirectory("media-repo").toFile(), Json),
            resolver = resolver,
            sourceProvider = { source },
        )

        val first = track(modified = 1)
        val a = async { repository.ensure(first) }
        val b = async { repository.ensure(first) }
        a.await(); b.await()
        repository.ensure(first)
        repository.ensure(track(modified = 2))

        assertEquals(2, resolver.calls)
        assertEquals("words", repository.states.value["track"]!!.lyrics!!.lines.single().text)
    }

    private class CountingResolver : TrackMediaResolver {
        var calls = 0
        override suspend fun resolve(source: com.javelinco.localmusicplayer.data.source.MusicSource, entry: SourceEntry, track: TrackEntity): ResolvedTrackMedia {
            calls++
            return ResolvedTrackMedia(null, LyricsDocument(listOf(LyricLine(null, "words")), false), "")
        }
    }

    private fun track(modified: Long) = TrackEntity("track", "source", "content://song", "Song.mp3", "Song", "Artist", "Album", null, null, "song", "artist", "album", "artist", "", null, null, 1_000, modified, 10, true, "album")
}
