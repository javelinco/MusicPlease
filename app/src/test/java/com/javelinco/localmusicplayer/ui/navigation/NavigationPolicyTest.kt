package com.javelinco.localmusicplayer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationPolicyTest {
    @Test
    fun startupWaitsForHistoryThenChoosesTheRelevantDestination() {
        assertNull(
            chooseInitialPrimaryDestination(
                recentLoaded = true,
                playbackReady = false,
                hasRecent = false,
                hasSession = false,
            ),
        )
        assertNull(
            chooseInitialPrimaryDestination(
                recentLoaded = false,
                playbackReady = true,
                hasRecent = false,
                hasSession = false,
            ),
        )
        assertEquals(
            PrimaryDestination.LIBRARY,
            chooseInitialPrimaryDestination(
                recentLoaded = true,
                playbackReady = true,
                hasRecent = false,
                hasSession = false,
            ),
        )
        assertEquals(
            PrimaryDestination.HOME,
            chooseInitialPrimaryDestination(
                recentLoaded = true,
                playbackReady = true,
                hasRecent = true,
                hasSession = false,
            ),
        )
        assertEquals(
            PrimaryDestination.HOME,
            chooseInitialPrimaryDestination(
                recentLoaded = true,
                playbackReady = true,
                hasRecent = false,
                hasSession = true,
            ),
        )
    }
}
