package com.javelinco.localmusicplayer.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.javelinco.localmusicplayer.data.db.AlbumSummary
import com.javelinco.localmusicplayer.data.db.NamedGroupSummary
import com.javelinco.localmusicplayer.data.db.PlaylistEntryEntity
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.db.IgnoredTrackEntity
import com.javelinco.localmusicplayer.data.media.TrackMediaState
import com.javelinco.localmusicplayer.data.scan.ScanProgress
import com.javelinco.localmusicplayer.data.source.MusicSource
import com.javelinco.localmusicplayer.library.LibrarySearchResult
import com.javelinco.localmusicplayer.playlists.PlaylistSummary
import com.javelinco.localmusicplayer.ui.components.LocalArtwork

data class LibraryScreenState(
    val selectedView: LibraryView = LibraryView.TRACKS,
    val tracks: List<TrackEntity> = emptyList(),
    val artists: List<NamedGroupSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val genres: List<NamedGroupSummary> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val playlistEntries: List<PlaylistEntryEntity> = emptyList(),
    val sources: List<MusicSource> = emptyList(),
    val ignoredTracks: List<IgnoredTrackEntity> = emptyList(),
    val scanProgress: ScanProgress? = null,
    val scanMessage: String? = null,
    val searchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResult: LibrarySearchResult? = null,
    val requestedArtist: String? = null,
    val trackMedia: Map<String, TrackMediaState> = emptyMap(),
)

@Suppress("LongParameterList")
data class LibraryActions(
    val onSelectView: (LibraryView) -> Unit = {},
    val onOpenSearch: () -> Unit = {},
    val onCloseSearch: () -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onPlayTrack: (TrackEntity) -> Unit = {},
    val onPlayTracks: (List<TrackEntity>) -> Unit = {},
    val onPlayNext: (TrackEntity) -> Unit = {},
    val onAddToQueue: (TrackEntity) -> Unit = {},
    val onRemoveTrackFromLibrary: (TrackEntity) -> Unit = {},
    val onRestoreIgnoredTrack: (String) -> Unit = {},
    val onArtistRequestConsumed: () -> Unit = {},
    val onPlayPlaylist: (String) -> Unit = {},
    val onChooseFolder: () -> Unit = {},
    val onFindAll: () -> Unit = {},
    val onBackgroundScan: () -> Unit = {},
    val onDedicatedScan: () -> Unit = {},
    val onPrioritizeScan: () -> Unit = {},
    val onDismissScanMessage: () -> Unit = {},
    val onCreatePlaylist: (String) -> Unit = {},
    val onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
    val onDeletePlaylist: (String) -> Unit = {},
    val onAddTracksToPlaylist: (String, List<String>) -> Unit = { _, _ -> },
    val onRemovePlaylistEntry: (String, String) -> Unit = { _, _ -> },
    val onMovePlaylistEntry: (String, Int, Int) -> Unit = { _, _, _ -> },
    val onRequestMedia: (TrackEntity) -> Unit = {},
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LibraryScreen(state: LibraryScreenState, actions: LibraryActions) {
    var menuExpanded by remember { mutableStateOf(false) }
    var openedGroup by remember(state.selectedView) { mutableStateOf<OpenedLibraryGroup?>(null) }
    var pendingAddition by remember { mutableStateOf<PendingPlaylistAddition?>(null) }
    var pendingInformation by remember { mutableStateOf<TrackEntity?>(null) }
    var localRequestedArtist by remember { mutableStateOf<String?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.searchOpen) {
        if (state.searchOpen) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = openedGroup != null || state.searchOpen) {
        if (openedGroup != null) {
            openedGroup = null
        } else {
            actions.onCloseSearch()
        }
    }

    fun requestTrackAddition(track: TrackEntity) {
        pendingAddition = PendingPlaylistAddition(track.title ?: track.fileName, listOf(track.trackId))
    }

    fun tracksFor(group: OpenedLibraryGroup): List<TrackEntity> = tracksForOpenedGroup(group, state.tracks)

    fun playAll(group: OpenedLibraryGroup) {
        tracksFor(group).takeIf { it.isNotEmpty() }?.let(actions.onPlayTracks)
    }

    fun requestGroupAddition(group: OpenedLibraryGroup) {
        pendingAddition = PendingPlaylistAddition(
            group.title,
            tracksFor(group).map(TrackEntity::trackId),
        )
    }

    LaunchedEffect(state.requestedArtist, localRequestedArtist, state.artists, state.selectedView) {
        (localRequestedArtist ?: state.requestedArtist)?.let { normalized ->
            state.artists.find { it.normalizedName == normalized }?.let { artist ->
                openedGroup = OpenedLibraryGroup.Named(LibraryView.ARTISTS, artist)
                localRequestedArtist = null
                if (state.requestedArtist != null) actions.onArtistRequestConsumed()
            }
        }
    }

    val trackActions = TrackActionCallbacks(
        onPlayNow = actions.onPlayTrack,
        onPlayNext = actions.onPlayNext,
        onAddToQueue = actions.onAddToQueue,
        onAddToPlaylist = ::requestTrackAddition,
        onGoToArtist = { track ->
            localRequestedArtist = track.normalizedArtist
            actions.onSelectView(LibraryView.ARTISTS)
        },
        onShowInformation = { pendingInformation = it },
        onRemoveFromLibrary = actions.onRemoveTrackFromLibrary,
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = it }) {
                Button(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                ) {
                    Text(state.selectedView.selectorLabel)
                    Icon(Icons.Rounded.ArrowDropDown, null)
                }
                ExposedDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    LibraryView.entries.forEach { view ->
                        DropdownMenuItem(
                            text = { Text(view.label) },
                            onClick = {
                                menuExpanded = false
                                actions.onSelectView(view)
                            },
                        )
                    }
                }
            }
            IconButton(onClick = if (state.searchOpen) actions.onCloseSearch else actions.onOpenSearch) {
                Icon(
                    if (state.searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                    if (state.searchOpen) "Close search" else "Search ${state.selectedView.label}",
                )
            }
        }
        if (state.searchOpen) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = actions.onSearch,
                label = { Text("Search ${state.selectedView.label.lowercase()}") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .focusRequester(searchFocusRequester)
                    .testTag("library-search-field"),
            )
        }
        ScanFeedback(
            progress = state.scanProgress,
            message = state.scanMessage,
            onPrioritizeScan = actions.onPrioritizeScan,
            onDismissMessage = actions.onDismissScanMessage,
        )
        if (shouldShowSourceSetupInLibrary(state.sources.size)) {
            SourcesScreen(
                sources = state.sources,
                onChooseFolder = actions.onChooseFolder,
                onFindAll = actions.onFindAll,
                onBackgroundScan = actions.onBackgroundScan,
                onDedicatedScan = actions.onDedicatedScan,
                scanProgress = state.scanProgress,
                scanMessage = state.scanMessage,
                onPrioritizeScan = actions.onPrioritizeScan,
                onDismissScanMessage = actions.onDismissScanMessage,
                ignoredTracks = state.ignoredTracks,
                onRestoreIgnoredTrack = actions.onRestoreIgnoredTrack,
            )
            return@Column
        }

        openedGroup?.let { opened ->
            val matchingTracks = remember(state.tracks, opened) {
                tracksForOpenedGroup(opened, state.tracks)
            }
            MetadataDetailScreen(
                title = opened.title,
                parentLabel = opened.parentLabel,
                tracks = matchingTracks,
                onBack = { openedGroup = null },
                onPlayTrack = actions.onPlayTrack,
                onPlayAll = { playAll(opened) },
                onAddAll = { requestGroupAddition(opened) },
                trackActions = trackActions,
                media = state.trackMedia,
                onRequestMedia = actions.onRequestMedia,
            )
            return@Column
        }

        when (val result = state.searchResult) {
            is LibrarySearchResult.Tracks -> TrackList(result.items, actions.onPlayTrack, actions = trackActions, media = state.trackMedia, onRequestMedia = actions.onRequestMedia)
            is LibrarySearchResult.NamedGroups -> MetadataListScreen(
                groups = result.items,
                onOpen = { openedGroup = OpenedLibraryGroup.Named(state.selectedView, it) },
                onPlayAll = { playAll(OpenedLibraryGroup.Named(state.selectedView, it)) },
                playAllDescription = { metadataPlayAllDescription(state.selectedView, it.displayName) },
            )
            is LibrarySearchResult.Albums -> AlbumListScreen(
                albums = result.items,
                onOpen = { openedGroup = OpenedLibraryGroup.Album(it) },
                onPlayAll = { playAll(OpenedLibraryGroup.Album(it)) },
                tracks = state.tracks,
                media = state.trackMedia,
                onRequestMedia = actions.onRequestMedia,
            )
            is LibrarySearchResult.Playlists -> PlaylistScreen(
                result.items,
                state.playlistEntries,
                state.tracks,
                actions.onPlayPlaylist,
                actions.onCreatePlaylist,
                actions.onRenamePlaylist,
                actions.onDeletePlaylist,
                actions.onAddTracksToPlaylist,
                actions.onRemovePlaylistEntry,
                actions.onMovePlaylistEntry,
                trackActions,
            )
            null -> LibraryBrowseContent(
                state = state,
                actions = actions,
                onOpenGroup = { openedGroup = it },
                onPlayGroup = ::playAll,
                trackActions = trackActions,
            )
        }
    }

    pendingAddition?.let { request ->
        PlaylistPickerDialog(
            request = request,
            playlists = state.playlists,
            onChoose = { playlistId ->
                actions.onAddTracksToPlaylist(playlistId, request.trackIds)
                pendingAddition = null
            },
            onGoToPlaylists = {
                pendingAddition = null
                openedGroup = null
                actions.onSelectView(LibraryView.PLAYLISTS)
            },
            onDismiss = { pendingAddition = null },
        )
    }
    pendingInformation?.let { track ->
        val source = state.sources.find { it.id.value == track.sourceId }
        TrackInformationDialog(
            track = track,
            sourceDescription = listOfNotNull(source?.label, source?.identity).joinToString(" — ").ifBlank { "Unknown" },
            onDismiss = { pendingInformation = null },
        )
    }
}

internal fun shouldShowSourceSetupInLibrary(sourceCount: Int): Boolean = sourceCount == 0

@Composable
private fun LibraryBrowseContent(
    state: LibraryScreenState,
    actions: LibraryActions,
    onOpenGroup: (OpenedLibraryGroup) -> Unit,
    onPlayGroup: (OpenedLibraryGroup) -> Unit,
    trackActions: TrackActionCallbacks,
) {
    when (state.selectedView) {
        LibraryView.TRACKS -> TrackList(state.tracks, actions.onPlayTrack, actions = trackActions, media = state.trackMedia, onRequestMedia = actions.onRequestMedia)
        LibraryView.ARTISTS -> MetadataListScreen(
            groups = state.artists,
            onOpen = { onOpenGroup(OpenedLibraryGroup.Named(LibraryView.ARTISTS, it)) },
            onPlayAll = { onPlayGroup(OpenedLibraryGroup.Named(LibraryView.ARTISTS, it)) },
            playAllDescription = { metadataPlayAllDescription(LibraryView.ARTISTS, it.displayName) },
        )
        LibraryView.ALBUMS -> AlbumListScreen(
            albums = state.albums,
            onOpen = { onOpenGroup(OpenedLibraryGroup.Album(it)) },
            onPlayAll = { onPlayGroup(OpenedLibraryGroup.Album(it)) },
            tracks = state.tracks,
            media = state.trackMedia,
            onRequestMedia = actions.onRequestMedia,
        )
        LibraryView.GENRES -> MetadataListScreen(
            groups = state.genres,
            onOpen = { onOpenGroup(OpenedLibraryGroup.Named(LibraryView.GENRES, it)) },
            onPlayAll = { onPlayGroup(OpenedLibraryGroup.Named(LibraryView.GENRES, it)) },
            playAllDescription = { metadataPlayAllDescription(LibraryView.GENRES, it.displayName) },
        )
        LibraryView.PLAYLISTS -> PlaylistScreen(
            state.playlists,
            state.playlistEntries,
            state.tracks,
            actions.onPlayPlaylist,
            actions.onCreatePlaylist,
            actions.onRenamePlaylist,
            actions.onDeletePlaylist,
            actions.onAddTracksToPlaylist,
            actions.onRemovePlaylistEntry,
            actions.onMovePlaylistEntry,
            trackActions,
        )
    }
}

@Composable
fun TrackList(
    tracks: List<TrackEntity>,
    onPlay: (TrackEntity) -> Unit,
    onAddToPlaylist: ((TrackEntity) -> Unit)? = null,
    actions: TrackActionCallbacks? = null,
    media: Map<String, TrackMediaState> = emptyMap(),
    onRequestMedia: (TrackEntity) -> Unit = {},
) {
    if (tracks.isEmpty()) {
        Text("No scanned MP3s yet.", modifier = Modifier.padding(top = 18.dp))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = TrackEntity::trackId) { track ->
            LaunchedEffect(track.trackId, track.modifiedAtEpochMs, track.sizeBytes) { onRequestMedia(track) }
            Card(
                onClick = { onPlay(track) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("track-card:${track.trackId}"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LocalArtwork(
                        path = media[track.trackId]?.artworkPath,
                        description = "Album art for ${track.title ?: track.fileName}",
                        size = 48.dp,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = track.title ?: track.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(track.artist, track.albumTitle)
                                .joinToString(" — ")
                                .ifBlank { track.fileName },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (actions != null) {
                        TrackActionMenu(track, actions)
                    } else onAddToPlaylist?.let { onAdd ->
                        IconButton(onClick = { onAdd(track) }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                "Add ${track.title ?: track.fileName} to playlist",
                            )
                        }
                    }
                }
            }
        }
    }
}

internal sealed interface OpenedLibraryGroup {
    val title: String
    val parentLabel: String

    data class Named(
        val view: LibraryView,
        val group: NamedGroupSummary,
    ) : OpenedLibraryGroup {
        override val title: String = group.displayName
        override val parentLabel: String = view.label
    }

    data class Album(val album: AlbumSummary) : OpenedLibraryGroup {
        override val title: String = album.displayTitle
        override val parentLabel: String = LibraryView.ALBUMS.label
    }
}

internal fun tracksForOpenedGroup(
    group: OpenedLibraryGroup,
    tracks: List<TrackEntity>,
): List<TrackEntity> = when (group) {
    is OpenedLibraryGroup.Named -> tracksForMetadataGroup(group.view, group.group.normalizedName, tracks)
    is OpenedLibraryGroup.Album -> tracksForAlbum(group.album, tracks)
}

internal fun metadataPlayAllDescription(view: LibraryView, displayName: String): String = when (view) {
    LibraryView.ARTISTS -> "Play all by $displayName"
    LibraryView.GENRES -> "Play all in $displayName"
    else -> "Play all $displayName"
}

internal fun tracksForMetadataGroup(
    view: LibraryView,
    normalizedName: String,
    tracks: List<TrackEntity>,
): List<TrackEntity> = when (view) {
    LibraryView.ARTISTS -> tracks.filter { it.normalizedArtist == normalizedName }
    LibraryView.GENRES -> tracks.filter { it.normalizedGenre == normalizedName }
    else -> emptyList()
}

internal fun tracksForAlbum(
    album: AlbumSummary,
    tracks: List<TrackEntity>,
): List<TrackEntity> = tracks
    .filter {
        it.normalizedAlbumArtist == album.normalizedAlbumArtist &&
            it.normalizedAlbumTitle == album.normalizedAlbumTitle
    }
    .sortedWith(
        compareBy<TrackEntity> { it.discNumber ?: 1 }
            .thenBy { it.trackNumber ?: 0 }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.fileName }
            .thenBy { it.fileName },
    )
