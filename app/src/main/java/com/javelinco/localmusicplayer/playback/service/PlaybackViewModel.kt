package com.javelinco.localmusicplayer.playback.service

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.javelinco.localmusicplayer.data.db.TrackEntity
import com.javelinco.localmusicplayer.data.media.DerivedMediaRepository
import com.javelinco.localmusicplayer.home.RecentPlayRepository
import java.security.SecureRandom
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import android.net.Uri
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val controllerReady: Boolean = false,
    val connected: Boolean = false,
    val hasSession: Boolean = false,
    val currentMediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueMediaIds: List<String> = emptyList(),
    val queueTracks: List<TrackEntity> = emptyList(),
    val actionMessage: String? = null,
)

internal fun queueInsertionIndex(
    currentIndex: Int,
    itemCount: Int,
    shuffleEnabled: Boolean,
    random: (Int) -> Int,
): Int {
    if (itemCount == 0 || currentIndex < 0) return 0
    if (!shuffleEnabled) return itemCount
    val choices = itemCount - currentIndex
    return currentIndex + 1 + random(choices)
}

class PlaybackViewModel(
    application: Application,
    private val recentPlays: RecentPlayRepository,
    private val derivedMedia: DerivedMediaRepository,
) : AndroidViewModel(application) {
    private val connection = PlaybackController(application)
    private val mutableState = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = mutableState.asStateFlow()
    private var controller: androidx.media3.session.MediaController? = null
    private var tracks: List<TrackEntity> = emptyList()
    private var progressJob: Job? = null
    private val historyTracker = PlaybackHistoryTracker()
    private var enrichedArtworkPath: String? = null
    private var requestedMediaId: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update(player)
    }

    init {
        val future = connection.connect()
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { connected ->
                    controller = connected
                    connected.addListener(listener)
                    update(connected)
                }.onFailure {
                    mutableState.value = mutableState.value.copy(controllerReady = true)
                }
            },
            application.mainExecutor,
        )
        viewModelScope.launch {
            derivedMedia.states.collectLatest { states ->
                val player = controller ?: return@collectLatest
                val id = player.currentMediaItem?.mediaId ?: return@collectLatest
                val path = states[id]?.artworkPath ?: return@collectLatest
                if (path == enrichedArtworkPath) return@collectLatest
                val track = tracks.find { it.trackId == id } ?: return@collectLatest
                val index = player.currentMediaItemIndex
                if (index >= 0) {
                    player.replaceMediaItem(index, MediaItemMapper.toMediaItem(track, Uri.fromFile(java.io.File(path)).toString()))
                    enrichedArtworkPath = path
                }
            }
        }
    }

    fun play(track: TrackEntity, view: List<TrackEntity>) {
        play(track, view, playlistId = null)
    }

    fun playPlaylist(playlistId: String, orderedTracks: List<TrackEntity>) {
        val first = orderedTracks.firstOrNull() ?: return
        play(first, orderedTracks, playlistId)
    }

    private fun play(track: TrackEntity, view: List<TrackEntity>, playlistId: String?) {
        tracks = view.filter(TrackEntity::available)
        val player = controller ?: return
        val index = tracks.indexOfFirst { it.trackId == track.trackId }
        if (index < 0) return
        historyTracker.queueStarted(playlistId)
        player.setMediaItems(tracks.map(MediaItemMapper::toMediaItem), index, 0)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun playNext(track: TrackEntity) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            play(track, listOf(track))
            return
        }
        val insertion = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertion, MediaItemMapper.toMediaItem(track))
        val sourceIndex = tracks.indexOfFirst { it.trackId == player.currentMediaItem?.mediaId }
        val mutable = tracks.toMutableList()
        mutable.add((sourceIndex + 1).coerceIn(0, mutable.size), track)
        tracks = mutable
        update(player)
        mutableState.value = mutableState.value.copy(actionMessage = "Playing next: ${track.title ?: track.fileName}")
    }

    fun addToQueue(track: TrackEntity) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            play(track, listOf(track))
            return
        }
        val insertion = queueInsertionIndex(
            currentIndex = player.currentMediaItemIndex,
            itemCount = player.mediaItemCount,
            shuffleEnabled = mutableState.value.shuffleEnabled,
            random = SecureRandom()::nextInt,
        )
        player.addMediaItem(insertion, MediaItemMapper.toMediaItem(track))
        tracks = tracks + track
        update(player)
        mutableState.value = mutableState.value.copy(actionMessage = "Added to queue: ${track.title ?: track.fileName}")
    }

    fun dismissActionMessage() {
        mutableState.value = mutableState.value.copy(actionMessage = null)
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun toggleShuffle() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        val enabling = !mutableState.value.shuffleEnabled
        val currentId = player.currentMediaItem?.mediaId ?: return
        val position = player.currentPosition
        val ordered = if (enabling) {
            val remaining = tracks.filterNot { it.trackId == currentId }.toMutableList()
            val random = SecureRandom()
            for (i in remaining.lastIndex downTo 1) {
                val j = random.nextInt(i + 1)
                val temporary = remaining[i]
                remaining[i] = remaining[j]
                remaining[j] = temporary
            }
            listOfNotNull(tracks.find { it.trackId == currentId }) + remaining
        } else {
            tracks
        }
        val index = ordered.indexOfFirst { it.trackId == currentId }.coerceAtLeast(0)
        player.setMediaItems(ordered.map(MediaItemMapper::toMediaItem), index, position)
        player.prepare()
        if (mutableState.value.isPlaying) player.play()
        mutableState.value = mutableState.value.copy(shuffleEnabled = enabling)
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stopForDedicatedScan() {
        controller?.pause()
    }

    private fun update(player: Player) {
        val metadata = player.mediaMetadata
        mutableState.value = mutableState.value.copy(
            controllerReady = true,
            connected = true,
            hasSession = player.mediaItemCount > 0,
            currentMediaId = player.currentMediaItem?.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            repeatMode = player.repeatMode,
            queueMediaIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            queueTracks = (0 until player.mediaItemCount).mapNotNull { index ->
                val id = player.getMediaItemAt(index).mediaId
                tracks.find { it.trackId == id }
            },
        )
        historyTracker.onPlaybackState(
            mediaId = player.currentMediaItem?.mediaId,
            isPlaying = player.isPlaying,
        )?.let { record ->
            viewModelScope.launch {
                recentPlays.recordTrack(record.trackId)
                record.playlistId?.let { recentPlays.recordPlaylist(it) }
            }
        }
        val currentId = player.currentMediaItem?.mediaId
        if (currentId != requestedMediaId) {
            requestedMediaId = currentId
            enrichedArtworkPath = null
            tracks.find { it.trackId == currentId }?.let { track ->
                viewModelScope.launch { derivedMedia.ensure(track) }
            }
        }
        if (player.isPlaying && progressJob?.isActive != true) {
            progressJob = viewModelScope.launch {
                while (true) {
                    delay(500)
                    controller?.let(::update)
                }
            }
        } else if (!player.isPlaying) {
            progressJob?.cancel()
            progressJob = null
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        connection.release()
    }

    class Factory(
        private val application: Application,
        private val recentPlays: RecentPlayRepository,
        private val derivedMedia: DerivedMediaRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlaybackViewModel(application, recentPlays, derivedMedia) as T
    }
}
