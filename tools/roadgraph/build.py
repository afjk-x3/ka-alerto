"""Builds the offline road graph the phone routes on when there is no internet (PRD 7.9).

Source: OpenStreetMap roads inside Pangasinan, via the Overpass API (the same province the
offline map pack covers). Car-usable roads only — the online router is a driving router, so the
two answer the same question. Footpaths, tracks and service roads (driveways, car parks) are left
out to keep the file small.

Run:  python tools/roadgraph/build.py
Writes: android/app/src/main/assets/roadgraph.bin

Format, little-endian:
  b"KRG1", int32 nodeCount, int32 edgeCount
  nodeCount x (int32 lat*1e6, int32 lon*1e6)
  (nodeCount + 1) x int32  CSR offsets into the edge array
  edgeCount x int32        target node of each directed edge
Edge lengths are not stored; the phone measures them from the node coordinates. A one-way road
gets only its forward edge.
"""
import json
import pathlib
import struct
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "android/app/src/main/assets/roadgraph.bin"
HIGHWAYS = ("motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|"
            "tertiary|tertiary_link|unclassified|residential|living_street|road")
QUERY = f"""[out:json][timeout:600];
area["name"="Pangasinan"]["admin_level"="4"]->.p;
way["highway"~"^({HIGHWAYS})$"](area.p);
out body qt;
>;
out skel qt;"""


MIRRORS = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"]
# The raw download is kept (gitignored) so tuning the build does not refetch 40 MB from Overpass.
CACHE = pathlib.Path(__file__).resolve().parent / "pangasinan-roads.cache.json"


def fetch():
    if CACHE.exists():
        return json.loads(CACHE.read_text(encoding="utf-8"))
    data = urllib.parse.urlencode({"data": QUERY}).encode()
    last = None
    for url in MIRRORS:
        try:
            req = urllib.request.Request(url, data=data, headers={"User-Agent": "KaAlerto/1.0 (offline road graph build)"})
            with urllib.request.urlopen(req, timeout=900) as r:
                elements = json.load(r)["elements"]
            CACHE.write_text(json.dumps(elements), encoding="utf-8")
            return elements
        except Exception as e:  # a busy mirror answers 429/504; try the next
            last = e
    raise last


TOLERANCE_M = 8.0


def simplify(nodes, coords, keep):
    """Douglas-Peucker on one way, never dropping a junction or an end. 8 m of drift is well
    inside what a route line or a flood's 75 m reach cares about, and roughly halves the file."""
    if len(nodes) < 3:
        return nodes
    import math
    lat0 = math.radians(coords[nodes[0]][0])
    xy = [(coords[n][1] * 111_320 * math.cos(lat0), coords[n][0] * 110_540) for n in nodes]

    def dist(p, a, b):
        dx, dy = b[0] - a[0], b[1] - a[1]
        if dx == dy == 0:
            return math.hypot(p[0] - a[0], p[1] - a[1])
        t = max(0, min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy)))
        return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy)

    kept = {0, len(nodes) - 1} | {i for i, n in enumerate(nodes) if n in keep}
    stack = [(0, len(nodes) - 1)]
    while stack:
        i, j = stack.pop()
        inner = [k for k in range(i + 1, j)]
        if not inner:
            continue
        must = [k for k in inner if k in kept]
        if must:  # split at fixed points first
            prev = i
            for k in must + [j]:
                stack.append((prev, k))
                prev = k
            continue
        k, d = max(((k, dist(xy[k], xy[i], xy[j])) for k in inner), key=lambda t: t[1])
        if d > TOLERANCE_M:
            kept.add(k)
            stack += [(i, k), (k, j)]
    return [nodes[i] for i in sorted(kept)]


def main():
    elements = fetch()
    coords = {e["id"]: (e["lat"], e["lon"]) for e in elements if e["type"] == "node"}
    ways = [e for e in elements if e["type"] == "way"]

    # A node on more than one way, or at a way's end, is a junction and must survive.
    uses = {}
    for w in ways:
        for n in w["nodes"]:
            uses[n] = uses.get(n, 0) + 1
    junctions = {n for n, c in uses.items() if c > 1} | {w["nodes"][0] for w in ways} | {w["nodes"][-1] for w in ways}
    for w in ways:
        w["nodes"] = simplify([n for n in w["nodes"] if n in coords], coords, junctions)

    index = {}
    for w in ways:
        for n in w["nodes"]:
            if n in coords and n not in index:
                index[n] = len(index)
    adj = [set() for _ in index]
    for w in ways:
        tags = w.get("tags", {})
        oneway = tags.get("oneway")
        forward = oneway != "-1"
        backward = oneway not in ("yes", "1", "true") and tags.get("junction") != "roundabout"
        if oneway == "-1":
            backward = True
        nodes = [index[n] for n in w["nodes"] if n in index]
        for a, b in zip(nodes, nodes[1:]):
            if a == b:
                continue
            if forward:
                adj[a].add(b)
            if backward:
                adj[b].add(a)

    order = sorted(index, key=index.get)
    offsets, targets = [0], []
    for edges in adj:
        targets.extend(sorted(edges))
        offsets.append(len(targets))

    with open(OUT, "wb") as f:
        f.write(b"KRG1")
        f.write(struct.pack("<ii", len(order), len(targets)))
        for n in order:
            lat, lon = coords[n]
            f.write(struct.pack("<ii", round(lat * 1e6), round(lon * 1e6)))
        f.write(struct.pack(f"<{len(offsets)}i", *offsets))
        f.write(struct.pack(f"<{len(targets)}i", *targets))
    print(f"{len(ways)} ways, {len(order)} nodes, {len(targets)} edges, {OUT.stat().st_size / 1e6:.1f} MB -> {OUT}")


if __name__ == "__main__":
    main()
