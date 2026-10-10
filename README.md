# NOTHING Video Player

A native Android video player concept inspired by Nothing OS: monochrome surfaces, bold typography, and a restrained red accent. This is an independent project and is not affiliated with Nothing Technology Limited.

## Features
- Native Android app using Kotlin and AndroidX Media3 ExoPlayer
- Open one or multiple local video files using Android's document picker
- In-app playlist with next/previous selection
- Play/pause, seek, playback speed (0.5x–2x)
- Embedded subtitle track selection through Media3's track selector
- Attach an external `.srt` subtitle file to the current video
- Double-tap left/right side to seek ±10 seconds
- Vertical swipe on left side adjusts brightness; right side adjusts stream/player volume
- GitHub Actions workflow builds a debug APK automatically

## Build locally
1. Install Android Studio (current stable) and Android SDK Platform 35.
2. Open this project folder in Android Studio.
3. Let Gradle sync finish.
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`.

## Build through GitHub
1. Create a new **public or private** GitHub repository named `nothing-video-player`.
2. Extract this ZIP and upload all files/folders to the repository root (make sure `app/` and `.github/` are at the root).
3. Open the repository's **Actions** tab and enable Actions if prompted.
4. Run **Build Android APK** from the workflow list, or push a commit to `main`.
5. Open the completed workflow run, then download the `nothing-video-player-debug-apk` artifact.
6. Extract the artifact ZIP; install `app-debug.apk` on your Android phone. You may need to allow installation from that source.

## Notes
- This starter project uses Android's Storage Access Framework; it does not request broad storage permission.
- External subtitles are attached to the current playlist item. Supported formats depend on Media3/Android decoder support; `.srt` is configured as SubRip.
- The APK is a debug build, not a Play Store release. Do not distribute it as a production release without testing and signing it appropriately.
