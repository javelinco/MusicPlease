# Local Artwork and Lyrics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display strictly local album artwork throughout Music, Please! and display local plain or synchronized lyrics on Now Playing with automatic following.

**Architecture:** Preserve the metadata-first scanner and add a trailing derived-media indexer. A file-backed app-cache repository resolves embedded MP3 data and, only for user-selected SAF folder sources, adjacent artwork and `.lrc` files; UI observes only requested track states, while the scanner fills the disposable cache in bounded background work. Playback starts immediately and current-item artwork is attached to Media3 asynchronously when it becomes available.

**Tech Stack:** Kotlin 2.3, Android 13+, Jetpack Compose Material 3, Room, Media3, coroutines, Kotlin serialization, Android `MediaMetadataRetriever`, Storage Access Framework.

## Global Constraints

- Add no internet, network-state, broad-file, image, video, or other permission.
- Read folder artwork and `.lrc` sidecars only within folders selected through Android's folder picker.
- Device-wide MediaStore and legacy single-document sources may use embedded MP3 artwork and lyrics only.
- Metadata becomes searchable before artwork or lyrics work begins.
- Derived work is cancellable, memory-bounded, off the main thread, and yields to playback and foreground UI work.
- Derived artwork, lyrics, negative results, and indexes live under the app cache directory and are excluded from portable backups.
- Runtime playback must not wait for artwork or lyrics.
- Do not add lyric editing, downloading, online search, translation, karaoke effects, or line-tap seeking.
- Do not create repositories or worktrees in OneDrive-synced directories.

---

### Task 1: Preserve selected-folder parent identity in the scan index

**Files:**
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/source/SourceEntry.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/source/SafTreeReader.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/db/Entities.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/db/DatabaseMigrations.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/db/LocalMusicDatabase.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/AppContainer.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/scan/ScanCoordinator.kt`
- Modify: `app/schemas/com.javelinco.localmusicplayer.data.db.LocalMusicDatabase/4.json`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/scan/ScanCoordinatorTest.kt`
- Test: `app/src/androidTest/java/com/javelinco/localmusicplayer/data/db/DatabaseMigrationTest.kt`

**Interfaces:**
- Produces: `SourceEntry.parentDocumentId: String?` and `TrackEntity.parentDocumentId: String?`.
- Produces: `DatabaseMigrations.MIGRATION_3_4` adding nullable `parentDocumentId` to `tracks`.
- Invariant: only `SafTreeReader` populates the value; MediaStore and single-document readers leave it null.

- [ ] **Step 1: Write failing scan and migration tests**

```kotlin
@Test
fun scannedTrackPreservesSelectedFolderParentDocumentId() = runTest {
    val entry = sourceEntry(stableId = "music/song.mp3", parentDocumentId = "music")
    val catalog = RecordingCatalog()
    coordinator(entries = listOf(entry), catalog = catalog).run(ScanExecutionMode.BACKGROUND)
    assertEquals("music", catalog.tracks.single().parentDocumentId)
}

@Test
fun migration3To4AddsNullableParentDocumentId() {
    helper.createDatabase(DB_NAME, 3).close()
    helper.runMigrationsAndValidate(DB_NAME, 4, true, DatabaseMigrations.MIGRATION_3_4).close()
}
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:
`./gradlew testDebugUnitTest --tests '*ScanCoordinatorTest*parentDocumentId*' compileDebugAndroidTestKotlin`

Expected: compilation fails because the two models and migration do not exist.

- [ ] **Step 3: Add the nullable fields and migration**

```kotlin
data class SourceEntry(
    // existing fields
    val parentDocumentId: String? = null,
)

data class TrackEntity(
    // existing fields
    val parentDocumentId: String? = null,
)

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN parentDocumentId TEXT")
    }
}
```

Set `parentDocumentId = parentId` in `SafTreeReader` and copy it in `NormalizedTrackMetadata.toTrack`.

- [ ] **Step 4: Generate the Room schema and run the focused tests**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin`

Expected: PASS; schema version 4 is generated and migration compiles.

- [ ] **Step 5: Commit**

```text
git add app/src/main app/src/test app/src/androidTest app/schemas
git commit -m "feat: retain selected-folder media context"
```

### Task 2: Parse local synchronized and plain lyrics safely

**Files:**
- Create: `app/src/main/java/com/javelinco/localmusicplayer/data/media/LyricsModels.kt`
- Create: `app/src/main/java/com/javelinco/localmusicplayer/data/media/LrcParser.kt`
- Create: `app/src/main/java/com/javelinco/localmusicplayer/data/media/Id3LyricsParser.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/media/LrcParserTest.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/media/Id3LyricsParserTest.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/media/LyricsTimelineTest.kt`

**Interfaces:**
- Produces: `@Serializable data class LyricLine(val timeMs: Long?, val text: String)`.
- Produces: `@Serializable data class LyricsDocument(val lines: List<LyricLine>, val synchronized: Boolean)`.
- Produces: `fun LyricsDocument.activeLineIndex(positionMs: Long): Int?`.
- Produces: `object LrcParser { fun parse(bytes: ByteArray): LyricsDocument? }`.
- Produces: `object Id3LyricsParser { fun parse(bytes: ByteArray): EmbeddedLyrics }` where `EmbeddedLyrics(synchronized: LyricsDocument?, plain: LyricsDocument?)`.

- [ ] **Step 1: Write LRC and timeline tests first**

```kotlin
@Test
fun parsesMultipleTimestampsOffsetAndCentiseconds() {
    val lyrics = LrcParser.parse("[offset:+250]\n[00:01.50][00:03.00]Hello".encodeToByteArray())!!
    assertEquals(listOf(1_750L, 3_250L), lyrics.lines.map { it.timeMs })
    assertEquals(listOf("Hello", "Hello"), lyrics.lines.map { it.text })
    assertEquals(0, lyrics.activeLineIndex(2_000))
}

@Test
fun lrcWithoutUsableTimestampsFallsBackToPlainLyrics() {
    val lyrics = LrcParser.parse("First line\nSecond line".encodeToByteArray())!!
    assertFalse(lyrics.synchronized)
    assertEquals(listOf("First line", "Second line"), lyrics.lines.map { it.text })
}
```

- [ ] **Step 2: Run the LRC tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*LrcParserTest' --tests '*LyricsTimelineTest'`

Expected: compilation fails because the lyric models and parser do not exist.

- [ ] **Step 3: Implement bounded LRC parsing and active-line selection**

Accept UTF-8 with an optional BOM, a maximum input of 2 MiB, timestamp forms `mm:ss`, `mm:ss.x`, `mm:ss.xx`, and `mm:ss.xxx`, multiple timestamps per line, and one signed millisecond `[offset:]`. Sort timed lines stably by timestamp. Ignore metadata tags other than offset. Trim only structural whitespace and return null for empty content.

- [ ] **Step 4: Run LRC tests and verify GREEN**

Run: `./gradlew testDebugUnitTest --tests '*LrcParserTest' --tests '*LyricsTimelineTest'`

Expected: PASS.

- [ ] **Step 5: Write ID3v2.3/v2.4 USLT and SYLT tests first**

```kotlin
@Test
fun parsesUtf8UsltAndSyltAndPrefersFirstNonemptyFrameOfEachKind() {
    val parsed = Id3LyricsParser.parse(id3Tag(uslt("Plain words"), sylt(1_000 to "One", 2_000 to "Two")))
    assertEquals("Plain words", parsed.plain!!.lines.single().text)
    assertEquals(listOf(1_000L, 2_000L), parsed.synchronized!!.lines.map { it.timeMs })
}

@Test
fun malformedOrOversizedId3ReturnsNoLyrics() {
    assertEquals(EmbeddedLyrics(), Id3LyricsParser.parse(byteArrayOf(1, 2, 3)))
}
```

- [ ] **Step 6: Run ID3 tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*Id3LyricsParserTest'`

Expected: compilation fails because `Id3LyricsParser` and `EmbeddedLyrics` do not exist.

- [ ] **Step 7: Implement the bounded ID3 parser**

Read only the ID3v2 tag at the beginning of the stream, cap the tag at 4 MiB, support v2.3 big-endian frame sizes and v2.4 synchsafe sizes, skip extended headers, remove tag-level unsynchronization, decode ISO-8859-1/UTF-16/UTF-16BE/UTF-8 strings, and parse `USLT` and millisecond-format `SYLT`. Unsupported MPEG-frame timestamps, compressed/encrypted frames, malformed lengths, and decoding errors return no result instead of throwing.

- [ ] **Step 8: Run all lyric parser tests and commit**

Run: `./gradlew testDebugUnitTest --tests 'com.javelinco.localmusicplayer.data.media.*Lyrics*' --tests '*LrcParserTest'`

Expected: PASS.

```text
git add app/src/main/java/com/javelinco/localmusicplayer/data/media app/src/test/java/com/javelinco/localmusicplayer/data/media
git commit -m "feat: parse local synchronized lyrics"
```

### Task 3: Resolve embedded and selected-folder media with deterministic precedence

**Files:**
- Create: `app/src/main/java/com/javelinco/localmusicplayer/data/media/LocalCompanionFiles.kt`
- Create: `app/src/main/java/com/javelinco/localmusicplayer/data/media/LocalMediaResolver.kt`
- Create: `app/src/main/java/com/javelinco/localmusicplayer/data/media/ArtworkTranscoder.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/scan/Mp3MetadataExtractor.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/media/LocalMediaResolverTest.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/media/ArtworkTranscoderTest.kt`

**Interfaces:**
- Produces: `data class CompanionFile(val name: String, val uri: String, val sizeBytes: Long, val modifiedAtEpochMs: Long)`.
- Produces: `interface CompanionFileReader { suspend fun list(source: SafTreeSource, parentDocumentId: String): List<CompanionFile>; suspend fun read(file: CompanionFile, maxBytes: Int): ByteArray? }`.
- Produces: `data class ResolvedTrackMedia(val artwork: ByteArray?, val lyrics: LyricsDocument?, val companionFingerprint: String)`.
- Produces: `LocalMediaResolver.resolve(source, entry, track): ResolvedTrackMedia`.
- Produces: `ArtworkTranscoder.downsample(bytes, maxDimension = 1024, maxInputBytes = 12 * 1024 * 1024): ByteArray?`.

- [ ] **Step 1: Write precedence and permission-boundary tests first**

```kotlin
@Test
fun selectedFolderUsesSidecarThenSyltThenUsltAndFolderArtAfterEmbeddedArt() = runTest {
    val result = resolver(
        companions = listOf(file("SONG.LRC", "[00:01.00]Sidecar"), file("cover.jpg", JPEG)),
        embedded = embedded(art = EMBEDDED_JPEG, sylt = "Embedded timed", uslt = "Embedded plain"),
    ).resolve(safTreeSource, entry(parentDocumentId = "album"), track())
    assertEquals("Sidecar", result.lyrics!!.lines.single().text)
    assertArrayEquals(EMBEDDED_JPEG, result.artwork)
}

@Test
fun mediaStoreNeverRequestsAdjacentFiles() = runTest {
    val companions = RecordingCompanionReader()
    resolver(companionReader = companions).resolve(mediaStoreSource, entry(parentDocumentId = null), track())
    assertEquals(0, companions.listCalls)
}
```

- [ ] **Step 2: Run resolver tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*LocalMediaResolverTest'`

Expected: compilation fails because resolver interfaces do not exist.

- [ ] **Step 3: Implement SAF sibling enumeration and source precedence**

Query the selected parent directory once per resolver pass and match names case-insensitively. Match `<mp3 basename>.lrc`; folder-art priority is `cover.jpg`, then `folder.jpg`, then `front.jpg`. Use embedded artwork before folder artwork. Use sidecar lyrics before embedded SYLT before embedded USLT. Compute the companion fingerprint from matched file names, sizes, and modified timestamps. Never call the companion reader for non-`SafTreeSource` sources or a null parent.

- [ ] **Step 4: Write and run artwork transcoder tests RED**

```kotlin
@Test
fun downsamplesLargeArtworkAndRejectsCorruptOrOversizedInput() {
    val output = ArtworkTranscoder.downsample(largePng(2400, 1800), maxDimension = 1024)!!
    assertTrue(decodedWidth(output) <= 1024)
    assertNull(ArtworkTranscoder.downsample(byteArrayOf(1, 2, 3)))
}
```

Run: `./gradlew testDebugUnitTest --tests '*ArtworkTranscoderTest'`

Expected: compilation fails because `ArtworkTranscoder` does not exist.

- [ ] **Step 5: Implement off-main bounded decoding and run tests GREEN**

Use `BitmapFactory` bounds decoding, a power-of-two sample size, a maximum decoded dimension of 1024, and WebP lossy output at quality 86. Return null for invalid dimensions, allocation failures, oversized input, or decode failures.

Run: `./gradlew testDebugUnitTest --tests 'com.javelinco.localmusicplayer.data.media.*Test'`

Expected: PASS.

- [ ] **Step 6: Commit**

```text
git add app/src/main/java/com/javelinco/localmusicplayer/data/media app/src/main/java/com/javelinco/localmusicplayer/data/scan/Mp3MetadataExtractor.kt app/src/test/java/com/javelinco/localmusicplayer/data/media
git commit -m "feat: resolve strictly local artwork and lyrics"
```

### Task 4: Add the disposable derived-media cache and observable repository

**Files:**
- Create: `app/src/main/java/com/javelinco/localmusicplayer/data/media/DerivedMediaCache.kt`
- Create: `app/src/main/java/com/javelinco/localmusicplayer/data/media/DerivedMediaRepository.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/AppContainer.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/library/LibraryViewModel.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/media/DerivedMediaCacheTest.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/media/DerivedMediaRepositoryTest.kt`

**Interfaces:**
- Produces: `@Serializable data class CachedTrackMedia(val trackFingerprint: String, val companionFingerprint: String, val artworkKey: String?, val lyrics: LyricsDocument?)`.
- Produces: `data class TrackMediaState(val loading: Boolean = false, val artworkPath: String? = null, val lyrics: LyricsDocument? = null)`.
- Produces: `interface DerivedMediaIndexer { suspend fun beginPass(source: MusicSource); suspend fun index(source: MusicSource, entry: SourceEntry, track: TrackEntity) }`.
- Produces: `DerivedMediaRepository.states: StateFlow<Map<String, TrackMediaState>>`.
- Produces: `suspend fun DerivedMediaRepository.ensure(track: TrackEntity, refreshCompanions: Boolean = false): TrackMediaState`.
- Produces: `fun LibraryViewModel.requestMedia(track: TrackEntity)`.

- [ ] **Step 1: Write atomic cache, negative-cache, deduplication, and invalidation tests first**

```kotlin
@Test
fun cacheDeduplicatesArtworkByAlbumAndKeepsNegativeResults() {
    cache.writeArtwork("album-key", WEBP)
    cache.writeRecord("one", record(artworkKey = "album-key", lyrics = null))
    cache.writeRecord("two", record(artworkKey = "album-key", lyrics = null))
    assertEquals(cache.artworkPath("album-key"), cache.read("one")!!.artworkPath)
    assertEquals(1, cache.artworkFiles().size)
}

@Test
fun changedTrackOrCompanionFingerprintReResolvesMedia() = runTest {
    repository.ensure(track(modified = 1), refreshCompanions = true)
    repository.ensure(track(modified = 2), refreshCompanions = true)
    assertEquals(2, resolver.calls)
}
```

- [ ] **Step 2: Run cache/repository tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*DerivedMediaCacheTest' --tests '*DerivedMediaRepositoryTest'`

Expected: compilation fails because cache and repository do not exist.

- [ ] **Step 3: Implement atomic JSON records and album artwork files**

Use SHA-256 filenames under `cacheDir/derived-media/records` and `cacheDir/derived-media/artwork`. Write through a same-directory `.tmp` file and atomic rename fallback. A valid record with null artwork and null lyrics is a negative cache hit. Do not enumerate or include this directory in portable backup code.

- [ ] **Step 4: Implement repository loading and observable requested-track states**

Resolve on `Dispatchers.IO`, guard each track with a keyed mutex, reuse valid records, write artwork once by normalized album identity plus artwork fingerprint, and publish only requested/indexed results to the state flow. `beginPass` clears per-directory sibling memoization but not persistent cache. Resolver failures publish an empty non-loading state and do not throw into the catalog scan.

- [ ] **Step 5: Run focused and backup-exclusion tests**

Run: `./gradlew testDebugUnitTest --tests '*DerivedMedia*' --tests '*BackupCodecTest'`

Expected: PASS and portable backups still contain only manifest and user data.

- [ ] **Step 6: Commit**

```text
git add app/src/main/java/com/javelinco/localmusicplayer/data/media app/src/main/java/com/javelinco/localmusicplayer/AppContainer.kt app/src/main/java/com/javelinco/localmusicplayer/library/LibraryViewModel.kt app/src/test
git commit -m "feat: cache derived local media"
```

### Task 5: Add the cancellable trailing derived-media scan pass

**Files:**
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/scan/ScanCoordinator.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/data/scan/ScanModels.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/AppContainer.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/data/scan/ScanCoordinatorTest.kt`

**Interfaces:**
- Consumes: `DerivedMediaIndexer.beginPass` and `DerivedMediaIndexer.index` from Task 4.
- Invariant: catalog batch application and reconciliation finish before `ScanPhase.ARTWORK` begins.
- Invariant: background mode calls `runtimeGate.awaitBackgroundWindow()` for every derived item; dedicated mode does not.

- [ ] **Step 1: Write trailing-pass ordering and cancellation tests first**

```kotlin
@Test
fun derivedPassStartsAfterSearchableCatalogAndYieldsInBackground() = runTest {
    coordinator(indexer = recordingIndexer, runtimeGate = gate).run(ScanExecutionMode.BACKGROUND)
    assertTrue(events.indexOf("catalog") < events.indexOf("derived"))
    assertEquals(mp3Entries.size * 2, gate.backgroundWindows) // metadata plus derived pass
}

@Test
fun cancellationDuringDerivedPassLeavesCatalogUsableAndCheckpointsExit() = runTest {
    recordingIndexer.onIndex = coordinator::cancelAndCheckpoint
    coordinator.run(ScanExecutionMode.BACKGROUND)
    assertTrue(catalog.appliedTracks.isNotEmpty())
    assertNotEquals(ScanPhase.COMPLETE, coordinator.progress.value!!.phase)
}
```

- [ ] **Step 2: Run scan tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*ScanCoordinatorTest'`

Expected: ordering/count assertions fail because no derived pass runs.

- [ ] **Step 3: Implement the second pass**

Retain a `trackId -> TrackEntity` map while scanning. After reconciliation, call `indexer.beginPass(source)`, set phase `ARTWORK`, iterate MP3 entries, cooperate with the runtime gate, and call `indexer.index` inside `runCatching`. Stop promptly when cancellation is requested. Do not add derived failures to catalog scan errors.

- [ ] **Step 4: Run scan and library tests GREEN**

Run: `./gradlew testDebugUnitTest --tests '*ScanCoordinatorTest' --tests '*LibraryViewModelTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add app/src/main/java/com/javelinco/localmusicplayer/data/scan app/src/main/java/com/javelinco/localmusicplayer/AppContainer.kt app/src/test/java/com/javelinco/localmusicplayer/data/scan
git commit -m "feat: trail scans with derived media indexing"
```

### Task 6: Supply current-track artwork to Media3 without delaying playback

**Files:**
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/playback/service/MediaItemMapper.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/playback/service/PlaybackViewModel.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/MainActivity.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/playback/service/MediaItemMapperTest.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/playback/service/PlaybackArtworkCoordinatorTest.kt`

**Interfaces:**
- Produces: `MediaItemMapper.toMediaItem(track: TrackEntity, artworkData: ByteArray? = null)`.
- Produces: pure `PlaybackArtworkCoordinator` that suppresses duplicate requests and validates that an async result still belongs to the current media ID before replacement.
- Consumes: `DerivedMediaRepository.ensure` and `DerivedMediaCache.readArtwork`.

- [ ] **Step 1: Write mapping and stale-result tests first**

```kotlin
@Test
fun mapperAddsFrontCoverArtworkDataWhenAvailable() {
    val item = MediaItemMapper.toMediaItem(track(), artworkData = WEBP)
    assertArrayEquals(WEBP, item.mediaMetadata.artworkData)
}

@Test
fun lateArtworkForPreviousTrackIsNotAppliedToCurrentItem() {
    val coordinator = PlaybackArtworkCoordinator()
    coordinator.requested("one")
    assertFalse(coordinator.shouldApply(resultFor = "one", currentMediaId = "two"))
}
```

- [ ] **Step 2: Run playback artwork tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests '*MediaItemMapperTest' --tests '*PlaybackArtworkCoordinatorTest'`

Expected: compilation fails because optional artwork and coordinator do not exist.

- [ ] **Step 3: Implement immediate playback plus asynchronous current-item enrichment**

Build and start the queue immediately with no disk read. On current-media transitions, request derived media in `viewModelScope`; if the result still matches the current ID, read the small cached artwork off-main and replace only that media item with identical ID/URI and enriched metadata. Never seek, re-prepare, or change play/pause state during enrichment.

- [ ] **Step 4: Run playback tests GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests 'com.javelinco.localmusicplayer.playback.**'`

Expected: PASS.

```text
git add app/src/main/java/com/javelinco/localmusicplayer/playback app/src/main/java/com/javelinco/localmusicplayer/MainActivity.kt app/src/test/java/com/javelinco/localmusicplayer/playback
git commit -m "feat: publish local artwork to media controls"
```

### Task 7: Display artwork on Now Playing, mini-player, Home, Library, and albums

**Files:**
- Create: `app/src/main/java/com/javelinco/localmusicplayer/ui/media/LocalArtwork.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/player/NowPlayingScreen.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/player/MiniPlayer.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/home/RecentItemCards.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/library/AlbumListScreen.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/library/MetadataDetailScreen.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/AppNavigation.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/MainActivity.kt`
- Test: `app/src/androidTest/java/com/javelinco/localmusicplayer/ui/ArtworkUiTest.kt`

**Interfaces:**
- Produces: `LocalArtwork(track, mediaState, onRequest, modifier, contentDescription)`.
- Consumes: `Map<String, TrackMediaState>` and `(TrackEntity) -> Unit` passed from MainActivity through navigation.
- Invariant: missing/corrupt art shows the existing themed music icon with no dialog.

- [ ] **Step 1: Write Compose tests for each required surface first**

```kotlin
@Test
fun availableArtworkAppearsOnPlayerRecentTrackLibraryTrackAlbumAndMiniPlayer() {
    compose.setContent { ArtworkSurfaceHarness(artworkPath = fixtureArtwork.absolutePath) }
    compose.onAllNodesWithTag("local-artwork").assertCountEquals(5)
}

@Test
fun missingArtworkUsesThemedFallback() {
    compose.setContent { LocalArtwork(track(), TrackMediaState(), {}, Modifier, "Artwork") }
    compose.onNodeWithContentDescription("Artwork unavailable").assertExists()
}
```

- [ ] **Step 2: Compile UI tests and verify RED**

Run: `./gradlew compileDebugAndroidTestKotlin`

Expected: compilation fails because `LocalArtwork` and media-state parameters do not exist.

- [ ] **Step 3: Implement off-main image loading and card integration**

Decode the cached WebP file on `Dispatchers.IO`, render with `ContentScale.Crop`, rounded clipping, and a `local-artwork` test tag. Request media with `LaunchedEffect(track.trackId, track.modifiedAtEpochMs, track.sizeBytes)`. Use a large square in Now Playing, 48dp thumbnails in mini-player/Home/track cards, and the first track in normalized album order as the album representative. Keep playlist, artist, and genre cards unchanged.

- [ ] **Step 4: Compile UI tests and run unit tests GREEN**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add app/src/main/java/com/javelinco/localmusicplayer/ui app/src/main/java/com/javelinco/localmusicplayer/MainActivity.kt app/src/androidTest/java/com/javelinco/localmusicplayer/ui/ArtworkUiTest.kt
git commit -m "feat: show local artwork throughout the player"
```

### Task 8: Add compact and full followed-lyrics UI

**Files:**
- Create: `app/src/main/java/com/javelinco/localmusicplayer/ui/player/LyricsCard.kt`
- Create: `app/src/main/java/com/javelinco/localmusicplayer/ui/player/LyricsScreen.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/player/NowPlayingScreen.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/NavigationHistory.kt`
- Modify: `app/src/main/java/com/javelinco/localmusicplayer/ui/navigation/AppNavigation.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/ui/player/LyricsPresentationTest.kt`
- Test: `app/src/androidTest/java/com/javelinco/localmusicplayer/ui/LyricsUiTest.kt`
- Test: `app/src/test/java/com/javelinco/localmusicplayer/ui/navigation/NavigationHistoryTest.kt`

**Interfaces:**
- Produces: `fun lyricsWindow(document: LyricsDocument, positionMs: Long, radius: Int = 1): List<PresentedLyricLine>`.
- Produces: `LyricsCard(document, positionMs, onOpen)`.
- Produces: `LyricsScreen(document, positionMs, reducedMotion)` with internal follow/manual-scroll state.
- Adds: `Destination.LYRICS` with header title `Lyrics` and normal navigation-history behavior.

- [ ] **Step 1: Write presentation, navigation, and UI tests first**

```kotlin
@Test
fun synchronizedWindowCentersCurrentLineWhilePlainPreviewUsesBeginning() {
    assertEquals(listOf("Before", "Current", "After"), lyricsWindow(timedLyrics(), 2_100).map { it.text })
    assertEquals(listOf("First", "Second", "Third"), lyricsWindow(plainLyrics(), 99_000).map { it.text })
}

@Test
fun lyricsDestinationReturnsToNowPlaying() {
    val history = NavigationHistory(Destination.NOW_PLAYING).navigateTo(Destination.LYRICS)
    assertEquals(Destination.NOW_PLAYING, history.goBack().current)
}

@Test
fun compactLyricsOpensFullViewAndFollowActionRestoresAutoFollow() {
    compose.setContent { LyricsHarness(timedLyrics()) }
    compose.onNodeWithTag("lyrics-card").performClick()
    compose.onNodeWithTag("lyrics-screen").assertExists()
    compose.onNodeWithTag("lyrics-list").performTouchInput { swipeUp() }
    compose.onNodeWithText("Follow").assertExists().performClick()
}
```

- [ ] **Step 2: Run pure tests and compile UI tests RED**

Run: `./gradlew testDebugUnitTest --tests '*LyricsPresentationTest' --tests '*NavigationHistoryTest' compileDebugAndroidTestKotlin`

Expected: compilation fails because lyrics presentation and destination do not exist.

- [ ] **Step 3: Implement compact lyrics card and full screen**

Show timed current line prominently with one adjacent line on each side; show the first three nonblank lines for plain lyrics. In the full screen, render timed lyrics in a `LazyColumn`, highlight the active line, and scroll to it when follow is enabled. Detect user-initiated list scrolling, disable following, and show a persistent `Follow` button; tapping it restores following and scrolls to the active line. Plain lyrics remain normally scrollable and do not show Follow. Respect reduced motion by using immediate rather than animated scroll.

- [ ] **Step 4: Wire current track media into Now Playing and navigation**

Only show `LyricsCard` when the current track has a nonempty document. Opening it navigates to `Destination.LYRICS`; playback controls and Media3 service continue independently. If the track changes to one without lyrics while the full screen is open, show `No local lyrics for this track` and retain normal back navigation.

- [ ] **Step 5: Run unit and compiled UI tests GREEN and commit**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin`

Expected: PASS.

```text
git add app/src/main/java/com/javelinco/localmusicplayer/ui app/src/test app/src/androidTest
git commit -m "feat: follow synchronized local lyrics"
```

### Task 9: Verify privacy, performance, packaging, and the connected Samsung build

**Files:**
- Modify only if verification exposes a tested defect in feature files from Tasks 1-8.

**Interfaces:**
- Verifies: no manifest permission changes, cache excluded from backup, metadata-first scan ordering, no connected-test data wipe, and a working debug APK.

- [ ] **Step 1: Review the final diff and permission surface**

Run:

```text
git diff --check
git diff main...HEAD -- app/src/main/AndroidManifest.xml
rg -n "INTERNET|ACCESS_NETWORK_STATE|MANAGE_EXTERNAL_STORAGE|READ_MEDIA_IMAGES|READ_MEDIA_VIDEO" app/src/main
```

Expected: no whitespace errors, no new permission declarations, and no network or broad-media permission.

- [ ] **Step 2: Run the complete local verification suite**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin`

Expected: BUILD SUCCESSFUL. Do not run connected instrumentation tests because this project's previous connected test flow could wipe phone data.

- [ ] **Step 3: Create and validate an in-app backup before installation**

Use the app's `Backup & restore` screen on the connected Samsung phone. Confirm the new final `.zip` exists in `Internal storage / Backups` and passes `unzip -t` before installation.

- [ ] **Step 4: Install with data preservation and verify on-device behavior**

Record `run-as ... du -sk databases files shared_prefs`, install with `adb install -r`, and verify the sizes remain present. Launch the app and manually verify: existing library loads, artwork appears for a known embedded-art MP3, a known local lyric source appears, timed lyrics follow playback when present, the mini-player/cards retain layout, backup creation still succeeds, and AndroidRuntime has no crash.

- [ ] **Step 5: Commit any verification-only test fixes, fast-forward main, and push**

```text
git status --short
git log --oneline --decorate -12
git push origin main
```

Expected: clean published main branch containing all feature commits.
