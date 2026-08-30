package com.javelinco.localmusicplayer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sources",
    indices = [Index(value = ["kind", "location"], unique = true)],
)
data class SourceEntity(
    @PrimaryKey val sourceId: String,
    val kind: String,
    val location: String,
    val label: String,
    val available: Boolean,
)

@Entity(
    tableName = "tracks",
    indices = [
        Index("sourceId"),
        Index(value = ["normalizedAlbumArtist", "normalizedAlbumTitle"]),
        Index("normalizedArtist"),
        Index("normalizedGenre"),
        Index("contentUri"),
    ],
)
data class TrackEntity(
    @PrimaryKey val trackId: String,
    val sourceId: String,
    val contentUri: String,
    val fileName: String,
    val title: String?,
    val artist: String?,
    val albumTitle: String?,
    val albumArtist: String?,
    val genre: String?,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val normalizedAlbumTitle: String,
    val normalizedAlbumArtist: String,
    val normalizedGenre: String,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMs: Long,
    val modifiedAtEpochMs: Long,
    val sizeBytes: Long,
    val available: Boolean,
    val parentDocumentId: String? = null,
)

@Entity(tableName = "ignored_tracks", indices = [Index("sourceId")])
data class IgnoredTrackEntity(
    @PrimaryKey val ignoreId: String,
    val trackId: String?,
    val sourceId: String?,
    val contentUri: String?,
    val relativePath: String?,
    val fileName: String,
    val title: String?,
    val artist: String?,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val ignoredAtEpochMs: Long,
)

@Fts4
@Entity(tableName = "track_search_fts")
data class TrackSearchFts(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val fileName: String,
) {
    companion object {
        fun from(track: TrackEntity) = TrackSearchFts(
            trackId = track.trackId,
            title = track.title.orEmpty(),
            artist = track.artist.orEmpty(),
            album = track.albumTitle.orEmpty(),
            genre = track.genre.orEmpty(),
            fileName = track.fileName,
        )
    }
}

@Entity(tableName = "scan_checkpoints")
data class ScanCheckpointEntity(
    @PrimaryKey val sourceId: String,
    val cursor: String?,
    val scannedCount: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "scan_errors", indices = [Index("sourceId")])
data class ScanErrorEntity(
    @PrimaryKey(autoGenerate = true) val errorId: Long = 0,
    val sourceId: String,
    val contentUri: String?,
    val message: String,
    val occurredAtEpochMs: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistEntryEntity(
    @PrimaryKey val entryId: String,
    val playlistId: String,
    val position: Int,
    val trackId: String,
    val titleSnapshot: String,
    val contentUriSnapshot: String,
    val addedAtEpochMs: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val titleSnapshot: String,
    val contentUriSnapshot: String,
    val addedAtEpochMs: Long,
)

@Entity(tableName = "queue_session")
data class QueueSessionEntity(
    @PrimaryKey val singletonId: Int = 1,
    val queueJson: String,
    val currentIndex: Int,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "settings_metadata")
data class SettingsMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(
    tableName = "recent_plays",
    primaryKeys = ["kind", "itemId"],
    indices = [Index("playedAtEpochMs")],
)
data class RecentPlayEntity(
    val kind: String,
    val itemId: String,
    val playedAtEpochMs: Long,
)

data class ScanBatch(
    val tracks: List<TrackEntity>,
    val checkpoint: ScanCheckpointEntity,
    val errors: List<ScanErrorEntity> = emptyList(),
)

data class AlbumSummary(
    val normalizedAlbumArtist: String,
    val normalizedAlbumTitle: String,
    val displayArtist: String,
    val displayTitle: String,
    val trackCount: Int,
)

data class NamedGroupSummary(
    val normalizedName: String,
    val displayName: String,
    val trackCount: Int,
)

data class UserDataSnapshot(
    val playlists: List<PlaylistEntity>,
    val entries: List<PlaylistEntryEntity>,
    val favorites: List<FavoriteEntity>,
    val queueSessions: List<QueueSessionEntity>,
    val settings: List<SettingsMetadataEntity>,
)
