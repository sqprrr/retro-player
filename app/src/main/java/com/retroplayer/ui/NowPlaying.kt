package com.retroplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.retroplayer.PlaybackState
import com.retroplayer.PlayerViewModel
import com.retroplayer.QueueEntry
import com.retroplayer.RemovedEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Two stacked windows: the player, and Up Next filling the rest. Up Next can be maximized over the player. */
@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onMinimize: () -> Unit, onShowArtist: (String) -> Unit) {
    val context = LocalContext.current
    val position = rememberPlaybackPosition(vm)
    var scrubbing by remember { mutableStateOf<Long?>(null) }
    val shownPosition = scrubbing ?: position.longValue
    var maximized by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = maximized) { maximized = false }
    val share: (Long) -> Unit = { songId -> context.startActivity(vm.shareIntent(songId)) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!maximized) {
            PlayerWindow(
                vm = vm,
                positionMs = shownPosition,
                onScrub = { scrubbing = it },
                onSeek = {
                    vm.seekTo(it)
                    position.longValue = it
                    scrubbing = null
                },
                onMinimize = onMinimize,
                onShowArtist = onShowArtist,
                onShare = { vm.playback.songId?.let(share) },
            )
        }
        UpNextWindow(
            vm = vm,
            positionMs = shownPosition,
            maximized = maximized,
            onToggleMaximize = { maximized = !maximized },
            onShowArtist = onShowArtist,
            onShare = share,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlayerWindow(
    vm: PlayerViewModel,
    positionMs: Long,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onMinimize: () -> Unit,
    onShowArtist: (String) -> Unit,
    onShare: () -> Unit,
) {
    val state = vm.playback
    val hasSong = state.songId != null
    val playing = hasSong && !state.showPlayButton
    var menuOpen by remember { mutableStateOf(false) }
    Win95Window(
        "Now Playing",
        Glyphs.Note,
        Modifier.fillMaxWidth(),
        trailing = {
            Box {
                TitleBarButton(Glyphs.Dots, "Track menu", { menuOpen = true }, enabled = hasSong, latched = menuOpen)
                if (menuOpen) {
                    Win95Menu(
                        entries = listOf(
                            MenuItem("&Share…", onClick = onShare),
                            MenuItem("Go to &artist") { onShowArtist(state.artist) },
                        ),
                        onDismiss = { menuOpen = false },
                        position = MenuPosition(alignEnd = true),
                        width = 230.dp,
                    )
                }
            }
            TitleBarButton(Glyphs.Minimize, "Minimize to mini player", onMinimize)
        },
    ) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LcdPanel(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RetroText(
                            if (hasSong) "TRACK ${pad3(state.queuePosition + 1)}/${pad3(state.queueSize)}" else "TRACK ---/---",
                            Modifier.weight(1f),
                            Retro.LcdTextDim,
                            12.sp,
                            letterSpacing = 0.08.em,
                        )
                        if (hasSong) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Glyph(if (playing) Glyphs.PlaySmall else Glyphs.PauseSmall)
                                RetroText(if (playing) "PLAYING" else "PAUSED", size = 12.sp, letterSpacing = 0.08.em)
                            }
                        }
                    }
                    MarqueeText(
                        if (hasSong) state.title else "-- NO SONG --",
                        retroStyle(Retro.LcdText, 36.sp, RetroFonts.Lcd, lineHeight = 1.05.em),
                    )
                    RetroText(if (hasSong) state.artist else "Pick a song in Library.", color = Retro.LcdTextDim, size = 15.sp)
                    Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RetroText(formatTime(positionMs), size = 68.sp, font = RetroFonts.Lcd, lineHeight = 0.78.em)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            RetroText("/ ${formatTime(state.durationMs)}", color = Retro.LcdTextDim, size = 26.sp, font = RetroFonts.Lcd, lineHeight = 1.em)
                            RetroText("-${formatTime(state.durationMs - positionMs)}", color = Retro.LcdTextDim, size = 20.sp, font = RetroFonts.Lcd, lineHeight = 1.em)
                        }
                        // On narrow screens the spectrum gives up width before the time does.
                        Box(Modifier.weight(1f).clipToBounds(), contentAlignment = Alignment.BottomEnd) {
                            LevelBars(14, 5.dp, 2.dp, 42.dp, Retro.LcdText, running = playing, Modifier.alpha(if (playing) 1f else 0.4f))
                        }
                    }
                }
            }
            Win95Slider(
                positionMs = positionMs,
                durationMs = if (hasSong) state.durationMs else 0,
                onScrub = onScrub,
                onSeek = onSeek,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val buttonModifier = Modifier.weight(1f).height(58.dp)
                // Media3 restarts the song when more than 3 s in, otherwise goes to the previous one.
                Win95Button(vm::previous, buttonModifier, enabled = hasSong, description = "Previous") { Glyph(Glyphs.Previous) }
                Win95Button(
                    vm::togglePlayPause,
                    buttonModifier,
                    enabled = hasSong,
                    contentColor = Retro.LcdText,
                    description = if (state.showPlayButton) "Play" else "Pause",
                ) { Glyph(if (state.showPlayButton) Glyphs.Play else Glyphs.Pause) }
                Win95Button(vm::next, buttonModifier, enabled = hasSong, description = "Next") { Glyph(Glyphs.Next) }
                Win95Button(vm::toggleShuffle, buttonModifier, latched = state.shuffle, role = Role.Switch, description = "Shuffle") {
                    ModeLabel(Glyphs.Shuffle, state.shuffle, "SHUF")
                }
                val repeatName = when (state.repeatMode) {
                    Player.REPEAT_MODE_ALL -> "all"
                    Player.REPEAT_MODE_ONE -> "one track"
                    else -> "off"
                }
                val repeatOn = state.repeatMode != Player.REPEAT_MODE_OFF
                Win95Button(vm::cycleRepeat, buttonModifier, latched = repeatOn, description = "Repeat: $repeatName") {
                    ModeLabel(Glyphs.Repeat, repeatOn, if (state.repeatMode == Player.REPEAT_MODE_ONE) "RPT 1" else "RPT")
                }
            }
        }
    }
}

/** Icon over an LED and a short label, so a mode reads at a glance. */
@Composable
private fun ModeLabel(icon: ImageVector, on: Boolean, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Glyph(icon)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Led(on)
            RetroText(label, size = 11.sp, font = RetroFonts.Chrome, letterSpacing = 0.06.em)
        }
    }
}

@Composable
private fun UpNextWindow(
    vm: PlayerViewModel,
    positionMs: Long,
    maximized: Boolean,
    onToggleMaximize: () -> Unit,
    onShowArtist: (String) -> Unit,
    onShare: (Long) -> Unit,
    modifier: Modifier,
) {
    val state = vm.playback
    var editing by rememberSaveable { mutableStateOf(false) }
    // The last removal, for Undo. A new removal replaces it; it hides on its own after 6 s.
    var removed by remember { mutableStateOf<RemovedEntry?>(null) }
    LaunchedEffect(removed) {
        if (removed != null) {
            delay(6_000)
            removed = null
        }
    }
    val upcoming = (state.queue.size - 1).coerceAtLeast(0)
    Win95Window(
        "Up Next",
        Glyphs.Queue,
        modifier.fillMaxWidth(),
        trailing = {
            RetroText(if (upcoming == 1) "1 track" else "$upcoming tracks", Modifier.padding(end = 4.dp), size = 13.sp, font = RetroFonts.Chrome)
            TitleBarButton(
                if (maximized) Glyphs.Restore else Glyphs.Maximize,
                if (maximized) "Restore player" else "Maximize queue",
                onToggleMaximize,
            )
        },
    ) {
        if (maximized) CompactPlayer(vm, state, positionMs, Modifier.padding(start = 6.dp, top = 6.dp, end = 6.dp))
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.End) {
            Win95Button({ editing = !editing }, Modifier.size(84.dp, 44.dp), enabled = editing || upcoming > 0, latched = editing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Glyph(Glyphs.Edit)
                    RetroText(if (editing) "Done" else "Edit", size = 15.sp, font = RetroFonts.Chrome)
                }
            }
        }
        QueueList(
            vm = vm,
            state = state,
            editing = editing,
            onRemove = { entry -> vm.removeFromQueue(entry)?.let { removed = it } },
            onShowArtist = onShowArtist,
            onShare = onShare,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        removed?.let { r ->
            UndoBar(r.title) {
                vm.restore(r)
                removed = null
            }
        }
        val repeat = when (state.repeatMode) {
            Player.REPEAT_MODE_ALL -> "ALL"
            Player.REPEAT_MODE_ONE -> "ONE"
            else -> "OFF"
        }
        StatusBar(
            listOf(
                if (state.songId != null) "Track ${state.queuePosition + 1} of ${state.queueSize}" else "No track loaded",
                "SHUF ${if (state.shuffle) "ON" else "OFF"} · RPT $repeat",
            ),
        )
    }
}

/** The player squeezed into one LCD strip, shown on top of the maximized queue. */
@Composable
private fun CompactPlayer(vm: PlayerViewModel, state: PlaybackState, positionMs: Long, modifier: Modifier) {
    val hasSong = state.songId != null
    LcdPanel(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 10.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetroText(
                        if (hasSong) state.title else "-- NO SONG --",
                        Modifier.weight(1f).alignByBaseline(),
                        size = 24.sp,
                        font = RetroFonts.Lcd,
                        lineHeight = 1.em,
                    )
                    RetroText(formatTime(positionMs), Modifier.alignByBaseline(), Retro.LcdTextDim, 20.sp, RetroFonts.Lcd, lineHeight = 1.em)
                }
                RetroText(state.artist, color = Retro.LcdTextDim, size = 13.sp)
                ProgressLine(progress(positionMs, state.durationMs))
            }
            Win95Button(
                vm::togglePlayPause,
                Modifier.size(44.dp),
                enabled = hasSong,
                contentColor = Retro.LcdText,
                description = if (state.showPlayButton) "Play" else "Pause",
            ) { Glyph(if (state.showPlayButton) Glyphs.Play else Glyphs.Pause, Modifier.size(14.dp, 16.dp)) }
        }
    }
}

/** A queue row: its entry and a key that stays put when playlist indexes shift. */
private class QueueRow(val entry: QueueEntry, val key: String)

private fun queueRows(queue: List<QueueEntry>): List<QueueRow> {
    val seen = mutableMapOf<String, Int>()
    return queue.map { entry ->
        val id = entry.songId?.toString() ?: "index-${entry.index}"
        val n = (seen[id] ?: 0) + 1
        seen[id] = n
        QueueRow(entry, "$id#$n")
    }
}

/** Drag-to-reorder: the dragged row follows the finger and swaps places with each row it passes halfway. */
private class QueueDrag(private val listState: LazyListState) {
    var key by mutableStateOf<String?>(null)
        private set
    /** The rows in their new order while dragging, and after a drop until the player reports the new queue. */
    var order by mutableStateOf<List<QueueRow>?>(null)
    val offset = mutableFloatStateOf(0f)
    private var base: List<QueueRow>? = null

    fun start(key: String, rows: List<QueueRow>) {
        this.key = key
        order = rows
        base = rows
        offset.floatValue = 0f
    }

    fun drag(dy: Float) {
        val rows = order ?: return
        val from = rows.indexOfFirst { it.key == key }
        if (from < 1) return
        offset.floatValue += dy
        // Row sizes rather than positions: the list may not have laid out the last swap yet.
        fun size(i: Int) = rows.getOrNull(i)?.let { row ->
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == row.key }?.size
        }
        val below = size(from + 1)
        val above = if (from > 1) size(from - 1) else null // the current song stays on top
        if (below != null && offset.floatValue > below / 2f) {
            order = rows.toMutableList().apply { add(from + 1, removeAt(from)) }
            offset.floatValue -= below
        } else if (above != null && offset.floatValue < -above / 2f) {
            order = rows.toMutableList().apply { add(from - 1, removeAt(from)) }
            offset.floatValue += above
        }
    }

    /** Ends the drag. Returns the moved entry and how many songs after the current one it now plays, or null if nothing moved. */
    fun end(rows: List<QueueRow>): Pair<QueueEntry, Int>? {
        val dropped = order
        val dragged = key
        key = null
        offset.floatValue = 0f
        val to = dropped?.indexOfFirst { it.key == dragged } ?: -1
        // Give up if the queue changed under the finger (say, the song ended) or the row is back where it was.
        if (dropped == null || rows !== base || to < 1 || to == rows.indexOfFirst { it.key == dragged }) {
            order = null
            return null
        }
        return dropped[to].entry to to
    }
}

@Composable
private fun QueueList(
    vm: PlayerViewModel,
    state: PlaybackState,
    editing: Boolean,
    onRemove: (QueueEntry) -> Unit,
    onShowArtist: (String) -> Unit,
    onShare: (Long) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    // The current song is always the first row, so jump back up when it changes.
    LaunchedEffect(state.songId) { listState.scrollToItem(0) }
    val rows = remember(state.queue) { queueRows(state.queue) }
    val currentRows by rememberUpdatedState(rows)
    val drag = remember(listState) { QueueDrag(listState) }
    LaunchedEffect(rows) { if (drag.key == null) drag.order = null }
    val scope = rememberCoroutineScope()
    // Numbers follow the place in the queue, so they stay in order while a row is dragged.
    val shown = (drag.order ?: rows).mapIndexed { i, row -> row to state.queuePosition + i + 1 }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val menuX = with(LocalDensity.current) { 40.dp.roundToPx() }

    LcdPanel(modifier.fillMaxWidth()) {
        when {
            state.queue.isEmpty() -> LcdMessage("QUEUE IS EMPTY")
            else -> Row {
                LazyColumn(Modifier.weight(1f), state = listState) {
                    items(shown, key = { (row) -> row.key }) { (row, number) ->
                        val entry = row.entry
                        val dragging = row.key == drag.key
                        QueueRowView(
                            entry = entry,
                            number = number,
                            selected = menuFor == row.key,
                            editing = editing,
                            gripModifier = Modifier.pointerInput(row.key) {
                                detectVerticalDragGestures(
                                    onDragStart = { drag.start(row.key, currentRows) },
                                    onVerticalDrag = { change, dy ->
                                        change.consume()
                                        drag.drag(dy)
                                    },
                                    onDragEnd = {
                                        drag.end(currentRows)?.let { (moved, offset) ->
                                            vm.moveInQueue(moved, offset)
                                            // If the player doesn't report a new order, stop showing ours.
                                            scope.launch {
                                                delay(1_500)
                                                if (drag.key == null) drag.order = null
                                            }
                                        }
                                    },
                                    onDragCancel = { drag.end(currentRows) },
                                )
                            },
                            onPlay = { vm.jumpTo(entry) },
                            onLongPress = { menuFor = row.key },
                            onRemove = { onRemove(entry) },
                            modifier = if (dragging) {
                                Modifier.zIndex(1f).graphicsLayer { translationY = drag.offset.floatValue }
                            } else {
                                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                            },
                        ) {
                            if (menuFor == row.key) {
                                Win95Menu(
                                    entries = rowMenu(entry, vm, onRemove, onShowArtist, onShare),
                                    onDismiss = { menuFor = null },
                                    position = MenuPosition(x = menuX),
                                    width = 262.dp,
                                )
                            }
                        }
                    }
                }
                Scrollbar(listState)
            }
        }
    }
}

private fun rowMenu(
    entry: QueueEntry,
    vm: PlayerViewModel,
    onRemove: (QueueEntry) -> Unit,
    onShowArtist: (String) -> Unit,
    onShare: (Long) -> Unit,
): List<MenuEntry> = buildList {
    if (!entry.isCurrent) {
        add(MenuItem("Play now", Glyphs.PlayNow, bold = true) { vm.jumpTo(entry) })
        add(MenuItem("Play &next", Glyphs.PlayNext) { vm.moveInQueue(entry, 1) })
        add(MenuItem("Move to &end", Glyphs.MoveToEnd) { vm.moveToEnd(entry) })
        add(MenuSeparator)
    }
    add(MenuItem("Go to &artist · ${entry.artist}", Glyphs.Artist) { onShowArtist(entry.artist) })
    entry.songId?.let { id -> add(MenuItem("&Share…", Glyphs.Share) { onShare(id) }) }
    if (!entry.isCurrent) {
        add(MenuSeparator)
        add(MenuItem("&Remove from queue", Glyphs.Remove) { onRemove(entry) })
    }
}

@Composable
private fun QueueRowView(
    entry: QueueEntry,
    number: Int,
    selected: Boolean,
    editing: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
    /** The drag gesture for the grip shown in edit mode. */
    gripModifier: Modifier,
    /** Popups anchored to this row. */
    overlay: @Composable () -> Unit,
) {
    val current = entry.isCurrent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inverted = current || (pressed && !selected)
    val background = when {
        inverted -> Retro.CurrentRowBg
        selected -> Retro.SelectedRowBg
        else -> Color.Transparent
    }
    val fg = when {
        inverted -> Retro.CurrentRowText
        selected -> Retro.SelectedRowText
        else -> Retro.LcdText
    }
    val sub = when {
        inverted -> Retro.CurrentRowSub
        selected -> Retro.SelectedRowSub
        else -> Retro.LcdTextDim
    }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(background)
                .dashedDivider()
                .dottedOutline(selected, Color.White, 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing && !current) {
                Box(Modifier.width(40.dp).height(58.dp).then(gripModifier), contentAlignment = Alignment.CenterEnd) {
                    Glyph(Glyphs.Grip, color = Retro.LcdTextFaint)
                }
            }
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp)
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClickLabel = "Play",
                        onLongClickLabel = "Show actions",
                        onLongClick = onLongPress,
                        onClick = onPlay,
                    )
                    .semantics { if (current) stateDescription = "Now playing" }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.width(34.dp)) {
                    if (current) Glyph(Glyphs.PlayMarker, color = sub)
                    else RetroText(pad3(number), color = sub, size = 20.sp, font = RetroFonts.Lcd)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    RetroText(entry.title, color = fg, size = 16.sp, bold = true)
                    RetroText(entry.artist, color = sub, size = 13.sp)
                }
                RetroText(formatTime(entry.durationMs), color = sub, size = 20.sp, font = RetroFonts.Lcd)
            }
            if (editing && !current) {
                Win95Button(onRemove, Modifier.padding(end = 6.dp).size(44.dp), description = "Remove ${entry.title} from queue") {
                    Glyph(Glyphs.Close)
                }
            }
        }
        overlay()
    }
}

@Composable
private fun UndoBar(title: String, onUndo: () -> Unit) {
    Row(
        Modifier
            .padding(start = 6.dp, top = 6.dp, end = 6.dp)
            .fillMaxWidth()
            .background(Retro.Face)
            .bevel()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 10.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(Glyphs.Info, color = null)
        RetroText("Removed “$title”", Modifier.weight(1f), size = 13.sp)
        Win95Button(onUndo, Modifier.size(78.dp, 44.dp)) {
            BasicText(accel("&Undo"), style = retroStyle(Retro.TextOnFace, 15.sp, RetroFonts.Chrome))
        }
    }
}
