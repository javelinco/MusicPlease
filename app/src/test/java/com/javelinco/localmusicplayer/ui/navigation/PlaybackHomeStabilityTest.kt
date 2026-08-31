package com.javelinco.localmusicplayer.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.javelinco.localmusicplayer.data.db.RecentPlaylistRow
import com.javelinco.localmusicplayer.data.settings.SettingsState
import com.javelinco.localmusicplayer.playback.service.PlaybackUiState
import com.javelinco.localmusicplayer.ui.library.LibraryActions
import com.javelinco.localmusicplayer.ui.library.LibraryScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackHomeStabilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pausingAnActiveSessionKeepsNowPlayingMounted() {
        lateinit var setPlaying: (Boolean) -> Unit
        compose.setContent {
            var isPlaying by remember { mutableStateOf(true) }
            setPlaying = { isPlaying = it }
            AppNavigation(
                libraryState = LibraryScreenState(),
                libraryActions = LibraryActions(),
                recentTracks = emptyList(),
                recentPlaylists = listOf(RecentPlaylistRow("mix", "Recent mix", 1)),
                recentLoaded = true,
                dedicated = false,
                settings = SettingsState(),
                playback = PlaybackUiState(
                    controllerReady = true,
                    connected = true,
                    hasSession = true,
                    currentMediaId = "paused-track",
                    title = "Paused track",
                    artist = "Test artist",
                    isPlaying = isPlaying,
                    positionMs = 42_000,
                    durationMs = 180_000,
                ),
                backupNames = emptyList(),
                status = null,
                onLeaveDedicated = {},
                onPrevious = {},
                onPlayPause = {},
                onNext = {},
                onSeek = {},
                onShuffle = {},
                onRepeat = {},
                onChooseBackupFolder = {},
                onManualBackup = {},
                onRefreshBackups = {},
                onRestore = {},
                onTheme = {},
                onReducedMotion = {},
            )
        }

        compose.onNodeWithText("Now playing").assertIsDisplayed()
        compose.runOnIdle { setPlaying(false) }

        compose.onNodeWithText("Now playing").assertIsDisplayed()
        compose.onNodeWithText("0:42").assertIsDisplayed()
        compose.onNodeWithText("Recently played").assertDoesNotExist()
    }
}
