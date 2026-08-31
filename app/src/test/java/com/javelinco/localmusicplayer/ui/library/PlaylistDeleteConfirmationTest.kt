package com.javelinco.localmusicplayer.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.javelinco.localmusicplayer.core.model.PlaylistId
import com.javelinco.localmusicplayer.playlists.PlaylistSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistDeleteConfirmationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playlistIsDeletedOnlyAfterExplicitConfirmation() {
        val deletedPlaylistIds = mutableListOf<String>()
        compose.setContent {
            PlaylistScreen(
                playlists = listOf(PlaylistSummary(PlaylistId("mix"), "Road Mix", 0)),
                entries = emptyList(),
                tracks = emptyList(),
                onPlay = {},
                onCreate = {},
                onRename = { _, _ -> },
                onDelete = deletedPlaylistIds::add,
                onAdd = { _, _ -> },
                onRemove = { _, _ -> },
                onMove = { _, _, _ -> },
            )
        }

        compose.onNodeWithContentDescription("Delete Road Mix").performClick()

        compose.runOnIdle { assertEquals(emptyList<String>(), deletedPlaylistIds) }
        compose.onNodeWithText("Delete playlist?").assertIsDisplayed()
        compose.onNodeWithText("Delete \"Road Mix\"?", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(emptyList<String>(), deletedPlaylistIds) }

        compose.onNodeWithContentDescription("Delete Road Mix").performClick()
        compose.onNodeWithTag("confirm-playlist-delete").performClick()
        compose.runOnIdle { assertEquals(listOf("mix"), deletedPlaylistIds) }
        compose.onNodeWithText("New playlist").assertIsDisplayed()
    }
}
