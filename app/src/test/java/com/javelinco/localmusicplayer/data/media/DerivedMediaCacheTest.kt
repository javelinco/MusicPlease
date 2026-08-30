package com.javelinco.localmusicplayer.data.media

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files

class DerivedMediaCacheTest {
    @Test
    fun recordsAreAtomicNegativeHitsAndArtworkIsDeduplicated() {
        val root = Files.createTempDirectory("derived-cache").toFile()
        val cache = DerivedMediaCache(root, Json)
        val record = CachedTrackMedia("fingerprint", "companions", "album-key", null)

        cache.writeArtwork("album-key", ART)
        cache.writeArtwork("album-key", ART)
        cache.writeRecord("one", record)
        cache.writeRecord("two", record)
        cache.writeRecord("negative", CachedTrackMedia("fingerprint", "", null, null))

        assertArrayEquals(ART, cache.artworkPath("album-key")!!.readBytes())
        assertEquals(1, cache.artworkFiles().size)
        assertNotNull(cache.read("negative"))
        assertEquals(record, cache.read("one"))
    }

    companion object { private val ART = byteArrayOf(1, 2, 3) }
}
