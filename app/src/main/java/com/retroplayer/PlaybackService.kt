package com.retroplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns the player. Media3 shows the notification and lock-screen controls for us
 * and keeps the service in the foreground while music is playing.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Play counting: how long the current song has actually been playing.
    private var listenedMs = 0L
    private var lastTick = 0L
    private var counted = false

    // Set when a controller replaces the playlist, so we can re-shuffle around the new current song.
    private var reshufflePending = false

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(playerListener)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Only our own UI gets the custom command; the notification and other apps get the defaults.
            if (controller.packageName != packageName) return super.onConnect(session, controller)
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(PLAY_NOW, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != PLAY_NOW) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            playNow(args.getInt(ARG_INDEX))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Items from a controller may arrive without a URI, so rebuild it from the song ID.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { it.buildUpon().setUri(songUri(it.mediaId.toLong())).build() }.toMutableList()
        )

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            reshufflePending = true
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(tick)
            if (isPlaying) {
                lastTick = SystemClock.elapsedRealtime()
                handler.postDelayed(tick, 1000)
            } else {
                addListenedTime()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            listenedMs = 0
            lastTick = SystemClock.elapsedRealtime()
            counted = false
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) shuffleAfterCurrent()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reshufflePending && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                reshufflePending = false
                if (player.shuffleModeEnabled) shuffleAfterCurrent()
            }
        }
    }

    /**
     * ExoPlayer's default shuffle order can put the current song anywhere, which would hide
     * part of the queue. Make the current song first and shuffle everything else after it.
     */
    private fun shuffleAfterCurrent() {
        val count = player.mediaItemCount
        if (count == 0) return
        val current = player.currentMediaItemIndex
        val order = intArrayOf(current) + (0 until count).filter { it != current }.shuffled().toIntArray()
        player.setShuffleOrder(DefaultShuffleOrder(order, System.nanoTime()))
    }

    /**
     * Plays a song that is already in the queue without rebuilding the queue.
     * In shuffle mode the song is moved to play right now and everything that was
     * up next stays in the same order. Without shuffle, playback simply continues from it.
     */
    private fun playNow(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        val current = player.currentMediaItemIndex
        if (player.shuffleModeEnabled && index != current) {
            val order = shuffleOrder().toMutableList()
            order.remove(index)
            order.add(order.indexOf(current) + 1, index)
            player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
        }
        player.seekTo(index, 0)
        player.prepare()
        player.play()
    }

    /** Playlist indexes in the order shuffle mode plays them. */
    private fun shuffleOrder(): List<Int> {
        val timeline = player.currentTimeline
        val order = mutableListOf<Int>()
        var i = timeline.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
        }
        return order
    }

    companion object {
        /** Custom command: play the queue item at [ARG_INDEX] now, keeping the rest of the queue. */
        const val PLAY_NOW = "com.retroplayer.PLAY_NOW"
        const val ARG_INDEX = "index"
    }

    private val tick = object : Runnable {
        override fun run() {
            addListenedTime()
            handler.postDelayed(this, 1000)
        }
    }

    private fun addListenedTime() {
        val now = SystemClock.elapsedRealtime()
        listenedMs += now - lastTick
        lastTick = now
        maybeCountPlay()
    }

    /** A play counts after 30 seconds or half the song, whichever comes first. */
    private fun maybeCountPlay() {
        if (counted) return
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val duration = player.duration
        val threshold = if (duration > 0) minOf(30_000L, duration / 2) else 30_000L
        if (listenedMs < threshold) return
        counted = true
        val dao = AppDatabase.get(this).playDao()
        scope.launch { dao.insert(Play(songId = songId, playedAt = System.currentTimeMillis())) }
    }
}
