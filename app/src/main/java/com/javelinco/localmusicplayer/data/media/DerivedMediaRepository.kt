package com.javelinco.localmusicplayer.data.media

import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.source.MusicSource
import com.javelinco.localmusicplayer.data.source.SourceEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class TrackMediaState(
    val loading: Boolean = false,
    val artworkPath: String? = null,
    val lyrics: LyricsDocument? = null,
)

interface DerivedMediaIndexer {
    suspend fun beginPass(source: MusicSource)
    suspend fun index(source: MusicSource, entry: SourceEntry, track: TrackEntity)
}

object NoOpDerivedMediaIndexer : DerivedMediaIndexer {
    override suspend fun beginPass(source: MusicSource) = Unit
    override suspend fun index(source: MusicSource, entry: SourceEntry, track: TrackEntity) = Unit
}

class DerivedMediaRepository(
    private val cache: DerivedMediaCache,
    private val resolver: TrackMediaResolver,
    private val sourceProvider: suspend (String) -> MusicSource?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DerivedMediaIndexer {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val mutableStates = MutableStateFlow<Map<String, TrackMediaState>>(emptyMap())
    val states: StateFlow<Map<String, TrackMediaState>> = mutableStates.asStateFlow()

    suspend fun ensure(track: TrackEntity, refreshCompanions: Boolean = false): TrackMediaState =
        locks.getOrPut(track.trackId) { Mutex() }.withLock {
            val fingerprint = trackFingerprint(track)
            val cached = withContext(ioDispatcher) { cache.read(track.trackId) }
            if (!refreshCompanions && cached?.trackFingerprint == fingerprint) {
                return@withLock publish(track.trackId, cached.toState())
            }

            mutableStates.update { it + (track.trackId to TrackMediaState(loading = true)) }
            val result = runCatching {
                withContext(ioDispatcher) {
                    val source = sourceProvider(track.sourceId) ?: return@withContext null
                    val entry = track.toSourceEntry()
                    resolver.resolve(source, entry, track)
                }
            }.getOrNull()
            if (result == null) return@withLock publish(track.trackId, TrackMediaState())

            val transcoded = result.artwork?.let(ArtworkTranscoder::downsample)
            val artworkKey = transcoded?.let { bytes ->
                digest("${track.normalizedAlbumArtist}\u0000${track.normalizedAlbumTitle}\u0000${digest(bytes)}")
            }
            withContext(ioDispatcher) {
                if (artworkKey != null && transcoded != null) cache.writeArtwork(artworkKey, transcoded)
                cache.writeRecord(track.trackId, CachedTrackMedia(fingerprint, result.companionFingerprint, artworkKey, result.lyrics))
            }
            publish(track.trackId, TrackMediaState(false, artworkKey?.let { cache.artworkPath(it)?.absolutePath }, result.lyrics))
        }

    override suspend fun beginPass(source: MusicSource) = Unit

    override suspend fun index(source: MusicSource, entry: SourceEntry, track: TrackEntity) {
        ensure(track, refreshCompanions = true)
    }

    private fun publish(trackId: String, state: TrackMediaState): TrackMediaState {
        mutableStates.update { it + (trackId to state) }
        return state
    }

    private fun CachedTrackMedia.toState() = TrackMediaState(
        artworkPath = artworkKey?.let { cache.artworkPath(it)?.absolutePath },
        lyrics = lyrics,
    )

    private fun TrackEntity.toSourceEntry() = SourceEntry(
        sourceId = com.javelinco.localmusicplayer.core.model.SourceId(sourceId),
        stableId = trackId,
        contentUri = contentUri,
        displayName = fileName,
        mimeType = "audio/mpeg",
        sizeBytes = sizeBytes,
        modifiedAtEpochMs = modifiedAtEpochMs,
        parentDocumentId = parentDocumentId,
    )

    private fun trackFingerprint(track: TrackEntity) = digest("${track.contentUri}\u0000${track.modifiedAtEpochMs}\u0000${track.sizeBytes}")
    private fun digest(value: String) = digest(value.encodeToByteArray())
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
