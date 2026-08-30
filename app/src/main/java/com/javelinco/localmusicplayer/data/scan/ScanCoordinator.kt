package com.javelinco.localmusicplayer.data.scan

import com.javelinco.localmusicplayer.data.db.ScanErrorEntity
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.source.MusicSource
import com.javelinco.localmusicplayer.data.source.SourceEntry
import com.javelinco.localmusicplayer.data.source.SourceReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

class DefaultScanCoordinator(
    private val sourceProvider: suspend () -> List<MusicSource>,
    private val readerFactory: (MusicSource) -> SourceReader,
    private val extractor: Mp3MetadataExtractor,
    private val catalog: ScanCatalog,
    private val runtimeGate: ScanRuntimeGate = AlwaysAvailableScanRuntimeGate,
    private val batchSize: Int = 100,
    private val clock: () -> Long = System::currentTimeMillis,
) : ScanCoordinator {
    private val runMutex = Mutex()
    private val mutableProgress = MutableStateFlow<ScanProgress?>(null)
    override val progress: StateFlow<ScanProgress?> = mutableProgress.asStateFlow()

    @Volatile
    private var cancellationRequested = false

    @Volatile
    private var resumeAfterCancellation = false

    override suspend fun run(mode: ScanExecutionMode) = runMutex.withLock {
        val resumeInterruptedScan = resumeAfterCancellation
        resumeAfterCancellation = false
        cancellationRequested = false
        if (mode == ScanExecutionMode.DEDICATED) runtimeGate.enterDedicated()
        try {
            mutableProgress.value = ScanProgress(ScanPhase.ENUMERATING)
            for (source in sourceProvider().filter(MusicSource::available)) {
                if (cancellationRequested) break
                scanSource(source, mode, resumeInterruptedScan)
            }
            if (!cancellationRequested) {
                mutableProgress.value = mutableProgress.value?.copy(
                    phase = ScanPhase.COMPLETE,
                    determinate = true,
                )
            }
        } finally {
            if (mode == ScanExecutionMode.DEDICATED) runtimeGate.leaveDedicated()
        }
    }

    override suspend fun cancelAndCheckpoint() {
        cancellationRequested = true
        resumeAfterCancellation = true
    }

    private suspend fun scanSource(
        source: MusicSource,
        mode: ScanExecutionMode,
        resumeInterruptedScan: Boolean,
    ) {
        val existingTracks = catalog.existingTracks(source.id).associateBy(TrackEntity::trackId)
        val checkpoint = if (resumeInterruptedScan) {
            catalog.checkpoint(source.id)
        } else {
            catalog.clearCheckpoint(source.id)
            null
        }
        val entries = mutableListOf<SourceEntry>()
        readerFactory(source).enumerate(source, null)
            .takeWhile { !cancellationRequested }
            .collect { entry ->
                cooperateWithRuntime(mode)
                entries += entry
                updateProgress { it.copy(phase = ScanPhase.ENUMERATING, found = it.found + 1) }
            }
        if (cancellationRequested) return

        val tracks = mutableListOf<TrackEntity>()
        val errors = mutableListOf<ScanErrorEntity>()
        val seenTrackIds = entries.asSequence()
            .filter(SourceEntry::isMp3)
            .mapTo(mutableSetOf()) { it.trackId }
        val checkpointIndex = checkpoint?.let { cursor ->
            entries.indexOfFirst { it.stableId == cursor }.takeIf { it >= 0 }
        }
        val processingStart = checkpointIndex?.plus(1) ?: 0
        var currentCheckpoint: String? = checkpoint
        var entriesSinceFlush = 0
        for (entry in entries.drop(processingStart)) {
            if (cancellationRequested) break
            cooperateWithRuntime(mode)
            updateProgress { it.copy(phase = ScanPhase.METADATA) }
            currentCheckpoint = entry.stableId
            entriesSinceFlush += 1
            if (!entry.isMp3()) {
                updateProgress { it.copy(skipped = it.skipped + 1) }
            } else if (existingTracks[entry.trackId]?.matches(entry) == true) {
                // The inventory still sees the file and its fingerprint is unchanged.
            } else {
                runCatching { extractor.extract(entry) }
                    .onSuccess { raw ->
                        val track = MetadataNormalizer.normalize(raw, entry).toTrack(entry)
                        tracks += track
                        seenTrackIds += track.trackId
                        updateProgress { it.copy(processed = it.processed + 1) }
                    }
                    .onFailure { error ->
                        errors += ScanErrorEntity(
                            sourceId = source.id.value,
                            contentUri = entry.contentUri,
                            message = error.message ?: error::class.java.simpleName,
                            occurredAtEpochMs = clock(),
                        )
                        updateProgress { it.copy(errors = it.errors + 1) }
                    }
            }
            if (entriesSinceFlush >= batchSize || cancellationRequested) {
                flush(source, tracks, errors, currentCheckpoint)
                entriesSinceFlush = 0
            }
        }
        flush(source, tracks, errors, currentCheckpoint)
        if (!cancellationRequested) {
            updateProgress { it.copy(phase = ScanPhase.RECONCILING) }
            val removed = catalog.reconcile(source.id, seenTrackIds)
            updateProgress { it.copy(removed = it.removed + removed) }
            catalog.clearCheckpoint(source.id)
        }
    }

    private suspend fun cooperateWithRuntime(mode: ScanExecutionMode) {
        if (mode == ScanExecutionMode.BACKGROUND) runtimeGate.awaitBackgroundWindow()
        yield()
    }

    private suspend fun flush(
        source: MusicSource,
        tracks: MutableList<TrackEntity>,
        errors: MutableList<ScanErrorEntity>,
        checkpoint: String?,
    ) {
        if (tracks.isEmpty() && errors.isEmpty() && checkpoint == null) return
        updateProgress { it.copy(phase = ScanPhase.INDEXING) }
        catalog.applyBatch(CatalogScanBatch(source.id, tracks.toList(), errors.toList(), checkpoint))
        tracks.clear()
        errors.clear()
    }

    private fun updateProgress(transform: (ScanProgress) -> ScanProgress) {
        mutableProgress.value = transform(mutableProgress.value ?: ScanProgress(ScanPhase.ENUMERATING))
    }
}

private fun SourceEntry.isMp3() =
    mimeType == "audio/mpeg" || displayName.endsWith(".mp3", ignoreCase = true)

private val SourceEntry.trackId: String
    get() = "${sourceId.value}:$stableId"

private fun TrackEntity.matches(entry: SourceEntry) =
    available &&
        contentUri == entry.contentUri &&
        fileName == entry.displayName &&
        sizeBytes == (entry.sizeBytes ?: 0) &&
        modifiedAtEpochMs == (entry.modifiedAtEpochMs ?: 0)

private fun NormalizedTrackMetadata.toTrack(entry: SourceEntry) = TrackEntity(
    trackId = "${entry.sourceId.value}:${entry.stableId}",
    sourceId = entry.sourceId.value,
    contentUri = entry.contentUri,
    fileName = entry.displayName,
    title = title,
    artist = artist,
    albumTitle = albumTitle,
    albumArtist = albumArtist,
    genre = genre,
    normalizedTitle = normalizedTitle,
    normalizedArtist = normalizedArtist,
    normalizedAlbumTitle = normalizedAlbumTitle,
    normalizedAlbumArtist = normalizedAlbumArtist,
    normalizedGenre = normalizedGenre,
    discNumber = discNumber,
    trackNumber = trackNumber,
    durationMs = durationMs,
    modifiedAtEpochMs = entry.modifiedAtEpochMs ?: 0,
    sizeBytes = entry.sizeBytes ?: 0,
    available = true,
    parentDocumentId = entry.parentDocumentId,
)
