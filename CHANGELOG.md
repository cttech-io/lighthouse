# Changelog

## Unreleased

**New**
- **Opt-in crash reports.** If Lighthouse closes unexpectedly, it saves a report on the device and asks about it next time: read it, then **Report on GitHub** (a pre-filled issue in your browser), **Email it** (to lighthouse@cttech.io, from your own email app), **Share log**, or **Delete**. Nothing is ever sent automatically, and storage paths (which can contain your folder names) are removed from the report. The latest report stays in Settings → About.

## 0.1.8 (release candidates)

**Changed**
- **The button hints follow what's open.** Menus, dialogs, Quick Settings, the cover picker and the app drawer show their own hints (B Close, A Select…) instead of HOME's, and the hints are no longer dimmed behind a pop-up.
- **"No emulator installed" says what to get and where.** RetroArch profiles read as "RetroArch (mGBA core)" instead of "RetroArch · mGBA", and the dialog has a **Get … (browser)** button for each emulator that would work. Settings → Emulators uses the same names.
- **Setup: L1/R1 move between steps**, like the sections in Settings, so you no longer have to scroll to the Next button at the bottom of each step. R1 stops on the last step: finishing still takes Finish.
- **The cover picker works without a SteamGridDB key.** It shows libretro's box art for the game (when there is one) first, marked "libretro", with or without a key. Without a key it no longer opens on a search box that can't search: it starts on the box art, or on **Image from storage**, and **Set up SteamGridDB key** opens Settings → Covers.
- **No "N games have no cover" pop-up straight after setup.** Setup's Covers step has just asked; games added later are still offered.
- **Game and app menus are an even list.** Full-width rows instead of pills of different widths, with Hide (or Remove shortcut) set apart at the bottom.
- **Remove cover and Remove shortcut ask first.** The first A changes the button to "Press A again to remove…"; moving away or waiting a few seconds cancels. Both deleted something straight away before. The cover picker only offers Remove cover when there is one.
- **Settings and setup polish.** One "L1 R1 Section" (or "Step") hint instead of a bare L1 chip; theme tiles only outline the current theme, now labelled "Current"; Root extras says "On · no root" when root isn't available; Platforms shows how many Android apps there are; an emulator's "Ready" sits in its row like the other statuses; ROM folders only mark the ones you added; setup's "Check each folder's platform" note is above the folders it refers to.
- **Grid layout: the focused row is always whole.** Moving down scrolls the row to the top, and the row below fades out at the bottom edge instead of looking cut off by the button hints.

**Fixed**
- A game added while HOME was open didn't pick up its own cover image or get the "no cover" pop-up until the next full scan or restart. Both now happen a moment after the file arrives.

## 0.1.7 (2026-10-22)

**New**
- **DS** games through melonDS: .nds, .dsi, .srl and .ids in a `DS` (or `Nintendo DS`) folder, with libretro box art.
- **Dreamcast** games through Flycast: .gdi, .cdi, .chd and .cue in a `Dreamcast` (or `DC`) folder, with libretro box art. Both are launched the way the emulators' own manifests describe but haven't been tried on a device yet. A Dreamcast game that is split into separate track files (.gdi, .cue) may not load from Lighthouse; a single .chd or .cdi will.
- **App drawer menu.** Press Select (or long-press) on an app in the drawer for **Open**, **App info**, **Uninstall** and **Hide from Lighthouse** (or **Unhide**). Uninstall isn't offered for system apps and Hide only for apps Lighthouse lists on its shelves. The drawer reloads when you come back, so an uninstalled app disappears.

**Fixed**
- The battery icon's red "low" warning never showed in practice: Android turns battery saver on at about 15%, and saver's amber took priority. Plugged in is green, then under 15% is red, then battery saver is amber. Battery saver also gets a small plus sign so it isn't only a colour, and the colours are deeper when the icon sits on the white focus pill.
- "Send from phone" now refuses a request that sends less data than it promised (it could have been half a key) instead of relying on padding to reject it.

**Improved**
- Platform tabs now run in release order within each family: PS1, PS2, PSP; NES, SNES, N64, GameCube, Wii, Switch; GBC, GBA, 3DS; Master System, Mega Drive, Game Gear. A tab order you set yourself in Settings is unchanged.

## 0.1.6 (2026-10-22)

**New**
- **PS1** games through DuckStation (from the Play Store) or RetroArch with PCSX ReARMed: .cue, .chd, .pbp and .m3u in a `PS1` (or `PSX`, `PlayStation`) folder, with libretro box art. For a .cue or .m3u game, the emulator can read the game's folder (for its track and disc files) for that launch.
- **PSP** games through PPSSPP (or PPSSPP Gold): .iso, .cso, .chd and .pbp in a `PSP` (or `PlayStation Portable`) folder, with libretro box art.
- **NES, SNES, Mega Drive, Master System, Game Gear, PC Engine and Atari 2600** through RetroArch (cores: FCEUmm, Snes9x, Genesis Plus GX, Beetle PCE Fast, Stella). Put games in a folder named for the system (`NES`, `SNES`, `Genesis`, `Master System`, `Game Gear`, `PC Engine`, `Atari 2600`, and the usual alternative names); download the core once in RetroArch. Libretro box art works for all of them.
- **Download missing RetroArch cores in one tap (root, optional).** When a game needs a core RetroArch doesn't have, the "Core not installed" pop-up now names every core that will do (e.g. Snes9x, or Snes9x 2010/2005/2002) and, on a rooted device, offers **Download core**: it fetches the core from libretro's own build server (buildbot.libretro.com) and installs it into RetroArch. Only when you press it, and nothing happens without root. Without root the pop-up still takes you to RetroArch's Core Downloader.
- **A one-time heads-up for RetroArch games when Lighthouse can't check the core (no root).** The first time you play a game that needs a core Lighthouse can't look for, it says which core to download (and where), then **Play anyway** starts the game. It shows once per core.
- **3DS** games through Azahar: .3ds, .cci, .cxi, .3dsx and .cia in a `3DS` (or `Nintendo 3DS`) folder, with libretro box art.
- **GameCube and Wii** games through Dolphin: .iso, .gcm, .rvz, .wbfs, .wia, .ciso, .gcz (plus .dol and .elf homebrew) in a `GameCube` (or `GC`) or `Wii` folder, with libretro box art. Lighthouse starts Dolphin correctly, but playback hasn't been tried with a full game on a device yet.

**Fixed**
- Installing an Android app no longer pops up "1 game has no cover" (or searches online for it). Apps keep their icons; **Settings → Covers → Covers for Android apps** turns online covers back on for them.
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
