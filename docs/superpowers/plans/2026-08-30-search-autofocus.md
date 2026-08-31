# Library Search Autofocus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make tapping the Library search icon immediately focus the revealed search box and open the Android software keyboard.

**Architecture:** Keep durable search state in `LibraryScreenState` and keep transient input focus inside `LibraryScreen`. Attach a remembered Compose `FocusRequester` to the search field, then request focus and explicitly show the software keyboard from a `LaunchedEffect` whenever search opens.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI focus APIs, Robolectric, Compose UI Test, Gradle

## Global Constraints

- Apply only to the Library search control.
- Do not change search matching, result rendering, layout, or IME actions.
- Do not move focus or keyboard state into the ViewModel.
- Verify with host-side tests only; do not run connected Android tests against the user's phone.

---

### Task 1: Focus and open the keyboard for Library search

**Files:**
- Create: `app/src/test/java/com/javelinco/localmusicplayer/ui/library/LibrarySearchFocusTest.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/library/LibraryScreen.kt:31-42,105-111,189-196`

**Interfaces:**
- Consumes: `LibraryScreen(state: LibraryScreenState, actions: LibraryActions)`, `LibraryActions.onOpenSearch`, and `LibraryScreenState.searchOpen`.
- Produces: a `library-search-field` Compose semantics tag and automatic focus/keyboard activation whenever `searchOpen` changes to `true`.

- [ ] **Step 1: Write the failing focus test**

Create `LibrarySearchFocusTest.kt` with a stateful test host. Click the existing search icon and assert that the newly rendered field owns focus:

```kotlin
package com.javelinco.localmusicplayer.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
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
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
rtk proxy "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" "-Dorg.gradle.appname=gradlew" "-Dgradle.user.home=C:\Users\javel\.gradle" -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest --tests com.javelinco.localmusicplayer.ui.library.LibrarySearchFocusTest
```

Expected: FAIL because `library-search-field` does not exist yet and the rendered field is not explicitly focused.

- [ ] **Step 3: Implement focus and keyboard activation**

In `LibraryScreen.kt`, import `FocusRequester`, `focusRequester`, and `LocalSoftwareKeyboardController`. Remember the requester and keyboard controller with the screen's other local UI state:

```kotlin
val searchFocusRequester = remember { FocusRequester() }
val keyboardController = LocalSoftwareKeyboardController.current

LaunchedEffect(state.searchOpen) {
    if (state.searchOpen) {
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }
}
```

Attach the requester and stable test tag to the existing `OutlinedTextField` without changing its layout:

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .padding(top = 8.dp)
    .focusRequester(searchFocusRequester)
    .testTag("library-search-field"),
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2 again.

Expected: PASS; after the search icon is clicked, the field tagged `library-search-field` owns focus.

- [ ] **Step 5: Run project verification**

Run:

```powershell
rtk proxy "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" "-Dorg.gradle.appname=gradlew" "-Dgradle.user.home=C:\Users\javel\.gradle" -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug
```

Expected: BUILD SUCCESSFUL. Do not run `connectedDebugAndroidTest`.

- [ ] **Step 6: Review and commit**

Inspect the diff for unrelated changes, then commit only the test and `LibraryScreen.kt`:

```powershell
rtk git add app/src/test/java/com/javelinco/localmusicplayer/ui/library/LibrarySearchFocusTest.kt app/src/main/java/com/javelinco/localmusicplayer/ui/library/LibraryScreen.kt
rtk git commit -m "fix: focus library search on open"
```
