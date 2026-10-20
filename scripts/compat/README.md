# Device compatibility runs

Headless Android emulators on Gaming PC (CachyOS, KVM): Android SDK in `~/Android/Sdk`,
working folder `~/lighthouse-compat` (`lighthouse.apk`, `Games/` test ROM names, `shots/`).

```bash
scp app/build/outputs/apk/sideload/profile/app-sideload-profile.apk gaming-pc:lighthouse-compat/lighthouse.apk
scp scripts/compat/*.sh gaming-pc:lighthouse-compat/
ssh gaming-pc 'cd ~/lighthouse-compat && ./compat.sh boot 33 5560 && ./compat.sh boot 30 5562'
ssh gaming-pc '~/lighthouse-compat/sweep.sh 5562 p'      # 7 device profiles x 4 screens -> shots/p-*.png
ssh gaming-pc 'cd ~/lighthouse-compat && ./compat.sh stop 5560 && ./compat.sh stop 5562'
```

Profiles (`sweep.sh`): Odin 2 Portal, Odin 2, Odin 2 Mini, Retroid Pocket Mini, RG406H (960×720),
RG Cube (720×720), RG405M (640×480 @240dpi, the smallest at ~427×320dp).
Emulator quirks: reinstalling can hand Home back to the Pixel launcher (compat.sh re-asserts the
role); Android's one-time "Viewing full screen" tip swallows the first key press after a reset.
