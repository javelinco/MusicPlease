package com.javelinco.localmusicplayer.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.javelinco.localmusicplayer.data.db.PlaylistEntryEntity
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.playlists.PlaylistSummary

@Composable
@Suppress("LongParameterList")
fun PlaylistScreen(
    playlists: List<PlaylistSummary>,
    entries: List<PlaylistEntryEntity>,
    tracks: List<TrackEntity>,
    onPlay: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (String, List<String>) -> Unit,
    onRemove: (String, String) -> Unit,
    onMove: (String, Int, Int) -> Unit,
    trackActions: TrackActionCallbacks? = null,
) {
    var name by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<PlaylistSummary?>(null) }
    val selected = playlists.find { it.id.value == selectedId }
    BackHandler(enabled = selected != null) {
        selectedId = null
    }
    Column {
        if (selected == null) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") })
            Button(onClick = { onCreate(name); name = "" }) { Text("Create playlist") }
            LazyColumn {
                items(playlists, key = { it.id.value }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.trackCount} tracks") },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, "Open ${playlist.name}") },
                        modifier = Modifier.clickable { selectedId = playlist.id.value; name = playlist.name },
                    )
                }
            }
        } else {
            Button(onClick = { selectedId = null }) { Text("Back to playlists") }
            Text(selected.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Button(onClick = { onPlay(selected.id.value) }) { Text("Play playlist") }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist name") })
            Row {
                Button(onClick = { onRename(selected.id.value, name) }) { Text("Rename") }
                Button(onClick = { pendingDelete = selected }) { Text("Delete") }
            }
            Text("Playlist order")
            val selectedEntries = entries.filter { it.playlistId == selected.id.value }.sortedBy { it.position }
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(selectedEntries, key = { _, it -> it.entryId }) { index, entry ->
                    val track = tracks.find { it.trackId == entry.trackId }
                    ListItem(
                        headlineContent = { Text(track?.title ?: entry.titleSnapshot.ifBlank { "Unavailable track" }) },
                        supportingContent = { Text(if (track == null) "Unavailable — kept in playlist" else track.artist ?: track.fileName) },
                        trailingContent = {
                            Row {
                                if (track != null && trackActions != null) TrackActionMenu(track, trackActions)
                                Button(onClick = { onMove(selected.id.value, index, index - 1) }, enabled = index > 0) { Text("↑") }
                                Button(onClick = { onMove(selected.id.value, index, index + 1) }, enabled = index < selectedEntries.lastIndex) { Text("↓") }
                                Button(onClick = { onRemove(selected.id.value, entry.entryId) }) { Text("Remove") }
                            }
                        },
                    )
                }
            }
            Text("Add a track")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tracks, key = TrackEntity::trackId) { track ->
                    ListItem(
                        headlineContent = { Text(track.title ?: track.fileName) },
                        trailingContent = {
                            Row {
                                if (trackActions != null) TrackActionMenu(track, trackActions)
                                Button(onClick = { onAdd(selected.id.value, listOf(track.trackId)) }) { Text("Add") }
                            }
                        },
                    )
                }
            }
        }
    }
    pendingDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete playlist?") },
            text = {
                Text("Delete \"${playlist.name}\"? This removes the playlist. Your music files will not be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val playlistId = playlist.id.value
                        pendingDelete = null
                        selectedId = null
                        onDelete(playlistId)
                    },
                    modifier = Modifier.testTag("confirm-playlist-delete"),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
