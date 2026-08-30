package com.javelinco.localmusicplayer.playback.service

import com.javelinco.localmusicplayer.data.db.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaItemMapperTest {
    @Test
    fun mapsOnlyLocalContentUris() {
        val item = MediaItemMapper.toMediaItem(track("content://media/external/audio/media/1"))

        assertEquals("track", item.mediaId)
        assertEquals("content", item.localConfiguration?.uri?.scheme)
    }

    @Test
    fun rejectsNetworkUrisEvenWithoutInternetPermission() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaItemMapper.toMediaItem(track("https://example.com/song.mp3"))
        }
    }

    @Test
    fun mapsOnlyLocalArtworkUris() {
        val item = MediaItemMapper.toMediaItem(track("content://media/song"), "file:///data/user/0/app/cache/art.webp")
        assertEquals("file", item.mediaMetadata.artworkUri?.scheme)
        assertThrows(IllegalArgumentException::class.java) {
            MediaItemMapper.toMediaItem(track("content://media/song"), "https://example.com/art.jpg")
        }
    }

    @Test
    fun replayGainParsingUsesUnityForMissingOrInvalidTags() {
        assertEquals(1f, ReplayGain.parseLinearGain(null))
        assertEquals(1f, ReplayGain.parseLinearGain("loud"))
        assertTrue(ReplayGain.parseLinearGain("-6.0 dB") in 0.50f..0.51f)
    }

    private fun track(uri: String) = TrackEntity(
        "track", "source", uri, "Song.mp3", "Song", "Artist", "Album", "Artist", null,
        "song", "artist", "album", "artist", "", 1, 1, 1, 1, 1, true,
    )
}
