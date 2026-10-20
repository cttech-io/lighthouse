#!/usr/bin/env python3
"""Generate Lighthouse's bundled theme backgrounds (1920x1080 SVG).

Layout guardrails (landscape): top bar y<80, game title ~y100-140, focused
cover x140-600 / y150-830, other covers x>640 / y300-560, button hints y>1030.
Keep detail low-contrast behind those; put interest top-right and in the floor
area bottom-right.
"""
import os
import random
import sys

W, H = 1920, 1080
OUT = sys.argv[1] if len(sys.argv) > 1 else "svg"
# File names map to Lighthouse view keys: ns -> platform:switch, wallpaper -> home/default.
PLATFORMS = ["android", "ns", "ps2", "gba", "gbc", "n64", "droiddeck", "gamenative", "wallpaper"]


def svg(body, defs=""):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
            f'viewBox="0 0 {W} {H}"><defs>{defs}</defs>{body}</svg>')


def grain(opacity=0.05, seed=3):
    return (f'<filter id="grain" x="0" y="0" width="100%" height="100%">'
            f'<feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="{seed}"/>'
            f'<feColorMatrix type="matrix" values="0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 {opacity} 0"/>'
            f'</filter>')


def readability_scrim():
    # darkens the left (cover/title) and bottom (metadata/buttons) bands
    return ('<linearGradient id="scrimL" x1="0" x2="1"><stop offset="0" stop-color="#000" stop-opacity=".55"/>'
            '<stop offset=".38" stop-color="#000" stop-opacity=".15"/><stop offset=".6" stop-color="#000" stop-opacity="0"/></linearGradient>'
            '<linearGradient id="scrimB" x1="0" y1="0" x2="0" y2="1"><stop offset=".78" stop-color="#000" stop-opacity="0"/>'
            '<stop offset="1" stop-color="#000" stop-opacity=".6"/></linearGradient>'), \
           (f'<rect width="{W}" height="{H}" fill="url(#scrimL)"/><rect width="{W}" height="{H}" fill="url(#scrimB)"/>')


# ---------------------------------------------------------------- Synthwave
SYNTH = {
    #            sky top,   horizon,   sun top,   sun bottom, grid
    "android":  ("#020d0f", "#0f3b3a", "#d4ff6a", "#1de9b6", "#3ddc84"),
    "ns":       ("#12030a", "#4a0f2a", "#ffd166", "#ff4554", "#00c3e3"),
    "ps2":      ("#03041a", "#1b1460", "#7fd4ff", "#2f6bff", "#b44cff"),
    "gba":      ("#0a0420", "#3a1470", "#ffb3f0", "#9b5cff", "#ff6ad5"),
    "gbc":      ("#021012", "#0f3b38", "#c8ffe9", "#26c6a6", "#26c6a6"),
    "n64":      ("#120c02", "#3d2a07", "#fff0a8", "#ffb300", "#3fa9f5"),
    "droiddeck": ("#02070f", "#0c2a4a", "#bfe6ff", "#1a9fff", "#1a9fff"),
    "gamenative": ("#07080a", "#2a2f36", "#e8edf2", "#b0bec5", "#8fa3b8"),
    "wallpaper": ("#0b0221", "#4a0f5c", "#ffd23f", "#ff2e97", "#ff2e97"),
}


def synthwave(p):
    sky0, hor, sun0, sun1, grid = SYNTH[p]
    hy = 640                 # horizon
    cx, cy, r = 1440, 560, 250
    rnd = random.Random(p)
    stars = "".join(
        f'<circle cx="{rnd.uniform(620, W):.0f}" cy="{rnd.uniform(0, hy - 120):.0f}" '
        f'r="{rnd.choice([0.8, 1, 1.2, 1.6])}" fill="#fff" opacity="{rnd.uniform(.25, .8):.2f}"/>'
        for _ in range(140))
    # sun stripes: gaps widen toward the bottom
    stripes = ""
    y, gap = cy - 90, 3
    while y < cy + r:
        stripes += f'<rect x="{cx - r}" y="{y:.0f}" width="{2 * r}" height="{gap:.0f}" fill="#000"/>'
        y += gap + 22 - gap * 0.8
        gap += 4
    # perspective floor grid converging on (cx, hy)
    lines = ""
    for i in range(-24, 25):
        x2 = cx + i * 170
        lines += f'<line x1="{cx + i * 14}" y1="{hy}" x2="{x2}" y2="{H}"/>'
    for k in range(1, 14):
        yy = hy + (H - hy) * (k / 13) ** 2.1
        lines += f'<line x1="0" y1="{yy:.1f}" x2="{W}" y2="{yy:.1f}"/>'
    mountains = (f'<path d="M560 {hy} L760 560 L880 610 L1010 520 L1120 600 L1230 {hy} Z" fill="#000" opacity=".55"/>'
                 f'<path d="M1650 {hy} L1760 575 L1840 600 L1920 545 L1920 {hy} Z" fill="#000" opacity=".55"/>')
    sc_defs, sc_body = readability_scrim()
    defs = (f'<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{sky0}"/>'
            f'<stop offset=".59" stop-color="{hor}"/><stop offset=".6" stop-color="#05010d"/><stop offset="1" stop-color="#000"/></linearGradient>'
            f'<linearGradient id="sun" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{sun0}"/><stop offset="1" stop-color="{sun1}"/></linearGradient>'
            f'<radialGradient id="glow"><stop offset="0" stop-color="{sun1}" stop-opacity=".55"/><stop offset="1" stop-color="{sun1}" stop-opacity="0"/></radialGradient>'
            f'<linearGradient id="fade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity=".9"/><stop offset=".35" stop-color="#000" stop-opacity="0"/></linearGradient>'
            f'<mask id="sunmask"><rect width="{W}" height="{H}" fill="#fff"/>{stripes}</mask>'
            f'<clipPath id="abovehor"><rect width="{W}" height="{hy}"/></clipPath>'
            f'<filter id="blur8"><feGaussianBlur stdDeviation="2.2"/></filter>'
            + grain(.05) + sc_defs)
    body = (f'<rect width="{W}" height="{H}" fill="url(#sky)"/>{stars}'
            f'<circle cx="{cx}" cy="{cy}" r="{r * 2.2}" fill="url(#glow)"/>'
            f'<g clip-path="url(#abovehor)"><circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#sun)" mask="url(#sunmask)"/></g>'
            f'{mountains}'
            f'<g stroke="{grid}" stroke-width="2" opacity=".8" filter="url(#blur8)">{lines}</g>'
            f'<g stroke="{grid}" stroke-width="1.4" opacity=".9">{lines}</g>'
            f'<rect y="{hy}" width="{W}" height="{H - hy}" fill="url(#fade)" transform="translate(0,0)"/>'
            f'<line x1="0" y1="{hy}" x2="{W}" y2="{hy}" stroke="{sun1}" stroke-width="2" opacity=".9"/>'
            f'{sc_body}<rect width="{W}" height="{H}" filter="url(#grain)"/>')
    return svg(body, defs)


# ---------------------------------------------------------------- Console Heritage
TINT = {"gbc": "#26c6a6", "n64": "#ffb300", "droiddeck": "#1a9fff", "gamenative": "#b0bec5"}


def heritage(p):
    sc_defs, sc_body = readability_scrim()
    rnd = random.Random("h" + p)
    if p == "ps2":
        base = ('<radialGradient id="bg" cx=".75" cy=".45" r=".9"><stop offset="0" stop-color="#16307a"/>'
                '<stop offset=".55" stop-color="#0a1440"/><stop offset="1" stop-color="#02040f"/></radialGradient>')
        shapes, col = "", "#7fa8ff"
        glyphs = [("tri", 1180, 250, 120), ("circ", 1600, 230, 100), ("x", 1420, 470, 95), ("sq", 1730, 560, 110),
                  ("circ", 1050, 640, 60), ("tri", 1820, 860, 70), ("sq", 1260, 820, 70), ("x", 1580, 760, 55),
                  ("tri", 900, 90, 45), ("circ", 1880, 120, 40)]
        for kind, x, y, s in glyphs:
            o = .22 if s > 80 else .14
            st = f'stroke="{col}" stroke-width="{max(6, s // 9)}" fill="none" opacity="{o}" stroke-linecap="round" stroke-linejoin="round"'
            if kind == "tri":
                shapes += f'<path d="M{x} {y - s} L{x + s * .95} {y + s * .7} L{x - s * .95} {y + s * .7} Z" {st}/>'
            elif kind == "circ":
                shapes += f'<circle cx="{x}" cy="{y}" r="{s}" {st}/>'
            elif kind == "x":
                shapes += f'<path d="M{x - s} {y - s} L{x + s} {y + s} M{x + s} {y - s} L{x - s} {y + s}" {st}/>'
            else:
                shapes += f'<rect x="{x - s}" y="{y - s}" width="{2 * s}" height="{2 * s}" rx="{s * .08}" {st}/>'
        lines = "".join(f'<line x1="{x}" y1="0" x2="{x - 600}" y2="{H}"/>' for x in range(700, 2600, 46))
        body = (f'<rect width="{W}" height="{H}" fill="url(#bg)"/>'
                f'<g stroke="#3d5cff" stroke-width="1" opacity=".07">{lines}</g>{shapes}')
    elif p == "ns":
        base = ('<linearGradient id="bg" x1="0" x2="1"><stop offset="0" stop-color="#121214"/><stop offset="1" stop-color="#1d1d22"/></linearGradient>'
                '<linearGradient id="red" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ff5a66"/><stop offset="1" stop-color="#c8102e"/></linearGradient>'
                '<linearGradient id="blue" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2fd6f0"/><stop offset="1" stop-color="#008fb3"/></linearGradient>'
                '<filter id="soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="60"/></filter>')
        def joycon(x, y, fill, side):
            w, h, r = 300, 820, 150
            path = (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"/>')
            stick_y = y + (220 if side == "L" else 470)
            btn_y = y + (470 if side == "L" else 220)
            cx = x + w / 2
            deco = (f'<circle cx="{cx}" cy="{stick_y}" r="70" fill="#000" opacity=".35"/>'
                    f'<circle cx="{cx}" cy="{stick_y}" r="48" fill="#000" opacity=".25"/>')
            for dx, dy in ((0, -62), (0, 62), (-62, 0), (62, 0)):
                deco += f'<circle cx="{cx + dx}" cy="{btn_y + dy}" r="26" fill="#000" opacity=".3"/>'
            return path + deco
        body = (f'<rect width="{W}" height="{H}" fill="url(#bg)"/>'
                f'<circle cx="1300" cy="540" r="330" fill="#ff4554" opacity=".22" filter="url(#soft)"/>'
                f'<circle cx="1760" cy="540" r="330" fill="#00c3e3" opacity=".22" filter="url(#soft)"/>'
                f'<g transform="rotate(-12 1500 540)" opacity=".9">{joycon(1170, 130, "url(#red)", "L")}{joycon(1610, 130, "url(#blue)", "R")}</g>'
                f'<rect width="{W}" height="{H}" fill="#000" opacity=".28"/>')
    elif p == "gba":
        base = ('<radialGradient id="bg" cx=".72" cy=".5" r=".85"><stop offset="0" stop-color="#3b2a8f"/>'
                '<stop offset=".6" stop-color="#1c1450"/><stop offset="1" stop-color="#08061a"/></radialGradient>'
                '<linearGradient id="pxfade" x1="0" x2="1"><stop offset=".25" stop-color="#fff" stop-opacity="0"/><stop offset=".7" stop-color="#fff"/></linearGradient><mask id="pxm"><rect width="1920" height="1080" fill="url(#pxfade)"/></mask>'
                '<pattern id="px" width="12" height="12" patternUnits="userSpaceOnUse"><rect width="11" height="11" fill="#fff" opacity=".035"/></pattern>')
        col = "#b9a8ff"
        dpad = (f'<path d="M1180 360 h90 v-120 h120 v120 h90 v120 h-90 v120 h-120 v-120 h-90 Z" '
                f'transform="translate(-60 40)" fill="{col}" opacity=".16"/>')
        ab = (f'<circle cx="1640" cy="470" r="80" fill="{col}" opacity=".16"/><circle cx="1800" cy="380" r="80" fill="{col}" opacity=".16"/>'
              f'<text x="1640" y="500" font-family="Arial Black, Arial" font-weight="900" font-size="80" text-anchor="middle" fill="#1c1450" opacity=".6">B</text>'
              f'<text x="1800" y="410" font-family="Arial Black, Arial" font-weight="900" font-size="80" text-anchor="middle" fill="#1c1450" opacity=".6">A</text>')
        speaker = "".join(f'<circle cx="{1700 + (i % 3) * 34 + (j % 2) * 17}" cy="{780 + j * 30}" r="7" fill="{col}" opacity=".14"/>'
                          for j in range(5) for i in range(3))
        body = (f'<rect width="{W}" height="{H}" fill="url(#bg)"/><rect width="{W}" height="{H}" fill="url(#px)" mask="url(#pxm)"/>'
                f'{dpad}{ab}{speaker}')
    elif p == "android":
        base = ('<radialGradient id="bg" cx=".75" cy=".4" r=".9"><stop offset="0" stop-color="#0f3d27"/>'
                '<stop offset=".6" stop-color="#072014"/><stop offset="1" stop-color="#020805"/></radialGradient>')
        tiles = ""
        for row in range(6):
            for colm in range(8):
                x, y = 980 + colm * 128, 60 + row * 160
                if rnd.random() < .25:
                    continue
                o = rnd.choice([.06, .09, .12, .18]) * (0.5 + colm / 14)
                shape = rnd.choice(["rr", "circ", "sq"])
                if shape == "circ":
                    tiles += f'<circle cx="{x + 44}" cy="{y + 44}" r="44" fill="#3ddc84" opacity="{o:.2f}"/>'
                else:
                    rx = 26 if shape == "rr" else 10
                    tiles += f'<rect x="{x}" y="{y}" width="88" height="88" rx="{rx}" fill="#3ddc84" opacity="{o:.2f}"/>'
        body = f'<rect width="{W}" height="{H}" fill="url(#bg)"/>{tiles}'
    elif p in TINT:  # newer platforms: platform-tinted glow + fine line texture
        c = TINT[p]
        base = (f'<radialGradient id="bg" cx=".75" cy=".45" r=".9"><stop offset="0" stop-color="{c}" stop-opacity=".45"/>'
                f'<stop offset=".6" stop-color="{c}" stop-opacity=".08"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient>')
        lines = "".join(f'<line x1="{x}" y1="0" x2="{x - 600}" y2="{H}"/>' for x in range(700, 2600, 46))
        body = (f'<rect width="{W}" height="{H}" fill="#0b0d12"/><rect width="{W}" height="{H}" fill="url(#bg)"/>'
                f'<g stroke="{c}" stroke-width="1" opacity=".08">{lines}</g>')
    else:  # wallpaper / favourites: all four console colours
        base = '<filter id="soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="120"/></filter>'
        blobs = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{c}" opacity=".45"/>' for x, y, r, c in
                        ((1250, 260, 260, "#2f6bff"), (1700, 330, 240, "#ff4554"), (1350, 760, 250, "#6a4cff"), (1780, 820, 230, "#3ddc84")))
        body = f'<rect width="{W}" height="{H}" fill="#0d0e12"/><g filter="url(#soft)">{blobs}</g>'
    return svg(body + sc_body + f'<rect width="{W}" height="{H}" filter="url(#grain)"/>', base + sc_defs + grain(.045))


# ---------------------------------------------------------------- Midnight
MID = {"android": "#3ddc84", "ns": "#ff3b4b", "ps2": "#2f6bff", "gba": "#7a5cff", "gbc": "#26c6a6",
       "n64": "#ffb300", "droiddeck": "#1a9fff", "gamenative": "#b0bec5", "wallpaper": "#8a93a6"}


def midnight(p):
    c = MID[p]
    defs = (f'<radialGradient id="g1" cx="1" cy="1" r="1"><stop offset="0" stop-color="{c}" stop-opacity=".55"/>'
            f'<stop offset=".45" stop-color="{c}" stop-opacity=".12"/><stop offset="1" stop-color="{c}" stop-opacity="0"/></radialGradient>'
            f'<radialGradient id="g2" cx=".85" cy="0" r=".6"><stop offset="0" stop-color="{c}" stop-opacity=".16"/><stop offset="1" stop-color="{c}" stop-opacity="0"/></radialGradient>'
            f'<linearGradient id="arc" gradientUnits="userSpaceOnUse" x1="700" y1="0" x2="1920" y2="1080"><stop offset="0" stop-color="{c}" stop-opacity="0"/><stop offset="1" stop-color="{c}" stop-opacity="1"/></linearGradient>'
            + grain(.06, 7))
    arcs = "".join(f'<circle cx="{W + 200}" cy="{H + 260}" r="{r}" fill="none" stroke="url(#arc)" stroke-width="2" opacity="{o}"/>'
                   for r, o in ((620, .7), (760, .5), (900, .35), (1040, .22), (1180, .12)))
    body = (f'<rect width="{W}" height="{H}" fill="#06070a"/><rect width="{W}" height="{H}" fill="url(#g1)"/>'
            f'<rect width="{W}" height="{H}" fill="url(#g2)"/>{arcs}<rect width="{W}" height="{H}" filter="url(#grain)"/>')
    return svg(body, defs)


THEMES = {"synthwave": synthwave, "heritage": heritage, "midnight": midnight}

for name, fn in THEMES.items():
    os.makedirs(os.path.join(OUT, name), exist_ok=True)
    for p in PLATFORMS:
        with open(os.path.join(OUT, name, f"{p}.svg"), "w") as f:
            f.write(fn(p))
print("ok")
