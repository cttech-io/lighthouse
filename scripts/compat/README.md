# Device compatibility runs

Headless Android emulators on a Linux PC with KVM (any x86-64 distro). Android SDK in
`~/Android/Sdk` (cmdline-tools, platform-tools, emulator, `system-images;android-{30,33};google_apis;x86_64`),
working folder `~/lighthouse-compat` holding `lighthouse.apk`, a `Games/` folder of test files named like
ROMs (contents don't matter), and `shots/` for screenshots. `HOST` below is that PC's SSH name.

```bash
scp app/build/outputs/apk/sideload/profile/app-sideload-profile.apk $HOST:lighthouse-compat/lighthouse.apk
scp scripts/compat/*.sh $HOST:lighthouse-compat/
ssh $HOST 'cd ~/lighthouse-compat && ./compat.sh boot 33 5560 && ./compat.sh boot 30 5562'
ssh $HOST '~/lighthouse-compat/sweep.sh 5562 p'      # 7 device profiles x 4 screens -> shots/p-*.png
ssh $HOST 'cd ~/lighthouse-compat && ./compat.sh stop 5560 && ./compat.sh stop 5562'
```

Profiles (`sweep.sh`): Odin 2 Portal, Odin 2, Odin 2 Mini, Retroid Pocket Mini, RG406H (960×720),
RG Cube (720×720), RG405M (640×480 @240dpi, the smallest at ~427×320dp).
Emulator quirks: reinstalling can hand Home back to the Pixel launcher (compat.sh re-asserts the
role); Android's one-time "Viewing full screen" tip swallows the first key press after a reset.
