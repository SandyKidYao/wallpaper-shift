# 壁纸时移 · Android

The Android app: it switches the lock screen and home wallpaper on a daily schedule. You build a
**wallpaper set** (pictures, each with a start time), and the active set takes over the wallpaper at
each start time. The lock screen gets the sharp picture and the home screen gets a blurred copy.

MVP scope: one active set at a time, a daily schedule (no weekday rules), and one picture per time slot,
used for both screens. The product, visual and behaviour spec shared by all platforms lives in
[`../design/README.md`](../design/README.md).

## Build

Requires the Android SDK (compileSdk 36). The JDK bundled with Android Studio works. Run from this
directory:

```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest assembleDebug     # → app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                      # → app/build/outputs/apk/release/app-release.apk
```

The debug build installs as `com.wallpapershift.app.debug` and shows up on the launcher as 「壁纸时移 Dev」,
so it can sit next to the release app without confusion. Don't leave both with a set enabled: each would
switch the wallpaper on its own schedule. Every build bundles the six demo pictures
from `../shared/demo-assets/demo/`, which `app/build.gradle.kts` adds as an asset folder. Release builds
(`com.wallpapershift.app`, 0.1.0) are minified, about 3.9 MB with the demo pictures. They are signed with
the key in `../.local/release-signing/` (`keystore.properties` plus the `.jks`). That folder is not in
version control, so back it up: updates must be signed with the same key. Without it the release build
comes out unsigned. The debug and release builds are separate apps on the phone and don't share data.

### CI

`.github/workflows/android.yml` runs the unit tests and builds both APKs on every push to `main` and
on every pull request. The APKs are attached to the run as an artifact (`wallpaper-shift-<version>-<sha>.apk`
plus a `-debug` one). Pushing a tag like `v0.2.0` also publishes the signed release APK as a GitHub
Release, so bump `versionCode` / `versionName` in `app/build.gradle.kts` first. CI signs with the same key,
restored from four repository secrets into `../.local/release-signing/`: `RELEASE_KEYSTORE_BASE64`
(the `.jks`, base64), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. Without
them (e.g. pull requests from forks) the release APK comes out unsigned.

On first launch (`DemoSet.setUpOnFirstLaunch`, once per install) the app creates the demo set if the user
has no sets yet, leaving it disabled. It also copies the pictures into the photo library under
`Pictures/壁纸时移/` through MediaStore. That needs no permission on Android 10+, and is skipped on
Android 9.

Moving sets between installs (debug ↔ release, or to a new phone) goes through the set package, not adb:
「导出」 in the editor saves `Download/壁纸时移/<name>.zip`, and 「导入」 on the home screen reads it back
(format in `../design/README.md` §8.9). `SetPackage` reads and writes the zip and is plain JVM code;
`SetImporter` and `SetExporter` connect it to the file chooser, MediaStore and `WallpaperLibrary`. A zip
opened or shared from another app (`ACTION_VIEW` / `ACTION_SEND`, `application/zip`) lands in `MainActivity`,
which is `singleTask` so the import runs in our own task.

## How it works

```
UI (Compose) ──► WallpaperLibrary ──► SetRepository (files/sets.json)
                      │                ImageStore   (files/images/<item>/lock.jpg, thumb.jpg, home_bN.jpg)
                      ▼
               WallpaperSwitcher.sync()  ◄── SwitchAlarmReceiver (each boundary)
                 │        │              ◄── RestoreScheduleReceiver (boot, time/zone change, update,
                 │        │                   exact-alarm permission granted)
                 ▼        ▼              ◄── app launch / resume
     WallpaperApplier   SwitchAlarmScheduler
     (WallpaperManager)  (one AlarmManager alarm: the next boundary)
```

- **`sync()` is the only switching entry point.** It reads the active set, works out which picture
  should be showing *now*, applies it if it isn't already showing, and arms a single alarm for the next
  boundary. Because it is idempotent, every trigger (alarm, boot, clock change, edit) just calls it.
- **Images are pre-rendered at import time.** Each picture is copied into private storage (picker
  grants are temporary) and scaled to cover the screen. Its blurred variant is rendered right away. At
  alarm time the receiver only streams two files into `WallpaperManager`, so it fits easily within the
  broadcast time budget.
- **Blur** is a three-pass box blur (close to a Gaussian) in pure Kotlin. It runs at a fixed 240px
  working width, so a given strength looks the same in the editor preview and on the real wallpaper.
- **Timing:** with "Alarms & reminders" access the switch is exact. Without it, the app falls back to a
  10-minute delivery window, and while the phone is dozing the switch happens when the screen wakes.
- **Manual preview** ("立即应用这张") holds the chosen picture until the next boundary (`holdUntil`);
  editing the active set or "恢复按时间表" releases it.

## OEM behaviour (verified on vivo V2454A, OriginOS 6 / Android 16)

Lock and home wallpapers set through `WallpaperManager` both show up. The vivo lock screen draws its
own clock over our picture.

With vivo's defaults, timed switching **does not work**. It needs three vivo settings, and the app guides
the user to all of them:

| Setting | Default | What happens at the default |
|---|---|---|
| 自启动 (autostart) | off | The alarm fires, but the system won't start the killed process for it (`dumpsys activity broadcasts history`: `SKIPPED … reason: skip for auto start`). Each alarm arms the next, so the chain stops. `BOOT_COMPLETED` is blocked too. |
| 后台耗电 (background power) | 智能控制 | Within seconds of leaving the foreground, the app is frozen and its alarms are taken off the system queue (`dumpsys alarm` removal history: `Reason=frozen`). They are **not delivered at their time**, not even when the screen turns on. They come back only when the app is opened or its process is killed. |
| 睡眠待机优化 (电池 → 省电管理) | on | Phone-wide, no per-app exemption. Inside the sleep window vivo predicts (secure settings `vivo_aisleep_start_time` / `vivo_aisleep_end_time`, e.g. 23:41–07:05), idle apps are frozen **even with 后台耗电 allowed**, and the alarm is withheld as above. Seen 2026-11 as nightly `Reason=frozen` removals (23:33–06:07) of the 05:00 or the sunrise alarm, which the user took for a sunrise bug. |

- With both set to allow, switches fired within 50 ms of their time in forced deep Doze with the
  screen off. That held both while the app was alive and after it was swiped away from recents.
  Swiping kills the process but does not force-stop the app (`stopped=false`), so the alarm survives.
- The standard battery-optimization whitelist made no difference on vivo, so on phones with known
  OEM steps the app doesn't ask for it.
- What the app does about it: `BackgroundSettings` links to each OEM page. vivo's autostart page is
  reachable via `com.iqoo.secure.BGSTARTUPMANAGER`. Its background-power page is protected by a vivo
  permission, so the app opens app info and shows the path 电量 → 后台耗电管理 → 允许后台耗电. When the
  app opens, `sync(checkMissed = true)` flags an exact alarm that is more than 15 min overdue, and the
  home screen then explains it.
- The app reads 自启动 back (`BackgroundSettings.detect`) every time the home screen resumes, so its
  guidance row disappears as soon as the user returns with it on. It is the app's row in
  `content://com.vivo.permissionmanager.provider.permission/bg_start_up_apps`, which a normal app can
  query: `currentstate` 0 = allowed, 1 = blocked (verified by toggling it), `setbyuser` 1 once the user
  has touched it.
- 睡眠待机优化 is read from the system setting `power_sleep_mode_enabled` (1 = on), so that row also
  disappears by itself. Its page (电池 → 省电管理) has no direct entry; the app opens the battery page
  (`com.iqoo.powersaving/.PowerSavingManagerActivity`) and shows the path.
- 后台耗电 can't be read. The power app's provider needs `READ_SEARCH_INDEXABLES`, and neither the
  battery-optimization whitelist (`dumpsys deviceidle whitelist`) nor the standby bucket follows the
  setting: switched to 智能控制, the app stayed a `user,` whitelist entry and in bucket 5 (EXEMPTED). So
  that row keeps an outlined 「我已设置」 button. Plain-text 「已设置」 read like a status label, and the
  user never tapped it.
- The first time the app sets a wallpaper, vivo asks 「“壁纸时移”请求修改壁纸」 with 允许 / 使用应用时允许 /
  禁止, and 禁止 counts down from 7 s, so an unattended dialog denies. Timed switches run in the
  background, so the user has to pick 允许. Clearing the app's data (`pm clear`) resets this choice and
  the dialog comes back. Whether 使用应用时允许 blocks the background switches is not yet verified.
- USB installs need on-screen confirmation, so the phone must be unlocked during `adb install`.
- Testing pitfall: killing the process by hand makes vivo put a frozen app's alarms back in the queue,
  which makes delivery look fine. Test with the app simply sent to the background instead.

## Testing

- Unit tests (JVM): `DayScheduleTest` covers the schedule maths (wrap-around, DST, boundaries,
  auto-spread start times), `BoxBlurTest` covers the blur, and `SetPackageTest` covers the set package
  (manifest rules, folders and macOS leftovers, GBK names, limits, export → import round trip).
- On-device hooks (debug builds only, see `app/src/debug/.../DebugReceiver.kt`). On vivo, open the app
  first: a broadcast that reaches a frozen process can be dropped.

```bash
R=com.wallpapershift.app.debug/com.wallpapershift.app.debug.DebugReceiver
adb shell am broadcast -n $R -a DEMO                                     # demo set, natural times
adb shell am broadcast -n $R -a DEMO --ei startInMin 1 --ei everyMin 1   # whole day in 6 minutes
adb shell am broadcast -n $R -a DEMO --ei copies 2 --ez activate false --es name scroll-test   # 12 pictures, inactive
adb shell am broadcast -n $R -a DELETE_SET --es name scroll-test
adb shell am broadcast -n $R -a SYNC --ez force true
adb shell am broadcast -n $R -a RESET                                    # delete all sets
adb logcat -s WallpaperSwitcher WallpaperShiftDebug
adb shell dumpsys wallpaper          # lock and system wallpapers should have different ids
adb shell dumpsys alarm | grep -A3 com.wallpapershift.action.SWITCH
```
