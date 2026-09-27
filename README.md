# Video Wallpaper 0.5.1

A native Kotlin Android app for one imported local video, silent wallpaper playback, and gamma adjustment. All app-owned UI text is English. Document filenames and Android-owned picker/preview screens retain their original language.

## Usage

1. Tap **Import video** to select one video using the Android document picker. A new import replaces the editor selection; images and additional playlist items are not supported.
2. Choose **Forward loop** or **Ping-pong loop (forward / reverse)**.
3. Set **Gamma** from 0.5 to 8.0 in increments of 0.5. 1.0 is unchanged; lower values brighten midtones, higher values darken them. Gamma applies to the generated ping-pong video. Forward playback uses the original file.
4. For a new ping-pong/gamma combination, tap **Prepare video**. Keep the app open while processing. Preparation can be cancelled; the selected settings remain available for retry. Completed files are cached per source and gamma.
5. Tap **Set wallpaper**, then choose the destination and confirm in the Android preview. The Reno6 system preview supports Home / Home and Lock screen; this app cannot force lock-screen-only video through the public Android API.

The app no longer offers images, playlists, shuffle, or screen-off rotation. Its rotation service and notification/foreground-service permissions have been removed. On upgrade, the editor keeps the first selected video (or the applied video if none is selected); it leaves the applied wallpaper in place until the user confirms another one. Original files are not deleted.

## Implementation

- `MainActivity`: English single-video editor and Android wallpaper preview.
- `MediaLibrary`: draft, preview snapshot, applied state, and gamma-specific video caches. Older media formats remain readable for upgrade compatibility.
- `PingPongPreparationModel`, `DecodedFrames`, `PingPongEncoder`: cancellable sequential MediaCodec decoding with disk-backed YUV frames, followed by forward/reverse encoding.
- `VideoWallpaperService`: one silent Media3 player per visible engine; releases the decoder when invisible, the screen is off, or the main display enters doze. Existing image wallpaper state remains readable for compatibility, without an image import UI.

The editor releases its preview decoder when paused or scrolled out of view, and reuses it for unchanged media. Preparation uses bulk YUV row copies and a shared gamma lookup table. See [POWER_REPORT.md](POWER_REPORT.md) for measured preparation costs and remaining battery-test limitations.

Ping-pong output is silent H.264, 30 FPS, with a maximum long edge of 1280 pixels. Decoded temporary frames consume approximately `width × height × 1.5 × frame count` bytes and are removed on completion, cancellation, or failure. Completed renders are reused. Source files are unchanged.

## Build

JDK 17, Gradle 9.6, AGP 9.4, Media3 1.11.1; min SDK 26, compile/target SDK 36.

For a fresh clone, install JDK 17 and Android SDK platform 36 (or open the project in Android Studio). Set `JAVA_HOME` to your JDK and configure the SDK location using Android Studio or a local `local.properties` file with `sdk.dir`. The `.tools` directory below is an optional local setup and is not included in Git.

```powershell
# If using the original workspace's optional local JDK:
# $env:JAVA_HOME = (Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Update-install to preserve selections and completed renders.

No internet permission, analytics, broad storage access, or uploads. Access to the imported video uses a persistent SAF read grant. Derived video files remain in private app storage and are excluded from backup. See TESTING.md for verification and device limitations.
