package com.javelinco.localmusicplayer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.javelinco.localmusicplayer.data.db.RecentPlaylistRow
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.media.TrackMediaState
import com.javelinco.localmusicplayer.home.RecentPlaybackQueue
import com.javelinco.localmusicplayer.home.recentPlaybackQueue
import com.javelinco.localmusicplayer.ui.library.TrackActionCallbacks

@Composable
fun HomeScreen(
    recentTracks: List<TrackEntity>,
    recentPlaylists: List<RecentPlaylistRow>,
    trackActions: TrackActionCallbacks,
    onPlayRecentQueue: (RecentPlaybackQueue) -> Unit,
    onPlayPlaylist: (String) -> Unit,
    onRemoveRecentTrack: (String) -> Unit,
    onRemoveRecentPlaylist: (String) -> Unit,
    trackMedia: Map<String, TrackMediaState> = emptyMap(),
    onRequestMedia: (TrackEntity) -> Unit = {},
) {
    fun playRecent(track: TrackEntity) {
        recentPlaybackQueue(track.trackId, recentTracks)?.let(onPlayRecentQueue)
    }
    val recentTrackActions = trackActions.copy(onPlayNow = ::playRecent)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (recentTracks.isNotEmpty()) {
                item(key = "songs-header") { RecentSectionHeader("Songs", recentTracks.size) }
                items(recentTracks, key = { "track:${it.trackId}" }) { track ->
                    LaunchedEffect(track.trackId, track.modifiedAtEpochMs, track.sizeBytes) { onRequestMedia(track) }
                    RecentTrackCard(
                        track = track,
                        actions = recentTrackActions,
                        onPlay = { playRecent(track) },
                        onRemoveFromRecentlyPlayed = { onRemoveRecentTrack(track.trackId) },
                        artworkPath = trackMedia[track.trackId]?.artworkPath,
                    )
                }
            }
            if (recentPlaylists.isNotEmpty()) {
                item(key = "playlists-header") {
                    RecentSectionHeader("Playlists", recentPlaylists.size)
                }
                items(recentPlaylists, key = { "playlist:${it.playlistId}" }) { playlist ->
                    RecentPlaylistCard(
                        playlist = playlist,
                        onPlay = { onPlayPlaylist(playlist.playlistId) },
                        onRemoveFromRecentlyPlayed = { onRemoveRecentPlaylist(playlist.playlistId) },
                    )
                }
            }
        }
    }
}
