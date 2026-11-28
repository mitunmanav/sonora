"""Splits each frame of a gfxinfo framestats dump into stages, in milliseconds.

usage: python3 -I stages.py label file [file ...]
Reads only the numeric rows between ---PROFILEDATA--- markers.
"""
import sys

COLS = ["FLAGS", "INTENDED_VSYNC", "VSYNC", "OLDEST_INPUT", "NEWEST_INPUT",
        "HANDLE_INPUT_START", "ANIMATION_START", "PERFORM_TRAVERSALS_START",
        "DRAW_START", "SYNC_QUEUED", "SYNC_START", "ISSUE_DRAW_COMMANDS_START",
        "SWAP_BUFFERS", "FRAME_COMPLETED"]
STAGES = [
    ("input", "HANDLE_INPUT_START", "ANIMATION_START"),
    ("animation", "ANIMATION_START", "PERFORM_TRAVERSALS_START"),
    ("layout", "PERFORM_TRAVERSALS_START", "DRAW_START"),
    ("record", "DRAW_START", "SYNC_QUEUED"),
    ("sync", "SYNC_START", "ISSUE_DRAW_COMMANDS_START"),
    ("issue", "ISSUE_DRAW_COMMANDS_START", "SWAP_BUFFERS"),
    ("swap", "SWAP_BUFFERS", "FRAME_COMPLETED"),
    ("TOTAL", "INTENDED_VSYNC", "FRAME_COMPLETED"),
]


def frames(path):
    rows, on = [], False
    with open(path, errors="replace") as f:
        for line in f:
            s = line.strip()
            if s == "---PROFILEDATA---":
                on = not on
                continue
            if not on or s.startswith("Flags"):
                continue
            parts = [p for p in s.split(",") if p != ""]
            try:
                v = [int(p) for p in parts]
            except ValueError:
                continue
            if len(v) >= len(COLS) and v[0] == 0:
                rows.append(dict(zip(COLS, v)))
    return rows


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p))] if xs else 0.0


label = sys.argv[1]
allrows = []
for p in sys.argv[2:]:
    seen = set()  # the same frame shows up in overlapping dumps of one run
    for r in frames(p):
        if r["INTENDED_VSYNC"] not in seen:
            seen.add(r["INTENDED_VSYNC"])
            allrows.append(r)
print(f"== {label}: {len(allrows)} frames (animated or not)")
print(f"{'stage':10} {'mean':>6} {'p50':>6} {'p90':>6} {'p99':>6} {'max':>6}")
for name, a, b in STAGES:
    d = [(r[b] - r[a]) / 1e6 for r in allrows if r[a] > 0 and r[b] >= r[a]]
    if not d:
        continue
    print(f"{name:10} {sum(d)/len(d):6.2f} {pct(d,.5):6.2f} {pct(d,.9):6.2f} {pct(d,.99):6.2f} {max(d):6.2f}")
slow = [r for r in allrows if (r["FRAME_COMPLETED"] - r["INTENDED_VSYNC"]) / 1e6 > 16.7]
print(f"frames over 16.7 ms: {len(slow)} of {len(allrows)}")
if slow:
    print("slow frames, stage mean ms:")
    for name, a, b in STAGES[:-1]:
        d = [(r[b] - r[a]) / 1e6 for r in slow if r[a] > 0 and r[b] >= r[a]]
        if d:
            print(f"  {name:10} {sum(d)/len(d):6.2f}")
