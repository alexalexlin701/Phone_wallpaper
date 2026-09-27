# Power optimization — 0.5.1 / 2026-09-26

## Cost assessment

During visible wallpaper playback, display output and continuous video decoding are the principal expected costs. This is a code/API assessment, not a device energy breakdown. Screen brightness, panel technology, source resolution/frame rate, and time spent on the home screen affect the result. Android's [Media3 battery guidance](https://developer.android.com/media/media3/exoplayer/battery-consumption) identifies display and decoding as major video costs and recommends SurfaceView; the editor already uses it, while the wallpaper renders directly to its system Surface.

Preparing a ping-pong clip is a separate, temporary CPU, codec, and disk workload. The previous implementation copied YUV samples individually through ByteBuffer and rebuilt gamma tables per frame. Repeated UI updates could also recreate a preview decoder unnecessarily.

## Implemented

- Wallpaper playback requires visibility, an interactive device, a non-dozing main display, and a valid Surface. Screen-off/display callbacks release the player and retain its position. Receivers/listeners are removed with the engine. No polling, wake lock, or background service was added.
- Disabled unused wallpaper offset notifications, following the [WallpaperService.Engine guidance](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine).
- Editor preview releases on pause or when scrolled out of view; returning restores playback. An unchanged URI reuses its current player.
- YUV decoding/scaling and encoder input use bulk row transfers with stride-aware sample handling. Gamma lookup is created once per render. Consecutive duplicate source frames reuse loaded/transformed bytes. Progress updates only dispatch when the percentage changes.
- Existing output resolution, frame rate, playback order, silence, and gamma semantics remain unchanged. The compatibility bitmap gamma path now uses a mutable bitmap before writing pixels.

## Same-device preparation comparison

OPPO CPH2251 (Reno6), Android 13. Same selected local video: 1440 × 2560, 6.037 seconds, 144 frames; complete ping-pong preparation with gamma 1.0. One run per version, not a statistical benchmark.

| Metric | 0.5.0 | 0.5.1 | Reduction |
| --- | ---: | ---: | ---: |
| Elapsed preparation | 56.558 s | 12.436 s | 78.0% |
| App process CPU time | 86.058 s | 16.110 s | 81.3% |

CPU time sums process threads, so it can exceed elapsed time. It excludes external codec-service/GPU energy. The phone was USB charging; these values do **not** measure battery drain or establish a battery-life percentage. Logs: `.tools/power-baseline-source.log`, `.tools/power-optimized-device.log`.

## Verification and limits

Build, debug lint, and all 15 JVM tests passed. Five device tests passed: the complete selected-source render, encoded forward/reverse order with silent output, cancellation cleanup, preview release/resume across Activity pause, and single-apply home UI. JVM coverage includes playback gating and planar/interleaved row handling with padding.

No wallpaper was applied during testing. Actual ColorOS AOD/screen-off engine lifecycle and the offscreen-scroll behavior still need device acceptance testing; the Activity pause/resume path was verified on-device. Visible continuous decoding is unchanged, so preparation improvements must not be presented as equivalent everyday playback savings.

For a battery comparison, test old/new builds unplugged at equal brightness, refresh rate, source, playback mode, and similar battery/temperature levels. Separately compare a fixed home-screen playback interval and a screen-off interval, repeat runs, and examine Perfetto/Battery Historian or external power measurements. Gamma brightness is not a substitute for measuring panel power.
