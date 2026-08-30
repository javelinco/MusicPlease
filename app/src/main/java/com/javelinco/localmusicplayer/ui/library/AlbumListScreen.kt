package com.javelinco.localmusicplayer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.javelinco.localmusicplayer.data.db.AlbumSummary
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.media.TrackMediaState
import com.javelinco.localmusicplayer.ui.components.LocalArtwork
import androidx.compose.ui.unit.dp

@Composable
internal fun AlbumListScreen(
    albums: List<AlbumSummary>,
    onOpen: (AlbumSummary) -> Unit,
    onPlayAll: (AlbumSummary) -> Unit,
    tracks: List<TrackEntity> = emptyList(),
    media: Map<String, TrackMediaState> = emptyMap(),
    onRequestMedia: (TrackEntity) -> Unit = {},
) {
    LazyColumn {
        items(albums, key = { "${it.normalizedAlbumArtist}:${it.normalizedAlbumTitle}" }) { album ->
            val representative = tracks.firstOrNull {
                it.normalizedAlbumArtist == album.normalizedAlbumArtist &&
                    it.normalizedAlbumTitle == album.normalizedAlbumTitle
            }
            if (representative != null) LaunchedEffect(representative.trackId, representative.modifiedAtEpochMs, representative.sizeBytes) { onRequestMedia(representative) }
            ListItem(
                leadingContent = {
                    LocalArtwork(
                        path = representative?.let { media[it.trackId]?.artworkPath },
                        description = "Album art for ${album.displayTitle}",
                        size = 48.dp,
                    )
                },
                headlineContent = { Text(album.displayTitle) },
                supportingContent = { Text("${album.displayArtist} · ${album.trackCount} tracks") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onPlayAll(album) }) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                "Play all from ${album.displayTitle}",
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                },
                modifier = Modifier.clickable { onOpen(album) },
            )
        }
    }
}
