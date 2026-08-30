package com.javelinco.localmusicplayer

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.javelinco.localmusicplayer.core.model.SourceId
import com.javelinco.localmusicplayer.data.source.AndroidSafPermissionStore
import com.javelinco.localmusicplayer.data.source.MediaStoreSource
import com.javelinco.localmusicplayer.data.source.SourceAcquisitionCoordinator
import com.javelinco.localmusicplayer.data.source.SourcePickerContracts
import com.javelinco.localmusicplayer.data.source.SourceSelectionHandler
import com.javelinco.localmusicplayer.library.LibraryViewModel
import com.javelinco.localmusicplayer.playback.service.PlaybackViewModel
import com.javelinco.localmusicplayer.ui.library.LibraryActions
import com.javelinco.localmusicplayer.ui.library.LibraryScreenState
import com.javelinco.localmusicplayer.ui.navigation.AppNavigation
import com.javelinco.localmusicplayer.ui.theme.LocalMusicPlayerTheme
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val acquisition = SourceAcquisitionCoordinator()
    private var showDevicePermissionExplanation by mutableStateOf(false)
    private val app: LocalMusicPlayerApp get() = application as LocalMusicPlayerApp
    private val libraryViewModel: LibraryViewModel by viewModels { LibraryViewModel.Factory(app.container) }
    private val playbackViewModel: PlaybackViewModel by viewModels {
        PlaybackViewModel.Factory(application, app.container.recentPlayRepository, app.container.derivedMediaRepository)
    }

    private val selectionHandler by lazy {
        SourceSelectionHandler(
            registry = app.sourceRegistry,
            permissionStore = AndroidSafPermissionStore(contentResolver),
            idFactory = { SourceId(UUID.randomUUID().toString()) },
        )
    }

    private val folderPicker = registerForActivityResult(SourcePickerContracts.chooseFolder) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val wasFirstSource = app.sourceRegistry.observeSources().first().isEmpty()
            selectionHandler.registerFolder(uri.toString(), uri.lastPathSegment ?: "Selected folder")
            libraryViewModel.onSourceAdded(wasFirstSource, playbackViewModel::stopForDedicatedScan)
        }
    }

    private val backupFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        libraryViewModel.selectBackupFolder(uri.toString())
    }

    private val devicePermission = registerForActivityResult(SourcePickerContracts.requestPermission) { granted ->
        if (granted) {
            lifecycleScope.launch {
                val wasFirstSource = app.sourceRegistry.observeSources().first().isEmpty()
                app.sourceRegistry.add(MediaStoreSource(SourceId("media-store"), "All music on this device"))
                libraryViewModel.onSourceAdded(wasFirstSource, playbackViewModel::stopForDedicatedScan)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libraryViewModel.runDailyBackupIfConfigured()
        setContent {
            val tracks by libraryViewModel.tracks.collectAsState()
            val ignoredTracks by libraryViewModel.ignoredTracks.collectAsState()
            val artists by libraryViewModel.artists.collectAsState()
            val albums by libraryViewModel.albums.collectAsState()
            val genres by libraryViewModel.genres.collectAsState()
            val sources by libraryViewModel.sources.collectAsState()
            val playlists by libraryViewModel.playlists.collectAsState()
            val playlistEntries by libraryViewModel.playlistEntries.collectAsState()
            val settings by libraryViewModel.settings.collectAsState()
            val selectedLibraryView by libraryViewModel.libraryView.collectAsState()
            val searchOpen by libraryViewModel.searchOpen.collectAsState()
            val searchQuery by libraryViewModel.searchQuery.collectAsState()
            val searchResult by libraryViewModel.librarySearchResult.collectAsState()
            val scanProgress by libraryViewModel.scanProgress.collectAsState()
            val scanMessage by libraryViewModel.scanMessage.collectAsState()
            val dedicated by libraryViewModel.dedicated.collectAsState()
            val history by libraryViewModel.homeHistory.collectAsState()
            val backups by libraryViewModel.backupNames.collectAsState()
            val status by libraryViewModel.status.collectAsState()
            val playback by playbackViewModel.state.collectAsState()
            val trackMedia by libraryViewModel.trackMedia.collectAsState()
            val playPlaylist: (String) -> Unit = { playlistId ->
                val tracksById = tracks.associateBy { it.trackId }
                val ordered = playlistEntries
                    .filter { it.playlistId == playlistId }
                    .sortedBy { it.position }
                    .mapNotNull { tracksById[it.trackId] }
                playbackViewModel.playPlaylist(playlistId, ordered)
            }
            LocalMusicPlayerTheme(settings.theme) {
                AppNavigation(
                    libraryState = LibraryScreenState(
                        selectedView = selectedLibraryView,
                        tracks = tracks,
                        artists = artists,
                        albums = albums,
                        genres = genres,
                        playlists = playlists,
                        playlistEntries = playlistEntries,
                        sources = sources,
                        ignoredTracks = ignoredTracks,
                        scanProgress = scanProgress,
                        scanMessage = scanMessage,
                        searchOpen = searchOpen,
                        searchQuery = searchQuery,
                        searchResult = searchResult,
                        trackMedia = trackMedia,
                    ),
                    libraryActions = LibraryActions(
                        onSelectView = libraryViewModel::selectLibraryView,
                        onOpenSearch = libraryViewModel::openLibrarySearch,
                        onCloseSearch = libraryViewModel::closeLibrarySearch,
                        onSearch = libraryViewModel::searchLibrary,
                        onPlayTrack = { playbackViewModel.play(it, tracks) },
                        onPlayTracks = { groupTracks ->
                            groupTracks.firstOrNull()?.let { first ->
                                playbackViewModel.play(first, groupTracks)
                            }
                        },
                        onPlayNext = playbackViewModel::playNext,
                        onAddToQueue = playbackViewModel::addToQueue,
                        onRemoveTrackFromLibrary = { libraryViewModel.ignoreTrack(it.trackId) },
                        onRestoreIgnoredTrack = libraryViewModel::restoreIgnoredTrack,
                        onPlayPlaylist = playPlaylist,
                        onChooseFolder = { folderPicker.launch(null) },
                        onFindAll = { showDevicePermissionExplanation = true },
                        onBackgroundScan = libraryViewModel::startBackgroundScan,
                        onDedicatedScan = {
                            libraryViewModel.enterDedicatedScan(playbackViewModel::stopForDedicatedScan)
                        },
                        onPrioritizeScan = {
                            libraryViewModel.prioritizeScan(playbackViewModel::stopForDedicatedScan)
                        },
                        onDismissScanMessage = libraryViewModel::dismissScanMessage,
                        onCreatePlaylist = libraryViewModel::createPlaylist,
                        onRenamePlaylist = libraryViewModel::renamePlaylist,
                        onDeletePlaylist = libraryViewModel::deletePlaylist,
                        onAddTracksToPlaylist = libraryViewModel::addTracksToPlaylist,
                        onRemovePlaylistEntry = libraryViewModel::removePlaylistEntry,
                        onMovePlaylistEntry = libraryViewModel::movePlaylistEntry,
                        onRequestMedia = libraryViewModel::requestMedia,
                    ),
                    recentTracks = history.tracks,
                    recentPlaylists = history.playlists,
                    recentLoaded = history.loaded,
                    dedicated = dedicated,
                    settings = settings,
                    playback = playback,
                    onPlayRecentQueue = { queue ->
                        playbackViewModel.play(queue.selected, queue.tracks)
                    },
                    onRemoveRecentTrack = libraryViewModel::removeRecentTrack,
                    onRemoveRecentPlaylist = libraryViewModel::removeRecentPlaylist,
                    backupNames = backups,
                    status = status,
                    onLeaveDedicated = libraryViewModel::leaveDedicatedScan,
                    onPrevious = playbackViewModel::previous,
                    onPlayPause = playbackViewModel::togglePlayPause,
                    onNext = playbackViewModel::next,
                    onSeek = playbackViewModel::seekTo,
                    onShuffle = playbackViewModel::toggleShuffle,
                    onRepeat = playbackViewModel::cycleRepeat,
                    onChooseBackupFolder = { backupFolderPicker.launch(null) },
                    onManualBackup = libraryViewModel::createManualBackup,
                    onRefreshBackups = libraryViewModel::refreshBackups,
                    onRestore = libraryViewModel::restoreBackup,
                    onTheme = libraryViewModel::setTheme,
                    onReducedMotion = libraryViewModel::setReducedMotion,
                    trackMedia = trackMedia,
                    onRequestMedia = libraryViewModel::requestMedia,
                )
                if (showDevicePermissionExplanation) DevicePermissionDialog()
            }
        }
    }

    @Composable
    private fun DevicePermissionDialog() {
        AlertDialog(
            onDismissRequest = { showDevicePermissionExplanation = false },
            title = { Text("Allow device-wide music access?") },
            text = { Text("Android will grant audio-only access. Selected folders continue to work without it. Music, Please! has no internet or all-files permission.") },
            confirmButton = {
                TextButton(onClick = {
                    showDevicePermissionExplanation = false
                    acquisition.confirmDeviceMusicExplanation()
                    devicePermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showDevicePermissionExplanation = false }) { Text("Cancel") }
            },
        )
    }
}
