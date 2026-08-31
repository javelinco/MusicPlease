package com.javelinco.localmusicplayer.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlin.math.abs
import kotlinx.coroutines.isActive

private const val SEEK_SNAP_THRESHOLD_MS = 1_000L

internal fun projectPlaybackPosition(
    positionMs: Long,
    elapsedMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
): Long {
    val end = durationMs.coerceAtLeast(0L)
    val start = positionMs.coerceIn(0L, end)
    if (!isPlaying) return start
    return start + elapsedMs.coerceIn(0L, end - start)
}

@Composable
internal fun PlaybackProgress(
    mediaId: String?,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val end = durationMs.coerceAtLeast(0L)
    var projectedPosition by remember(mediaId) {
        mutableLongStateOf(positionMs.coerceIn(0L, end))
    }
    var dragPosition by remember(mediaId) { mutableStateOf<Float?>(null) }

    LaunchedEffect(mediaId, positionMs, durationMs, isPlaying) {
        val authoritativePosition = positionMs.coerceIn(0L, end)
        if (!isPlaying || abs(authoritativePosition - projectedPosition) > SEEK_SNAP_THRESHOLD_MS) {
            projectedPosition = authoritativePosition
        } else if (authoritativePosition > projectedPosition) {
            projectedPosition = authoritativePosition
        }
        if (!isPlaying || end == 0L) return@LaunchedEffect

        val anchorPosition = projectedPosition
        val anchorNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameNanos ->
                projectedPosition = projectPlaybackPosition(
                    positionMs = anchorPosition,
                    elapsedMs = (frameNanos - anchorNanos) / 1_000_000L,
                    isPlaying = true,
                    durationMs = end,
                )
            }
        }
    }

    val sliderEnd = end.toFloat().coerceAtLeast(1f)
    val displayedPosition = (dragPosition ?: projectedPosition.toFloat()).coerceIn(0f, sliderEnd)
    Column(modifier) {
        Slider(
            value = displayedPosition,
            onValueChange = {
                dragPosition = it
                onSeek(it.toLong())
            },
            onValueChangeFinished = { dragPosition = null },
            valueRange = 0f..sliderEnd,
            modifier = Modifier.fillMaxWidth().testTag("playback-progress"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(displayedPosition.toLong().asTime(), style = MaterialTheme.typography.labelSmall)
            Text(end.asTime(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun Long.asTime(): String {
    val totalSeconds = coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
