# Retro Player

A small music player for Android that looks like early-2000s MP3 players and desktop software: gray
beveled buttons, a green LCD screen, and monospace text. It plays the audio files already on your
phone, keeps playing in the background, and remembers what you listen to.

## Features

- **Library**: every music file on the phone with title, artist and duration. A search field
  filters by title and artist as you type, and a scrollbar lets you jump through long lists.
- **Random song**: one button that immediately plays a random track.
- **Shuffle**: turning it on shuffles everything after the current song. Tapping a song that is
  already in the queue plays it right away without reshuffling, so Up Next stays the same.
- **Now Playing**: play/pause, previous, next, shuffle, a seek bar, and the current time and duration.
- **Queue**: shows what plays next. Tap a song to jump to it, or tap X to remove it.
- **Share**: sends the current song's audio file through the standard Android share sheet.
- **Listen Again**: the songs you played most in the last 30 days (up to 20) as a grid of tiles,
  plus your last 20 different songs under **Recently Played**. **Play all** plays the grid as a
  queue (shuffled if shuffle is on). Songs deleted from the phone are hidden.
- **Background playback** with a media notification and lock-screen controls.
- **Dark mode** that follows the system theme: charcoal instead of gray, same green LCD.

### Navigation

- Three tabs at the bottom of the screen: **Listen Again**, **Library**, **Now Playing**.
- Swipe left or right anywhere to switch tabs.
- Back steps one tab to the left, then leaves the app.
- A mini player above the tabs shows the current song with a play/pause button. Tap it to open
  Now Playing.

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
| `PlaybackService.kt` | Owns the ExoPlayer and the media session (notification, lock screen), counts plays, and manages the shuffle order. |
| `PlayerViewModel.kt` | Connects the UI to the service through a `MediaController` and holds the screen state. |
| `History.kt` | Room database of plays (song ID and time) and the Listen Again queries. |
| `ui/Retro.kt` | The palette, beveled borders, buttons, LCD panel, seek bar and scrollbar. |
| `ui/Screens.kt` | Library, Listen Again, Now Playing, the queue and the mini player. |
| `ui/App.kt` | Permissions, tabs, swipes and Back handling. |

The player lives in a Media3 `MediaSessionService`, so music keeps playing when the app is closed.
The UI talks to it through a `MediaController`.

Each queue remembers which list it came from (Library, Listen Again or Recently Played). When you
tap a song from that same list, the app sends the service a `PLAY_NOW` command instead of replacing
the queue. In shuffle mode the service then moves that song to play right now and keeps everything
that was up next in the same order.

## Tech stack

- Kotlin, Jetpack Compose (foundation only, no Material components)
- AndroidX Media3 1.11 (ExoPlayer and MediaSession)
- Room 2.8 for listening history
- Android Gradle Plugin 9.5, Gradle 9.8
- minSdk 26, targetSdk 37

## License

[MIT](LICENSE)
