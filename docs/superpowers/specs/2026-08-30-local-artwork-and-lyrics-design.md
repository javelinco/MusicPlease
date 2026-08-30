# Local Artwork and Lyrics Design

## Goal

Show locally available album artwork throughout Music, Please! and show local
lyrics on Now Playing. Timed lyrics follow playback automatically. The feature
must preserve the app's no-network architecture, minimal permissions, fast
metadata scan, and responsive playback with tens of thousands of tracks.

## Privacy and source boundaries

The app does not add internet or network-state access and does not contact a
metadata or lyrics service. It does not request image, video, broad-files, or
other new permissions.

Supported local sources are:

- artwork embedded in an MP3;
- `cover.jpg`, `folder.jpg`, or `front.jpg` in a user-selected music folder;
- synchronized ID3 `SYLT` lyrics;
- unsynchronized ID3 `USLT` lyrics; and
- a case-insensitive, same-basename `.lrc` file beside an MP3 in a user-selected
  music folder.

Folder artwork and `.lrc` sidecars are available only inside folders the user
selected with Android's folder picker. Tracks found through the optional
device-wide audio permission use embedded MP3 data only; the app will not add a
broader permission to reach adjacent image or text files.

For lyrics, an editable same-basename `.lrc` file takes precedence. Without a
usable sidecar, synchronized embedded lyrics take precedence over plain
embedded lyrics. An `.lrc` file without usable timestamps is displayed as plain
lyrics.

## Loading and cache architecture

The implementation uses a hybrid pipeline:

1. The existing text-metadata scan remains the first priority and makes tracks
   searchable without waiting for images or lyrics.
2. Artwork and lyrics are processed afterward in bounded, cancellable batches.
   Background scans use low-priority work; dedicated scanning completes the
   derived-media pass at dedicated-scan priority.
3. Artwork or lyrics needed for the current track or a visible card can be
   loaded immediately instead of waiting for the trailing pass.

Decoded/downsampled artwork and parsed lyrics are stored in an app-private,
derived-data cache. Cache keys include stable track or album identity and file
fingerprint information so changed MP3s or sidecars invalidate old results.
Negative results are cached to avoid repeatedly reparsing files with no art or
lyrics. Cache reads, decoding, and parsing stay off the main thread and are
memory bounded.

Artwork is deduplicated by normalized album identity where possible. The cache
provides appropriately sized results for compact thumbnails and Now Playing;
the UI never decodes full embedded images repeatedly while scrolling.

Artwork, lyrics, and cache indexes are disposable library-derived data. They
are excluded from portable backups, which continue to contain user-owned data
only.

## Artwork presentation

- Now Playing uses available artwork as its primary large visual.
- The mini-player, recently played track cards, Library track cards, and album
  cards show compact artwork thumbnails.
- Playlist cards retain their playlist identity rather than pretending one
  album represents the playlist. Artist and genre group cards need not invent a
  representative album in this version.
- Missing, corrupt, or undecodable artwork falls back to the existing themed
  music icon without an error dialog.
- Available current-track artwork is supplied to Media3 metadata so Android
  system playback surfaces can display it.

## Lyrics presentation

When lyrics exist, Now Playing displays a compact lyrics card below the track
information. Timed lyrics show the active line prominently with nearby lines
for context and automatically advance with playback. Plain lyrics show a short
preview.

Tapping the card opens a full lyrics view:

- synchronized lyrics highlight and follow the active line;
- unsynchronized lyrics are normally scrollable;
- manual scrolling temporarily suspends automatic following so the view does
  not fight the user, with an explicit **Follow** action to return to the
  current line; and
- transport playback continues normally while the lyrics view is open.

No lyric editing, downloading, searching, translation, karaoke effects, or
line-tap seeking is added in this version.

## Failure and performance behavior

Malformed tags, oversized images, unsupported encodings, and unreadable
sidecars produce a missing-art/missing-lyrics result for that item and do not
fail the catalog scan. Parsing imposes size limits before allocating large
buffers. The derived-media pass yields to playback, library interaction,
playlist editing, and foreground UI work.

## Verification

Tests cover:

- ID3 `USLT` and `SYLT` parsing, `.lrc` timestamps, offsets, untimed fallback,
  malformed input, and deterministic source precedence;
- embedded and selected-folder artwork selection, album cache deduplication,
  invalidation, downsampling, and corrupt-image fallback;
- no sidecar/folder-art access for device-wide MediaStore sources;
- low-priority trailing work and immediate current/visible-item loading;
- Now Playing artwork and lyric-card states, timed active-line selection,
  manual-scroll follow suspension, and full-view reopening;
- thumbnails on the specified Home, Library, album, and mini-player surfaces;
- Media3 current-item artwork metadata; and
- unchanged manifest permissions, backup exclusions, scan responsiveness,
  navigation, playback, unit, lint, and packaging checks.
