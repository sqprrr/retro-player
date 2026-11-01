package com.retroplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.retroplayer.PlayerViewModel
import com.retroplayer.QueueSource
import com.retroplayer.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LibraryScreen(vm: PlayerViewModel, listState: LazyListState) {
    val query = vm.query.trim()
    val songs = remember(vm.songs, query) {
        if (query.isEmpty()) vm.songs
        else vm.songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }
    val rows = remember(songs) { libraryRows(songs) }
    val playing = !vm.playback.showPlayButton
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    Win95Window(
        "Library",
        Glyphs.Library,
        Modifier.fillMaxSize(),
        trailing = {
            RetroText(if (songs.size == 1) "1 song" else "${songs.size} songs", Modifier.padding(end = 6.dp), size = 13.sp, font = RetroFonts.Chrome)
        },
    ) {
        LcdSearchField(
            vm.query,
            onChange = {
                vm.query = it
                scope.launch { listState.scrollToItem(0) }
            },
            placeholder = "Songs, artists, albums…",
            label = "Search library",
            modifier = Modifier.padding(6.dp).fillMaxWidth(),
        )
        // A Win95 tab control. The library has a single view, so there is a single tab.
        SelectedTab("Songs", Modifier.padding(horizontal = 6.dp).zIndex(1f))
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .offset(y = (-2).dp)
                .background(Retro.Face)
                .bevel()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val enabled = songs.isNotEmpty()
                val accent = if (enabled) Retro.LcdText else Retro.TextDisabled
                Win95Button({ vm.playAll(songs, QueueSource.LIBRARY) }, Modifier.weight(1f).height(44.dp), enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Glyph(Glyphs.PlayMarker, color = accent)
                        RetroText("Play all", size = 15.sp, font = RetroFonts.Chrome)
                    }
                }
                Win95Button({ vm.shuffleAll(songs, QueueSource.LIBRARY) }, Modifier.weight(1f).height(44.dp), enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Glyph(Glyphs.Shuffle, Modifier.size(18.dp, 14.dp), accent)
                        RetroText("Shuffle all", size = 15.sp, font = RetroFonts.Chrome)
                    }
                }
            }
            LcdPanel(Modifier.weight(1f).fillMaxWidth()) {
                Column {
                    // Column headers of a details list. The library is always sorted by title.
                    Row {
                        ColumnHeader("Title", Modifier.weight(1f), sortedUp = true)
                        ColumnHeader("Time", Modifier.width(74.dp))
                    }
                    when {
                        !vm.libraryLoaded -> LcdMessage("READING MUSIC...")
                        vm.songs.isEmpty() -> LcdMessage("NO MUSIC FOUND ON THIS PHONE")
                        songs.isEmpty() -> LcdMessage("NO MATCHES")
                        else -> Row {
                            LazyColumn(Modifier.weight(1f), state = listState) {
                                items(rows, key = { it.key }) { row ->
                                    when (row) {
                                        is LetterRow -> RetroText(
                                            row.letter,
                                            Modifier
                                                .fillMaxWidth()
                                                .background(Retro.LcdGroupBg)
                                                .padding(start = 10.dp, top = 4.dp, end = 10.dp)
                                                .semantics { heading() },
                                            Retro.LcdTextFaint,
                                            18.sp,
                                            RetroFonts.Lcd,
                                        )
                                        is SongItem -> SongRow(
                                            title = row.song.title,
                                            artist = row.song.artist,
                                            duration = formatTime(row.song.durationMs),
                                            current = row.song.id == vm.playback.songId,
                                            playing = playing,
                                            onClick = {
                                                focusManager.clearFocus()
                                                // Search only finds the song; the queue is always the whole library.
                                                vm.play(vm.songs, row.song, QueueSource.LIBRARY)
                                            },
                                        )
                                    }
                                }
                            }
                            Scrollbar(listState)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

private sealed interface LibraryRow {
    val key: Any
}

private class LetterRow(val letter: String, override val key: Any) : LibraryRow

private class SongItem(val song: Song) : LibraryRow {
    override val key: Any get() = song.id
}

/** The songs with a letter header wherever the first letter of the title changes. */
private fun libraryRows(songs: List<Song>): List<LibraryRow> {
    val rows = mutableListOf<LibraryRow>()
    var last: String? = null
    for (song in songs) {
        val first = song.title.firstOrNull()
        val letter = if (first != null && first.isLetter()) first.uppercase() else "#"
        // Keyed by position: symbols can sort both before and after the letters, so "#" may appear twice.
        if (letter != last) rows += LetterRow(letter, "letter-${rows.size}")
        last = letter
        rows += SongItem(song)
    }
    return rows
}

/** The selected tab of a Win95 tab control: open at the bottom, so it merges into the panel below it. */
@Composable
private fun SelectedTab(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(44.dp)
            .background(Retro.Face)
            .drawBehind {
                val w = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
                drawRect(Retro.BevelHighlight, Offset.Zero, Size(size.width, w))
                drawRect(Retro.BevelHighlight, Offset.Zero, Size(w, size.height))
                drawRect(Retro.BevelLight, Offset(w, w), Size(size.width - 2 * w, w))
                drawRect(Retro.BevelLight, Offset(w, w), Size(w, size.height - w))
                drawRect(Retro.BevelDark, Offset(size.width - w, 0f), Size(w, size.height))
                drawRect(Retro.BevelShadow, Offset(size.width - 2 * w, w), Size(w, size.height - w))
            }
            .semantics {
                role = Role.Tab
                selected = true
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        RetroText(label, size = 14.sp, font = RetroFonts.Chrome, bold = true)
    }
}

@Composable
private fun ColumnHeader(label: String, modifier: Modifier, sortedUp: Boolean = false) {
    Row(
        modifier
            .height(30.dp)
            .background(Retro.Face)
            .bevel()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (sortedUp) Alignment.Start else Alignment.CenterHorizontally),
    ) {
        RetroText(label, color = Retro.TextOnFace, size = 13.sp, font = RetroFonts.Chrome)
        if (sortedUp) Glyph(Glyphs.SortUp, color = Retro.TextOnFace)
    }
}

@Composable
fun ListenAgainScreen(vm: PlayerViewModel, listState: LazyListState) {
    val top = vm.listenAgain
    val recent = vm.recentlyPlayed
    val playing = !vm.playback.showPlayButton
    Win95Window("Listen Again", Glyphs.ListenAgain, Modifier.fillMaxSize()) {
        if (top.isEmpty() && recent.isEmpty()) {
            LcdPanel(Modifier.padding(6.dp).fillMaxSize()) { LcdMessage("Nothing here yet — play some music") }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("Most played", Modifier.weight(1f))
                        Win95Button({ vm.playAll(top, QueueSource.LISTEN_AGAIN) }, Modifier.width(132.dp), enabled = top.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Glyph(Glyphs.PlayMarker, color = if (top.isNotEmpty()) Retro.LcdText else Retro.TextDisabled)
                                RetroText("Play all", size = 15.sp, font = RetroFonts.Chrome)
                            }
                        }
                    }
                }
                if (top.isEmpty()) {
                    item { RetroText("No plays in the last 30 days.", maxLines = 2) }
                }
                items(top.chunked(3)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { song ->
                            Win95Button({ vm.play(top, song, QueueSource.LISTEN_AGAIN) }, Modifier.weight(1f).aspectRatio(1f)) {
                                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    RetroText(song.title, size = 13.sp, bold = true, maxLines = 3)
                                    RetroText(song.artist, size = 11.sp, maxLines = 2)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                item { SectionLabel("Recently played", Modifier.padding(top = 8.dp)) }
                item {
                    LcdPanel(Modifier.fillMaxWidth()) {
                        Column {
                            recent.forEach { song ->
                                SongRow(
                                    title = song.title,
                                    artist = song.artist,
                                    duration = formatTime(song.durationMs),
                                    current = song.id == vm.playback.songId,
                                    playing = playing,
                                    onClick = { vm.play(recent, song, QueueSource.RECENTLY_PLAYED) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    RetroText(text, modifier.semantics { heading() }, Retro.TextOnFace, 15.sp, RetroFonts.Chrome, bold = true)
}

/** The playback position, re-read twice a second while shown. */
@Composable
fun rememberPlaybackPosition(vm: PlayerViewModel): MutableLongState {
    val position = remember { mutableLongStateOf(vm.currentPosition()) }
    LaunchedEffect(Unit) {
        while (true) {
            position.longValue = vm.currentPosition()
            delay(500)
        }
    }
    return position
}

/** Small "Now Playing" window docked above the taskbar. Tap the LCD or the restore button to open the full player. */
@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val state = vm.playback
    val position by rememberPlaybackPosition(vm)
    Win95Window(
        "Now Playing",
        Glyphs.Note,
        Modifier.fillMaxWidth(),
        compact = true,
        trailing = { TitleBarButton(Glyphs.Maximize, "Open full player", onOpen, height = 22.dp) },
    ) {
        Row(
            Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LcdPanel(Modifier.weight(1f).clickable(null, null, onClickLabel = "Open full player", role = Role.Button, onClick = onOpen)) {
                Column(
                    Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetroText(state.title, Modifier.weight(1f).alignByBaseline(), size = 22.sp, font = RetroFonts.Lcd, lineHeight = 1.em)
                        RetroText(formatTime(position), Modifier.alignByBaseline(), Retro.LcdTextDim, 18.sp, RetroFonts.Lcd, lineHeight = 1.em)
                    }
                    RetroText(state.artist, color = Retro.LcdTextDim, size = 12.sp)
                    ProgressLine(progress(position, state.durationMs))
                }
            }
            Win95Button(
                vm::togglePlayPause,
                Modifier.size(44.dp, 52.dp),
                contentColor = Retro.LcdText,
                description = if (state.showPlayButton) "Play" else "Pause",
            ) { Glyph(if (state.showPlayButton) Glyphs.Play else Glyphs.Pause, Modifier.size(14.dp, 16.dp)) }
            Win95Button(vm::next, Modifier.size(44.dp, 52.dp), description = "Next") {
                Glyph(Glyphs.Next, Modifier.size(18.dp, 14.dp))
            }
        }
    }
}

/** One song in an LCD list. Inverted colors and a little level meter mark the current song; a press inverts too. */
@Composable
fun SongRow(
    title: String,
    artist: String,
    duration: String,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inverted = current || pressed
    val fg = if (inverted) Retro.CurrentRowText else Retro.LcdText
    val sub = if (inverted) Retro.CurrentRowSub else Retro.LcdTextDim
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(if (inverted) Retro.CurrentRowBg else Color.Transparent)
            .dashedDivider()
            .clickable(interaction, indication = null, onClickLabel = "Play", onClick = onClick)
            .semantics { if (current) stateDescription = "Now playing" }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            RetroText(title, color = fg, size = 15.sp, bold = true)
            RetroText(artist, color = sub, size = 13.sp)
        }
        if (current) LevelBars(4, 3.dp, 2.dp, 14.dp, fg, running = playing)
        RetroText(duration, Modifier.width(54.dp), sub, 19.sp, RetroFonts.Lcd, align = TextAlign.End)
    }
}

@Composable
fun LcdMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        RetroText(text, color = Retro.LcdText, size = 15.sp, maxLines = 4, align = TextAlign.Center)
    }
}
