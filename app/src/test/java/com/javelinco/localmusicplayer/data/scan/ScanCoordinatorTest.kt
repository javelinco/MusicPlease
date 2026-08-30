package com.javelinco.localmusicplayer.data.scan

import com.javelinco.localmusicplayer.core.model.SourceId
import com.javelinco.localmusicplayer.data.db.ScanErrorEntity
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.media.DerivedMediaIndexer
import com.javelinco.localmusicplayer.data.source.MusicSource
import com.javelinco.localmusicplayer.data.source.SafTreeSource
import com.javelinco.localmusicplayer.data.source.SourceEntry
import com.javelinco.localmusicplayer.data.source.SourceReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCoordinatorTest {
    private val source = SafTreeSource(SourceId("source"), "content://tree/music", "Music")

    @Test
    fun corruptAndNonMp3EntriesDoNotStopTheScan() = runTest {
        val catalog = RecordingCatalog()
        val coordinator = DefaultScanCoordinator(
            sourceProvider = { listOf(source) },
            readerFactory = { ListReader(entries()) },
            extractor = FakeExtractor(),
            catalog = catalog,
            batchSize = 2,
        )

        coordinator.run(ScanExecutionMode.DEDICATED)

        assertEquals(listOf("one", "two"), catalog.tracks.map { it.trackId.substringAfterLast(':') })
        assertEquals(1, catalog.errors.size)
        assertEquals(ScanPhase.COMPLETE, coordinator.progress.value?.phase)
        assertEquals(1L, coordinator.progress.value?.skipped)
        assertEquals(1L, coordinator.progress.value?.errors)
        assertTrue(catalog.reconciled)
    }

    @Test
    fun cancellationCheckpointsAndResumeDoesNotRepeatCompletedEntry() = runTest {
        val catalog = RecordingCatalog()
        lateinit var coordinator: DefaultScanCoordinator
        catalog.afterBatch = { coordinator.cancelAndCheckpoint() }
        coordinator = DefaultScanCoordinator(
            sourceProvider = { listOf(source) },
            readerFactory = { ListReader(entries().filter { it.displayName.endsWith(".mp3") }) },
            extractor = FakeExtractor(),
            catalog = catalog,
            batchSize = 1,
        )

        coordinator.run(ScanExecutionMode.BACKGROUND)
        val firstPass = catalog.tracks.map(TrackEntity::trackId)
        catalog.afterBatch = null
        coordinator.run(ScanExecutionMode.BACKGROUND)

        assertEquals(firstPass.distinct(), firstPass)
        assertEquals(2, catalog.tracks.map(TrackEntity::trackId).distinct().size)
        assertEquals(ScanPhase.COMPLETE, coordinator.progress.value?.phase)
    }

    @Test
    fun fullInventoryReconcilesDeletionDespiteOldCompletedCheckpoint() = runTest {
        val catalog = RecordingCatalog().apply {
            checkpoints[source.id.value] = "two"
            existing += track("one")
            existing += track("two")
        }
        val reader = ListReader(listOf(entry("one", "One.mp3")))
        val extractor = FakeExtractor()
        val coordinator = DefaultScanCoordinator(
            sourceProvider = { listOf(source) },
            readerFactory = { reader },
            extractor = extractor,
            catalog = catalog,
        )

        coordinator.run(ScanExecutionMode.BACKGROUND)

        assertEquals(listOf<String?>(null), reader.checkpoints)
        assertEquals(setOf("source:one"), catalog.reconciledTrackIds)
        assertEquals(1L, coordinator.progress.value?.removed)
        assertEquals(0, extractor.extractCount)
        assertNull(catalog.checkpoints[source.id.value])
    }

    @Test
    fun unchangedAvailableTrackReusesIndexedMetadata() = runTest {
        val catalog = RecordingCatalog().apply { existing += track("one") }
        val extractor = FakeExtractor()
        val coordinator = DefaultScanCoordinator(
            sourceProvider = { listOf(source) },
            readerFactory = { ListReader(listOf(entry("one", "One.mp3"))) },
            extractor = extractor,
            catalog = catalog,
        )

        coordinator.run(ScanExecutionMode.BACKGROUND)

        assertEquals(0, extractor.extractCount)
        assertEquals(1L, coordinator.progress.value?.found)
        assertEquals(0L, coordinator.progress.value?.processed)
    }

    @Test
    fun changedTrackIsReindexed() = runTest {
        val catalog = RecordingCatalog().apply {
            existing += track("one").copy(modifiedAtEpochMs = 0)
        }
        val extractor = FakeExtractor()
        val coordinator = DefaultScanCoordinator(
            sourceProvider = { listOf(source) },
            readerFactory = { ListReader(listOf(entry("one", "One.mp3"))) },
            extractor = extractor,
            catalog = catalog,
        )

        coordinator.run(ScanExecutionMode.BACKGROUND)

        assertEquals(1, extractor.extractCount)
        assertEquals(1L, coordinator.progress.value?.processed)
    }

    @Test
    fun scannedTrackPreservesSelectedFolderParentDocumentId() = runTest {
        val catalog = RecordingCatalog()
        val coordinator = DefaultScanCoordinator(
            sourceProvider = { listOf(source) },
            readerFactory = {
                ListReader(
                    listOf(
                        entry(
                            id = "one",
                            name = "One.mp3",
                            parentDocumentId = "music/album",
                        ),
                    ),
                )
            },
            extractor = FakeExtractor(),
            catalog = catalog,
        )

        coordinator.run(ScanExecutionMode.BACKGROUND)

        assertEquals("music/album", catalog.tracks.single().parentDocumentId)
    }

    @Test
    fun derivedPassRunsAfterCatalogReconciliationAndYieldsInBackground() = runTest {
        val events = mutableListOf<String>()
        val catalog = RecordingCatalog().apply { onReconcile = { events += "catalog" } }
        val indexer = object : DerivedMediaIndexer {
            var indexed = 0
            override suspend fun beginPass(source: MusicSource) { events += "begin" }
            override suspend fun index(source: MusicSource, entry: SourceEntry, track: TrackEntity) {
                indexed++
                events += "derived"
            }
        }
        val gate = object : ScanRuntimeGate {
            var windows = 0
            override suspend fun awaitBackgroundWindow() { windows++ }
        }
        val coordinator = DefaultScanCoordinator(
            sourceProvider = { listOf(source) },
            readerFactory = { ListReader(listOf(entry("one", "One.mp3"))) },
            extractor = FakeExtractor(),
            catalog = catalog,
            runtimeGate = gate,
            derivedMediaIndexer = indexer,
        )

        coordinator.run(ScanExecutionMode.BACKGROUND)

        assertTrue(events.indexOf("catalog") < events.indexOf("derived"))
        assertEquals(1, indexer.indexed)
        assertEquals(3, gate.windows) // enumeration, metadata, derived media
    }

    private fun entries() = listOf(
        entry("one", "One.mp3"),
        entry("skip", "Notes.txt", "text/plain"),
        entry("bad", "Bad.mp3"),
        entry("two", "Two.mp3"),
    )

    private fun entry(
        id: String,
        name: String,
        mime: String = "audio/mpeg",
        parentDocumentId: String? = null,
    ) = SourceEntry(
        sourceId = source.id,
        stableId = id,
        contentUri = "content://music/$id",
        displayName = name,
        mimeType = mime,
        sizeBytes = 1,
        modifiedAtEpochMs = 1,
        parentDocumentId = parentDocumentId,
    )

    private fun track(id: String) = TrackEntity(
        trackId = "source:$id",
        sourceId = source.id.value,
        contentUri = "content://music/$id",
        fileName = "${id.replaceFirstChar(Char::uppercase)}.mp3",
        title = id,
        artist = null,
        albumTitle = null,
        albumArtist = null,
        genre = null,
        normalizedTitle = id,
        normalizedArtist = "",
        normalizedAlbumTitle = "",
        normalizedAlbumArtist = "",
        normalizedGenre = "",
        discNumber = null,
        trackNumber = null,
        durationMs = 10,
        modifiedAtEpochMs = 1,
        sizeBytes = 1,
        available = true,
    )

    private class ListReader(private val entries: List<SourceEntry>) : SourceReader {
        val checkpoints = mutableListOf<String?>()

        override fun enumerate(source: MusicSource, checkpoint: String?): Flow<SourceEntry> = flow {
            checkpoints += checkpoint
            val start = checkpoint?.let { value -> entries.indexOfFirst { it.stableId == value } + 1 } ?: 0
            entries.drop(start.coerceAtLeast(0)).forEach { emit(it) }
        }
    }

    private class FakeExtractor : Mp3MetadataExtractor {
        var extractCount = 0

        override suspend fun extract(entry: SourceEntry): RawMp3Metadata {
            extractCount += 1
            if (entry.stableId == "bad") error("corrupt")
            return RawMp3Metadata(title = entry.displayName.removeSuffix(".mp3"), durationMs = 10)
        }

        override suspend fun extractArtwork(entry: SourceEntry): ByteArray? = null
    }

    private class RecordingCatalog : ScanCatalog {
        val tracks = mutableListOf<TrackEntity>()
        val existing = mutableListOf<TrackEntity>()
        val errors = mutableListOf<ScanErrorEntity>()
        val checkpoints = mutableMapOf<String, String?>()
        var reconciled = false
        var reconciledTrackIds = emptySet<String>()
        var afterBatch: (suspend () -> Unit)? = null
        var onReconcile: (() -> Unit)? = null

        override suspend fun checkpoint(sourceId: SourceId): String? = checkpoints[sourceId.value]

        override suspend fun existingTracks(sourceId: SourceId): List<TrackEntity> = existing

        override suspend fun clearCheckpoint(sourceId: SourceId) {
            checkpoints.remove(sourceId.value)
        }

        override suspend fun applyBatch(batch: CatalogScanBatch) {
            tracks += batch.tracks
            errors += batch.errors
            checkpoints[batch.sourceId.value] = batch.checkpoint
            afterBatch?.invoke()
        }

        override suspend fun reconcile(sourceId: SourceId, seenTrackIds: Set<String>): Int {
            onReconcile?.invoke()
            reconciled = true
            reconciledTrackIds = seenTrackIds
            return existing.count { it.available && it.trackId !in seenTrackIds }
        }
    }
}
