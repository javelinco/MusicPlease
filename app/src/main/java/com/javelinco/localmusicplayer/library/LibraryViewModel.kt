package com.javelinco.localmusicplayer.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.javelinco.localmusicplayer.AppContainer
import com.javelinco.localmusicplayer.core.model.PlaylistEntryId
import com.javelinco.localmusicplayer.core.model.PlaylistId
import com.javelinco.localmusicplayer.core.model.TrackId
import com.javelinco.localmusicplayer.data.db.PlaylistEntryEntity
import com.javelinco.localmusicplayer.data.db.RecentPlaylistRow
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.scan.ScanProgress
import com.javelinco.localmusicplayer.data.settings.SettingsState
import com.javelinco.localmusicplayer.data.settings.ThemePreference
import com.javelinco.localmusicplayer.data.source.MusicSource
import com.javelinco.localmusicplayer.playlists.PlaylistSummary
import com.javelinco.localmusicplayer.ui.library.LibraryView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeHistoryState(
    val loaded: Boolean = false,
    val tracks: List<TrackEntity> = emptyList(),
    val playlists: List<RecentPlaylistRow> = emptyList(),
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val libraryDao = container.database.libraryDao()
    private val librarySearchEngine = LibrarySearchEngine(libraryDao)
    val tracks = libraryDao.observeAvailableTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ignoredTracks = libraryDao.observeIgnoredTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = libraryDao.observeArtistGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val albums = libraryDao.observeAlbumGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val genres = libraryDao.observeGenreGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sources: StateFlow<List<MusicSource>> = container.sourceRegistry.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = container.playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlistEntries: StateFlow<List<PlaylistEntryEntity>> =
        container.database.userDataDao().observeAllPlaylistEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<SettingsState> = container.settings.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())
    val scanProgress: StateFlow<ScanProgress?> = container.scanCoordinator.progress
    val trackMedia = container.derivedMediaRepository.states
    val homeHistory: StateFlow<HomeHistoryState> = combine(
        container.recentPlayRepository.observeRecentTracks(),
        container.recentPlayRepository.observeRecentPlaylists(),
    ) { recentTracks, recentPlaylists ->
        HomeHistoryState(loaded = true, tracks = recentTracks, playlists = recentPlaylists)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeHistoryState())

    private val mutableLibrarySearchResult = MutableStateFlow<LibrarySearchResult?>(null)
    val librarySearchResult = mutableLibrarySearchResult.asStateFlow()
    private val mutableLibraryView = MutableStateFlow(LibraryView.TRACKS)
    val libraryView = mutableLibraryView.asStateFlow()
    private val mutableSearchOpen = MutableStateFlow(false)
    val searchOpen = mutableSearchOpen.asStateFlow()
    private val mutableSearchQuery = MutableStateFlow("")
    val searchQuery = mutableSearchQuery.asStateFlow()
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status = mutableStatus.asStateFlow()
    private val scanSession = ScanSessionManager(container.scanCoordinator, viewModelScope)
    val dedicated = scanSession.dedicated
    val scanMessage = scanSession.message
    private val mutableBackupNames = MutableStateFlow<List<String>>(emptyList())
    val backupNames = mutableBackupNames.asStateFlow()
    private var librarySearchJob: Job? = null

    fun selectLibraryView(view: LibraryView) {
        librarySearchJob?.cancel()
        mutableLibraryView.value = view
        mutableLibrarySearchResult.value = null
        scheduleLibrarySearch()
        viewModelScope.launch { container.settings.setLibraryView(view) }
    }

    fun openLibrarySearch() {
        mutableSearchOpen.value = true
    }

    fun closeLibrarySearch() {
        librarySearchJob?.cancel()
        mutableSearchOpen.value = false
        mutableSearchQuery.value = ""
        mutableLibrarySearchResult.value = null
    }

    fun searchLibrary(query: String) {
        mutableSearchQuery.value = query
        scheduleLibrarySearch()
    }

    private fun scheduleLibrarySearch() {
        librarySearchJob?.cancel()
        val query = mutableSearchQuery.value
        if (!mutableSearchOpen.value || query.isBlank()) {
            mutableLibrarySearchResult.value = null
            return
        }
        val selectedView = mutableLibraryView.value
        librarySearchJob = viewModelScope.launch {
            delay(180)
            val result = librarySearchEngine.search(
                view = selectedView,
                query = query,
                playlists = playlists.value,
            )
            if (
                mutableSearchOpen.value &&
                mutableLibraryView.value == selectedView &&
                mutableSearchQuery.value == query
            ) {
                mutableLibrarySearchResult.value = result
            }
        }
    }

    fun startBackgroundScan() {
        scanSession.startBackground()
    }

    fun requestMedia(track: TrackEntity) {
        viewModelScope.launch { container.derivedMediaRepository.ensure(track) }
    }

    fun enterDedicatedScan(stopPlayback: () -> Unit) {
        scanSession.startDedicated(stopPlayback)
    }

    fun leaveDedicatedScan() {
        scanSession.leaveDedicated()
    }

    fun onSourceAdded(wasFirstSource: Boolean, stopPlayback: () -> Unit) {
        scanSession.sourceAdded(wasFirstSource, stopPlayback)
    }

    fun prioritizeScan(stopPlayback: () -> Unit) {
        scanSession.prioritize(stopPlayback)
    }

    fun dismissScanMessage() {
        scanSession.dismissMessage()
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { container.playlistRepository.create(name) }
    }

    fun renamePlaylist(id: String, name: String) {
        viewModelScope.launch { container.playlistRepository.rename(PlaylistId(id), name) }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch { container.playlistRepository.delete(PlaylistId(id)) }
    }

    fun removeRecentTrack(trackId: String) {
        viewModelScope.launch { container.recentPlayRepository.removeTrack(trackId) }
    }

    fun removeRecentPlaylist(playlistId: String) {
        viewModelScope.launch { container.recentPlayRepository.removePlaylist(playlistId) }
    }

    fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch {
            container.playlistRepository.addTracks(PlaylistId(playlistId), trackIds.map(::TrackId))
        }
    }

    fun removePlaylistEntry(playlistId: String, entryId: String) {
        viewModelScope.launch {
            container.playlistRepository.removeEntry(PlaylistId(playlistId), PlaylistEntryId(entryId))
        }
    }

    fun movePlaylistEntry(playlistId: String, from: Int, to: Int) {
        viewModelScope.launch { container.playlistRepository.moveEntry(PlaylistId(playlistId), from, to) }
    }

    fun ignoreTrack(trackId: String) {
        viewModelScope.launch { libraryDao.ignoreTrack(trackId, System.currentTimeMillis()) }
    }

    fun restoreIgnoredTrack(ignoreId: String) {
        viewModelScope.launch { libraryDao.restoreIgnoredTrack(ignoreId) }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { container.settings.setTheme(theme) }
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch { container.settings.setReducedMotion(enabled) }
    }

    fun selectBackupFolder(uri: String) {
        viewModelScope.launch {
            container.settings.setBackupTreeUri(uri)
            mutableStatus.value = "Backup folder selected"
            val manager = container.backupManager(uri)
            manager.createAutomaticIfDue()
            mutableBackupNames.value = manager.listBackups()
        }
    }

    fun refreshBackups() {
        viewModelScope.launch {
            settings.value.backupTreeUri?.let { uri ->
                mutableBackupNames.value = container.backupManager(uri).listBackups()
            }
        }
    }

    fun createManualBackup() {
        viewModelScope.launch {
            val uri = settings.value.backupTreeUri
            if (uri == null) {
                mutableStatus.value = "Choose a backup folder first"
                return@launch
            }
            val manager = container.backupManager(uri)
            val name = manager.createManual()
            mutableBackupNames.value = manager.listBackups()
            mutableStatus.value = "Created $name"
        }
    }

    fun restoreBackup(name: String) {
        viewModelScope.launch {
            val uri = settings.value.backupTreeUri ?: return@launch
            runCatching { container.backupManager(uri).restore(name) }
                .onSuccess { mutableStatus.value = "Restore complete; unavailable tracks remain visible in playlists" }
                .onFailure { mutableStatus.value = "Restore failed: ${it.message}" }
        }
    }

    fun runDailyBackupIfConfigured() {
        viewModelScope.launch {
            settings.first { it.backupTreeUri != null }.backupTreeUri?.let { uri ->
                runCatching { container.backupManager(uri).createAutomaticIfDue() }
            }
        }
    }

    init {
        viewModelScope.launch {
            settings.collect { mutableLibraryView.value = it.libraryView }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel(container) as T
    }
}
