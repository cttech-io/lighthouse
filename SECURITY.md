# Security

## Reporting a problem

Please report security problems privately, not in a public issue:
- **GitHub:** use [Report a vulnerability](../../security/advisories/new) (the Security tab → Advisories).
- **Email:** charlie@cttech.io, with "Lighthouse security" in the subject.

Include the Lighthouse version, your device and Android version, and how to reproduce the problem. You'll get a reply within a week. Lighthouse is a one-person project, so fixes go out as a normal release as soon as they're ready.

## Supported versions

Only the latest release gets fixes.

## Areas worth a look

Lighthouse is a Home app with broad access, so these parts matter most:
- **"Send from phone"** (Settings → Covers): while its QR code is showing, Lighthouse runs a one-time HTTPS page on its Wi-Fi address to receive a SteamGridDB key.
  - The page lives at a random path that's only in the QR code.
  - The certificate is self-signed, and its private key stays in the Android Keystore.
  - The server stops after the first key arrives, or when the pop-up closes.
- **Root extras:** these only run when the device is rooted and the user leaves them on. Lighthouse runs a small, fixed set of `su` commands: switching Wi-Fi, Bluetooth and battery saver, granting an emulator storage access, checking for emulator cores, and reading Moonlight's game list. Any file path in a command is shell-quoted.
- **All files access** (sideload build only): used to read ROM folders and watch them for changes.
- **Exported components:** the Home activity and the pinned-shortcut handler, which only accepts shortcuts through Android's `LauncherApps` API.

## Verifying a release

Release APKs are signed by GitHub Actions with the project's release key. Check an APK before installing it:

```sh
apksigner verify --print-certs lighthouse-<version>.apk
```

The certificate SHA-256 must be:

```
2f026aa38fc2a4fa7ef124b95f69c40ff3da34a08e66d696a56ec01ebaac1082
```

Each release also lists this fingerprint and includes a `SHA256SUMS` file.
