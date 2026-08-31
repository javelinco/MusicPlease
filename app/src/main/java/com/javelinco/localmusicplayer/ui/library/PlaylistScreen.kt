package com.javelinco.localmusicplayer.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javelinco.localmusicplayer.data.db.PlaylistEntryEntity
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.playlists.PlaylistSummary

@Composable
@Suppress("LongParameterList", "UNUSED_PARAMETER")
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
    var creating by remember { mutableStateOf(false) }
    var pendingRename by remember { mutableStateOf<PlaylistSummary?>(null) }
    var pendingDelete by remember { mutableStateOf<PlaylistSummary?>(null) }
    val expandedIds = remember { mutableStateListOf<String>() }
    val entriesByPlaylistId = remember(entries) {
        entries.sortedBy(PlaylistEntryEntity::position).groupBy(PlaylistEntryEntity::playlistId)
    }
    val tracksById = remember(tracks) { tracks.associateBy(TrackEntity::trackId) }

    LaunchedEffect(playlists) {
        val currentIds = playlists.mapTo(mutableSetOf()) { it.id.value }
        expandedIds.retainAll(currentIds)
    }

    BackHandler(enabled = expandedIds.isNotEmpty()) {
        expandedIds.removeAt(expandedIds.lastIndex)
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                Text("New playlist", modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (playlists.isEmpty()) {
            item { EmptyPlaylistsCard() }
        } else {
            items(playlists, key = { it.id.value }) { playlist ->
                val playlistId = playlist.id.value
                PlaylistCard(
                    playlist = playlist,
                    entries = entriesByPlaylistId[playlistId].orEmpty(),
                    tracksById = tracksById,
                    expanded = playlistId in expandedIds,
                    onToggleExpanded = {
                        if (playlistId in expandedIds) expandedIds.remove(playlistId)
                        else expandedIds.add(playlistId)
                    },
                    onPlay = { onPlay(playlistId) },
                    onRename = { pendingRename = playlist },
                    onDelete = { pendingDelete = playlist },
                    onRemove = { entryId -> onRemove(playlistId, entryId) },
                    onMove = { from, to -> onMove(playlistId, from, to) },
                    trackActions = trackActions,
                )
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = "Create a playlist",
            initialName = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
        )
    }

    pendingRename?.let { playlist ->
        PlaylistNameDialog(
            title = "Rename playlist",
            initialName = playlist.name,
            confirmLabel = "Save",
            onDismiss = { pendingRename = null },
            onConfirm = { name ->
                pendingRename = null
                onRename(playlist.id.value, name)
            },
        )
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
                        expandedIds.remove(playlistId)
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

@Composable
private fun EmptyPlaylistsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("No playlists yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Create one, then add songs from a track’s action menu.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistSummary,
    entries: List<PlaylistEntryEntity>,
    tracksById: Map<String, TrackEntity>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    trackActions: TrackActionCallbacks?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistIcon()
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        if (expanded) "Hide tracks in ${playlist.name}" else "Show tracks in ${playlist.name}",
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 60.dp, end = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, "Play ${playlist.name}")
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Rounded.Edit, "Rename ${playlist.name}")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, "Delete ${playlist.name}")
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                if (entries.isEmpty()) {
                    Text(
                        "This playlist is empty.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        entries.forEachIndexed { index, entry ->
                            PlaylistTrackRow(
                                playlistName = playlist.name,
                                entry = entry,
                                track = tracksById[entry.trackId],
                                canMoveUp = index > 0,
                                canMoveDown = index < entries.lastIndex,
                                onMoveUp = { onMove(index, index - 1) },
                                onMoveDown = { onMove(index, index + 1) },
                                onRemove = { onRemove(entry.entryId) },
                                trackActions = trackActions,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistIcon() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    playlistName: String,
    entry: PlaylistEntryEntity,
    track: TrackEntity?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    trackActions: TrackActionCallbacks?,
) {
    val title = track?.title ?: entry.titleSnapshot.ifBlank { "Unavailable track" }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (track == null) "Unavailable — kept in playlist"
                        else track.artist?.takeIf(String::isNotBlank) ?: track.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (track != null && trackActions != null) {
                    TrackActionMenu(track, trackActions)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Move $title up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Move $title down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.RemoveCircleOutline, "Remove $title from $playlistName")
                }
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
