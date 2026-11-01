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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.retroplayer.PlayerViewModel
import com.retroplayer.Tab
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Retro.Face)
            .safeDrawingPadding()
            .padding(6.dp),
    ) {
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
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LcdPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RetroText("NO ACCESS TO MUSIC", color = Retro.LcdText, size = 20.sp, bold = true)
                RetroText(
                    "Retro Player needs permission to read the audio files on this phone. " +
                        "Without it, there is nothing to play.",
                    color = Retro.LcdText,
                    maxLines = 6,
                )
                if (blocked) {
                    RetroText(
                        "Android will not ask again. Open Settings > Permissions and allow \"Music and audio\".",
                        color = Retro.LcdDim,
                        maxLines = 6,
                    )
                }
            }
        }
        RetroButton(
            if (blocked) "OPEN SETTINGS" else "ALLOW ACCESS",
            onClick = onRequest,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            size = 18.sp,
        )
    }
}

@Composable
private fun MainScreen(vm: PlayerViewModel) {
    val tab = vm.tab
    if (tab == null) {
        RetroText("LOADING...", Modifier.fillMaxWidth(), align = TextAlign.Center)
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

    val currentTab by rememberUpdatedState(tab)
    Column(
        Modifier.pointerInput(Unit) {
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
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.LISTEN_AGAIN -> ListenAgainScreen(vm, listenAgainListState)
                Tab.LIBRARY -> LibraryScreen(vm, libraryListState)
                Tab.NOW_PLAYING -> NowPlayingScreen(vm)
            }
        }
        if (tab != Tab.NOW_PLAYING) {
            MiniPlayer(vm, onOpen = { vm.tab = Tab.NOW_PLAYING })
        }
        // Tabs sit at the bottom, within thumb reach.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach {
                RetroButton(
                    it.label,
                    onClick = { vm.tab = it },
                    modifier = Modifier.weight(1f),
                    latched = it == tab,
                    size = 12.sp,
                )
            }
        }
    }
}
