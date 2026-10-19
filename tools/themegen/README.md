# themegen

Generates the bundled background sets (Synthwave, Console Heritage, Midnight) as 1920×1080 SVGs, which are then rendered to PNG.

```bash
python3 tools/themegen/gen.py out/svg
for s in out/svg/*/*.svg; do o="out/png/${s#out/svg/}"; mkdir -p "$(dirname "$o")"; rsvg-convert -w 1920 -h 1080 "$s" -o "${o%.svg}.png"; done
```

Each theme outputs `android`, `ns`, `ps2`, `gba` and `wallpaper` backgrounds. Busy detail is kept away from the top-left (title/cover) and the bottom (button hints).
