# Playback Screen Stability and Smooth Progress Design

## Goal

Keep the Now Playing screen visually stable when playback controls are used and make playback progress move continuously instead of jumping every half second.

## Root cause

The Home destination currently chooses its entire content from `PlaybackUiState.isPlaying`. Pause changes that value by design, and skip or queue-rebuilding operations can change it transiently. Every such change can dispose the complete `NowPlayingScreen` subtree and replace it with Recently Played. Recreating the subtree also restarts local artwork loading, which produces the visible whole-screen flash.

The player publishes authoritative position updates every 500 ms. `NowPlayingScreen` passes those discrete values directly to the slider, so the thumb visibly advances in steps.

## Screen stability

Home will show Now Playing whenever `PlaybackUiState.hasSession` is true, regardless of whether the active item is playing or paused. Recently Played will appear only when no playback session is queued. The Home header and initial-destination policy will use the same durable-session definition so the title and content cannot disagree.

This preserves the user's earlier Home behavior while making “not playing” precise: a paused track is still the current playback session; an empty queue is not.

## Smooth progress

The progress slider and elapsed-time label will move into a focused `PlaybackProgress` composable. It will retain the latest authoritative player position and use the Compose frame clock to project forward from that point while playback is active. Projection is clamped to the track duration.

When playback is paused, the track changes, the duration changes, or the authoritative position differs from the projection by more than 1,000 ms, the displayed value will snap to the authoritative value. Ordinary 500 ms updates will re-anchor the projection without moving the thumb backward because of small timing differences.

Slider dragging will keep a local drag value so the thumb stays directly under the user's finger. The existing live-seek behavior remains: drag changes continue to call `onSeek`, and releasing the thumb returns display control to the authoritative/projected position.

The frame-driven state is read only inside `PlaybackProgress`, so its frequent updates do not require the artwork, lyrics, metadata, or transport controls to redraw at frame rate. Progress interpolation is functional playback feedback rather than decorative motion and remains smooth when reduced-motion mode is enabled.

## Error and edge behavior

- A missing or zero duration produces a zero-to-one slider range and clamps displayed progress safely.
- Progress never becomes negative or exceeds duration.
- Pausing immediately stops projection and displays the reported paused position.
- Track changes reset both projected and drag state.
- The player remains authoritative; interpolation never writes playback state unless the user drags the slider.

## Testing

A host-side Robolectric Compose regression test will render Home with an active session, press Pause, update `isPlaying` to false, and verify that Now Playing and its position remain visible instead of Recently Played.

Pure unit tests will verify the projection rules for playing, paused, negative, and end-of-track positions. Existing Android UI tests will continue to compile, but connected tests will not be run against the user's phone.

## Scope

This change does not alter playback commands, queue order, shuffle, repeat, artwork resolution, lyrics timing, or the visual layout of the playback controls.
