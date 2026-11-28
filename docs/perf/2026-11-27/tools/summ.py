"""One line per capture set: python3 -I summ.py label file [file ...]"""
import sys

def load(paths):
    rows, seen = [], set()
    for p in paths:
        on, hdr = False, None
        for line in open(p, errors="replace"):
            s = line.strip()
            if s == "---PROFILEDATA---":
                on = not on
                continue
            if not on:
                continue
            if s.startswith("Flags"):
                hdr = s.rstrip(",").split(",")
                continue
            try:
                v = [int(x) for x in s.rstrip(",").split(",")]
            except ValueError:
                continue
            if hdr and len(v) == len(hdr) and v[0] == 0:
                r = dict(zip(hdr, v))
                key = (p, r["IntendedVsync"])
                if key not in seen:
                    seen.add(key)
                    rows.append(r)
    return rows

def pct(x, p):
    x = sorted(x)
    return x[min(len(x) - 1, int(len(x) * p))] if x else 0

label, paths = sys.argv[1], sys.argv[2:]
rows = load(paths)
tot = [(r["FrameCompleted"] - r["IntendedVsync"]) / 1e6 for r in rows]
swap = [(r["FrameCompleted"] - r["SwapBuffers"]) / 1e6 for r in rows]
slow = sum(1 for t in tot if t > 16.7)
worst = max(tot) if tot else 0
print(f"{label:18} frames {len(rows):4d}  slow {slow:3d} ({100*slow/max(1,len(rows)):4.1f}%)  "
      f"total p90 {pct(tot,.9):5.1f} p99 {pct(tot,.99):5.1f} max {worst:5.1f}  swap p90 {pct(swap,.9):5.1f}")
