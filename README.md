# Lighthouse

An open-source, controller-first game launcher for Android handhelds.

Lighthouse puts your Android apps and emulated games in one library, with a tab per platform switched by the shoulder buttons. It has a console-style HOME dashboard and built-in quick settings, and **no analytics or tracking**. It works without root; root unlocks a few optional extras.

> **Status:** early development, used daily as the Home app on an AYN Odin2 Portal (LineageOS 24). Games are found and shown, but **launching ROMs is the next milestone**; Android apps already launch.

## What works today
- **Library**
  - Auto-detects platform folders under `Games/`, `ROMs/` or `Emulation/` on every storage volume.
  - Supported: Switch, PS2, GBA, GBC, N64, and DroidDeck/GameNative `.steam` shortcuts.
  - Scans recursively, cleans No-Intro names, and skips BIOS, keys, saves, and Switch updates/DLC.
  - Collapses duplicate copies across storage volumes.
- **Folder watching:** new or removed ROMs appear or disappear within about 2 seconds (`sideload` flavour).
- **Android tab:** installed apps with their real icons; A opens them.
- **HOME dashboard:** status strip, Continue playing (games only), Favourites, Recently added, Streaming, and platform shortcuts.
- **Platform tabs:**
  - L1/R1 with wrap-around; tap a tab to switch.
  - Carousel or grid (Y).
  - Focus remembered per tab; haptic tick on focus.
- **Quick settings** in the top bar (Wi-Fi · Bluetooth · Volume · Battery · Clock): built-in panels; with root, Wi-Fi, Bluetooth and battery saver switch silently.
- **Home app:** can be set as Android's default home screen. Back never exits; the Home button returns to the HOME tab.

## Planned
Launching games through per-emulator launch profiles, covers (SteamGridDB, libretro, manual), a JSON theme engine, first-run setup and settings, play-time tracking, collections, search, and Moonlight host apps. See the milestones in the project plan.

## Build flavours
| Flavour | Storage | Folder watching | Distribution |
|---|---|---|---|
| `sideload` | All files access | Instant (file observers) | GitHub Releases, Obtainium, adb |
| `play` | Folders you pick | Rescans on resume and periodically | Future Play Store build |

CI builds both, and fails if the `play` APK ever requests `MANAGE_EXTERNAL_STORAGE`.

| Build type | Use |
|---|---|
| `profile` | Release-optimised and debug-signed (ID suffix `.profile`). **Judge smoothness on this one.** |
| `debug` | Fast to build, slow to run. Labelled "Lighthouse Debug". |
| `release` | Minified; signing to be set up before public releases. |

## Building
Requires JDK 21 and the Android SDK (API 37).

```bash
./gradlew testSideloadDebugUnitTest      # scanner / title-cleaning tests
./gradlew assembleSideloadProfile        # or assemblePlayDebug, assembleSideloadDebug
scripts/deploy.sh [sideload|play] [profile|debug]   # build, install and launch on a USB device
```

`deploy.sh` launches through the HOME intent when Lighthouse is the default home app, so only one instance exists. It never presses buttons, wakes or unlocks the device.

## Project layout
```
app/src/main/java/io/cttech/lighthouse/
  library/     PlatformCatalog, FolderDetector, RomScanner, TitleCleaner, AppClassifier, LibraryRepository
  data/db/     Room entities (sources, games, favourites, plays) + DAO; schemas in app/schemas/
  storage/     StorageAccess interface (flavour implementations in src/sideload and src/play)
  system/      Wi-Fi/Bluetooth/volume/battery state, optional Root helper, AppSettings
  ui/home/     HomeScreen (tabs, carousel, grid), HomeDashboard, HomeViewModel, AccessPrompt
  ui/components/ CoverCard, top bar, quick-settings panels, status glyphs
tools/themegen/  SVG generator for the bundled background themes
```

## Licence
GPL-3.0-or-later. See [LICENSE](LICENSE).

This is an independent project, written from scratch. It is not affiliated with, and contains no code or assets from, any other launcher.
