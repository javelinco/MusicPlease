package com.javelinco.localmusicplayer.ui.player

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackProgressUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playingProgressMovesBetweenAuthoritativeUpdates() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlaybackProgress(
                mediaId = "track",
                positionMs = 1_000L,
                durationMs = 10_000L,
                isPlaying = true,
                onSeek = {},
            )
        }
        compose.mainClock.advanceTimeByFrame()
        val startingPosition = currentProgress()

        compose.mainClock.advanceTimeBy(250L)

        assertTrue(currentProgress() > startingPosition)
    }

    private fun currentProgress(): Float = compose
        .onNodeWithTag("playback-progress")
        .fetchSemanticsNode()
        .config[SemanticsProperties.ProgressBarRangeInfo]
        .current
}
