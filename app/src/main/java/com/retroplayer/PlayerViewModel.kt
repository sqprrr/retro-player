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
    LISTEN_AGAIN("LISTEN AGAIN"),
    LIBRARY("LIBRARY"),
    NOW_PLAYING("NOW PLAYING"),
}

/** Which list the current queue was built from. */
enum class QueueSource { LIBRARY, LISTEN_AGAIN, RECENTLY_PLAYED }

/** A song in the play queue. [index] is its position in the player's playlist. */
data class QueueEntry(val index: Int, val title: String, val artist: String, val isCurrent: Boolean)

data class PlaybackState(
    val songId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val showPlayButton: Boolean = true,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
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
            queue = upcoming(c),
        )
    }

    /** The current song followed by everything that will play after it, in play order. */
    private fun upcoming(c: Player): List<QueueEntry> {
        val timeline = c.currentTimeline
        val result = mutableListOf<QueueEntry>()
        var index = c.currentMediaItemIndex
        while (index != C.INDEX_UNSET && index < timeline.windowCount) {
            val metadata = c.getMediaItemAt(index).mediaMetadata
            result += QueueEntry(
                index = index,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                isCurrent = index == c.currentMediaItemIndex,
            )
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return result
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

    fun playRandom() {
        if (songs.isNotEmpty()) play(songs, songs.random(), QueueSource.LIBRARY)
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

    fun jumpTo(entry: QueueEntry) {
        controller?.run {
            seekTo(entry.index, 0)
            play()
        }
    }

    fun removeFromQueue(entry: QueueEntry) {
        controller?.removeMediaItem(entry.index)
    }

    /** A share-sheet intent carrying the current song's audio file, or null if nothing is loaded. */
    fun shareIntent(): Intent? {
        val id = playback.songId ?: return null
        val mimeType = songs.find { it.id == id }?.mimeType ?: "audio/*"
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, songUri(id))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Share song")
    }
}
