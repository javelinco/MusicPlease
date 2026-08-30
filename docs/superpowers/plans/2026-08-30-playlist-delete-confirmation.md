# Playlist Delete Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require explicit confirmation before deleting a playlist.

**Architecture:** `PlaylistScreen` owns a short-lived pending deletion target and renders a Material `AlertDialog` only while that target exists. The existing deletion callback remains the sole persistence boundary and is invoked only by the dialog's confirm action.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI testing, Robolectric, JUnit 4, Gradle.

## Global Constraints

- Cancel, Android Back, and outside-tap must not delete the playlist.
- Confirmation must delete exactly the selected playlist and return to the playlist list.
- Dialog copy must name the playlist and state that music files remain untouched.
- Add no permission, database migration, network access, or media-file write.
- Run computer-only verification; do not invoke connected-device tasks or `adb`.

---

### Task 1: Playlist deletion safety dialog

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/com/javelinco/localmusicplayer/ui/library/PlaylistDeleteConfirmationTest.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/library/PlaylistScreen.kt`

**Interfaces:**
- Consumes: `PlaylistScreen(..., onDelete: (String) -> Unit, ...)` and the selected `PlaylistSummary`.
- Produces: a confirmation dialog that invokes `onDelete(selected.id.value)` only after explicit confirmation.

- [ ] **Step 1: Enable computer-only Compose UI tests and write the failing regression test**

Add `testImplementation(libs.androidx.compose.ui.test.junit4)` and create a Robolectric Compose test. The test opens `Road Mix`, taps Delete, verifies no deletion occurred and `Delete playlist?` is displayed, cancels and verifies no deletion, then opens the dialog again, confirms, and verifies the callback received only `mix`.

The production mutation caught by this test is any direct Delete-button call to `onDelete`, any Cancel path that calls it, or a confirm path that supplies the wrong playlist ID.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
./gradlew.bat testDebugUnitTest --tests "com.javelinco.localmusicplayer.ui.library.PlaylistDeleteConfirmationTest"
```

Expected: FAIL because the current Delete button immediately invokes `onDelete` and no confirmation dialog is shown.

- [ ] **Step 3: Implement the minimal confirmation dialog**

In `PlaylistScreen`, remember a nullable `PlaylistSummary` pending target. Change the existing Delete button to set that target. Render `AlertDialog` with:

- `onDismissRequest` clearing the target;
- title `Delete playlist?`;
- body naming the target and explaining that its music files will remain;
- Cancel clearing the target;
- Delete capturing the target ID, clearing pending and selected state, then calling `onDelete` once.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command again. Expected: PASS.

- [ ] **Step 5: Run complete computer-only verification**

```powershell
./gradlew.bat testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug
```

Expected: every task succeeds without invoking a connected device.

- [ ] **Step 6: Review and commit**

Run `git diff --check`, verify only the plan, test dependency, regression test, and playlist UI changed, then commit with `fix: confirm playlist deletion`.

### Task 2: Integrate and publish

**Files:**
- Verify: the design, plan, test, Gradle dependency, and playlist UI changes.

**Interfaces:**
- Consumes: the verified `fix/playlist-delete-confirmation` branch.
- Produces: synchronized local and GitHub `main` branches.

- [ ] **Step 1: Fast-forward the verified feature branch into `main`**

Confirm both worktrees are clean, then fast-forward `main` to `fix/playlist-delete-confirmation`.

- [ ] **Step 2: Re-run the complete computer-only verification on `main`**

Run the Step 1 full Gradle command from the main checkout. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Push and verify synchronization**

Push `main` to `origin`, fetch `origin/main`, and verify local `HEAD` equals `origin/main`.
