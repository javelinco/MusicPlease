# Playback Screen Stability and Smooth Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Now Playing mounted throughout an active playback session and render continuous, isolated playback progress between authoritative player updates.

**Architecture:** Navigation will choose Home content from durable `hasSession` state instead of transient `isPlaying` state. A new `PlaybackProgress` composable will own frame-clock projection and drag state so only the slider and elapsed label update continuously while the player remains authoritative.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose frame clock, Robolectric, Compose UI Test, JUnit 4, Gradle

## Global Constraints

- Recently Played appears only when no playback session is queued.
- Paused playback remains on Now Playing.
- Projected progress is clamped to zero through the track duration.
- An authoritative position difference greater than 1,000 ms snaps the display.
- Slider dragging retains the existing live-seek behavior.
- Progress animation remains active when reduced-motion mode is enabled because it is functional playback feedback.
- Do not alter playback commands, queue order, shuffle, repeat, artwork resolution, lyrics timing, or control layout.
- Do not run connected Android tests against the user's phone.

---

### Task 1: Keep Home on Now Playing for the full playback session

**Files:**
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/NavigationHistory.kt`
- Modify: `app/src/test/java/com/javelinco/localmusicplayer/ui/navigation/NavigationPolicyTest.kt`
- Modify: `app/src/test/java/com/javelinco/localmusicplayer/ui/navigation/AppScreenHeaderTest.kt`
- Create: `app/src/test/java/com/javelinco/localmusicplayer/ui/navigation/PlaybackHomeStabilityTest.kt`

**Interfaces:**
- Consumes: `PlaybackUiState.hasSession`, `PlaybackUiState.isPlaying`, `Destination.HOME`, and `AppNavigation`.
- Produces: `chooseInitialPrimaryDestination(recentLoaded, playbackReady, hasRecent, hasSession)` and `screenHeaderTitle(destination, homeHasSession)` with one shared durable-session rule.

- [ ] **Step 1: Add failing policy and header tests**

Update the existing pure tests so a paused active session chooses Home and labels it Now playing:

```kotlin
assertEquals(
    PrimaryDestination.HOME,
    chooseInitialPrimaryDestination(
        recentLoaded = true,
        playbackReady = true,
        hasRecent = false,
        hasSession = true,
    ),
)
assertEquals("Now playing", screenHeaderTitle(Destination.HOME, homeHasSession = true))
assertEquals("Recently played", screenHeaderTitle(Destination.HOME, homeHasSession = false))
```

- [ ] **Step 2: Add a failing host-side screen-stability test**

Create `PlaybackHomeStabilityTest.kt` with a real `AppNavigation` test host. Start Home with `hasSession = true` and `isPlaying = true`, mutate only `isPlaying` to false, then assert that `Now playing`, the track title, and elapsed time remain while `Recently played` is absent:

```kotlin
package com.javelinco.localmusicplayer.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.javelinco.localmusicplayer.data.db.RecentPlaylistRow
import com.javelinco.localmusicplayer.data.settings.SettingsState
import com.javelinco.localmusicplayer.playback.service.PlaybackUiState
import com.javelinco.localmusicplayer.ui.library.LibraryActions
import com.javelinco.localmusicplayer.ui.library.LibraryScreenState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackHomeStabilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pausingAnActiveSessionKeepsNowPlayingMounted() {
        lateinit var setPlaying: (Boolean) -> Unit
        compose.setContent {
            var isPlaying by remember { mutableStateOf(true) }
            setPlaying = { isPlaying = it }
            AppNavigation(
                libraryState = LibraryScreenState(),
                libraryActions = LibraryActions(),
                recentTracks = emptyList(),
                recentPlaylists = listOf(RecentPlaylistRow("mix", "Recent mix", 1)),
                recentLoaded = true,
                dedicated = false,
                settings = SettingsState(),
                playback = PlaybackUiState(
                    controllerReady = true,
                    connected = true,
                    hasSession = true,
                    currentMediaId = "paused-track",
                    title = "Paused track",
                    artist = "Test artist",
                    isPlaying = isPlaying,
                    positionMs = 42_000,
                    durationMs = 180_000,
                ),
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

        compose.onNodeWithText("Now playing").assertIsDisplayed()
        compose.runOnIdle { setPlaying(false) }

        compose.onNodeWithText("Now playing").assertIsDisplayed()
        compose.onNodeWithText("Paused track").assertIsDisplayed()
        compose.onNodeWithText("0:42").assertIsDisplayed()
        compose.onNodeWithText("Recently played").assertDoesNotExist()
    }
}
```

Use Robolectric SDK 36 and `androidx.compose.ui.test.junit4.v2.createComposeRule` so this test runs in `testDebugUnitTest` without touching a connected phone.

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```powershell
rtk proxy "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" "-Dorg.gradle.appname=gradlew" "-Dgradle.user.home=C:\Users\javel\.gradle" -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest --tests com.javelinco.localmusicplayer.ui.navigation.NavigationPolicyTest --tests com.javelinco.localmusicplayer.ui.navigation.AppScreenHeaderTest --tests com.javelinco.localmusicplayer.ui.navigation.PlaybackHomeStabilityTest
```

Expected: FAIL because the production functions still accept/use `isPlaying` and Home replaces Now Playing when it becomes false.

- [ ] **Step 4: Implement the durable-session rule**

Rename the policy parameter to `hasSession`, rename the header parameter to `homeHasSession`, pass `playback.hasSession` at both call sites, and change Home content selection:

```kotlin
Destination.HOME -> if (playback.hasSession) {
    NowPlayingScreen(
        playback,
        settings.reducedMotion,
        onPrevious,
        onPlayPause,
        onNext,
        onSeek,
        onShuffle,
        onRepeat,
        { navigateTo(Destination.QUEUE) },
        currentMedia,
    )
} else {
    HomeScreen(
        recentTracks,
        recentPlaylists,
        trackActions,
        onPlayRecentQueue,
        libraryActions.onPlayPlaylist,
        onRemoveRecentTrack,
        onRemoveRecentPlaylist,
        trackMedia,
        onRequestMedia,
    )
}
```

Do not change the explicit `Destination.NOW_PLAYING` branch.

- [ ] **Step 5: Run the focused tests and verify GREEN**

Run the command from Step 3 again.

Expected: PASS; pausing changes the icon/state but does not replace the screen.

- [ ] **Step 6: Commit the stable navigation change**

```powershell
rtk git add app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/AppNavigation.kt app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/NavigationHistory.kt app/src/test/java/com/javelinco/localmusicplayer/ui/navigation/NavigationPolicyTest.kt app/src/test/java/com/javelinco/localmusicplayer/ui/navigation/AppScreenHeaderTest.kt app/src/test/java/com/javelinco/localmusicplayer/ui/navigation/PlaybackHomeStabilityTest.kt
rtk git commit -m "fix: keep active playback screen mounted"
```

### Task 2: Render smooth isolated playback progress

**Files:**
- Create: `app/src/main/java/com/javelinco/localmusicplayer/ui/player/PlaybackProgress.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/player/NowPlayingScreen.kt`
- Create: `app/src/test/java/com/javelinco/localmusicplayer/ui/player/PlaybackProgressTest.kt`

**Interfaces:**
- Consumes: `mediaId: String?`, `positionMs: Long`, `durationMs: Long`, `isPlaying: Boolean`, and `onSeek: (Long) -> Unit` from `NowPlayingScreen`.
- Produces: `projectPlaybackPosition(positionMs: Long, elapsedMs: Long, isPlaying: Boolean, durationMs: Long): Long` and `PlaybackProgress(...)`.

- [ ] **Step 1: Write failing projection tests**

Create `PlaybackProgressTest.kt` with literal expectations:

```kotlin
@Test
fun playingPositionAdvancesWithElapsedTime() {
    assertEquals(1_250L, projectPlaybackPosition(1_000L, 250L, true, 10_000L))
}

@Test
fun pausedAndOutOfRangePositionsAreClamped() {
    assertEquals(1_000L, projectPlaybackPosition(1_000L, 250L, false, 10_000L))
    assertEquals(250L, projectPlaybackPosition(-500L, 250L, true, 10_000L))
    assertEquals(10_000L, projectPlaybackPosition(9_900L, 250L, true, 10_000L))
    assertEquals(0L, projectPlaybackPosition(1_000L, 250L, true, 0L))
}
```

- [ ] **Step 2: Run the focused projection test and verify RED**

Run:

```powershell
rtk proxy "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" "-Dorg.gradle.appname=gradlew" "-Dgradle.user.home=C:\Users\javel\.gradle" -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest --tests com.javelinco.localmusicplayer.ui.player.PlaybackProgressTest
```

Expected: compilation FAIL because `projectPlaybackPosition` does not exist.

- [ ] **Step 3: Implement the projection helper and progress composable**

Create `PlaybackProgress.kt`. The pure helper clamps the authoritative position, advances it only while playing, and clamps the sum to duration. The composable re-anchors on authoritative updates and keeps frame-driven state local:

```kotlin
package com.javelinco.localmusicplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlin.math.abs
import kotlinx.coroutines.isActive

private const val SEEK_SNAP_THRESHOLD_MS = 1_000L

internal fun projectPlaybackPosition(
    positionMs: Long,
    elapsedMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
): Long {
    val end = durationMs.coerceAtLeast(0L)
    val start = positionMs.coerceIn(0L, end)
    if (!isPlaying) return start
    return start + elapsedMs.coerceIn(0L, end - start)
}

@Composable
internal fun PlaybackProgress(
    mediaId: String?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val end = durationMs.coerceAtLeast(0L)
    var projectedPosition by remember(mediaId) {
        mutableLongStateOf(positionMs.coerceIn(0L, end))
    }
    var dragPosition by remember(mediaId) { mutableStateOf<Float?>(null) }

    LaunchedEffect(mediaId, positionMs, durationMs, isPlaying) {
        val authoritativePosition = positionMs.coerceIn(0L, end)
        if (!isPlaying || abs(authoritativePosition - projectedPosition) > SEEK_SNAP_THRESHOLD_MS) {
            projectedPosition = authoritativePosition
        } else if (authoritativePosition > projectedPosition) {
            projectedPosition = authoritativePosition
        }
        if (!isPlaying || end == 0L) return@LaunchedEffect

        val anchorPosition = projectedPosition
        val anchorNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameNanos ->
                projectedPosition = projectPlaybackPosition(
                    positionMs = anchorPosition,
                    elapsedMs = (frameNanos - anchorNanos) / 1_000_000L,
                    isPlaying = true,
                    durationMs = end,
                )
            }
        }
    }

    val sliderEnd = end.toFloat().coerceAtLeast(1f)
    val displayedPosition = (dragPosition ?: projectedPosition.toFloat()).coerceIn(0f, sliderEnd)
    Column(modifier) {
        Slider(
            value = displayedPosition,
            onValueChange = {
                dragPosition = it
                onSeek(it.toLong())
            },
            onValueChangeFinished = { dragPosition = null },
            valueRange = 0f..sliderEnd,
            modifier = Modifier.fillMaxWidth().testTag("playback-progress"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(displayedPosition.toLong().asTime(), style = MaterialTheme.typography.labelSmall)
            Text(end.asTime(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Long.asTime(): String {
    val totalSeconds = coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
```

- [ ] **Step 4: Replace direct progress rendering in Now Playing**

Replace the existing `Slider` and time-label `Row` in `NowPlayingScreen` with:

```kotlin
PlaybackProgress(
    mediaId = state.currentMediaId,
    positionMs = state.positionMs,
    durationMs = state.durationMs,
    isPlaying = state.isPlaying,
    onSeek = onSeek,
    modifier = Modifier.fillMaxWidth(),
)
```

Move the `Long.asTime()` formatter into `PlaybackProgress.kt` and keep it private.

- [ ] **Step 5: Run the projection tests and verify GREEN**

Run the command from Step 2 again.

Expected: PASS for playing, paused, negative, zero-duration, and end-of-track behavior.

- [ ] **Step 6: Run full project verification**

Run:

```powershell
rtk proxy "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" "-Dorg.gradle.appname=gradlew" "-Dgradle.user.home=C:\Users\javel\.gradle" -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug
```

Expected: BUILD SUCCESSFUL. Do not run `connectedDebugAndroidTest`.

- [ ] **Step 7: Review and commit smooth progress**

Review the diff for frame-loop cancellation, clamping, seek behavior, unrelated changes, and whitespace errors, then commit:

```powershell
rtk git add app/src/main/java/com/javelinco/localmusicplayer/ui/player/PlaybackProgress.kt app/src/main/java/com/javelinco/localmusicplayer/ui/player/NowPlayingScreen.kt app/src/test/java/com/javelinco/localmusicplayer/ui/player/PlaybackProgressTest.kt
rtk git commit -m "fix: smooth playback progress updates"
```
