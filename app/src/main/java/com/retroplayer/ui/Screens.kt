package com.retroplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplayer.PlayerViewModel
import com.retroplayer.QueueSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(vm: PlayerViewModel, listState: LazyListState) {
    val query = vm.query.trim()
    val songs = remember(vm.songs, query) {
        if (query.isEmpty()) vm.songs
        else vm.songs.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SearchField(vm.query, onChange = {
            vm.query = it
            scope.launch { listState.scrollToItem(0) }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RetroButton("$PLAY RANDOM", vm::playRandom, Modifier.weight(1f), enabled = vm.songs.isNotEmpty())
            RetroButton(
                if (vm.playback.shuffle) "SHUFFLE ON" else "SHUFFLE OFF",
                vm::toggleShuffle,
                Modifier.weight(1f),
                latched = vm.playback.shuffle,
            )
        }
        RetroText(if (songs.size == 1) "1 SONG" else "${songs.size} SONGS", size = 12.sp, bold = true)
        LcdPanel(Modifier.fillMaxSize()) {
            when {
                !vm.libraryLoaded -> LcdMessage("READING MUSIC...")
                vm.songs.isEmpty() -> LcdMessage("NO MUSIC FOUND ON THIS PHONE")
                songs.isEmpty() -> LcdMessage("NO MATCHES")
                else -> Row {
                    LazyColumn(Modifier.weight(1f), state = listState) {
                        items(songs, key = { it.id }) { song ->
                            SongRow(
                                title = song.title,
                                artist = song.artist,
                                duration = formatTime(song.durationMs),
                                highlighted = song.id == vm.playback.songId,
                                onClick = {
                                    focusManager.clearFocus()
                                    // Search only finds the song; the queue is always the whole library.
                                    vm.play(vm.songs, song, QueueSource.LIBRARY)
                                },
                            )
                        }
                    }
                    Scrollbar(listState)
                }
            }
        }
    }
}

@Composable
fun ListenAgainScreen(vm: PlayerViewModel, listState: LazyListState) {
    val top = vm.listenAgain
    val recent = vm.recentlyPlayed
    if (top.isEmpty() && recent.isEmpty()) {
        LcdPanel(Modifier.fillMaxSize()) { LcdMessage("Nothing here yet — play some music") }
        return
    }
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroText("LISTEN AGAIN", Modifier.weight(1f), size = 16.sp, bold = true)
                RetroButton(
                    "$PLAY PLAY ALL",
                    { vm.playAll(top, QueueSource.LISTEN_AGAIN) },
                    enabled = top.isNotEmpty(),
                    size = 13.sp,
                )
            }
        }
        if (top.isEmpty()) {
            item { RetroText("No plays in the last 30 days.", color = Retro.Disabled, maxLines = 2) }
        }
        items(top.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { song ->
                    Tile(song.title, song.artist, Modifier.weight(1f)) {
                        vm.play(top, song, QueueSource.LISTEN_AGAIN)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { RetroText("RECENTLY PLAYED", Modifier.padding(top = 8.dp), size = 16.sp, bold = true) }
        item {
            LcdPanel(Modifier.fillMaxWidth()) {
                Column {
                    recent.forEach { song ->
                        SongRow(
                            title = song.title,
                            artist = song.artist,
                            duration = formatTime(song.durationMs),
                            highlighted = song.id == vm.playback.songId,
                            onClick = { vm.play(recent, song, QueueSource.RECENTLY_PLAYED) },
                        )
                    }
                }
            }
        }
    }
}

/** A plain beveled square with the song's title and artist. */
@Composable
private fun Tile(title: String, artist: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .aspectRatio(1f)
            .background(Retro.Face)
            .bevel(sunken = pressed)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        RetroText(title, size = 13.sp, bold = true, maxLines = 3)
        RetroText(artist, size = 11.sp, maxLines = 2)
    }
}

@Composable
fun NowPlayingScreen(vm: PlayerViewModel) {
    val context = LocalContext.current
    val state = vm.playback
    val hasSong = state.songId != null
    var position by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            position = vm.currentPosition()
            delay(500)
        }
    }
    val shownPosition = scrubbing ?: position
    // The current song is always the first row, so jump back up when it changes.
    val queueState = rememberLazyListState()
    LaunchedEffect(state.songId) { queueState.scrollToItem(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LcdPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    RetroText("NOW PLAYING", Modifier.weight(1f), Retro.LcdDim, 11.sp)
                    if (hasSong) {
                        RetroText(if (state.showPlayButton) "$PAUSE PAUSED" else "$PLAY PLAYING", color = Retro.LcdText, size = 11.sp)
                    }
                }
                RetroText(
                    if (hasSong) state.title else "-- NO SONG --",
                    color = Retro.LcdText, size = 20.sp, bold = true, maxLines = 2,
                )
                if (!hasSong) RetroText("Pick a song in LIBRARY.", color = Retro.LcdDim, size = 14.sp)
                RetroText(state.artist, color = Retro.LcdDim, size = 14.sp)
                RetroText(
                    "${formatTime(shownPosition)} / ${formatTime(state.durationMs)}",
                    color = Retro.LcdText, size = 30.sp, bold = true,
                )
            }
        }
        SeekBar(
            positionMs = shownPosition,
            durationMs = if (hasSong) state.durationMs else 0,
            onScrub = { scrubbing = it },
            onSeek = {
                vm.seekTo(it)
                position = it
                scrubbing = null
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val buttonModifier = Modifier.weight(1f).height(52.dp)
            RetroButton(PREV, vm::previous, buttonModifier, enabled = hasSong, description = "Previous")
            RetroButton(
                if (state.showPlayButton) PLAY else PAUSE,
                vm::togglePlayPause,
                buttonModifier,
                enabled = hasSong,
                description = if (state.showPlayButton) "Play" else "Pause",
            )
            RetroButton(NEXT, vm::next, buttonModifier, enabled = hasSong, description = "Next")
            RetroButton(
                SHUFFLE,
                vm::toggleShuffle,
                buttonModifier,
                latched = state.shuffle,
                size = 28.sp,
                description = if (state.shuffle) "Shuffle on" else "Shuffle off",
            )
            RetroButton(
                "SHARE",
                onClick = { vm.shareIntent()?.let(context::startActivity) },
                modifier = buttonModifier,
                enabled = hasSong,
                size = 12.sp,
            )
        }
        RetroText("UP NEXT (${(state.queue.size - 1).coerceAtLeast(0)})", size = 12.sp, bold = true)
        LcdPanel(Modifier.fillMaxSize()) {
            if (state.queue.isEmpty()) {
                LcdMessage("QUEUE IS EMPTY")
            } else {
                Row {
                    LazyColumn(Modifier.weight(1f), state = queueState) {
                        // No keys: playlist indexes shift on removal, and the current song is always the first row.
                        items(state.queue) { entry ->
                            SongRow(
                                title = if (entry.isCurrent) "$PLAY ${entry.title}" else entry.title,
                                artist = entry.artist,
                                duration = null,
                                highlighted = entry.isCurrent,
                                onClick = { vm.jumpTo(entry) },
                                trailing = if (entry.isCurrent) null else {
                                    {
                                        RetroButton(
                                            "X",
                                            { vm.removeFromQueue(entry) },
                                            Modifier.size(40.dp),
                                            size = 13.sp,
                                            description = "Remove ${entry.title} from queue",
                                        )
                                    }
                                },
                            )
                        }
                    }
                    Scrollbar(queueState)
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Retro.Beige)
            .bevel(sunken = true)
            .heightIn(min = 48.dp)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RetroText("SEARCH>", bold = true)
        Spacer(Modifier.size(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = Retro.Ink, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(Retro.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            decorationBox = { field ->
                if (value.isEmpty()) RetroText("title or artist", color = Retro.Disabled, size = 15.sp)
                field()
            },
        )
        if (value.isNotEmpty()) {
            RetroText(
                "[X]",
                Modifier
                    .clickable(null, indication = null, onClickLabel = "Clear search") { onChange("") }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                bold = true,
            )
        }
    }
}

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val state = vm.playback
    val hasSong = state.songId != null
    Row(
        Modifier
            .fillMaxWidth()
            .background(Retro.Face)
            .bevel()
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LcdPanel(
            Modifier
                .weight(1f)
                .clickable(null, indication = null, enabled = hasSong, onClick = onOpen),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                RetroText(if (hasSong) state.title else "-- NO SONG --", color = Retro.LcdText, bold = true)
                RetroText(if (hasSong) state.artist else " ", color = Retro.LcdDim, size = 12.sp)
            }
        }
        RetroButton(
            if (state.showPlayButton) PLAY else PAUSE,
            vm::togglePlayPause,
            Modifier.size(52.dp),
            enabled = hasSong,
            size = 18.sp,
            description = if (state.showPlayButton) "Play" else "Pause",
        )
    }
}

/** One line in an LCD list. Inverted colors mark the current song (or a press). */
@Composable
fun SongRow(
    title: String,
    artist: String,
    duration: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inverted = highlighted || pressed
    val fg = if (inverted) Retro.Lcd else Retro.LcdText
    val dim = if (inverted) Retro.Lcd else Retro.LcdDim
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (inverted) Retro.LcdText else Color.Transparent)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RetroText(title, color = fg, bold = true)
            RetroText(artist, color = dim, size = 12.sp)
        }
        if (duration != null) {
            Spacer(Modifier.size(8.dp))
            RetroText(duration, color = fg, size = 13.sp)
        }
        trailing?.invoke()
    }
}

@Composable
fun LcdMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        RetroText(text, color = Retro.LcdText, size = 15.sp, maxLines = 4, align = TextAlign.Center)
    }
}
