package com.javelinco.localmusicplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javelinco.localmusicplayer.data.media.LyricsDocument
import com.javelinco.localmusicplayer.data.media.activeLineIndex

@Composable
internal fun CompactLyrics(lyrics: LyricsDocument, positionMs: Long, onOpen: () -> Unit) {
    val active = lyrics.activeLineIndex(positionMs) ?: 0
    val start = if (lyrics.synchronized) (active - 1).coerceAtLeast(0) else 0
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).testTag("lyrics-card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Lyrics", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            lyrics.lines.drop(start).take(3).forEachIndexed { index, line ->
                val isActive = lyrics.synchronized && start + index == active
                Text(
                    line.text,
                    maxLines = 1,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun FullLyrics(lyrics: LyricsDocument, positionMs: Long, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val active = lyrics.activeLineIndex(positionMs) ?: -1
    val listState = rememberLazyListState()
    var following by remember(lyrics) { mutableStateOf(lyrics.synchronized) }
    LaunchedEffect(active, following) {
        if (lyrics.synchronized && active >= 0 && following) {
            if (reducedMotion) listState.scrollToItem(active) else listState.animateScrollToItem(active)
        }
    }
    Box(modifier.fillMaxWidth().testTag("lyrics-screen")) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().testTag("lyrics-list").pointerInput(lyrics.synchronized) {
                if (lyrics.synchronized) awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    following = false
                }
            },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(lyrics.lines.size) { index ->
                val selected = lyrics.synchronized && index == active
                Text(
                    lyrics.lines[index].text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center,
                    style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (lyrics.synchronized && !following) {
            Button(
                onClick = { following = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) { Text("Follow") }
        }
    }
}
