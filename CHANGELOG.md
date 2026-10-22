# Changelog

## 0.1.6 (in progress)

**New**
- **PS1** games through DuckStation (from the Play Store) or RetroArch with PCSX ReARMed: .cue, .chd, .pbp and .m3u in a `PS1` (or `PSX`, `PlayStation`) folder, with libretro box art. For a .cue or .m3u game, the emulator can read the game's folder (for its track and disc files) for that launch.
- **PSP** games through PPSSPP (or PPSSPP Gold): .iso, .cso, .chd and .pbp in a `PSP` (or `PlayStation Portable`) folder, with libretro box art.
- **NES, SNES, Mega Drive, Master System, Game Gear, PC Engine and Atari 2600** through RetroArch (cores: FCEUmm, Snes9x, Genesis Plus GX, Beetle PCE Fast, Stella). Put games in a folder named for the system (`NES`, `SNES`, `Genesis`, `Master System`, `Game Gear`, `PC Engine`, `Atari 2600`, and the usual alternative names); download the core once in RetroArch. Libretro box art works for all of them.
- **Download missing RetroArch cores in one tap (root, optional).** When a game needs a core RetroArch doesn't have, the "Core not installed" pop-up now names every core that will do (e.g. Snes9x, or Snes9x 2010/2005/2002) and, on a rooted device, offers **Download core**: it fetches the core from libretro's own build server (buildbot.libretro.com) and installs it into RetroArch. Only when you press it, and nothing happens without root. Without root the pop-up still takes you to RetroArch's Core Downloader.

**Fixed**
- RetroArch games (GBA, N64) said "Core not installed" on rooted devices even when the core was installed. Lighthouse's root check couldn't see inside RetroArch's folder on Android 11 and later. It now looks in the right place, and if it can't check, it lets the game start instead of blocking it.
- N64 games still said "Core not installed": Lighthouse looked for a Mupen64Plus-Next core that RetroArch doesn't offer on Android. It now finds the GLES3 or GLES2 version, whichever you downloaded.

## 0.1.5 (2026-10-21)

**Improved**
- Pinned shortcut tiles show which app added them ("Shortcut · Chrome").
- Choosing a ROM folder: "Use this folder" isn't offered on a whole storage volume (open a folder inside it instead), and Lighthouse watches at most 2,000 folders so it can't use up the system's file-watch limit.

**Fixed**
- A huge or wrongly named file (a cover image, a download, a game-ID file) could crash Lighthouse by filling its memory. Anything over 20 MB for images, or 64 bytes for ID files, is now ignored.
- Emulators no longer keep read access to every game you've launched until the next reboot; access lasts for the launch only.
- The list of games already offered a cover search no longer keeps games you've deleted.

## 0.1.4 (2026-10-21)

**Improved**
- Returning to Lighthouse (after a game, or from Android settings) does less work: repeated refreshes are merged, Moonlight's list is reread at most every 5 minutes (Settings → rescan forces it), and Lighthouse doesn't look for your own cover images for games that already have a downloaded or picked cover.

**Fixed**
- Setup went back to step 1 after making Lighthouse the Home app. It now carries on where you were.
- A cover search that got an unexpected reply (a Wi-Fi sign-in page, a proxy) could crash Lighthouse. It now just finds nothing.
- Coming back to HOME with a lower row (such as Platforms) or a far-right item remembered left nothing focused, so the controller did nothing on HOME.
- Send from phone: closing the pop-up while it was starting could leave its server running in the background. It now always stops.

## 0.1.3 (2026-10-21)

**New**
- Signed release APKs are published on the GitHub Releases page, with a `SHA256SUMS` file and the signing certificate's fingerprint. Install it like any APK, or add the repository to Obtainium to get updates.
- The release build installs as its own app ("Lighthouse", `io.cttech.lighthouse`), separate from development builds.
- Settings → About lists the open-source libraries Lighthouse uses, with their licences.

**Fixed**
- The focused cover in the carousel was blurry: artwork is now loaded at the size it's shown when enlarged.
- Root commands that include a file path now quote it safely, so unusual file names can't break them.

## 0.1.2 (2026-10-20)

**Fixed**
- Android 11 crashed at startup. Bluetooth device names on Android 11 also work now.
- Start pressed whatever was highlighted when nothing used it (for example, it opened the highlighted app in the app drawer). It now does nothing there.
- The first controller press after a restart or update was lost.
- Setup: pressing Down to reach the buttons landed on Back instead of Next.

**Small screens** (4:3 and square handhelds, from 640×480)
- The top bar keeps room for tabs: smaller status icons, and no battery percentage on narrow screens.
- Button hints stay on one line; the least important drop off first.
- Settings and setup keep side margins; the Settings tabs scroll; setup shows "Step 2 of 6".
- Pop-ups fit the screen and scroll when they're taller than it.

## 0.1.1 (2026-10-20)

**New**
- **PC game apps:** DroidDeck and GameNative games appear on their own tabs, kept in sync by each app's folder sync. GameNative's Epic, GOG, Amazon and custom games launch with the right store.
- **Moonlight:** your PCs' games on a Moonlight tab and an "On your PC" HOME row, with box art; A starts the stream directly (root needed to read the game list).
- **Shortcuts:** "Create shortcut" / "Add to home screen" from any app goes to a Shortcuts tab.
- **HOME sections:** reorder or hide each HOME row (Settings → Appearance); Favourites now comes first. The library stats line is optional, and off by default.
- The Home button returns to HOME from Settings, the app drawer and any pop-up.

**Improved**
- A bigger tab bar that uses the free space, with faded edges when tabs don't fit.
- Focus fixes: the first press after touching the screen works, and Down from the SteamGridDB key field goes to the right button.

## 0.1 (2026-10-20)

First tagged version, used daily on an AYN Odin 2 Portal.
- **Library:** Switch, PS2, GBA, GBC, N64 and Android, found automatically and watched for changes.
- **Launching:** through per-emulator profiles (Eden, ARMSX2, RetroArch, DroidDeck, GameNative), with clear fixes when something's missing.
- **Covers:** your own images, libretro box art, SteamGridDB, and a cover picker.
- **Interface:** HOME dashboard, carousel and grid shelves, quick settings, app drawer, and four themes plus user themes.
- **Setup and Settings:** a first-run setup wizard and full controller-driven Settings, and Lighthouse can be the default Home app.
