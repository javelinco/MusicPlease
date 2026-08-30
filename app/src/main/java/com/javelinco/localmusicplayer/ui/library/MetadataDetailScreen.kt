package com.javelinco.localmusicplayer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.media.TrackMediaState

@Composable
internal fun MetadataDetailScreen(
    title: String,
    parentLabel: String,
    tracks: List<TrackEntity>,
    onBack: () -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onPlayAll: () -> Unit,
    onAddAll: () -> Unit,
    trackActions: TrackActionCallbacks,
    media: Map<String, TrackMediaState> = emptyMap(),
    onRequestMedia: (TrackEntity) -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to $parentLabel")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${tracks.size} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onPlayAll,
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Text("Play all", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(
            onClick = onAddAll,
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
            Text("Add all to playlist", modifier = Modifier.padding(start = 8.dp))
        }
        TrackList(tracks, onPlayTrack, actions = trackActions, media = media, onRequestMedia = onRequestMedia)
    }
}
