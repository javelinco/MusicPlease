package com.javelinco.localmusicplayer.ui.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.javelinco.localmusicplayer.core.model.PlaylistId
import com.javelinco.localmusicplayer.data.db.PlaylistEntryEntity
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.playlists.PlaylistSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistCardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playlistCardsStayVisibleWhileOneOrderedTrackListExpandsAndCollapses() {
        compose.setContent {
            PlaylistScreen(
                playlists = playlists(),
                entries = listOf(
                    entry("second-entry", "mix", 1, "second", "Second track"),
                    entry("quiet-entry", "quiet", 0, "quiet", "Quiet song"),
                    entry("first-entry", "mix", 0, "first", "First track"),
                ),
                tracks = listOf(
                    track("first", "First track"),
                    track("second", "Second track"),
                    track("quiet", "Quiet song"),
                ),
                onPlay = {},
                onCreate = {},
                onRename = { _, _ -> },
                onDelete = {},
                onAdd = { _, _ -> },
                onRemove = { _, _ -> },
                onMove = { _, _, _ -> },
            )
        }

        compose.onNodeWithText("New playlist").assertIsDisplayed()
        compose.onNodeWithText("Road Mix").assertIsDisplayed()
        compose.onNodeWithText("Quiet Mix").assertIsDisplayed()
        compose.onNodeWithText("Playlist name").assertDoesNotExist()
        compose.onNodeWithText("Back to playlists").assertDoesNotExist()
        compose.onNodeWithText("Playlist order").assertDoesNotExist()
        compose.onNodeWithText("Add a track").assertDoesNotExist()

        compose.onNodeWithContentDescription("Show tracks in Road Mix").performClick()
        compose.onNodeWithText("First track").assertIsDisplayed()
        compose.onNodeWithText("Second track").assertIsDisplayed()
        compose.onNodeWithText("Quiet song").assertDoesNotExist()
        compose.onNodeWithText("Road Mix").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToIndex(2)
        compose.onNodeWithText("Quiet Mix").assertIsDisplayed()

        compose.onNode(hasScrollAction()).performScrollToIndex(1)
        compose.onNodeWithContentDescription("Hide tracks in Road Mix").performClick()
        compose.onNodeWithText("First track").assertDoesNotExist()
        compose.onNodeWithText("Second track").assertDoesNotExist()
    }

    @Test
    fun createAndRenameUseDialogsAndSubmitTrimmedNames() {
        val createdNames = mutableListOf<String>()
        val renames = mutableListOf<Pair<String, String>>()
        compose.setContent {
            PlaylistScreen(
                playlists = playlists(),
                entries = emptyList(),
                tracks = emptyList(),
                onPlay = {},
                onCreate = createdNames::add,
                onRename = { id, name -> renames += id to name },
                onDelete = {},
                onAdd = { _, _ -> },
                onRemove = { _, _ -> },
                onMove = { _, _, _ -> },
            )
        }

        compose.onNodeWithText("New playlist").performClick()
        compose.onNodeWithText("Create a playlist").assertIsDisplayed()
        compose.onNodeWithText("Playlist name").performTextInput("  Driving  ")
        compose.onNodeWithText("Create").performClick()
        compose.runOnIdle { assertEquals(listOf("Driving"), createdNames) }
        compose.onNodeWithText("Playlist name").assertDoesNotExist()

        compose.onNodeWithContentDescription("Rename Road Mix").performClick()
        compose.onNodeWithText("Rename playlist").assertIsDisplayed()
        compose.onNodeWithText("Playlist name").assertTextContains("Road Mix")
        compose.onNodeWithText("Playlist name").performTextClearance()
        compose.onNodeWithText("Playlist name").performTextInput("  Road Songs  ")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf("mix" to "Road Songs"), renames) }
    }

    @Test
    fun compactActionsTargetTheCorrectPlaylistAndEntry() {
        val played = mutableListOf<String>()
        val moves = mutableListOf<Triple<String, Int, Int>>()
        val removals = mutableListOf<Pair<String, String>>()
        compose.setContent {
            PlaylistScreen(
                playlists = playlists(),
                entries = listOf(
                    entry("first-entry", "mix", 0, "first", "First track"),
                    entry("second-entry", "mix", 1, "second", "Second track"),
                ),
                tracks = listOf(track("first", "First track"), track("second", "Second track")),
                onPlay = played::add,
                onCreate = {},
                onRename = { _, _ -> },
                onDelete = {},
                onAdd = { _, _ -> },
                onRemove = { playlistId, entryId -> removals += playlistId to entryId },
                onMove = { playlistId, from, to -> moves += Triple(playlistId, from, to) },
            )
        }

        compose.onNodeWithContentDescription("Play Road Mix").performClick()
        compose.onNodeWithContentDescription("Show tracks in Road Mix").performClick()
        compose.onNodeWithContentDescription("Move First track down").performClick()
        compose.onNodeWithContentDescription("Remove First track from Road Mix").performClick()

        compose.runOnIdle {
            assertEquals(listOf("mix"), played)
            assertEquals(listOf(Triple("mix", 0, 1)), moves)
            assertEquals(listOf("mix" to "first-entry"), removals)
        }
    }

    @Test
    fun anEmptyExpandedPlaylistExplainsThatItHasNoTracks() {
        compose.setContent {
            PlaylistScreen(
                playlists = listOf(PlaylistSummary(PlaylistId("empty"), "Empty Mix", 0)),
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

        compose.onAllNodesWithText("This playlist is empty.").assertCountEquals(0)
        compose.onNodeWithContentDescription("Show tracks in Empty Mix").performClick()
        compose.onNodeWithText("This playlist is empty.").assertIsDisplayed()
    }

    private fun playlists() = listOf(
        PlaylistSummary(PlaylistId("mix"), "Road Mix", 2),
        PlaylistSummary(PlaylistId("quiet"), "Quiet Mix", 1),
    )

    private fun entry(
        entryId: String,
        playlistId: String,
        position: Int,
        trackId: String,
        title: String,
    ) = PlaylistEntryEntity(entryId, playlistId, position, trackId, title, "content://music/$trackId", 1)

    private fun track(id: String, title: String) = TrackEntity(
        trackId = id,
        sourceId = "source",
        contentUri = "content://music/$id",
        fileName = "$title.mp3",
        title = title,
        artist = "Artist $id",
        albumTitle = "Album $id",
        albumArtist = "Artist $id",
        genre = "Genre",
        normalizedTitle = title.lowercase(),
        normalizedArtist = "artist $id",
        normalizedAlbumTitle = "album $id",
        normalizedAlbumArtist = "artist $id",
        normalizedGenre = "genre",
        discNumber = 1,
        trackNumber = 1,
        durationMs = 180_000,
        modifiedAtEpochMs = 1,
        sizeBytes = 1,
        available = true,
    )
}
