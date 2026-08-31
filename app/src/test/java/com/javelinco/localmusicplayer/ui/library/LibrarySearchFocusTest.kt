package com.javelinco.localmusicplayer.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibrarySearchFocusTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun openingSearchFocusesTheSearchField() {
        compose.setContent {
            var searchOpen by remember { mutableStateOf(false) }
            LibraryScreen(
                state = LibraryScreenState(searchOpen = searchOpen),
                actions = LibraryActions(onOpenSearch = { searchOpen = true }),
            )
        }

        compose.onNodeWithContentDescription("Search Tracks").performClick()

        compose.onNodeWithTag("library-search-field").assertIsFocused()
    }
}
