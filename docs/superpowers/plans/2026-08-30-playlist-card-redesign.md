# Playlist Card Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the legacy full-page playlist editor with compact expandable playlist cards and dialog-based create, rename, and delete actions.

**Architecture:** Keep the existing `PlaylistScreen` public callback interface and persistence layer unchanged. Split its rendering into focused private Compose functions, keep transient expansion/dialog state inside the screen, and exercise the real composables with host-side Robolectric Compose tests so the attached phone’s data is never touched by tests.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Robolectric, Compose UI testing, JUnit 4, Gradle.

## Global Constraints

- Use rounded cards as the default playlist and track-row presentation.
- Do not show permanent create or rename text fields.
- Use compact icon actions for play, rename, delete, expand/collapse, reorder, and remove.
- Keep playlist titles and track titles to one line with an ellipsis when space is insufficient.
- Keep the existing delete confirmation and state clearly that music files are not deleted.
- Remove the duplicate full-library `Add a track` list; adding tracks remains available through each track’s existing `Add to playlist` action.
- Do not change permissions, database schema, backup format, playback, scanning, or media files.
- Do not run connected-device test tasks or clear/uninstall the app.

---

### Task 1: Define and prove the compact playlist-card behavior

**Files:**
- Create: `app/src/test/java/com/javelinco/localmusicplayer/ui/library/PlaylistCardScreenTest.kt`
- Modify: `app/src/test/java/com/javelinco/localmusicplayer/ui/library/PlaylistDeleteConfirmationTest.kt`

**Interfaces:**
- Consumes: the existing `PlaylistScreen` parameters and `PlaylistSummary`, `PlaylistEntryEntity`, and `TrackEntity` models.
- Produces: regression coverage for initial compact state, create/rename dialogs, per-card disclosure, ordered track contents, play, and confirmed deletion.

- [ ] **Step 1: Write a failing initial-layout and disclosure test**

Create a Robolectric Compose test fixture with `Road Mix` and `Quiet Mix`. Assert that `New playlist` and both card names are visible while `Playlist name`, `Back to playlists`, `Playlist order`, and `Add a track` are absent. Click `Show tracks in Road Mix`, assert its two entries appear in position order and `Quiet song` remains absent, then click `Hide tracks in Road Mix` and assert its entries disappear.

The production break caught is restoration of the legacy editor, disclosure of the wrong playlist, or failure to respect playlist-entry order.

- [ ] **Step 2: Write failing create and rename tests**

Click `New playlist`, assert the `Create playlist` dialog and `Playlist name` field appear, enter `  Driving  `, and click the dialog’s `Create` action. Assert the callback receives exactly `Driving`. Then click `Rename Road Mix`, assert the prefilled text is `Road Mix`, replace it with `  Road Songs  `, confirm, and assert the callback receives `mix` to `Road Songs`.

The production break caught is a permanently visible field, missing prefill, wrong playlist ID, or untrimmed submitted name.

- [ ] **Step 3: Write a failing compact-action test**

Click `Play Road Mix` and assert `onPlay` receives `mix`. Expand the playlist, click `Move First track down`, and assert `onMove` receives `mix, 0, 1`; click `Remove First track from Road Mix` and assert `onRemove` receives `mix, first-entry`.

The production break caught is an icon wired to the wrong playlist or entry.

- [ ] **Step 4: Update the delete-confirmation test for card actions**

Remove its legacy detail-screen navigation. Click `Delete Road Mix`, verify the callback remains untouched until `confirm-playlist-delete` is clicked, and retain the existing Cancel and explanatory-copy assertions.

- [ ] **Step 5: Run the focused tests and verify RED**

Run:

```powershell
./gradlew.bat testDebugUnitTest --tests "com.javelinco.localmusicplayer.ui.library.PlaylistCardScreenTest" --tests "com.javelinco.localmusicplayer.ui.library.PlaylistDeleteConfirmationTest"
```

Expected: FAIL because the production screen still exposes the permanent name field and legacy detail editor and has none of the new content descriptions.

### Task 2: Implement the playlist card screen

**Files:**
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/library/PlaylistScreen.kt`

**Interfaces:**
- Consumes: unchanged `PlaylistScreen` data and callbacks.
- Produces: `PlaylistCard`, `PlaylistTrackRow`, and `PlaylistNameDialog` private composables plus transient create/rename/delete/expanded state.

- [ ] **Step 1: Replace legacy selected-playlist state with card state**

Use a `remember { mutableStateListOf<String>() }` for expanded playlist IDs, nullable `PlaylistSummary` values for rename/delete, and a Boolean for create. Remove `selectedId`, the legacy `BackHandler`, and the permanent `name` field.

- [ ] **Step 2: Render the compact screen shell**

Use one `LazyColumn` with 8 dp vertical spacing and bottom content padding. Add one full-width `Button` labeled `New playlist`, an empty-state card when there are no playlists, and one keyed `PlaylistCard` per summary. Do not nest another vertical `LazyColumn`.

- [ ] **Step 3: Render each playlist card and actions**

Use a 16 dp rounded Material card with a playlist icon, one-line ellipsized name, track count, and `IconButton`s whose descriptions are exactly `Play <name>`, `Rename <name>`, `Delete <name>`, and `Show tracks in <name>`/`Hide tracks in <name>`. Clicking the card header or disclosure toggles only that playlist.

- [ ] **Step 4: Render compact ordered track rows**

When expanded, filter entries by playlist ID and sort by `position`. Render nested 12 dp rounded cards with a one-line ellipsized title and one-line artist/file fallback. Retain `TrackActionMenu` for available tracks. Use compact icon controls described as `Move <title> up`, `Move <title> down`, and `Remove <title> from <playlist>`, disabling movement at the corresponding boundary. Show `This playlist is empty.` when appropriate.

- [ ] **Step 5: Add create and rename dialogs**

Use one reusable `PlaylistNameDialog`. Create starts blank; rename starts with the selected playlist name. Disable confirmation when `name.trim()` is empty. Confirmation passes the trimmed name to the existing callback and dismisses the dialog. Use dialog titles `Create playlist` and `Rename playlist`, confirmation labels `Create` and `Save`, and field label `Playlist name`.

- [ ] **Step 6: Preserve and adapt delete confirmation**

Keep the existing dialog wording and `confirm-playlist-delete` tag. Confirmation calls `onDelete` once, clears the pending target, and removes that ID from expanded state.

- [ ] **Step 7: Run focused tests and verify GREEN**

Run the Task 1 command again. Expected: both test classes PASS.

- [ ] **Step 8: Run the playlist instrumentation-test compilation**

Run:

```powershell
./gradlew.bat compileDebugAndroidTestKotlin
```

Expected: PASS after updating any legacy `LibraryUiTest` and `SystemBackUiTest` assertions to the new card contract. Do not run them on the phone.

- [ ] **Step 9: Commit the implementation**

Run `git diff --check`, inspect the diff, and commit the tests and production UI with `feat: redesign playlists as compact cards`.

### Task 3: Verify, publish, and install

**Files:**
- Verify: all changed source, tests, design, and plan files.

**Interfaces:**
- Consumes: the completed playlist card implementation.
- Produces: a verified debug APK installed as an in-place update on the attached primary-user phone.

- [ ] **Step 1: Run full computer-only verification**

Run:

```powershell
./gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug
```

Expected: all tasks succeed without invoking a connected-device task.

- [ ] **Step 2: Commit the plan and any verification-only corrections**

Verify `git status`, `git diff --check`, and the final commit history. Commit the plan with `docs: plan compact playlist cards` if it is not already committed.

- [ ] **Step 3: Push and verify GitHub synchronization**

Push `main`, fetch `origin/main`, and verify local `HEAD` equals `origin/main`.

- [ ] **Step 4: Install without deleting data**

Confirm the authorized Samsung device with `adb devices -l`, then install the assembled APK using `adb install -r`. Do not use uninstall, `pm clear`, or connected tests.

- [ ] **Step 5: Verify the installed package**

Run `adb shell pm path com.javelinco.localmusicplayer` and `adb shell pm list packages --user 0 --show-versioncode com.javelinco.localmusicplayer`. Confirm the package is present for user 0.
