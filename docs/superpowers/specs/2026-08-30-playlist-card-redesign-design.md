# Playlist Card Redesign

## Goal

Replace the current oversized playlist editor with a compact, card-based interface that makes creating, renaming, deleting, playing, and inspecting playlists easy to understand without wasting screen space.

## Confirmed problem

The current `PlaylistScreen` still contains the legacy UI: playlist creation is a permanently visible text field, opening a playlist replaces the list with a large editor, rename is another permanently visible text field, management actions are large text buttons, and playlist contents compete vertically with a second full-library “Add a track” list. The playback-only APK update did not touch this screen, so reinstalling it correctly produced no visible playlist change.

## Considered approaches

1. **Expandable playlist cards on one screen (selected).** Each playlist remains visible as a compact card. A disclosure icon expands its ordered tracks in place. Play, rename, and delete are compact icon actions. This preserves context, matches the app’s existing card language, and fits the most content on a phone screen.
2. **Playlist list plus a separate detail screen.** This provides more room per playlist but recreates the navigation and “where am I?” problem in the current editor and requires more back navigation.
3. **Playlist list plus a bottom sheet editor.** This is compact initially, but a sheet becomes crowded when a playlist contains many tracks and is less discoverable than inline disclosure.

## Design

The Playlists library view begins with one full-width `New playlist` button. The playlist name field is never permanently visible. Tapping the button opens an `AlertDialog` with a focused name field, Cancel, and Create. Blank or whitespace-only names cannot be submitted.

Each playlist is rendered as a rounded card with:

- a playlist icon;
- its name, limited to one line with an ellipsis;
- a singular/plural track count;
- a compact play icon;
- a pencil icon that opens a rename dialog prefilled with the current name;
- a delete icon that opens the existing confirmation dialog;
- a disclosure icon with an explicit accessibility label that expands or collapses the ordered track list.

Opening a playlist does not replace the playlist list. Expanded contents appear directly below the card header. Each entry is a compact nested track card with a one-line ellipsized title, artist or unavailable-track explanation, the existing track action menu when the track is available, small move-up and move-down icon controls, and a remove icon. A playlist with no entries shows `This playlist is empty.` instead of a large blank area.

The old `Back to playlists`, permanent rename field, `Playlist order` heading, and full-library `Add a track` list are removed. Tracks continue to be added through the existing track action menu’s `Add to playlist` command, which avoids duplicating the entire library inside every playlist.

Create and rename dialogs retain their input if recomposition occurs while the dialog is open. Confirming a rename trims surrounding whitespace. Delete continues to name the playlist, explain that music files are untouched, and call the deletion callback only after confirmation.

## State and architecture

`PlaylistScreen` owns only short-lived presentation state: the expanded playlist IDs and the pending create, rename, or delete dialog. Playlist data and callbacks remain owned by the existing view model and repository. No database, backup format, permission, playback, scan, or media-file behavior changes.

Small private composables in `PlaylistScreen.kt` separate the screen, playlist card, nested track row, and name dialog. This keeps the public `PlaylistScreen` interface unchanged and avoids changes at its call sites.

## Testing

Host-side Robolectric Compose tests will verify the user-visible contract without touching the connected phone’s data:

- the screen initially shows compact playlist cards and a `New playlist` action, but no permanent name field;
- creation opens a dialog and submits a trimmed nonblank name;
- the pencil action opens a prefilled rename dialog and submits the edited name;
- disclosure expands the correct playlist’s ordered tracks and collapses them again;
- play invokes the correct playlist callback;
- delete still requires explicit confirmation;
- the Android back action dismisses an open dialog or collapses the most recently expanded card before leaving the screen.

The complete computer-only suite will run JVM tests, compile instrumentation tests, run lint, and assemble the debug APK. Only after those pass will the APK be installed in place with `adb install -r`, preserving app data.
