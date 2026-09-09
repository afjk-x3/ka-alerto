# Family / household check-in — design

Status: approved, not yet implemented. First sub-project of build day 11
("Family check-in + route check" — the second half, route check, is a
separate design once this one ships). Build order for days 11-13 overall:
family check-in → route check → SMS → server sync/dashboard, per
`BUILD_TASKS.md`'s day numbering (deliberately chosen over the PRD's
value-ordered cut ladder, which would have put SMS first).

## Source of truth

- `docs/02-prd.md` §7.8 "Family & Household Check-In" (row in the
  requirements table): "A household circle joined by local exchange; 'I am
  safe' as a single-tap event that travels the relay; no server, and no
  message body to congest the network."
- `docs/02-prd.md` §17 Implementation Phases: this feature is **Phase 4
  ("Reach")**, not Phase 1-3 — lower priority than everything shipped so
  far, and it's third-from-last on the MVP cut ladder
  (`docs/04-build-plan.md` §12: "Protecting MVP — cut in order: route
  check → server+dashboard → **family check-in** → SMS receive → Filipino
  strings"). Build it, but don't over-invest relative to that priority.
- `docs/03-architecture.md` §1.6.4: "A one-tap 'Ligtas ako' (I'm safe)
  that propagates to a small, pre-configured household circle, with
  location optional. Shows each member's last-known status and its age."
  Also §1.6.4: "a check-in is a ~20-byte event. It rides the mesh happily,
  fits many-per-SMS, and can be relayed by a stranger's phone that later
  reaches signal." Note §933/§947 of the same doc lists family check-in
  under "explicitly deferred from MVP" in an older pass — the PRD's own
  Phase 4 listing supersedes that; it's in scope, just low priority.
- `BUILD_TASKS.md` Day 11: "Check-in: circle pairing by QR (works
  offline). 'Ligtas ako' emits ~20-byte event" / "Circle list showing each
  member's status and age" / "If behind: cut route check entirely; keep
  check-in (cheaper demo)."

## Non-goals (explicitly out of scope for this pass)

- **No "not safe" / distress status.** The PRD's own language is "I am
  safe" only — a resident who needs help already has SOS (long-press,
  full escalation chain). Check-in is a presence ping, not a second SOS
  path. The circle list can only ever show "safe, N ago" or "no check-in
  yet" — never a bad-news state.
- **No mutual-consent handshake protocol, no revocation/removal UI.**
  Pairing is unilateral per scan (see below); removing a circle member is
  a local delete with no confirmation dialog, same weight as any other
  local-only preference edit.
- **No server-side circle sync.** Circle membership is a local address
  book only (see Architecture). It never leaves the device except
  implicitly, in the sense that check-in *events* ride the mesh — the
  membership list itself is never transmitted.
- **No group size enforcement in code.** "Small group" is a UX/product
  expectation (the pairing flow is one-at-a-time, so nobody is going to
  pair 50 people by hand), not a hard cap. No max-size check needed.

## Architecture

**Circle membership is a local address book, not a replicated event-log
concept.** Each device stores its own list of paired members —
`(authorId, displayName, pairedAtMs)` — the same way `HomeLocationStore`
and `LocalIdentity` persist small local state: SharedPreferences, one key,
JSON-encoded list. No new Room table. This deliberately differs from the
`role_*`/`sos_*` event families: there is no need for two devices to agree
on who is in a circle (NFR-4 doesn't apply here — a circle is a personal
watch-list, not a shared fact about the world), and inventing a
replicated membership protocol (invite/accept/revoke events, conflict
resolution) would be pure over-engineering against a feature whose own
spec says "cut me first" three times over in the planning docs.

**The check-in itself is a plain, ordinary `Event`** — this is the part
that *does* replicate, because "is my circle member safe" must reach a
phone that has no idea a pairing even happened locally elsewhere:

- `type = "family_checkin"`, a new top-level `const val` alongside the
  existing `TYPE_SOS`/`"flood_report"`/etc. string constants.
- **No JSON payload.** Unlike `sos*` or the report-photo hash, a check-in
  needs nothing beyond what `Event`'s existing flat columns already
  carry: `authorId`, `authorName` (both from `LocalIdentity.getOrCreate`
  at write time, embedded — never looked up later, same rule as every
  other event type), `createdAtMs`, and optional `lat`/`lon` ("location
  optional" per the architecture doc — omit both if the resident declines
  to share position, which the UI should offer as a toggle or just always
  send without asking, TBD at implementation time against whatever the
  existing report-flow location-consent pattern is).
- `featureRef = null`, same as `sos*` — a check-in is not an observation
  of a flooded segment, and `data/Reducer.kt` must never see it (verified
  by the same style of unit test that asserts an SOS never becomes a map
  marker, per `CLAUDE.md`'s day-8 note).
- Rides the existing mesh with **zero transport changes** — `MeshProtocol`
  already exchanges every event ID regardless of type; a new `type` string
  needs no new plumbing there.

**A new `family/CircleReducer.kt`** folds the shared event stream
independently of the flood reducer, the same shape as `SosReducer.kt`/
`RoleReducer.kt`:

```
fun circleStatuses(
    allEvents: List<Event>,
    circle: List<CircleMember>,
): List<CircleMemberStatus>
```

Filters `allEvents` to `type == TYPE_CHECKIN && authorId in circle.ids`,
groups by `authorId`, keeps the latest by `createdAtMs` per member, and
joins against the local `circle` list for display name (falling back to
whatever name rode on the event itself if the locally-stored name is
stale — the event's `authorName` is the ground truth for what to *show*,
same rule as everywhere else in the app; the local address book is only
used to know *which* authorIds to filter for). A member with zero
matching events renders as "no check-in yet," not absent from the list —
the list is keyed off the local circle membership, always showing every
paired member regardless of whether any check-in has arrived, same
"absence isn't the same claim as presence" reasoning `RoleActionStrip`
already established for the empty rescue queue.

## Pairing flow (QR)

Two people pair by each scanning the other's on-screen QR once — no
phone-swapping, no multi-step "invite pending" state. Each scan is a
unilateral local write:

1. Screen shows **your own QR**: encodes `authorId` + `authorName`,
   drawn the same way as the rescue card — pure-ZXing `Encoder.encode`
   producing a matrix, rendered on a Compose `Canvas` with whole-pixel
   modules (reuse `sos/SosQr.kt`'s pattern, new prefix
   `KAALERTO/CIRCLE/1:` so a scanner can tell circle-pairing QRs apart
   from SOS cards or anything else).
2. A "Mag-scan ng QR" button opens a **live camera scanner** — this is
   the one genuinely new technical piece. Recommendation: pull in
   `com.journeyapps:zxing-android-embedded` for the scan side only (the
   encode side keeps the existing hand-drawn Canvas approach unchanged).
   This reverses the day-8 decision to reject that dependency ("a camera
   scanner UI this build never needs") — it's needed now, for a stated
   reason, and hand-rolling CameraX + frame-by-frame ZXing decoding for a
   solved problem isn't a good use of hackathon time. The library handles
   camera permission request and the scanning UI itself; the only new
   code is launching it (`registerForActivityResult(ScanContract())`) and
   handling the result string.
3. On a successful scan whose content starts with `KAALERTO/CIRCLE/1:`,
   parse out the other device's `authorId`/`authorName`, append to the
   local circle list (dedup on `authorId` — re-scanning an existing
   member is a no-op, not a duplicate entry), persist, show a brief
   confirmation, return to the circle screen.
4. A scan that doesn't match the prefix (wrong QR entirely) shows an
   error, no crash, no silent failure — mirrors `decodeSosCard`'s
   `runCatching { }.getOrNull()` pattern.

**New permission**: `android.permission.CAMERA`. This is the app's first
request for it — flag this explicitly wherever permissions are declared/
requested (`AndroidManifest.xml`, plus whatever runtime-permission pattern
`MeshPermissions.kt` or the location-permission flow already establishes,
for consistency).

## UI

- **New drawer entry** "Aking Pamilya" / "My Family", slotted after "Ang
  profile ko" and before "Mga silungan" in `nav/NavDrawer.kt`, following
  the exact `DrawerRow(...)` + `onOpenX` callback pattern the existing
  four rows use.
- **New `Screen.FamilyCircle` data object** in `nav/Screen.kt` (no params
  needed — it's a single always-the-same view, like `Screen.EvacCentres`).
  The scanner is launched via an Activity Result contract from within this
  screen, not a second `Screen` case — it's a modal system flow, not
  in-app navigation.
- **`family/FamilyCircleScreen.kt`**: a big "Ligtas ako" button at the
  top (one tap → writes the check-in event immediately, no confirmation
  dialog, matches the PRD's "single-tap" framing exactly — compare to how
  the SOS button itself requires a hold specifically *because* it's
  high-stakes; check-in is the opposite, low-stakes, so a single tap is
  correct here, not an inconsistency), then the circle list below (name,
  status + age or "Wala pang balita"), then a "+" affordance that shows
  your own QR and offers the scan button.

## Notifications

New channel `CHANNEL_FAMILY_CHECKIN` in `notification/
NotificationChannels.kt`, alongside the existing flood/SOS channels — a
resident who mutes flood chatter must not thereby mute "your sister
checked in," same reasoning as why SOS already has its own channel.
Firing side: a new watcher (`family/CircleCheckInNotifier.kt` or similar)
mirroring `GeofenceNotifier`'s shape — diffs `EventRepository.observeAll()`
for new `family_checkin` events whose `authorId` is in the local circle,
skips the historical backlog on first launch/cold-start the same way
`GeofenceNotifier` does (don't fire on events that already existed before
this watcher started observing).

## Testing

**Unit tests:**
- `CircleReducer`: latest-per-member folding, an event from a non-circle
  author is ignored, a member with no events still appears as "no
  check-in yet," a rotation of the input event list folds identically
  (same style as the day-4 reducer's own ordering-independence test).
- QR encode/decode round-trip for the new `KAALERTO/CIRCLE/1:` payload,
  mirroring `SosQrTest`.
- Circle-store persistence (add, dedup-on-rescan, list survives a
  simulated process restart via the same SharedPreferences read/write
  round-trip `HomeLocationStore`'s own tests presumably already cover the
  pattern for).

**Manual device verification** (airplane mode, per this repo's standing
rule that nothing counts as built until proven offline):
1. Pair two phones by QR (each scans the other).
2. Airplane mode on both.
3. Check in ("Ligtas ako") on phone A.
4. Confirm the check-in appears on phone B's circle list via mesh, with
   correct age label, within the mesh's normal exchange latency.
5. Confirm a notification fires on B.
6. Cold-relaunch both, confirm circle membership and last-known status
   both survive (membership from SharedPreferences, status from the
   Room-backed event log via the reducer — two different persistence
   paths, both need checking independently).

## Files touched (new)

- `family/CircleStore.kt` — local address book (SharedPreferences JSON).
- `family/CircleEvents.kt` — `TYPE_CHECKIN` constant, `newCheckInEvent(...)`
  factory.
- `family/CircleReducer.kt` — event-stream fold, as above.
- `family/CircleQr.kt` — encode/decode for `KAALERTO/CIRCLE/1:` payloads
  (mirrors `sos/SosQr.kt`).
- `family/FamilyCircleScreen.kt` — the screen itself.
- `family/CircleCheckInNotifier.kt` — the notification watcher.
- `nav/Screen.kt` — add `data object FamilyCircle`.
- `nav/NavDrawer.kt` — add the "Aking Pamilya" row + `onOpenFamily`
  callback.
- `notification/NotificationChannels.kt` — add `CHANNEL_FAMILY_CHECKIN`.
- `ui/KaAlertoApp.kt` — wire the new screen case + drawer callback, same
  shape as every other screen already wired there.
- `android/gradle/libs.versions.toml` + `app/build.gradle.kts` — add
  `com.journeyapps:zxing-android-embedded`.
- `AndroidManifest.xml` — add `CAMERA` permission.

## Open questions for implementation time (not blocking design approval)

- Exact copy/UX for the location-optional toggle on the check-in button
  (always-send vs. an explicit ask) — follow whatever pattern the report
  flow's own location handling already establishes, for consistency.
- Whether `zxing-android-embedded`'s scan Activity needs any Storm-mode-
  aware theming, or whether it's acceptable as-is (it's a modal system
  flow, brief, arguably fine to be visually distinct — same judgment call
  as the system camera intent already used for report photos).
