package com.javelinco.localmusicplayer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.javelinco.localmusicplayer.data.db.RecentPlaylistRow
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.settings.SettingsState
import com.javelinco.localmusicplayer.data.settings.ThemePreference
import com.javelinco.localmusicplayer.home.RecentPlaybackQueue
import com.javelinco.localmusicplayer.playback.service.PlaybackUiState
import com.javelinco.localmusicplayer.ui.home.HomeScreen
import com.javelinco.localmusicplayer.ui.components.AppScreenHeader
import com.javelinco.localmusicplayer.ui.library.BackupScreen
import com.javelinco.localmusicplayer.ui.library.DedicatedScanScreen
import com.javelinco.localmusicplayer.ui.library.LibraryActions
import com.javelinco.localmusicplayer.ui.library.LibraryScreen
import com.javelinco.localmusicplayer.ui.library.LibraryScreenState
import com.javelinco.localmusicplayer.ui.library.LibraryView
import com.javelinco.localmusicplayer.ui.library.PendingPlaylistAddition
import com.javelinco.localmusicplayer.ui.library.PlaylistPickerDialog
import com.javelinco.localmusicplayer.ui.library.SourcesScreen
import com.javelinco.localmusicplayer.ui.library.TrackActionCallbacks
import com.javelinco.localmusicplayer.ui.library.TrackInformationDialog
import com.javelinco.localmusicplayer.ui.player.MiniPlayer
import com.javelinco.localmusicplayer.ui.player.NowPlayingScreen
import com.javelinco.localmusicplayer.ui.player.QueueScreen

enum class PrimaryDestination(val label: String) { HOME("Home"), LIBRARY("Library"), MORE("More") }

internal fun chooseInitialPrimaryDestination(
    recentLoaded: Boolean,
    playbackReady: Boolean,
    hasRecent: Boolean,
    isPlaying: Boolean,
): PrimaryDestination? = when {
    !recentLoaded || !playbackReady -> null
    hasRecent || isPlaying -> PrimaryDestination.HOME
    else -> PrimaryDestination.LIBRARY
}

@Composable
fun PrimaryNavigationBar(selected: PrimaryDestination, onSelect: (PrimaryDestination) -> Unit) {
    NavigationBar {
        PrimaryDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        when (destination) {
                            PrimaryDestination.HOME -> Icons.Rounded.Home
                            PrimaryDestination.LIBRARY -> Icons.Rounded.LibraryMusic
                            PrimaryDestination.MORE -> Icons.Rounded.MoreHoriz
                        },
                        destination.label,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
fun AppNavigation(
    libraryState: LibraryScreenState,
    libraryActions: LibraryActions,
    recentTracks: List<TrackEntity>,
    recentPlaylists: List<RecentPlaylistRow>,
    recentLoaded: Boolean,
    dedicated: Boolean,
    settings: SettingsState,
    playback: PlaybackUiState,
    onPlayRecentQueue: (RecentPlaybackQueue) -> Unit = {},
    onRemoveRecentTrack: (String) -> Unit = {},
    onRemoveRecentPlaylist: (String) -> Unit = {},
    backupNames: List<String>,
    status: String?,
    onLeaveDedicated: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onChooseBackupFolder: () -> Unit,
    onManualBackup: () -> Unit,
    onRefreshBackups: () -> Unit,
    onRestore: (String) -> Unit,
    onTheme: (ThemePreference) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
) {
    var navigation by rememberSaveable(
        stateSaver = listSaver(
            save = { saveNavigationHistory(it) },
            restore = { restoreNavigationHistory(it) },
        ),
    ) { mutableStateOf(NavigationHistory()) }
    var pendingPlaylistTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var pendingInformationTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var requestedArtist by remember { mutableStateOf<String?>(null) }
    if (dedicated) {
        DedicatedScanScreen(libraryState.scanProgress, onLeaveDedicated)
        return
    }
    LaunchedEffect(recentLoaded, playback.controllerReady) {
        if (navigation.current == null) {
            val initial = when (chooseInitialPrimaryDestination(
                recentLoaded = recentLoaded,
                playbackReady = playback.controllerReady,
                hasRecent = recentTracks.isNotEmpty() || recentPlaylists.isNotEmpty(),
                isPlaying = playback.isPlaying,
            )) {
                PrimaryDestination.HOME -> Destination.HOME
                PrimaryDestination.LIBRARY -> Destination.LIBRARY
                PrimaryDestination.MORE -> Destination.MORE
                null -> null
            }
            if (initial != null) navigation = NavigationHistory(current = initial)
        }
    }
    fun navigateTo(target: Destination) {
        navigation = navigation.navigateTo(target)
    }
    val current = navigation.resolvedCurrent
    BackHandler {
        navigation = navigation.goBack()
    }
    val trackActions = TrackActionCallbacks(
        onPlayNow = libraryActions.onPlayTrack,
        onPlayNext = libraryActions.onPlayNext,
        onAddToQueue = libraryActions.onAddToQueue,
        onAddToPlaylist = { pendingPlaylistTrack = it },
        onGoToArtist = { track ->
            requestedArtist = track.normalizedArtist
            libraryActions.onSelectView(LibraryView.ARTISTS)
            navigateTo(Destination.LIBRARY)
        },
        onShowInformation = { pendingInformationTrack = it },
        onRemoveFromLibrary = libraryActions.onRemoveTrackFromLibrary,
    )
    val primary = when (current) {
        Destination.HOME, Destination.NOW_PLAYING -> PrimaryDestination.HOME
        Destination.LIBRARY -> PrimaryDestination.LIBRARY
        else -> PrimaryDestination.MORE
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                MiniPlayer(
                    playback,
                    onOpen = { navigateTo(Destination.NOW_PLAYING) },
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                )
                PrimaryNavigationBar(primary) { selected ->
                    navigateTo(when (selected) {
                        PrimaryDestination.HOME -> Destination.HOME
                        PrimaryDestination.LIBRARY -> Destination.LIBRARY
                        PrimaryDestination.MORE -> Destination.MORE
                    })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            AppScreenHeader(
                title = screenHeaderTitle(current, homeIsPlaying = playback.isPlaying),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (current) {
                Destination.HOME -> if (playback.isPlaying) {
                    NowPlayingScreen(
                        playback,
                        settings.reducedMotion,
                        onPrevious,
                        onPlayPause,
                        onNext,
                        onSeek,
                        onShuffle,
                        onRepeat,
                        { navigateTo(Destination.QUEUE) },
                    )
                } else {
                    HomeScreen(
                        recentTracks,
                        recentPlaylists,
                        trackActions,
                        onPlayRecentQueue,
                        libraryActions.onPlayPlaylist,
                        onRemoveRecentTrack,
                        onRemoveRecentPlaylist,
                    )
                }
                Destination.LIBRARY -> LibraryScreen(
                    libraryState.copy(requestedArtist = requestedArtist),
                    libraryActions.copy(onArtistRequestConsumed = { requestedArtist = null }),
                )
                Destination.MORE -> MoreScreen(
                    onMusicFolders = { navigateTo(Destination.MUSIC_FOLDERS) },
                    onBackup = { navigateTo(Destination.BACKUP) },
                    onSettings = { navigateTo(Destination.SETTINGS) },
                )
                Destination.NOW_PLAYING -> NowPlayingScreen(
                    playback,
                    settings.reducedMotion,
                    onPrevious,
                    onPlayPause,
                    onNext,
                    onSeek,
                    onShuffle,
                    onRepeat,
                    { navigateTo(Destination.QUEUE) },
                )
                Destination.QUEUE -> QueueScreen(
                    playback.queueTracks,
                    playback.currentMediaId,
                    trackActions,
                )
                Destination.MUSIC_FOLDERS -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    SourcesScreen(
                        sources = libraryState.sources,
                        onChooseFolder = libraryActions.onChooseFolder,
                        onFindAll = libraryActions.onFindAll,
                        onBackgroundScan = libraryActions.onBackgroundScan,
                        onDedicatedScan = libraryActions.onDedicatedScan,
                        scanProgress = libraryState.scanProgress,
                        scanMessage = libraryState.scanMessage,
                        onPrioritizeScan = libraryActions.onPrioritizeScan,
                        onDismissScanMessage = libraryActions.onDismissScanMessage,
                        ignoredTracks = libraryState.ignoredTracks,
                        onRestoreIgnoredTrack = libraryActions.onRestoreIgnoredTrack,
                    )
                }
                Destination.BACKUP -> BackupScreen(
                    settings.backupTreeUri,
                    backupNames,
                    status,
                    onChooseBackupFolder,
                    onManualBackup,
                    onRefreshBackups,
                    onRestore,
                )
                Destination.SETTINGS -> SettingsScreen(settings, onTheme, onReducedMotion)
                }
            }
        }
    }
    pendingPlaylistTrack?.let { track ->
        PlaylistPickerDialog(
            request = PendingPlaylistAddition(track.title ?: track.fileName, listOf(track.trackId)),
            playlists = libraryState.playlists,
            onChoose = { playlistId ->
                libraryActions.onAddTracksToPlaylist(playlistId, listOf(track.trackId))
                pendingPlaylistTrack = null
            },
            onGoToPlaylists = {
                pendingPlaylistTrack = null
                libraryActions.onSelectView(LibraryView.PLAYLISTS)
                navigateTo(Destination.LIBRARY)
            },
            onDismiss = { pendingPlaylistTrack = null },
        )
    }
    pendingInformationTrack?.let { track ->
        val source = libraryState.sources.find { it.id.value == track.sourceId }
        TrackInformationDialog(
            track,
            listOfNotNull(source?.label, source?.identity).joinToString(" — ").ifBlank { "Unknown" },
            onDismiss = { pendingInformationTrack = null },
        )
    }
}

@Composable
private fun SettingsScreen(
    settings: SettingsState,
    onTheme: (ThemePreference) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
) {
    Column {
        ThemePreference.entries.forEach { theme ->
            ListItem(
                headlineContent = { Text(theme.name.lowercase().replaceFirstChar(Char::uppercase)) },
                supportingContent = { if (settings.theme == theme) Text("Selected") },
                modifier = Modifier.clickable { onTheme(theme) },
            )
        }
        ListItem(
            headlineContent = { Text("Reduced motion") },
            supportingContent = { Text(if (settings.reducedMotion) "On" else "Off") },
            modifier = Modifier.clickable { onReducedMotion(!settings.reducedMotion) },
        )
    }
}
