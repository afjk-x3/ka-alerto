# OSM extract for the offline tile pipeline

`demo-area.osm.pbf` is a real OpenStreetMap extract clipped to `DemoArea.bounds`
(`android/app/src/main/kotlin/com/macci/kaalerto/demo/DemoArea.kt`) — the last Day 0
fixture item (`BUILD_TASKS.md`, `SETUP_CHECKLIST.md`). It's a source input for building
the bundled-MBTiles fallback in `BUILD_TASKS.md` day 1, not something the app reads
directly and not something shipped in `assets/` — that's already real point/line JSON
(`seed_data.json`, `evacuation_centres.json`, `routes/`), which this is not a
replacement for.

**Fetched 5 Sep 2026.** 10,239 nodes, 1,787 ways, 3 relations. Verified against the
fixtures: contains Sotto Street, Josefa Llanes Escoda National Highway, San
Nicolas-Laoag Diversion Road, Filipinas East/West Elementary Schools, San Nicolas
National High School, and Padsan River — the same real streets and landmarks the seed
reports and evacuation centres already reference.

**Committed, not regenerated on demand.** OSM is a live, editable database, so re-running
`fetch.py` next month would pull different (newer) data than what's here — the opposite
of the "frozen demo area" invariant `DemoArea.kt` establishes for every other fixture.
Treat this file the same way: frozen, checked in, not rebuilt casually. Regenerate only
if the demo area itself is ever reconfirmed or re-scoped, and note the new fetch date
here.

## How this differs from the original plan

`BUILD_TASKS.md`/`SETUP_CHECKLIST.md` originally called for "Geofabrik PH extract ->
clip to `DemoArea.bounds`" via `osmium`/`osmconvert`/`ogr2ogr`, none of which were
installed on this machine. Those tools clip a whole-country file (the Philippines
extract is ~600 MB) down to a bounding box.

`fetch.py` gets the same result — a real, region-clipped `.osm.pbf` covering exactly
`DemoArea.bounds` — from the official OSM API's own `/api/0.6/map` endpoint, which
performs that bbox clip server-side. No multi-hundred-MB download, and the only local
tool needed is `pip install osmium` (pyosmium's Python bindings, used here solely for
the XML -> PBF format conversion — not the separate `osmium-tool` CLI package, which
has no prebuilt Windows wheel). Verified lossless: node/way/relation counts match
exactly between the fetched XML and the converted PBF.

## Regenerating (only if the demo area is reconfirmed/re-scoped)

```bash
pip install osmium
python tools/osm-extract/fetch.py
```

Update `BBOX` in `fetch.py` first if `DemoArea.bounds` ever changes — it's a literal
copy, not read from the Kotlin source, so the two can silently drift if one changes
without the other.
