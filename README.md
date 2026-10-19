# Lighthouse

An open-source, controller-first game launcher for Android handhelds.

Lighthouse puts your Android apps and emulated games in one library, with a tab per platform switched by the shoulder buttons. It uses cover-art views and a fully custom theme engine. It has **no analytics or tracking** and works without root.

> **Status:** early development (milestone 0). Built first for the AYN Odin2 Portal (LineageOS); other Android handhelds should work as it matures.

## Goals
- Platform tabs (L1/R1). Android apps and games side by side.
- Grid, carousel, list + details, and dashboard layouts.
- Covers from SteamGridDB, libretro thumbnails, or your own images.
- Themes are folders with a `theme.json` plus per-view backgrounds, and every view (Favourites included) can be themed.
- ROM folders are watched, so new games appear automatically.
- Play-time tracking, collections, search, Moonlight host apps, and save-sync awareness.

## Build flavours
| Flavour | Storage | Folder watching | Distribution |
|---|---|---|---|
| `sideload` | All files access | Instant (file observers) | GitHub Releases, Obtainium, adb |
| `play` | Folders you pick | Rescans on resume and periodically | Future Play Store build |

## Building
Requires JDK 21 and the Android SDK (API 37).

```bash
./gradlew assembleSideloadDebug      # or assemblePlayDebug
scripts/deploy.sh                    # build, install and launch on a USB-connected device
```

## Licence
GPL-3.0-or-later. See [LICENSE](LICENSE).

This is an independent project, written from scratch. It is not affiliated with, and contains no code or assets from, any other launcher.
