"""Builds the compact Philippine Standard Geographic Code list the phone and the dashboard bundle.

Source: the PSA's PSGC, published as static JSON at https://psgc.gitlab.io/api/ (the same data
OSSPhilippines/psgc-api serves; that hosted API was returning HTTP 500 when this was written, and a
bundled copy works offline anyway). Names and codes only: PSGC has no coordinates.

Run:  python tools/psgc/build.py
Writes: android/app/src/main/assets/psgc.json and dashboard/src/data/psgc.json (identical).

Shape: {"m": [[code, name, province, isCity], ...], "b": {municipalityCode: ["barangay", ...]}}
  name  "Laoag City" (PSGC's "City of Laoag" reordered, which is how people write it)
  label the apps show is "<name>, <province>"; NCR cities use "Metro Manila" as the province.
"""
import json
import pathlib
import re
import urllib.request

BASE = "https://psgc.gitlab.io/api/"
ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = [ROOT / "android/app/src/main/assets/psgc.json", ROOT / "dashboard/src/data/psgc.json"]


def get(name):
    with urllib.request.urlopen(BASE + name + ".json", timeout=120) as r:
        return json.load(r)


def city_name(name):
    m = re.match(r"^City of (.+)$", name)
    return f"{m.group(1)} City" if m else name


provinces = {p["code"]: p["name"] for p in get("provinces")}
regions = {r["code"]: r["name"] for r in get("regions")}

municipalities = []
for x in get("cities-municipalities"):
    province = provinces.get(x["provinceCode"] or "")
    if not province:
        # NCR has districts instead of provinces; the few others (an independent city) fall back to their region.
        region = regions.get(x["regionCode"], "")
        province = "Metro Manila" if "NCR" in region or "National Capital" in region else region
    municipalities.append([x["code"], city_name(x["name"]), province, 1 if x["isCity"] else 0])
municipalities.sort(key=lambda m: (m[1], m[2]))

barangays = {}
for b in get("barangays"):
    parent = b.get("municipalityCode") or b.get("cityCode")
    if parent:
        barangays.setdefault(parent, []).append(b["name"])
for names in barangays.values():
    names.sort()

data = {"m": municipalities, "b": barangays}
text = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
for path in OUT:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
print(f"{len(municipalities)} municipalities/cities, {sum(len(v) for v in barangays.values())} barangays, {len(text) / 1e6:.2f} MB")
