package com.javelinco.localmusicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import com.javelinco.localmusicplayer.core.model.PlaylistId
import com.javelinco.localmusicplayer.core.model.SourceId
import com.javelinco.localmusicplayer.data.db.NamedGroupSummary
import com.javelinco.localmusicplayer.data.db.RecentPlaylistRow
import com.javelinco.localmusicplayer.data.settings.SettingsState
import com.javelinco.localmusicplayer.data.source.MediaStoreSource
import com.javelinco.localmusicplayer.playback.service.PlaybackUiState
import com.javelinco.localmusicplayer.playlists.PlaylistSummary
import com.javelinco.localmusicplayer.ui.library.LibraryActions
import com.javelinco.localmusicplayer.ui.library.LibraryScreen
import com.javelinco.localmusicplayer.ui.library.LibraryScreenState
import com.javelinco.localmusicplayer.ui.library.LibraryView
import com.javelinco.localmusicplayer.ui.library.PlaylistScreen
import com.javelinco.localmusicplayer.ui.navigation.AppNavigation
import org.junit.Rule
import org.junit.Test

class SystemBackUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun systemBackUnwindsAppearanceThenMoreThenStaysHome() {
        compose.setContent { AppNavigationFixture() }

        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Reduced motion").assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Backup and restore").assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Recently played").assertIsDisplayed()

        pressBack()
        compose.onNodeWithText("Recently played").assertIsDisplayed()
    }

    @Test
    fun systemBackClosesArtistDetailBeforeLeavingLibrary() {
        compose.setContent {
            LibraryScreen(
                state = LibraryScreenState(
                    selectedView = LibraryView.ARTISTS,
                    artists = listOf(NamedGroupSummary("artist", "Artist One", 0)),
                    sources = listOf(MediaStoreSource(SourceId("device"), "On this device")),
                ),
                actions = LibraryActions(),
            )
        }

        compose.onNodeWithText("Artist One").performClick()
        compose.onNodeWithContentDescription("Back to Artists").assertIsDisplayed()

        pressBack()
        compose.onAllNodesWithContentDescription("Back to Artists").assertCountEquals(0)
        compose.onNodeWithContentDescription("Play all by Artist One").assertIsDisplayed()
    }

    @Test
    fun systemBackClosesLibrarySearch() {
        compose.setContent {
            var searchOpen by remember { mutableStateOf(true) }
            LibraryScreen(
                state = LibraryScreenState(
                    searchOpen = searchOpen,
                    sources = listOf(MediaStoreSource(SourceId("device"), "On this device")),
                ),
                actions = LibraryActions(onCloseSearch = { searchOpen = false }),
            )
        }

        compose.onNodeWithText("Search tracks").assertIsDisplayed()
        pressBack()
        compose.onAllNodesWithText("Search tracks").assertCountEquals(0)
        compose.onNodeWithContentDescription("Search Tracks").assertIsDisplayed()
    }

    @Test
    fun systemBackCollapsesExpandedPlaylistCard() {
        compose.setContent {
            PlaylistScreen(
                playlists = listOf(PlaylistSummary(PlaylistId("mix"), "My Mix", 0)),
                entries = emptyList(),
                tracks = emptyList(),
                onPlay = {},
                onCreate = {},
                onRename = { _, _ -> },
                onDelete = {},
                onAdd = { _, _ -> },
                onRemove = { _, _ -> },
                onMove = { _, _, _ -> },
            )
        }

        compose.onNodeWithContentDescription("Show tracks in My Mix").performClick()
        compose.onNodeWithContentDescription("Hide tracks in My Mix").assertIsDisplayed()

        pressBack()
        compose.onAllNodesWithContentDescription("Hide tracks in My Mix").assertCountEquals(0)
        compose.onNodeWithContentDescription("Show tracks in My Mix").assertIsDisplayed()
        compose.onNodeWithText("New playlist").assertIsDisplayed()
    }
}

@Composable
private fun AppNavigationFixture() {
    AppNavigation(
        libraryState = LibraryScreenState(),
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
