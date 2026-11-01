package com.retroplayer

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String) {
    LISTEN_AGAIN("Listen again"),
    LIBRARY("Library"),
    NOW_PLAYING("Playing"),
}

/** Which list the current queue was built from. */
enum class QueueSource { LIBRARY, LISTEN_AGAIN, RECENTLY_PLAYED }

/** A song in the play queue. [index] is its position in the player's playlist. */
data class QueueEntry(
    val index: Int,
    val songId: Long?,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val isCurrent: Boolean,
)

/** A song taken out of the queue, with what [PlayerViewModel.restore] needs to put it back. */
class RemovedEntry(val title: String, val item: MediaItem, val index: Int, val position: Int)

data class PlaybackState(
    val songId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val showPlayButton: Boolean = true,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    /** Where the current song is in play order (0-based), and how many songs the queue holds. */
    val queuePosition: Int = 0,
    val queueSize: Int = 0,
)

@OptIn(UnstableApi::class)
class PlayerViewModel(private val app: Application) : AndroidViewModel(app) {

    var songs by mutableStateOf(emptyList<Song>())
        private set
    var libraryLoaded by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var tab by mutableStateOf<Tab?>(null)
    var playback by mutableStateOf(PlaybackState())
        private set
    var listenAgain by mutableStateOf(emptyList<Song>())
        private set
    var recentlyPlayed by mutableStateOf(emptyList<Song>())
        private set

    private val library = MutableStateFlow<List<Song>?>(null)
    private var songsById = emptyMap<Long, Song>()
    private val controllerFuture = MediaController.Builder(
        app, SessionToken(app, ComponentName(app, PlaybackService::class.java)),
    ).buildAsync()
    private var controller: MediaController? = null

    init {
        controllerFuture.addListener({
            controller = controllerFuture.get().also { it.addListener(playerListener) }
            refresh()
        }, ContextCompat.getMainExecutor(app))

        val dao = AppDatabase.get(app).playDao()
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        viewModelScope.launch {
            // Joining history with the current library hides songs deleted from the phone.
            combine(library.filterNotNull(), dao.mostPlayedSince(since), dao.recentlyPlayed()) { songs, top, recent ->
                val byId = songs.associateBy { it.id }
                top.mapNotNull(byId::get).take(20) to recent.mapNotNull(byId::get).take(20)
            }.collect { (top, recent) ->
                listenAgain = top
                recentlyPlayed = recent
                if (tab == null) tab = if (recent.isEmpty()) Tab.LIBRARY else Tab.LISTEN_AGAIN
            }
        }
    }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
    }

    /** (Re)reads the library, e.g. on every resume so deleted songs disappear. */
    fun loadLibrary() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { MusicLibrary.load(app) }
            songs = loaded
            songsById = loaded.associateBy { it.id }
            library.value = loaded
            libraryLoaded = true
            refresh()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val songId = item?.mediaId?.toLongOrNull()
        playback = PlaybackState(
            songId = songId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            showPlayButton = Util.shouldShowPlayButton(c),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET }
                ?: songs.find { it.id == songId }?.durationMs ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = upcoming(c),
            queuePosition = playOrder(c).indexOf(c.currentMediaItemIndex).coerceAtLeast(0),
            queueSize = c.mediaItemCount,
        )
    }

    /** The current song followed by everything that will play after it, in play order. */
    private fun upcoming(c: Player): List<QueueEntry> {
        val timeline = c.currentTimeline
        val result = mutableListOf<QueueEntry>()
        var index = c.currentMediaItemIndex
        while (index != C.INDEX_UNSET && index < timeline.windowCount) {
            val item = c.getMediaItemAt(index)
            val songId = item.mediaId.toLongOrNull()
            result += QueueEntry(
                index = index,
                songId = songId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                durationMs = songId?.let(songsById::get)?.durationMs ?: 0,
                isCurrent = index == c.currentMediaItemIndex,
            )
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return result
    }

    /** Every playlist index, in the order they play. */
    private fun playOrder(c: Player): List<Int> {
        val timeline = c.currentTimeline
        val order = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(c.shuffleModeEnabled)
        while (index != C.INDEX_UNSET) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return order
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0

    /**
     * Plays [song] from the list shown in [source]. If the queue already came from that
     * section, the song is played within it and the queue (and its shuffle) is kept.
     * Otherwise the queue is replaced with [list], which reshuffles it when shuffle is on.
     */
    fun play(list: List<Song>, song: Song, source: QueueSource) {
        val c = controller ?: return
        val queuedAt = if (c.playlistMetadata.title == source.name) {
            (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId == song.id.toString() }
        } else {
            null
        }
        if (queuedAt != null) {
            val args = Bundle().apply { putInt(PlaybackService.ARG_INDEX, queuedAt) }
            c.sendCustomCommand(SessionCommand(PlaybackService.PLAY_NOW, Bundle.EMPTY), args)
            return
        }
        c.setPlaylistMetadata(MediaMetadata.Builder().setTitle(source.name).build())
        c.setMediaItems(list.map { it.toMediaItem() }, list.indexOf(song).coerceAtLeast(0), 0)
        c.prepare()
        c.play()
    }

    /** Starts a fresh queue from [list]: in order, or freshly shuffled when shuffle is on. */
    fun playAll(list: List<Song>, source: QueueSource) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setPlaylistMetadata(MediaMetadata.Builder().setTitle(source.name).build())
        c.setMediaItems(list.map { it.toMediaItem() })
        c.prepare()
        c.play()
    }

    /** Turns shuffle on and starts a fresh queue from [list], beginning with a random song. */
    fun shuffleAll(list: List<Song>, source: QueueSource) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        playAll(list, source)
    }

    fun togglePlayPause() {
        controller?.let { Util.handlePlayPauseButtonAction(it) }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Repeat cycles off → all → one → off. */
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun jumpTo(entry: QueueEntry) {
        controller?.run {
            seekTo(entry.index, 0)
            play()
        }
    }

    /** Moves [entry] so it plays [offset] songs after the current one (1 = next). */
    fun moveInQueue(entry: QueueEntry, offset: Int) {
        val args = Bundle().apply {
            putInt(PlaybackService.ARG_INDEX, entry.index)
            putInt(PlaybackService.ARG_OFFSET, offset)
        }
        controller?.sendCustomCommand(SessionCommand(PlaybackService.MOVE, Bundle.EMPTY), args)
    }

    fun moveToEnd(entry: QueueEntry) {
        moveInQueue(entry, playback.queueSize)
    }

    /** Removes [entry] and returns what [restore] needs to undo it. */
    fun removeFromQueue(entry: QueueEntry): RemovedEntry? {
        val c = controller ?: return null
        if (entry.index !in 0 until c.mediaItemCount) return null
        val removed = RemovedEntry(
            title = entry.title,
            item = c.getMediaItemAt(entry.index),
            index = entry.index,
            position = playOrder(c).indexOf(entry.index),
        )
        c.removeMediaItem(entry.index)
        return removed
    }

    /** Puts a removed song back where it was in the queue. The current song keeps playing. */
    fun restore(removed: RemovedEntry) {
        val args = Bundle().apply {
            putBundle(PlaybackService.ARG_ITEM, removed.item.toBundle(MediaLibraryInfo.INTERFACE_VERSION))
            putInt(PlaybackService.ARG_INDEX, removed.index)
            putInt(PlaybackService.ARG_POSITION, removed.position)
        }
        controller?.sendCustomCommand(SessionCommand(PlaybackService.RESTORE, Bundle.EMPTY), args)
    }

    /** A share-sheet intent carrying the audio file of song [songId]. */
    fun shareIntent(songId: Long): Intent {
        val mimeType = songsById[songId]?.mimeType ?: "audio/*"
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, songUri(songId))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share song")
    }
}
