# Contributing to Lighthouse

Thanks for helping. Lighthouse is a small project, so a short issue before a big pull request saves everyone time.

## Reporting a problem

Please include:
- your device and Android version, and whether it's rooted;
- the Lighthouse version (Settings → About);
- what you did, what happened and what you expected, ideally with a screenshot.

For crashes, a log helps a lot: `adb logcat -d | grep -i lighthouse`.

## Adding an emulator

Most emulators only need a launch profile in `app/src/main/assets/launch_profiles.json`:

```json
{
  "id": "eden",
  "label": "Eden",
  "platforms": ["switch"],
  "packages": ["dev.eden.eden_emulator"],
  "activity": "org.yuzu.yuzu_emu.activities.EmulationActivity",
  "action": "android.intent.action.VIEW",
  "rom": "content",
  "mime": "application/octet-stream"
}
```

- `rom`: how the game is passed. `content` passes a shareable URI, which is what most modern emulators want. `extras` passes it in intent extras, using `{path}` in `extras`.
- `core` (RetroArch): the core's path, so Lighthouse can tell you when it's missing.
- Test it on a real device. A pull request should say which device and emulator version you tested.

New platforms also need an entry in `library/PlatformCatalog.kt` (folder names and file extensions).

## Code

- Kotlin and Jetpack Compose. Match the style of the code around your change.
- **Everything must work with a controller alone.** Test with the D-pad, A/B, L1/R1 and Start, not only touch.
- Check small screens: Lighthouse runs from 640×480 up. See [`scripts/compat/`](scripts/compat/).
- Run `./gradlew testSideloadDebugUnitTest` and build both flavours (`assembleSideloadDebug assemblePlayDebug`) before opening a pull request.

## Rules

- **No analytics, tracking or ads**, and nothing that phones home without the user asking.
- **Root stays optional.** Root-only features need a sensible non-root path or message.
- **Clean room:** don't contribute code, assets or text copied from other launchers, including decompiled ones.
- Contributions are licensed under GPL-3.0-or-later, the project's licence.
