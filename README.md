# MeNothing Video Player

A Nothing OS-inspired Android video player starter project built with Kotlin, Jetpack Compose, and AndroidX Media3.

## Current features

- Dark, minimalist Nothing-inspired library UI
- Import multiple local videos through Android's system document picker
- Playback using AndroidX Media3 ExoPlayer
- Play/pause and ±10-second seek controls
- Horizontal swipe to seek ±10 seconds
- Playback speed menu (0.5×–2×)
- Seek bar and current/duration display
- Playlist / queue selector
- External subtitle file picker (SRT-oriented)
- Settings dialog for default speed and subtitle track toggle
- Android system bars styled dark

## Build the APK

Requirements: Android Studio with Android SDK 36 installed, JDK 17, and Gradle support. Open this folder in Android Studio and allow Gradle sync to finish. Then choose **Build → Build APK(s)**. The debug APK is usually written to:

`app/build/outputs/apk/debug/app-debug.apk`

CLI build (after Gradle is available):

```bash
gradle assembleDebug
```

> This source has not been compiled in this environment because Android SDK / Gradle are not installed here. Treat it as a starter project and run the build in Android Studio; fix any SDK/dependency sync issues shown there before installing.

## GitHub

Create a repository named `menothing-video-player` under your GitHub account, then upload this project's files. The connected GitHub integration currently lists no accessible repositories and does not expose a create-repository action, so this project could not be pushed directly from this session.
