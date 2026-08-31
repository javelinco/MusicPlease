package com.javelinco.localmusicplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressTest {
    @Test
    fun playingPositionAdvancesWithElapsedTime() {
        assertEquals(1_250L, projectPlaybackPosition(1_000L, 250L, true, 10_000L))
    }

    @Test
    fun pausedPositionDoesNotAdvance() {
        assertEquals(1_000L, projectPlaybackPosition(1_000L, 250L, false, 10_000L))
    }

    @Test
    fun projectedPositionIsClampedToTheTrackRange() {
        assertEquals(250L, projectPlaybackPosition(-500L, 250L, true, 10_000L))
        assertEquals(10_000L, projectPlaybackPosition(9_900L, 250L, true, 10_000L))
        assertEquals(0L, projectPlaybackPosition(1_000L, 250L, true, 0L))
    }
}
