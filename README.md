# Car Launcher

A home-screen launcher for Android 13 car AI boxes, made for landscape screens.

- **Left quarter:** live dark map (OpenStreetMap) following your GPS with a **Navigate** button on top, and a **Now playing** card below (song, artist, artwork, previous / play-pause / next)
- **Middle:** **shortcut folders** and a **6-app favourites dock** with an All-apps button
- **Right edge:** a CarPlay-style status rail with the clock, date, today's high / low and animated **mobile signal, Wi-Fi and GPS** icons
- On first launch your installed apps are sorted into starter folders (Navigation, Music, Video, Social, Apps, System) and the dock is filled with common car apps

## Using it

| Do this | To |
|---|---|
| Tap a folder | Open it |
| Long-press a folder | Edit apps, rename, move to front, delete |
| Tap the **+ New** tile after your folders | Create a folder and pick its apps |
| Tap the **Now playing** card | Open the music app (or, the first time, the notification-access screen) |
| Tap an empty dock slot (+) | Add an app to the dock |
| Long-press a dock icon | Replace or remove it |
| Tap the grid button at the end of the dock | All apps |
| Long-press an app in All apps | Add to dock / folder, set as Navigate app, app info |
| Tap **Navigate** | Open your navigation app (Google Maps, Waze, …) |
| Long-press **Navigate** | Choose which navigation app it opens |

The map and weather need internet (box hotspot or SIM) and location permission. Tap ◎ to re-centre after panning. Weather shows today's low / high from Open-Meteo (no account needed) and refreshes every 20 minutes.

**Song info:** Android only shares what's playing with apps that have *notification access*. Tap the Now playing card once and switch on **Car Launcher** in that list. The play/skip buttons work even without it; only the song title and artwork need it. Some car boxes hide this setting; if so, the buttons still control your music.

**Status icons** (no text):
- Mobile bars: white = signal strength, with the network type beside them (LTE, 5G, H+, 3G…) in white when mobile data is connected and grey when it isn't; a sweeping animation = SIM found but searching for signal; slashed = no SIM.
- Wi-Fi arcs: white = signal strength; arcs pulsing = Wi-Fi on but not connected; slashed = Wi-Fi off.
- Satellite: green with signal waves streaming out = GPS fix (number = satellites in use); amber and rocking = searching (number = satellites in view); grey and slashed = GPS off or no permission.

## Getting the APK

### Option A — free cloud build on GitHub (works from a phone)

1. Sign in at github.com and create a new **public or private repository** (any name).
2. **Add file → Upload files** and upload `CarLauncher.zip` exactly as it is (don't unzip it). Commit.
3. **Add file → Create new file**, name it `.github/workflows/build.yml`, paste the contents of `build.yml`, commit.
4. Open the **Actions** tab and wait ~5 minutes for the green tick.
5. Open **Releases** (right side of the repo's main page) and download `CarLauncher.apk`.

Do step 2 before step 3, so the first build has the project to work with. If a build fails, re-run it from the Actions tab.

### Option B — Android Studio on a PC

Unzip, open the folder in Android Studio, let it sync, then **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The APK lands in `app/build/outputs/apk/debug/`.

## Installing on the car box

1. Copy `CarLauncher.apk` to the box (USB stick, download it in the box's browser, or `adb install CarLauncher.apk`).
2. Open it with the file manager and allow "install unknown apps" when asked.
3. Open **Car Launcher** once to test it, allow location and phone access (phone access lets it show LTE / 5G), and tap the Now playing card to allow notification access.
4. Press the Home button and choose **Car Launcher → Always**, or set it in **Settings → Apps → Default apps → Home app**.

To go back to the old launcher, change the Home app in the same settings screen, or uninstall Car Launcher.

## Notes

- Built with plain Android (no extra libraries); minSdk 26, targetSdk 33.
- Folders and dock are saved on the device; uninstalled apps simply disappear from them.
- Map tiles © OpenStreetMap contributors.
