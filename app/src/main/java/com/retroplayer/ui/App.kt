package com.retroplayer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.retroplayer.PlayerViewModel
import com.retroplayer.Tab
import kotlinx.coroutines.launch
import kotlin.math.abs

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private val requestedPermissions =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPermission, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(audioPermission)

private fun Context.isGranted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun App(vm: PlayerViewModel) {
    val context = LocalContext.current
    var hasAudio by remember { mutableStateOf(context.isGranted(audioPermission)) }
    // True once the system stops showing the dialog ("don't ask again"); then we send the user to Settings.
    var blocked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) {
        hasAudio = context.isGranted(audioPermission)
        blocked = !hasAudio && !(context as Activity).shouldShowRequestPermissionRationale(audioPermission)
    }

    var askedOnStart by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!askedOnStart && requestedPermissions.any { !context.isGranted(it) }) launcher.launch(requestedPermissions)
        askedOnStart = true
    }
    // Re-check on every resume: the user may have changed the permission in Settings,
    // and re-reading the library drops songs that were deleted in the meantime.
    LifecycleResumeEffect(Unit) {
        hasAudio = context.isGranted(audioPermission)
        if (hasAudio) vm.loadLibrary()
        onPauseOrDispose {}
    }

    Box(Modifier.fillMaxSize().background(Retro.Desktop)) {
        if (hasAudio) {
            MainScreen(vm)
        } else {
            PermissionScreen(
                blocked = blocked,
                onRequest = {
                    if (blocked) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    } else {
                        launcher.launch(requestedPermissions)
                    }
                },
            )
        }
    }
}

@Composable
private fun PermissionScreen(blocked: Boolean, onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Win95Window("Retro Player", Glyphs.Note, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LcdPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RetroText("NO ACCESS TO MUSIC", size = 28.sp, font = RetroFonts.Lcd)
                        RetroText(
                            "Retro Player needs permission to read the audio files on this phone. " +
                                "Without it, there is nothing to play.",
                            maxLines = 6,
                        )
                        if (blocked) {
                            RetroText(
                                "Android will not ask again. Open Settings > Permissions and allow \"Music and audio\".",
                                color = Retro.LcdTextDim,
                                maxLines = 6,
                            )
                        }
                    }
                }
                Win95Button(onRequest, Modifier.fillMaxWidth()) {
                    RetroText(if (blocked) "Open Settings" else "Allow access", size = 15.sp, font = RetroFonts.Chrome)
                }
            }
        }
    }
}

@Composable
private fun MainScreen(vm: PlayerViewModel) {
    val tab = vm.tab
    if (tab == null) {
        RetroText("LOADING...", Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp), align = TextAlign.Center)
        return
    }
    // Kept here so each list remembers its scroll position while you switch tabs.
    val libraryListState = rememberLazyListState()
    val listenAgainListState = rememberLazyListState()

    // Back steps one tab to the left (like swiping right, which is also what the edge back
    // gesture looks like) until it reaches the start tab, then leaves the app.
    val homeTab = if (vm.listenAgain.isEmpty() && vm.recentlyPlayed.isEmpty()) Tab.LIBRARY else Tab.LISTEN_AGAIN
    BackHandler(enabled = tab != homeTab) {
        vm.tab = if (tab.ordinal > homeTab.ordinal) Tab.entries[tab.ordinal - 1] else homeTab
    }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(tab) { focusManager.clearFocus() }

    val scope = rememberCoroutineScope()
    val showArtist: (String) -> Unit = { artist ->
        vm.query = artist
        vm.tab = Tab.LIBRARY
        scope.launch { libraryListState.scrollToItem(0) }
    }

    val currentTab by rememberUpdatedState(tab)
    Column(
        Modifier
            .fillMaxSize()
            // The taskbar reaches the screen edges and runs under the navigation bar.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding()
            .pointerInput(Unit) {
                // Swipe left/right anywhere to change tabs. Lists, the seek bar and the scrollbar
                // handle their own drags first, so this only sees mostly-horizontal swipes.
                val threshold = 56.dp.toPx()
                var distance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        distance += dragAmount
                    },
                    onDragEnd = {
                        if (abs(distance) > threshold) {
                            val next = currentTab.ordinal + if (distance < 0) 1 else -1
                            Tab.entries.getOrNull(next)?.let { vm.tab = it }
                        }
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            Modifier.weight(1f).padding(start = 6.dp, top = 6.dp, end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.LISTEN_AGAIN -> ListenAgainScreen(vm, listenAgainListState)
                    Tab.LIBRARY -> LibraryScreen(vm, libraryListState)
                    Tab.NOW_PLAYING -> NowPlayingScreen(vm, onMinimize = { vm.tab = Tab.LIBRARY }, onShowArtist = showArtist)
                }
            }
            if (tab != Tab.NOW_PLAYING && vm.playback.songId != null) {
                MiniPlayer(vm, onOpen = { vm.tab = Tab.NOW_PLAYING })
            }
        }
        // Tabs sit at the bottom, within thumb reach.
        Taskbar(tab, onSelect = { vm.tab = it })
    }
}

@Composable
private fun Taskbar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Retro.Face).bevel().selectableGroup()) {
        Row(
            Modifier.height(58.dp).padding(start = 6.dp, top = 7.dp, end = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Tab.entries.forEach {
                val active = it == current
                Win95Button(
                    onClick = { onSelect(it) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    latched = active,
                    role = Role.Tab,
                    contentColor = if (active) Retro.LcdText else Retro.TextOnFace,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Glyph(it.icon)
                        RetroText(it.label, size = 14.sp, font = RetroFonts.Chrome, bold = active)
                    }
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

private val Tab.icon
    get() = when (this) {
        Tab.LISTEN_AGAIN -> Glyphs.ListenAgain
        Tab.LIBRARY -> Glyphs.Library
        Tab.NOW_PLAYING -> Glyphs.Playing
    }
