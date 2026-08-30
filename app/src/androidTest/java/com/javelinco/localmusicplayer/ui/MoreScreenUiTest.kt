package com.javelinco.localmusicplayer.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.javelinco.localmusicplayer.ui.navigation.MORE_OPTION_CARD_TAG
import com.javelinco.localmusicplayer.ui.navigation.MoreScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MoreScreenUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun moreShowsThreeDescriptiveOptionCardsWithoutPrivacySlogan() {
        compose.setContent {
            MoreScreen(onMusicFolders = {}, onBackup = {}, onSettings = {})
        }

        compose.onAllNodesWithTag(MORE_OPTION_CARD_TAG).assertCountEquals(3)
        listOf(
            "Music folders and scanning",
            "Choose folders, find device music, rescan, and manage tracks removed from the index.",
            "Backup and restore",
            "Create USB-visible backups or restore playlists and app settings.",
            "Appearance",
            "Choose light, dark, or system colors and reduce motion.",
            "Manage",
            "Open",
            "Customize",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onAllNodesWithText("Offline only", substring = true).assertCountEquals(0)
    }

    @Test
    fun eachOptionButtonRunsItsDestinationAction() {
        val actions = mutableListOf<String>()
        compose.setContent {
            MoreScreen(
                onMusicFolders = { actions += "folders" },
                onBackup = { actions += "backup" },
                onSettings = { actions += "appearance" },
            )
        }

        compose.onNodeWithText("Manage").performClick()
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Customize").performClick()

        compose.runOnIdle {
            assertEquals(listOf("folders", "backup", "appearance"), actions)
        }
    }
}
