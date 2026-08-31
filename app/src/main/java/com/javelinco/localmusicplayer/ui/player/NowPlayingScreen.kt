package com.javelinco.localmusicplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.javelinco.localmusicplayer.playback.service.PlaybackUiState
import com.javelinco.localmusicplayer.data.media.TrackMediaState
import com.javelinco.localmusicplayer.ui.components.LocalArtwork

@Composable
@Suppress("UNUSED_PARAMETER")
fun NowPlayingScreen(
    state: PlaybackUiState,
    reducedMotion: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onQueue: () -> Unit,
    media: TrackMediaState = TrackMediaState(),
) {
    var lyricsExpanded by remember(state.currentMediaId) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (lyricsExpanded && media.lyrics != null) {
            FullLyrics(media.lyrics, state.positionMs, reducedMotion, Modifier.fillMaxWidth().weight(1f))
        } else {
            LocalArtwork(
                path = media.artworkPath,
                description = "Album art for ${state.title}",
                modifier = Modifier.fillMaxWidth().weight(1f),
                size = 320.dp,
                cornerRadius = 28.dp,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            state.title.ifBlank { "Nothing queued" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        media.lyrics?.let { lyrics ->
            Spacer(Modifier.height(10.dp))
            if (lyricsExpanded) {
                TextButton(onClick = { lyricsExpanded = false }) { Text("Show album art") }
            } else {
                CompactLyrics(lyrics, state.positionMs) { lyricsExpanded = true }
            }
        }
        Text(
            state.artist.ifBlank { "Unknown artist" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PlaybackProgress(
            mediaId = state.currentMediaId,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().testTag("transport-controls").padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(34.dp))
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(38.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(34.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().testTag("playback-modes"),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ModeAction(
                label = if (state.shuffleEnabled) "Shuffle on" else "Shuffle off",
                selected = state.shuffleEnabled,
                icon = { Icon(Icons.Rounded.Shuffle, it) },
                onClick = onShuffle,
            )
            val repeatLabel = when (state.repeatMode) {
                Player.REPEAT_MODE_ALL -> "Repeat all"
                Player.REPEAT_MODE_ONE -> "Repeat one"
                else -> "Repeat off"
            }
            ModeAction(
                label = repeatLabel,
                selected = state.repeatMode != Player.REPEAT_MODE_OFF,
                icon = { description ->
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        description,
                    )
                },
                onClick = onRepeat,
            )
            ModeAction(
                label = "Queue",
                selected = false,
                icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, it) },
                onClick = onQueue,
            )
        }
    }
}

@Composable
private fun ModeAction(
    label: String,
    selected: Boolean,
    icon: @Composable (String) -> Unit,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ) {
                Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) { icon(label) }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
