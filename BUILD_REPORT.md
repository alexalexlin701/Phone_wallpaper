# Build verification — 0.5.1 / 2026-09-26

Power optimization: stride-aware bulk YUV copies, one gamma table per render, duplicate source-frame reuse, preview decoder reuse and pause/offscreen release, and wallpaper screen-off/doze playback gating. See POWER_REPORT.md for scope and measurements.

`assembleDebug testDebugUnitTest lintDebug` passed after the final lifecycle cleanup; all 15 JVM tests passed. Five device tests passed on OPPO CPH2251 / Android 13 before the final receiver-unregistration context correction. The same selected clip took 12.436 seconds to prepare versus 56.558 seconds before optimization; app CPU time was 16.110 versus 86.058 seconds. These are single-run processing measurements, not battery-drain measurements.

Version 0.5.1 / code 6 update-installed; test package removed and app reopened. No wallpaper was applied. Final build log: `.tools/power-final-build.log`; device log: `.tools/power-optimized-device.log`.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `5E1263853353566DEEE12F187B1764E7D425F5FC82388CA86C40F507687C8974`.

## Historical 0.5.0 verification — 2026-09-25

Delivered the English single-video UI. Import replaces the selected video; image imports, playlist navigation, screen-off rotation and its background service have been removed. Forward/ping-pong playback and gamma 0.5–8.0 in 0.5 steps remain. Destination selection is delegated to Android live wallpaper preview. Legacy editor data is reduced to one video without overwriting the applied wallpaper. Original media files are not deleted.

Build, unit tests and lint passed. Three focused instrumentation tests passed on the connected OPPO CPH2251 / Android 13, covering the new UI and migration/compatibility behavior. Inspected the real device preview and corrected horizontal padding. All app-owned main-source UI/resources are English; filenames retain their original language. No wallpaper was applied during verification.

Final APK update-installed successfully; version 0.5.0 / code 5. Test APK and diagnostic device files removed; app reopened. Final build log: `.tools/single-video-final-build.log`. Device test log: `.tools/single-video-device.log`.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `DA18EF2CC3C06211DB6C37EF686876EDC7308BE1B0D92AD03C9118E0E13D70E2`.

## Historical 0.4.0 verification

The following results describe the earlier mixed-media release.

Project: `D:\phone_wallpaper`, package `com.example.videowallpaper`.

## Delivered

Unified Traditional Chinese home screen, mixed SAF image/video list (up to 50), one fixed Set wallpaper action, draft/applied playlist isolation, Shuffle/Random screen-off rotation through either static flags or the live engine, persistent desired ping-pong selection and visible retry errors. Removed separate Random Wallpaper Activity and old layouts.

Three destinations work for pure images. Mixed/video lists require Android live wallpaper confirmation; the public preview intent cannot force its destination. Lock-only video is explicitly blocked with an explanation on this supported Reno6 configuration.

## Build

`assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`: BUILD SUCCESSFUL.

11 JVM tests passed. Lint: 0 errors, 81 warnings (50 unused legacy resources, 16 Kotlin convenience suggestions, 7 toolchain/dependency update notices, 4 text-localization suggestions, 2 synchronous preference commits, 2 filesystem usable-space API notices).

APK signature verification passed. Version name 0.4.0 / code 4; min 26, target 36.

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 6,064,552 bytes
- SHA-256: `61F929F9F2815277E4D15BD53FC80B74044FAFFC7C41D61072390DB24FD7DAEC`

## Device evidence

Connected OPPO CPH2251 runs Android 13, not Android 11.

- Reproduced original failure on the user's selected 1440×2560, 6.037-second, 144-frame source: MediaMetadataRetriever `No frames from retriever`.
- Replaced retrieval with sequential MediaCodec decoding and disk-backed YUV frames. Complete diagnostic render passed in 68.371 seconds; its output/temp files were removed.
- Four final device tests passed in 7.241 seconds: unified home has one apply action; grayscale forward/reverse frame order and silent MP4; cancellation cleanup; isolated draft/preview/applied state and mixed rotation selection.
- After user unlocked phone, inspected actual UI. Tapped ping-pong in the home screen; preparation completed, selection remained checked and the completed cache was saved. No wallpaper-apply button was pressed. Existing applied mode remained forward; draft is now ping-pong, ready for user confirmation.
- Final APK update-installed; package manager confirms 0.4.0/code 4. Test APK and shared diagnostic screenshots/XML removed. App reopened.

## Acceptance limits

Did not overwrite the user's wallpaper to test system destinations. Actual mixed live-engine screen-off transitions, each static destination, reboot/ColorOS background survival and Android 11 still need acceptance testing. Supported codecs and unusual HDR/rotation formats may vary by device. See TESTING.md.
