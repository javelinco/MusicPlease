# More Option Cards Design

## Goal

Make the More screen read as a small set of useful actions rather than a loose
list of labels. Remove promotional privacy copy and make each destination's
purpose and entry action immediately clear.

## Scope

This change redesigns only the More destination. It preserves the existing
Music folders and scanning, Backup and restore, and Appearance destinations,
their navigation behavior, the persistent app header, mini-player, and bottom
navigation. It introduces a reusable option-card composable as the preferred
pattern for future menu-style screens, but it does not refactor unrelated
screens in this change.

## Layout

The screen uses a vertically scrolling column with consistent horizontal and
bottom padding. Three separate full-width cards are arranged with visible space
between them. Each card contains:

- a rounded, tinted icon tile;
- a prominent option title;
- a concise description of the tasks available at that destination; and
- an explicit trailing or lower action button, arranged responsively so larger
  text does not collide with the description.

The action labels are:

- **Manage** for Music folders and scanning;
- **Open** for Backup and restore; and
- **Customize** for Appearance.

The cards use Material theme colors and therefore work in light and dark mode.
The content scrolls when screen height or font scaling requires it.

## Copy

- **Music folders and scanning** — Choose folders, find device music, rescan,
  and manage tracks removed from the index.
- **Backup and restore** — Create USB-visible backups or restore playlists and
  app settings.
- **Appearance** — Choose light, dark, or system colors and reduce motion.

The `Offline only · MP3 · no telemetry · no internet permission` line is
removed. Privacy remains an implementation property and permissions contract,
not promotional copy on this utility screen.

## Interaction and accessibility

Each action button navigates to the same destination as its current row. The
button has a visible text label and the card's icon remains decorative. Cards
have distinct semantic/test tags so automated tests can verify separation.
Minimum touch targets and normal Material focus behavior are preserved.

## Verification

Compose UI coverage will verify that:

- all three option cards are separate and visible;
- each title, description, and action label is present;
- the privacy slogan is absent; and
- each action button opens its existing destination.

Existing navigation, system-Back, unit, lint, packaging, and manifest-permission
checks remain required. No new permission, dependency, persistence, playback,
backup, or scanning behavior is introduced.
