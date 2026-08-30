# More Option Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the More screen's loose list and privacy slogan with three clear, descriptive, responsive option cards.

**Architecture:** Extract the destination content from `AppNavigation.kt` into a focused `MoreScreen.kt`. A reusable private `MoreOptionCard` owns the visual pattern while `MoreScreen` supplies exact copy, icons, action labels, and existing navigation callbacks.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI testing, Gradle

## Global Constraints

- Preserve the existing Music folders and scanning, Backup and restore, and Appearance destinations and navigation behavior.
- Remove `Offline only · MP3 · no telemetry · no internet permission` from the UI.
- Add no permission, dependency, persistence, playback, backup, or scanning behavior.
- Use Material theme colors in light and dark mode.
- Keep the content scrollable and responsive under larger font scales.
- Do not run connected Android instrumentation tests on the user's phone; compile Android tests only.
- Preserve package `com.javelinco.localmusicplayer`, label `Music, Please!`, and backup compatibility.

---

### Task 1: Characterize the More option-card contract

**Files:**
- Create: `app/src/androidTest/java/com/javelinco/localmusicplayer/ui/MoreScreenUiTest.kt`

**Interfaces:**
- Consumes: no production interface; this task intentionally names the not-yet-created `MoreScreen` and `MORE_OPTION_CARD_TAG` contract.
- Produces: executable UI requirements for three cards, exact copy, explicit action callbacks, and removal of the privacy slogan.

- [ ] **Step 1: Write the failing card and copy test**

Create `MoreScreenUiTest.kt` with a Compose rule and this test body:

```kotlin
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
    compose.onNodeWithText("Offline only", substring = true).assertDoesNotExist()
}
```

Import `MoreScreen` and `MORE_OPTION_CARD_TAG` from
`com.javelinco.localmusicplayer.ui.navigation`, plus the Compose test APIs
`assertCountEquals`, `assertDoesNotExist`, `assertIsDisplayed`,
`onAllNodesWithTag`, `onNodeWithText`, and `createComposeRule`.

- [ ] **Step 2: Write the failing callback test**

Add one test that records callback names and clicks the three unique buttons:

```kotlin
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
```

Import JUnit `assertEquals` and Compose `performClick`.

- [ ] **Step 3: Compile to verify the contract fails before production code**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\javel\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME='C:\Users\javel\.gradle'
./gradlew compileDebugAndroidTestKotlin
```

Expected: FAIL because `MoreScreen` and `MORE_OPTION_CARD_TAG` are not visible production declarations yet.

- [ ] **Step 4: Commit the red test only after recording the expected failure**

```powershell
git add -- app/src/androidTest/java/com/javelinco/localmusicplayer/ui/MoreScreenUiTest.kt
git commit -m "test: specify More option cards"
```

### Task 2: Build the responsive card-row screen

**Files:**
- Create: `app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/MoreScreen.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/AppNavigation.kt:3-21,230-234,309-332`
- Modify: `app/src/androidTest/java/com/javelinco/localmusicplayer/ui/MoreMusicFoldersUiTest.kt:61-64`

**Interfaces:**
- Consumes: `MoreScreen(onMusicFolders: () -> Unit, onBackup: () -> Unit, onSettings: () -> Unit)` callbacks already supplied by `AppNavigation`.
- Produces: `internal const val MORE_OPTION_CARD_TAG = "more-option-card"` and `@Composable internal fun MoreScreen(...)`.

- [ ] **Step 1: Create the focused screen file and card model**

In `MoreScreen.kt`, define:

```kotlin
internal const val MORE_OPTION_CARD_TAG = "more-option-card"

private data class MoreOption(
    val title: String,
    val description: String,
    val actionLabel: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)
```

`MoreScreen` builds the exact three options from the specification and renders
them in a `LazyColumn` with `PaddingValues(horizontal = 16.dp, vertical = 12.dp)`
and `Arrangement.spacedBy(12.dp)`. Use `Icons.Rounded.Folder`,
`Icons.Rounded.Backup`, and `Icons.Rounded.Palette`.

- [ ] **Step 2: Implement the responsive option card**

Create a private `MoreOptionCard(option: MoreOption)` using a Material `Card`
tagged with `MORE_OPTION_CARD_TAG`, `RoundedCornerShape(18.dp)`,
`CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)`,
and a small default elevation. Inside it:

- render a 48 dp rounded icon tile using `primaryContainer` and
  `onPrimaryContainer`;
- render title with `titleMedium` and `FontWeight.SemiBold`;
- render description with `bodyMedium` and `onSurfaceVariant`;
- render a `FilledTonalButton` with the option's action label and callback; and
- use `BoxWithConstraints` to keep icon, text, and button in one row when
  `maxWidth >= 360.dp`, but move the button below the icon/text row and align it
  to the end when narrower.

The icon uses `contentDescription = null`; the visible action text provides the
accessible action name. The card itself is not separately clickable, avoiding
duplicate nested actions.

- [ ] **Step 3: Replace the inline More screen without changing navigation**

Delete the private `MoreScreen` implementation from `AppNavigation.kt`. Keep
the existing call and callbacks at `Destination.MORE`. Remove imports used only
by the deleted implementation: `clickable`, `Backup`, and `Palette`. Keep
`Column`, `Icon`, `ListItem`, and `Text` if still used by navigation or
`SettingsScreen`.

- [ ] **Step 4: Update the established navigation test to use the explicit button**

In `MoreMusicFoldersUiTest`, replace:

```kotlin
compose.onNodeWithText("Music folders and scanning").assertIsDisplayed().performClick()
```

with:

```kotlin
compose.onNodeWithText("Music folders and scanning").assertIsDisplayed()
compose.onNodeWithText("Manage").assertIsDisplayed().performClick()
```

The existing assertions for `Music sources` and `Add another folder` continue
to prove that the callback opens the same destination.

- [ ] **Step 5: Compile and run local tests**

Run:

```powershell
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin
```

Expected: BUILD SUCCESSFUL. The Android test compilation may report the
existing `createComposeRule` deprecation warning but no errors.

- [ ] **Step 6: Check the diff and commit the implementation**

```powershell
git diff --check
git diff --stat
git add -- app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/MoreScreen.kt app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/AppNavigation.kt app/src/androidTest/java/com/javelinco/localmusicplayer/ui/MoreMusicFoldersUiTest.kt
git commit -m "feat: redesign More with option cards"
```

### Task 3: Full regression and privacy verification

**Files:**
- Verify only; no source change is expected.

**Interfaces:**
- Consumes: the completed More screen and existing build/test gates.
- Produces: fresh evidence that the redesign is safe to merge and publish.

- [ ] **Step 1: Run the complete computer-only verification suite**

Run:

```powershell
./gradlew testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin
```

Expected: BUILD SUCCESSFUL with no unit-test, lint, packaging, or Android-test
compilation failure. Do not run `connectedDebugAndroidTest` on the user's phone.

- [ ] **Step 2: Recheck the manifest permission contract and removed slogan**

Run:

```powershell
rg -n "Offline only|no telemetry|no internet permission" app/src/main
rg -n "uses-permission" app/src/main/AndroidManifest.xml
```

Expected: the slogan search returns no matches; the manifest still declares
only the existing audio-discovery/playback permissions and explicit removals.

- [ ] **Step 3: Review repository state**

Run:

```powershell
git diff main...HEAD --check
git status --short
git log -5 --oneline
```

Expected: no whitespace errors, no uncommitted task files, and only the planned
test and implementation commits after the design and plan commits.
