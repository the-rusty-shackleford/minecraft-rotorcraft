"""The collective's dial, as code (D-0005): a round housing on a pedestal with the rotor seen from
above inside it, and a horseshoe arc around it, open at the bottom, lit orange from its bottom-left
end up to the lever, as a throttle's; the detent a white tick at twelve o'clock, so a fill past the
tick is a climb. Built as Immersive Aircraft's engine dial is and drawn where it draws it, 18 by 18
GUI pixels (Rusty, 2026-10-10: "I like the circular throttle bar from immersive aircraft more"; of
the two colourings shown him, the throttle's over green-up amber-down); the pixels are ours.

    uv run --no-project --with pillow python devtools/art/gauge.py
    uv run --no-project --with pillow python devtools/art/gauge.py mock SCREENSHOT OUT.png

The sheet, src/main/resources/assets/rotorcraft/textures/gui/collective.png, is a grid of 18-pixel
frames, NINE a row: frames 0..LEVELS-1 are the housing and the arc with the lever at step i of
LEVELS-1 (the detent's middle is the middle step), frames LEVELS..LEVELS+ROTOR-1 the rotor's blades
turned by i * 180/ROTOR degrees, on a clear ground. CollectiveGauge reads the same three numbers.
"""
import math
import sys
from pathlib import Path

from PIL import Image

SIZE = 18
COLUMNS = 9
LEVELS = 41          # lever steps, 0 the bottom, 20 the detent's middle, 40 the top
ROTOR = 8            # rotor frames over half a turn (two blades)
SPAN = 135.0         # the arc runs from -SPAN (lever at the bottom) to +SPAN degrees (the top), 0 at twelve o'clock

OUTLINE = (20, 22, 25, 255)
TRACK = (58, 50, 40, 255)
TRACK_HI = (78, 68, 54, 255)
THROTTLE = (232, 136, 32, 255)
THROTTLE_HI = (255, 190, 80, 255)
TICK = (250, 250, 245, 255)
BEZEL_LIGHT = (108, 114, 122, 255)
BEZEL_DARK = (44, 48, 54, 255)
FACE = (30, 33, 37, 255)
FACE_RING = (38, 42, 47, 255)
BLADE = (206, 210, 216, 255)
BLADE_SHADE = (150, 155, 162, 255)
HUB = (96, 102, 110, 255)

ROOT = Path(__file__).resolve().parents[2]
SHEET = ROOT / 'src/main/resources/assets/rotorcraft/textures/gui/collective.png'


def polar(x, y):
    """The pixel (x, y)'s centre from the dial's centre: its distance, and its angle in degrees,
    0 at twelve o'clock, growing clockwise, in (-180, 180]."""
    dx, dy = x + 0.5 - SIZE / 2, y + 0.5 - SIZE / 2
    return math.hypot(dx, dy), math.degrees(math.atan2(dx, -dy))


def lever_angle(step):
    return (step / (LEVELS - 1) - 0.5) * 2 * SPAN


PEDESTAL = (62, 66, 72, 255)
PEDESTAL_HI = (98, 104, 112, 255)


def dial(step):
    """The housing on its pedestal and the arc with the lever at step {@code step} of LEVELS - 1: a
    ring about three pixels wide sharing its inner outline with the housing, open at the bottom
    where the pedestal stands, as Immersive Aircraft's dial is built; the detent a white tick at
    twelve o'clock."""
    im = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    at = lever_angle(step)
    for y in range(SIZE):
        for x in range(SIZE):
            r, a = polar(x, y)
            if r >= 9.15:
                continue
            on_arc = abs(a) <= SPAN
            if 8.35 <= r and (on_arc or abs(a) <= SPAN + 10):
                px[x, y] = TICK if abs(a) <= 7 else OUTLINE
            elif 6.0 <= r < 8.35 and on_arc:
                inner = r < 7.1
                if a <= at:
                    px[x, y] = THROTTLE_HI if inner else THROTTLE
                else:
                    px[x, y] = TRACK_HI if inner else TRACK
            elif 6.0 <= r < 9.15 and abs(a) <= SPAN + 10:
                px[x, y] = OUTLINE              # the arc's ends, capped
            elif 5.3 <= r < 6.0:
                px[x, y] = OUTLINE
            elif r < 5.3:
                if r >= 4.4:
                    px[x, y] = BEZEL_LIGHT if a < -20 or a > 160 else BEZEL_DARK
                else:
                    px[x, y] = FACE_RING if r >= 3.5 else FACE
    # The pedestal, in the horseshoe's opening: the housing stands on it.
    for y in range(13, SIZE):
        for x in range(5, 13):
            if px[x, y][3] == 0 or polar(x, y)[0] >= 5.3:
                edge = x in (5, 12) or y == SIZE - 1
                px[x, y] = OUTLINE if edge else (PEDESTAL_HI if y == 13 else PEDESTAL)
    return im


def rotor(frame):
    """Two blades through the hub, turned frame * 180/ROTOR degrees, on a clear ground."""
    im = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    turn = math.radians(frame * 180.0 / ROTOR)
    ux, uy = math.sin(turn), -math.cos(turn)
    c = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            r = math.hypot(dx, dy)
            along = dx * ux + dy * uy
            across = abs(-dx * uy + dy * ux)
            if r < 1.3:
                px[x, y] = HUB
            elif r < 4.6 and across < 0.72:
                px[x, y] = BLADE if along * (1 if frame % 2 == 0 else 1) >= 0 else BLADE_SHADE
    return im


def sheet():
    frames = [dial(i) for i in range(LEVELS)] + [rotor(i) for i in range(ROTOR)]
    rows = -(-len(frames) // COLUMNS)
    out = Image.new('RGBA', (COLUMNS * SIZE, rows * SIZE), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.alpha_composite(f, ((i % COLUMNS) * SIZE, (i // COLUMNS) * SIZE))
    return out


def frame(im, i):
    return im.crop(((i % COLUMNS) * SIZE, (i // COLUMNS) * SIZE, (i % COLUMNS + 1) * SIZE, (i // COLUMNS + 1) * SIZE))


def mock(screenshot, out):
    """Three states of the dial (descending, in the detent, climbing) laid where the game draws it on
    a 1280 by 720 booth screenshot at GUI scale 3, creative (no experience bar), each crop at three times."""
    s = sheet()
    base = Image.open(screenshot).convert('RGBA')
    scale = 3
    gw, gh = base.width // scale, base.height // scale
    cx, cy = gw // 2, gh - 37 + 7
    crops = []
    for step, turn in ((8, 1), (20, 3), (33, 6)):
        im = base.copy()
        layer = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
        layer.alpha_composite(frame(s, step))
        layer.alpha_composite(frame(s, LEVELS + turn))
        big = layer.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
        im.alpha_composite(big, ((cx - SIZE // 2) * scale, (cy - SIZE // 2) * scale))
        box = ((cx - 100) * scale, (cy - 20) * scale, (cx + 100) * scale, gh * scale)
        crops.append(im.crop(box).resize(((box[2] - box[0]), (box[3] - box[1])), Image.NEAREST))
    sheet_big = s.resize((s.width * 8, s.height * 8), Image.NEAREST)
    w = max(max(c.width for c in crops), sheet_big.width)
    h = sum(c.height for c in crops) + sheet_big.height + 10 * 4
    board = Image.new('RGBA', (w, h), (235, 235, 235, 255))
    y = 0
    for c in crops:
        board.alpha_composite(c, (0, y))
        y += c.height + 10
    board.alpha_composite(sheet_big, (0, y))
    board.save(out)


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'mock':
        mock(sys.argv[2], sys.argv[3])
    else:
        SHEET.parent.mkdir(parents=True, exist_ok=True)
        sheet().save(SHEET)
        print(f'wrote {SHEET.relative_to(ROOT)}')
