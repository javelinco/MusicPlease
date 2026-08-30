package com.javelinco.localmusicplayer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SourceEntity::class,
        TrackEntity::class,
        IgnoredTrackEntity::class,
        TrackSearchFts::class,
        ScanCheckpointEntity::class,
        ScanErrorEntity::class,
        PlaylistEntity::class,
        PlaylistEntryEntity::class,
        FavoriteEntity::class,
        QueueSessionEntity::class,
        SettingsMetadataEntity::class,
        RecentPlayEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class LocalMusicDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    abstract fun userDataDao(): UserDataDao

    abstract fun recentPlayDao(): RecentPlayDao
}
