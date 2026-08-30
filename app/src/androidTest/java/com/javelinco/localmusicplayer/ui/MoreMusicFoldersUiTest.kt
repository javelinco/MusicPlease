package com.javelinco.localmusicplayer.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.javelinco.localmusicplayer.core.model.SourceId
import com.javelinco.localmusicplayer.data.db.RecentPlaylistRow
import com.javelinco.localmusicplayer.data.settings.SettingsState
import com.javelinco.localmusicplayer.data.source.SafTreeSource
import com.javelinco.localmusicplayer.playback.service.PlaybackUiState
import com.javelinco.localmusicplayer.ui.library.LibraryActions
import com.javelinco.localmusicplayer.ui.library.LibraryScreenState
import com.javelinco.localmusicplayer.ui.navigation.AppNavigation
import org.junit.Rule
import org.junit.Test

class MoreMusicFoldersUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun establishedLibraryMovesFolderAndScanToolsToWrittenMoreOption() {
        compose.setContent {
            AppNavigation(
                libraryState = LibraryScreenState(
                    sources = listOf(
                        SafTreeSource(SourceId("music"), "content://tree/music", "Music"),
                    ),
                ),
                libraryActions = LibraryActions(),
                recentTracks = emptyList(),
                recentPlaylists = listOf(RecentPlaylistRow("mix", "Recent mix", 1)),
                recentLoaded = true,
                dedicated = false,
                settings = SettingsState(),
                playback = PlaybackUiState(controllerReady = true, connected = true),
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
        compose.waitForIdle()

        compose.onNodeWithText("Library").performClick()
        compose.onAllNodesWithContentDescription("Library tools").assertCountEquals(0)

        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Music folders and scanning").assertIsDisplayed()
        compose.onNodeWithText("Manage").assertIsDisplayed().performClick()
        compose.onNodeWithText("Music sources").assertIsDisplayed()
        compose.onNodeWithText("Add another folder").assertIsDisplayed()
    }
}
