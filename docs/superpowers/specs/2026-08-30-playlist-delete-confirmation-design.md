# Playlist Delete Confirmation

## Goal

Prevent accidental playlist deletion. The existing Delete button inside an opened playlist must ask for explicit confirmation before it invokes the deletion callback.

## Considered approaches

1. Keep the pending-deletion state in `PlaylistScreen` and show a Material `AlertDialog` (recommended). This is the smallest change, keeps the safety prompt next to the action it guards, and follows the app's existing confirmation-dialog pattern.
2. Put pending confirmation state in `LibraryViewModel`. This would make the state survive recreation but would add application-layer state for a short-lived UI decision and expand the interface unnecessarily.
3. Delete immediately and offer an Undo snackbar. This is useful for recoverable actions, but it does not satisfy the explicit requirement to ask before deleting and would require repository support for restoring a playlist and its ordered entries.

## Design

Tapping Delete will set the selected playlist as the pending deletion target instead of calling `onDelete`. `PlaylistScreen` will render a Material confirmation dialog whose title is `Delete playlist?` and whose text includes the playlist name. The supporting text will explain that the playlist is removed while its music files are left alone.

The dialog has `Cancel` and `Delete` actions. Cancel, the Android Back action, or tapping outside the dialog dismisses it without calling `onDelete`. Confirming calls `onDelete` exactly once for the pending playlist, clears the pending state, and returns to the playlist list.

No repository, database, playback, permission, network, or media-file behavior changes.

## Testing

Add a Compose regression test that opens a playlist, taps Delete, verifies the playlist remains until confirmation, verifies Cancel is harmless, then reopens the dialog and confirms deletion. The computer-only verification suite will compile instrumentation tests without running connected-device tasks, and will run all JVM unit tests, lint, and the debug APK build.
