# Car Launcher

A home-screen launcher for Android 13 car AI boxes, designed for **800×480 landscape** screens and running full screen with no status bar.

## The home screen

- **Left:** live dark map with a **Navigate** button. While Google Maps or Waze is navigating, the map tile becomes a full **turn-by-turn card**. Below it, **Now playing** shows full-bleed album art, title, artist and controls, with synced **lyrics** scrolling over the art (from LRCLIB). Tap the lyrics to hide them for that song.
- **Middle:** a **4×2 grid of tiles**. Each tile is a folder of apps or a widget. Under the tiles is a **news / traffic ticker**, and under that the **dock** with pinned apps, recent apps and All apps.
- **Right rail (CarPlay style):** clock, weather, mobile signal, Wi-Fi, GPS satellites, hotspot and Tailscale. The settings gear and the voice assistant mic sit at the bottom.

## Widgets

Car Connect (CarPlay / Android Auto bridge such as Speed Play, mirror link, or back to the native car screen) · Speed · Trip · Heading · Weather · Go to (Home / Work) · Quick dial · Contact (photo tile) · Next prayer · Prayer times · Qibla · Home Assistant

## How to use it

| Do this | To |
|---|---|
| Long-press any tile | Edit mode: drag tiles to reorder, tap **⋯** to replace with a widget, edit a folder or change widget settings. Tap **Done** when finished |
| Tap the ticker | Switch between News and Traffic; the ring shows when it goes back to automatic |
| Long-press a dock app | Pin, unpin, hide or auto start it |
| All apps → **Select** | New folder, Add to folder, Auto start, Pin, Hide |
| Tap a rail icon | Quick card (calendar, weather forecast, signal, GPS, hotspot, Tailscale) |
| Long-press a rail icon | Open its full Android settings or app |
| Tap the gear | Settings: quick toggles, brightness, assistant, wallpaper, and everything else |

## First-time setup on the box

1. Install the APK and press Home; choose **Car Launcher** and **Always**.
2. Allow location, phone, calendar and contacts when asked.
3. Tap **Now playing** once and switch on **Car Launcher** under notification access (needed for song info, lyrics and the turn-by-turn card).
4. Optional: Settings → **Usage access** for better recent apps, and **Modify system settings** for the brightness slider.

## Good to know

- Android 13 doesn't let apps switch Wi-Fi, Bluetooth or the hotspot directly, so those toggles open Android's own panels. Tailscale connects directly.
- Spotify doesn't share its lyrics with other apps; the **Spotify lyrics** badge opens Spotify.
- Hijri dates can differ by a day from your local moon sighting; adjust in Settings → Calendar.
- Most car boxes have no compass, so the Qibla tile shows the bearing from North while parked and turns with your GPS heading while driving.
- Home Assistant needs a URL the box can reach (Tailscale or a remote URL) and a long-lived access token.

## Building

Every push to `main` builds the APK with GitHub Actions and publishes it under **Releases** as `CarLauncher.apk`.
