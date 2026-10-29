# Car Launcher — project notes for Claude

Dr. Joolay's own home-screen launcher for his Android 13 car AI box (800×480 landscape).
Package `com.carbox.launcher`, plain Java Android views (no XML layouts, no AndroidX UI), minSdk 26.

## How we work (the owner's rules)
- Edit only the parts needed; keep token use low. No screenshots or renders unless asked.
- Mockups only when asked ("show me a mockup").
- Commit locally as you go, but **do not push until the owner says "compile" / "build"**. Every push to
  `main` triggers a GitHub Actions build and release.
- Versions: `version-major.txt` (currently 0) + next number from existing `v<major>.<n>-alpha` tags,
  e.g. 0.14-alpha → 0.15-alpha. Only change the major when the owner says it's a major update.
- After a compile, check the run (`gh run watch`), read errors from the check-run annotations
  (`gh api repos/joolay/CarLauncher/check-runs/<job id>/annotations`), fix, push again.
- Speak plainly to the owner: what changed and how to use it, not class names.

## Build
- CI: `.github/workflows/build.yml` builds the APK, signs it with the fixed `app/carlauncher.keystore`
  (password `carlauncher`, so updates install over each other) and publishes a GitHub release.
- Local: `./gradlew assembleDebug` (needs JDK 17 + Android SDK 34). Install: `adb install -r app/build/outputs/apk/...`
- `voices.yml` makes neural voice mp3s into `assets/voice`; `screens.yml` renders Robolectric screenshots to branch `screens`.

## Layout of the screen
Left column: map panel (`MapPanel`, WebView `assets/map.html`, OSM or Google with key) over
Now playing (`NowPlaying`). While the map is big, the map panel shows the **Top Left Widget** (`TopLeft`:
swipe = next chosen widget, hold = chooser; same widget/settings as the tiles, made with `Widgets.TL` for its
roomier design, `W.tl`/`Widgets.ts()`). Middle: 2×4 tile grid (`Tiles`, column-major, endless wrap) that also hosts
panels (`Sheets` → `MainActivity.tileArea`/`sheetHost`), news/traffic ticker (`Ticker`), dock (`Dock`).
Ticker at the top of the middle column (hidden: map/tiles take its space, tiles padded). Right: Rail Bar (`Rail`; quick cards pop out of their icons).

## Key code
- `U` design units (`U.u(du)` for an 800×480 design), drawing helpers. `Store` = SharedPreferences
  (call `Store.init(ctx)` first in every service). `Loc` = GPS (speed, `heading()` held while parked, trip, town).
- `Widgets` = tile catalog (`CATALOG`), `make()`, class `W` (constructor + `tick()` every second),
  `configurable()/configure()` for each tile's settings. Category lists in `AppPanels.gallery` (`cats`).
  Separate tile classes: `Safety`, `Logbook`.
- `Sheets`: `show/push/showFull`, `panel(title, body, w, h)` (in the tile area panels fill it exactly).
- `SettingsPanels.main()` compact 3-column rows. Map & navigation settings = hold the map (`mapNav`);
  Music settings = hold Now playing (`music`).
- `BackButtonService` (accessibility): floating back button, hold menu (`QuickActions`), Android's
  accessibility-button tab, and `BtAuto` (reads/presses the box's "BT Phone" app for calls).
- `MediaListener` (notification listener): media sessions, nav turn-by-turn from Maps/Waze notifications,
  BT Phone call notifications. `CallState`/`CallCard`/`CallScreen`/`HfpCalls`/`CarInCallService` for calls.
- `Music` (auto play at car start + 45 s guard), `Rest` (driving timer, TTS with ducking), `Salah`,
  `SpeedLimit` (Overpass), `CarHA`/`HomeApproach`/`HA` (Home Assistant), `About` (version, crash reports
  saved via `About.record`, shown in Settings → About).

## With ADB on the box
- Crashes: `adb logcat -b crash` or Settings → About → crash reports.
- BT Phone screens for `BtAuto`: `adb shell uiautomator dump /sdcard/x.xml && adb pull /sdcard/x.xml`
  while it's ringing / on the keypad.
- Accessibility service state: `adb shell settings get secure enabled_accessibility_services`.
