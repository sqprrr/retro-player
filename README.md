# Retro Player

A small music player for Android that looks like Windows 95 desktop software with a green LCD:
beveled charcoal windows with title bars, a taskbar, and green-on-black screens. It plays the audio
files already on your phone, keeps playing in the background, and remembers what you listen to.

## Features

- **Library**: every music file on the phone with title, artist and duration, grouped by first
  letter. A search field filters by title, artist and album as you type, and a scrollbar lets you
  jump through long lists. **Play all** plays the songs shown; **Shuffle all** turns shuffle on and
  plays them starting with a random one.
- **Shuffle**: turning it on shuffles everything after the current song. Tapping a song that is
  already in the queue plays it right away without reshuffling, so Up Next stays the same.
- **Repeat**: cycles off → all → one track.
- **Now Playing**: play/pause, previous (restarts the song if more than 3 seconds in), next, shuffle,
  repeat, a seek bar, and the elapsed, total and remaining time. Long titles scroll back and forth.
  Shuffle and repeat show their state with a lit LED, a label and a pressed-in button.
- **Up Next**: shows what plays next. Tap a song to jump to it.
  Long-press a song for **Play now**, **Play next**, **Move to end**, **Go to artist**, **Share** and
  **Remove from queue**. After a removal, **Undo** puts the song back where it was. The title-bar
  button maximizes the queue over the player.
- **Share**: sends a song's audio file through the standard Android share sheet, from the player's
  ⋯ menu or a queue song's menu.
- **Go to artist**: opens Library searched for that artist.
- **Listen Again**: the songs you played most in the last 30 days (up to 20) as a grid of tiles,
  plus your last 20 different songs under **Recently Played**. **Play all** plays the grid as a
  queue (shuffled if shuffle is on). Songs deleted from the phone are hidden.
- **Background playback** with a media notification and lock-screen controls.
- **One dark look** regardless of the system theme: a charcoal desktop, green title bars and the
  green LCD. Fonts are VT323 (LCD digits), IBM Plex Mono (song text) and Pixelify Sans (window
  chrome), bundled from Google Fonts under the SIL Open Font License.

### Navigation

- A taskbar at the bottom of the screen with three tabs: **Listen again**, **Library**, **Playing**.
- Swipe left or right anywhere to switch tabs.
- Back restores a maximized queue first, then steps one tab to the left, then leaves the app.
- While a song is loaded, a docked **Now Playing** mini window above the taskbar shows it with the
  elapsed time, a progress line, play/pause and next. Tap it to open the full player. The `_` button
  on the full player goes back to Library with the mini player.

The app opens on Listen Again once you have listening history, and on Library before that.

### What counts as a play

A play is recorded once you have listened to a song for **30 seconds or half its length, whichever
comes first**. Skipping, seeking ahead and pausing don't count towards that time.

## Requirements

- An Android phone running Android 8.0 (API 26) or newer.
- To build it: JDK 17 or newer and the Android SDK with platform 37 and build-tools 37.

## Build and install

Point Gradle at your Android SDK, either with `ANDROID_HOME` or a `local.properties` file in the
project root:

```properties
sdk.dir=/path/to/android/sdk
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

With the phone connected over USB and USB debugging turned on, install and start it:

```bash
./gradlew installDebug
```

```bash
adb shell am start -n com.retroplayer/.MainActivity
```

On first launch the app asks for access to your music and for permission to show notifications.
Without music access it shows a screen with a button to ask again, or to open Settings if Android
has stopped asking.

## How it works

| File | What it does |
| --- | --- |
| `MusicLibrary.kt` | Reads music files from MediaStore. |
| `PlaybackService.kt` | Owns the ExoPlayer and the media session (notification, lock screen), counts plays, and manages the shuffle order, queue moves and undo. |
| `PlayerViewModel.kt` | Connects the UI to the service through a `MediaController` and holds the screen state. |
| `History.kt` | Room database of plays (song ID and time) and the Listen Again queries. |
| `ui/Retro.kt` | Design tokens, fonts, bevels and the Win95 parts: window, button, LCD panel, LED, slider, menu, status bar, search field, scrollbar. |
| `ui/Glyphs.kt` | The vector icons. |
| `ui/Screens.kt` | Library, Listen Again and the mini player. |
| `ui/NowPlaying.kt` | Now Playing and the Up Next queue (long-press menu, undo). |
| `ui/App.kt` | Permissions, the taskbar, swipes and Back handling. |

The player lives in a Media3 `MediaSessionService`, so music keeps playing when the app is closed.
The UI talks to it through a `MediaController`.

Each queue remembers which list it came from (Library, Listen Again or Recently Played). When you
tap a song from that same list, the app sends the service a `PLAY_NOW` command instead of replacing
the queue. In shuffle mode the service then moves that song to play right now and keeps everything
that was up next in the same order. Moving a song in the queue and undoing a removal are custom
commands too: in shuffle mode they rearrange the shuffle order instead of the playlist.

## Tech stack

- Kotlin, Jetpack Compose (foundation only, no Material components)
- AndroidX Media3 1.11 (ExoPlayer and MediaSession)
- Room 2.8 for listening history
- Android Gradle Plugin 9.5, Gradle 9.8
- minSdk 26, targetSdk 37

## License

[MIT](LICENSE)
