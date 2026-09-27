# Verification — 0.5.0 / 2026-09-25

Current UI: single imported video, English labels, Forward/Ping-pong playback, Gamma 0.5–8.0 in 0.5 increments, and one Set wallpaper button. New imports replace the editor selection. Images, playlists and rotation controls are removed; the rotation service is no longer declared.

Build, host unit tests and lint pass. On CPH2251 / Android 13, MainUiDeviceTest and MediaLibraryDeviceTest pass (3 tests total), covering the single import/apply actions, absence of rotation UI, gamma slider range, upgrade selection, disabled rotation, and preservation of applied state. The compatibility test for older saved playlist state is retained. Device screenshot inspection confirmed the English controls and working video preview. No wallpaper was applied during testing.

Manual acceptance: import a replacement video (cancel must preserve selection); select a gamma and Prepare video; confirm persistence after restart; confirm the destination in the Android system preview. System UI language and source filenames are outside app translation. Gamma processing and Android 11 acceptance limits from earlier versions still apply.

## Historical 0.4.0 verification

The sections below describe the older mixed-media version, not the current UI.

## Automated

Build: `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`.

Host: 11 tests cover rotation repeat avoidance, ping-pong endpoint order, timestamps and YUV conversion.

On-device CPH2251 / Android 13:

```sh
adb shell am instrument -w -r -e class com.example.videowallpaper.media.MainUiDeviceTest,com.example.videowallpaper.media.PingPongEncoderDeviceTest,com.example.videowallpaper.media.MediaLibraryDeviceTest com.example.videowallpaper.test/androidx.test.runner.AndroidJUnitRunner
```

Four final tests passed (including MainUiDeviceTest): unified home with one apply button; real grayscale forward/reverse sequence and silent MP4; cancellation removes partial output; draft and system preview isolation, preserved ping-pong intent, mixed playlist routing and repeat avoidance. Tests use fixture/cache files and isolated preferences, never set the user's wallpaper.

Explicit selected-source diagnostic:

```sh
adb shell am instrument -w -r -e class com.example.videowallpaper.media.SelectedSourceDeviceTest com.example.videowallpaper.test/androidx.test.runner.AndroidJUnitRunner
```

Reads the legacy selected URI only when explicitly invoked. Original encoder failed immediately on `getFrameAtIndex`; new sequential decoder completed in 68.371 seconds. Output and raw frames deleted afterwards. This test does not commit a prepared cache or change user preferences.

## Manual acceptance

- Add mixed images/videos through SAF; cancel picker, reject unreadable media, enforce total 50, restart and verify grants.
- Preview each media. Ping-pong stays checked while preparing, after cancellation/failure and after restart. Retry and switch forward/back using cache.
- Pure images: apply Home, Lock and Both independently and verify opposite destination when relevant.
- Mixed: choose Home or Both in system preview; confirm image and video entries render through the live engine after screen off/on. Lock-only must explain platform limitation.
- Cancel system preview while another playlist is active; verify old playlist continues unchanged.
- Enable Shuffle / Random, test multiple screen-off events, stop via notification and confirm service releases resources.
- Delete a source file, interrupt app during processing, rotate Activity, test low storage and unusual codecs.
- Test Android 11 / ColorOS separately; device used here is Android 13.

No automated test applies or overwrites the user's wallpaper. System destination behavior and long-running ColorOS screen-off rotation still require user acceptance.
