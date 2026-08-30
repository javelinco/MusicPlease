package com.javelinco.localmusicplayer

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.javelinco.localmusicplayer.data.backup.BackupManager
import com.javelinco.localmusicplayer.data.backup.RoomBackupDataSource
import com.javelinco.localmusicplayer.data.backup.SafBackupStorage
import com.javelinco.localmusicplayer.data.db.LocalMusicDatabase
import com.javelinco.localmusicplayer.data.db.DatabaseMigrations
import com.javelinco.localmusicplayer.data.media.AndroidCompanionFileReader
import com.javelinco.localmusicplayer.data.media.AndroidEmbeddedMediaReader
import com.javelinco.localmusicplayer.data.media.DerivedMediaCache
import com.javelinco.localmusicplayer.data.media.DerivedMediaRepository
import com.javelinco.localmusicplayer.data.media.LocalMediaResolver
import com.javelinco.localmusicplayer.data.scan.AndroidMp3MetadataExtractor
import com.javelinco.localmusicplayer.data.scan.DefaultScanCoordinator
import com.javelinco.localmusicplayer.data.scan.RoomScanCatalog
import com.javelinco.localmusicplayer.data.settings.AppSettings
import com.javelinco.localmusicplayer.data.source.MediaStoreReader
import com.javelinco.localmusicplayer.data.source.MediaStoreSource
import com.javelinco.localmusicplayer.data.source.RoomSourceRegistry
import com.javelinco.localmusicplayer.data.source.SafDocumentReader
import com.javelinco.localmusicplayer.data.source.SafDocumentSource
import com.javelinco.localmusicplayer.data.source.SafTreeReader
import com.javelinco.localmusicplayer.data.source.SafTreeSource
import com.javelinco.localmusicplayer.library.LibraryRepository
import com.javelinco.localmusicplayer.home.RecentPlayRepository
import com.javelinco.localmusicplayer.playback.queue.QueueEngine
import com.javelinco.localmusicplayer.playlists.RoomPlaylistRepository
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: LocalMusicDatabase = Room.databaseBuilder(
        appContext,
        LocalMusicDatabase::class.java,
        "local-music.db",
    ).addMigrations(
        DatabaseMigrations.MIGRATION_1_2,
        DatabaseMigrations.MIGRATION_2_3,
        DatabaseMigrations.MIGRATION_3_4,
    ).build()
    val sourceRegistry = RoomSourceRegistry(database.libraryDao())
    val libraryRepository = LibraryRepository(database.libraryDao())
    val recentPlayRepository = RecentPlayRepository(database.recentPlayDao())
    val playlistRepository = RoomPlaylistRepository(
        dao = database.userDataDao(),
        libraryDao = database.libraryDao(),
    )
    val queueEngine = QueueEngine()
    val settings = AppSettings(appContext)
    val backupData = RoomBackupDataSource(database.libraryDao(), database.userDataDao())
    private val metadataExtractor = AndroidMp3MetadataExtractor(appContext.contentResolver)
    val derivedMediaRepository = DerivedMediaRepository(
        cache = DerivedMediaCache(appContext.cacheDir),
        resolver = LocalMediaResolver(
            companionReader = AndroidCompanionFileReader(appContext.contentResolver),
            embeddedReader = AndroidEmbeddedMediaReader(appContext.contentResolver, metadataExtractor),
        ),
        sourceProvider = { sourceId ->
            sourceRegistry.observeSources().first().firstOrNull { it.id.value == sourceId }
        },
    )
    val scanCoordinator = DefaultScanCoordinator(
        sourceProvider = { sourceRegistry.observeSources().first() },
        readerFactory = { source ->
            when (source) {
                is SafTreeSource -> SafTreeReader(appContext.contentResolver)
                is SafDocumentSource -> SafDocumentReader(appContext.contentResolver)
                is MediaStoreSource -> MediaStoreReader(appContext.contentResolver)
            }
        },
        extractor = metadataExtractor,
        catalog = RoomScanCatalog(database.libraryDao()),
        derivedMediaIndexer = derivedMediaRepository,
    )

    fun backupManager(treeUri: String): BackupManager {
        val storage = SafBackupStorage(appContext.contentResolver, Uri.parse(treeUri))
        return BackupManager(
            storage = storage,
            snapshot = backupData::snapshot,
            restore = backupData::restore,
        )
    }
}
