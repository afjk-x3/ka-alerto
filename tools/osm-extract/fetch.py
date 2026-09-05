"""
Fetches a real OpenStreetMap extract clipped to DemoArea.bounds (see
android/app/src/main/kotlin/com/macci/kaalerto/demo/DemoArea.kt) and converts it to
.osm.pbf for the offline tile-building pipeline (BUILD_TASKS.md day 0 /
SETUP_CHECKLIST.md).

The build plan originally called for "Geofabrik PH extract -> clip to DemoArea.bounds"
using osmium/osmconvert/ogr2ogr, none of which were installed on this machine. Those
tools clip a whole-country file down to a bounding box; the official OSM API's own
/api/0.6/map endpoint does the same bbox clip server-side and hands back exactly the
area asked for, with no multi-hundred-MB country download and no CLI tool install
beyond `pip install osmium` (used here only for the XML -> PBF format conversion, via
its Python bindings — not the separate osmium-tool CLI package). Same output shape
(a real, region-clipped .osm.pbf covering DemoArea.bounds), cheaper path to it.

Usage: python tools/osm-extract/fetch.py
Requires: pip install osmium
"""

import urllib.request
from pathlib import Path

import osmium

# Must match DemoArea.bounds exactly (DemoArea.kt) — minLon, minLat, maxLon, maxLat.
BBOX = (120.5990, 18.1660, 120.6130, 18.1760)

OUT_DIR = Path(__file__).parent
XML_PATH = OUT_DIR / "demo-area.osm"
PBF_PATH = OUT_DIR / "demo-area.osm.pbf"

OSM_API_URL = (
    "https://api.openstreetmap.org/api/0.6/map"
    f"?bbox={','.join(str(v) for v in BBOX)}"
)


def fetch_xml() -> None:
    print(f"Fetching {OSM_API_URL}")
    request = urllib.request.Request(OSM_API_URL, headers={"User-Agent": "kaalerto-osm-extract/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        XML_PATH.write_bytes(response.read())
    print(f"Wrote {XML_PATH} ({XML_PATH.stat().st_size:,} bytes)")


def convert_to_pbf() -> None:
    if PBF_PATH.exists():
        PBF_PATH.unlink()

    class CopyHandler(osmium.SimpleHandler):
        def __init__(self, writer: osmium.SimpleWriter) -> None:
            super().__init__()
            self.writer = writer

        def node(self, n: osmium.osm.Node) -> None:
            self.writer.add_node(n)

        def way(self, w: osmium.osm.Way) -> None:
            self.writer.add_way(w)

        def relation(self, r: osmium.osm.Relation) -> None:
            self.writer.add_relation(r)

    writer = osmium.SimpleWriter(str(PBF_PATH))
    try:
        CopyHandler(writer).apply_file(str(XML_PATH))
    finally:
        writer.close()
    print(f"Wrote {PBF_PATH} ({PBF_PATH.stat().st_size:,} bytes)")


if __name__ == "__main__":
    fetch_xml()
    convert_to_pbf()
