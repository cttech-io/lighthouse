# Builds made-up "games" with generated placeholder covers for README screenshots (no real titles or box art).
import os, random, colorsys, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
OUT = sys.argv[1]
BOLD = "/usr/share/fonts/noto/NotoSans-Bold.ttf"
REG = "/usr/share/fonts/noto/NotoSans-Regular.ttf"
GAMES = {
    "Switch": (".nsp", ["Lumen Drift", "Paper Comets", "Harbor Lights", "Moss and Marrow", "Signal Fire", "Neon Orchard", "Quiet Glacier"]),
    "PS2": (".iso", ["Copper Tide", "Foxglove Run", "Iron Lullaby", "Velvet Circuit", "Ashfall Rally"]),
    "GBA": (".gba", ["Pocket Atlas", "Pixel Lantern", "Kite Knight", "Mint Dungeon", "Comet Courier", "Tiny Tempest"]),
    "GBC": (".gbc", ["Bramble Bay", "Glow Worm", "Puzzle Pier"]),
    "N64": (".z64", ["Cloud Derby", "Marble Peaks", "Orbit Golf"]),
}
random.seed(7)
def cover(title, plat, path):
    W, H = 600, 900
    h = random.random()
    c1 = tuple(int(x * 255) for x in colorsys.hsv_to_rgb(h, 0.65, 0.85))
    c2 = tuple(int(x * 255) for x in colorsys.hsv_to_rgb((h + 0.12) % 1, 0.8, 0.25))
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(c1[i] * (1 - t) + c2[i] * t) for i in range(3)))
    # soft shapes so covers don't all look the same
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0)); ld = ImageDraw.Draw(layer)
    for _ in range(6):
        r = random.randint(60, 220); x = random.randint(-50, W); y = random.randint(150, H - 200)
        ld.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, random.randint(18, 45)))
    img = Image.alpha_composite(img.convert("RGBA"), layer.filter(ImageFilter.GaussianBlur(6))).convert("RGB")
    d = ImageDraw.Draw(img)
    words = title.upper().split()
    lines, cur = [], ""
    for w in words:
        if len(cur) + len(w) > 10 and cur: lines.append(cur); cur = w
        else: cur = (cur + " " + w).strip()
    lines.append(cur)
    size = 86
    while size > 40 and max(d.textlength(ln, font=ImageFont.truetype(BOLD, size)) for ln in lines) > W - 88:
        size -= 2
    f = ImageFont.truetype(BOLD, size); step = int(size * 1.12)
    y = H - 140 - len(lines) * step
    for ln in lines:
        d.text((44, y), ln, font=f, fill=(255, 255, 255)); y += step
    d.text((46, 44), plat, font=ImageFont.truetype(REG, 30), fill=(255, 255, 255, 200))
    d.text((46, H - 70), "PLACEHOLDER ART", font=ImageFont.truetype(REG, 22), fill=(255, 255, 255))
    img.save(path, "PNG")
for plat, (ext, titles) in GAMES.items():
    os.makedirs(f"{OUT}/{plat}", exist_ok=True)
    for t in titles:
        open(f"{OUT}/{plat}/{t}{ext}", "wb").write(b"\0" * 1024)
        cover(t, plat, f"{OUT}/{plat}/{t}.png")
print("ok")
