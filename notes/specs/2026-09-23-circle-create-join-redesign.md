# Circle create/join — redesign

Status: proposed, not yet implemented. Supersedes
`specs/2026-09-12-circle-unification-redesign.md`'s pairwise-edge model for
circle *membership*. Everything in `specs/2026-09-09-family-checkin-design.md`
about the check-in event, the notification watcher, and the screen layout
stands unchanged — only "how a device becomes part of a circle" changes.

## Why this exists

The user, while reviewing the responder/official screens, flagged the
Pamilya screen for a Life360-style rebuild. After scoping (see the
brainstorming session this spec comes out of), the concrete ask narrowed to
one thing: **a real named circle with a shareable join code**, not QR-only
pairwise pairing. Everything else in Life360's feature set (live location
map, place alerts) was explicitly dropped as out of scope — this app has no
continuous background location tracking anywhere, and building it now would
fight several already-settled ground rules (no crypto, the Xiaomi
background-kill lesson from `BackgroundTip.kt`, the general no-forecasting/
minimal-surveillance posture).

This reopens a decision the 12 Sep spec closed on purpose. That spec killed
an earlier `circleId` attempt because nothing could make two devices agree
on a shared identifier with **no server and no crypto** — the attempt's
`CircleStore.Circle` was a local, unsynced `SharedPreferences` object that
only the scanning device ever wrote, so the id never actually propagated.
The reasoning was correct for 12 September. It is dated now: Supabase sync
was reopened on 18 September and is already the arbiter of real, named,
ID'd, multi-member entities — `evac_centre` is exactly this shape (a
creator posts an entity with a real id; other events reference that id;
every device folds the same replicated log to the same answer). A circle is
not a new kind of problem, it is the same pattern applied to a different
entity.

## Design

**A circle is an event-sourced entity, folded the same way `evac_centre` is
— not a graph, not a local store, not a re-invented id scheme.**

Two event types:

```kotlin
const val TYPE_CIRCLE_CREATE = "circle_create"
const val TYPE_CIRCLE_JOIN = "circle_join"

@Serializable
data class CircleCreatePayload(val circleId: String, val name: String)

@Serializable
data class CircleJoinPayload(val circleId: String)
```

`circle_create` is authored once, by whoever taps "Gumawa ng Circle."
`circle_join` is authored by every device that later enters or scans the
code. Both carry `circleId` (a `UUID.randomUUID()` string) as the only
piece of shared identity a device needs to agree on — and it needs no
agreement protocol, because it originates from exactly one event, the same
way an `evac_centre`'s id does.

```kotlin
data class ResolvedCircle(val circleId: String, val name: String, val members: List<CircleMember>)

/**
 * My own circle: whichever of my own circle_create/circle_join events is
 * newest gives my circleId (switching circles is just authoring a newer
 * one — see Non-goals). Membership is everyone whose own newest
 * create/join event points at that same circleId. Pure fold, same shape
 * as evac/EvacCentre.kt's resolveCentres — no persisted store, recomputed
 * on every call.
 */
fun resolveCircle(allEvents: List<Event>, myAuthorId: String): ResolvedCircle? {
    val relevant = allEvents.filter { it.type == TYPE_CIRCLE_CREATE || it.type == TYPE_CIRCLE_JOIN }
    // Newest event per author wins -- this is what makes "switch circles" free.
    val latestPerAuthor = relevant.groupBy { it.authorId }
        .mapValues { (_, events) -> events.maxBy { it.timestampMs } }

    val myCircleId = latestPerAuthor[myAuthorId]?.let { circleIdOf(it) } ?: return null
    val name = relevant
        .filter { it.type == TYPE_CIRCLE_CREATE && circleIdOf(it) == myCircleId }
        .maxByOrNull { it.timestampMs }
        ?.let { decodeCircleCreatePayload(it.payload)?.name } ?: return null

    val members = latestPerAuthor.values
        .filter { circleIdOf(it) == myCircleId && it.authorId != myAuthorId }
        .map { CircleMember(authorId = it.authorId, displayName = it.authorName, pairedAtMs = it.timestampMs) }

    return ResolvedCircle(myCircleId, name, members)
}
```

(`circleIdOf` just decodes whichever payload shape the event is and returns
its `circleId` — a small helper, not shown in full here.)

**Sync.** Both types join the Supabase allowlist in
`sync/SupabaseSync.kt`'s `SYNCED_TYPES` (currently `flood_report, confirm,
dispute, official_status, flood_withdraw, evac_status, evac_centre,
sos, sos_amend, sos_state` — see that file's own doc comment, which
currently says `circle_invite` stays mesh-only and needs updating to say
why `circle_create`/`circle_join` no longer do). They also still ride the
mesh unconditionally, like every event type — so a join made offline still
resolves once the mesh or a later sync carries the matching `circle_create`
in, same eventual-consistency tolerance every other event-sourced feature
here already accepts.

`family_checkin` (the actual "I'm safe" pings) is **not** part of this
change and stays mesh-only exactly as today — only circle membership
(who's in the circle, and its name) moves to Supabase, not the check-in
traffic itself.

## Screen flow

1. **No circle yet**: two entry points, side by side.
   - "Gumawa ng Circle" — a name field (e.g. "Pamilya Reyes"), submitting
     writes one `circle_create` event with a fresh `circleId`.
   - "Sumali sa Circle" — a code field, pre-filled from the clipboard if
     its contents look like a valid `circleId` (paste-and-go, not
     character-by-character retyping). Submitting writes one `circle_join`
     event for that `circleId`. No validation that the circle actually
     exists yet at write time — same as every other event this app writes
     optimistically; if the code was wrong, the device simply resolves to
     a circle of one until proven otherwise, no error state needed.
2. **Sharing the code**: an "Mag-imbita" action on an existing circle opens
   Android's native share sheet with a pre-filled message ("Sumali sa
   aming Circle sa KaAlerto: <circleId>"), so the code travels over
   whatever channel is already installed (SMS, Messenger, etc.). The same
   code still renders as a QR (`KAALERTO/CIRCLE/2:<json>`, version bumped
   from `/1:` so an old-format scan is rejected cleanly rather than
   misparsed) for in-person scanning. One join mechanism, two delivery
   paths for the same code.
3. **Has a circle**: circle name at the top, member list, and the existing
   `MyStatusCard`/`CircleMemberRow`/`DeliveryMethodIcon` check-in UI
   unchanged underneath — check-ins already fold on top of "who's in my
   circle" regardless of how membership is computed, so none of that code
   needs to know this redesign happened.
4. **Switching circles**: joining a different code while already in a
   circle just authors a newer `circle_join` for the new `circleId`.
   `resolveCircle` picks the newest per author, so the device's own view
   updates immediately, and it drops out of the old circle's member list
   for everyone else on their next fold — no explicit "leave" action
   needed, matching how a home-barangay correction already works.

## Non-goals

- **No leave/remove-member UI.** Switching circles via a newer
  `circle_join` is the only way out — carried over from the 12 Sep spec's
  own non-goal, now achieved differently.
- **No multi-circle-per-device.** One active circle at a time. A
  circle-switcher UI is real added scope for a feature that didn't exist
  until this spec; revisit only if actually needed.
- **No rename-after-creation.** The name is fixed at `circle_create` time.
- **No creator-only privileges.** Anyone with the code can join, and
  anyone already in the circle can re-share the code further. Same trust
  model this app already uses for names and roles-in-testing-mode — no
  verification, self-declared, social cost only.
- **No protection against joining the wrong circle by mistake.** Whoever
  holds the code is trusted. Consistent with the rest of the app's
  no-verification posture.
- **No group size enforcement**, same as the 12 Sep spec's own non-goal.

## Privacy residual — restated, not newly introduced

`sync/SupabaseSync.kt`'s own doc comment already states the reasoning for
what stays mesh-only: Supabase has no access control, so anything synced is
readable by anyone who extracts the embedded anon key. Adding
`circle_create`/`circle_join` to that allowlist means real household
groupings — who is related to whom, and under what name they chose for the
circle — now sit in that same unprotected table, permanently, alongside
flood reports and redacted SOS data. This is a genuine expansion of what
Supabase exposes, not a wash: the 12 Sep spec's residual was scoped to
"a relaying phone can reconstruct pairs, or with enough edges, a whole
household" (mesh-only, bounded to who has relayed data recently). This
redesign trades that bounded exposure for a permanent, remotely-queryable
one, in exchange for the join code actually working from anywhere. Update
`family/CircleEvents.kt`'s file-level residual comment to say so plainly
when this lands, matching how `evac_centre`'s own residual is documented.

## What gets deleted / replaced

- `family/CircleStore.kt` — `effectiveCircle`'s BFS-over-invite-graph fold
  is replaced by `resolveCircle` above. Genuinely simpler: no graph, no
  traversal, just two `maxBy`/`filter` passes over the same event list
  every other reducer in this app already works this way over.
- `family/CircleEvents.kt` — `TYPE_CIRCLE_INVITE`/`CircleInvitePayload`/
  `newCircleInviteEvent` replaced by `TYPE_CIRCLE_CREATE`/
  `TYPE_CIRCLE_JOIN` and their payloads/constructors above. File-level
  residual comment updated per the Privacy section.
- `family/CircleQr.kt` — `CircleCard(authorId, authorName)` replaced by a
  QR payload of `{circleId, name}`; prefix bumped `KAALERTO/CIRCLE/1:` →
  `KAALERTO/CIRCLE/2:`.
- `family/FamilyCircleScreen.kt` — gains the create/join entry state when
  `resolveCircle` returns null, and the "Mag-imbita" share action when it
  doesn't. Member list and check-in UI otherwise unchanged.
- `family/QrScannerScreen.kt` — decodes the new QR shape; a successful scan
  writes one `circle_join` event for the decoded `circleId` (mirrors how a
  scan today writes one `circle_invite` event — same one-write shape,
  different payload).
- `ui/KaAlertoApp.kt` — `Screen.FamilyCircle`/`Screen.QrScanner` branches
  updated for the new write shape; no new top-level screens needed beyond
  what `FamilyCircleScreen.kt` renders internally for its empty state.
- `sync/SupabaseSync.kt` — `SYNCED_TYPES` gains `TYPE_CIRCLE_CREATE`,
  `TYPE_CIRCLE_JOIN`; doc comment updated to drop `circle_invite` from the
  mesh-only list and explain why circles moved.

**Not part of this spec**, but worth carrying forward from the 12 Sep
spec's own closing note: nothing here changes `family_checkin`'s own event
shape, the notification watcher, or the SMS-placeholder status — those
stay exactly as `specs/2026-09-09-family-checkin-design.md` left them.

## Testing

**Unit tests** (`family/ResolveCircleTest.kt`, replacing
`family/EffectiveCircleTest.kt`):

- A device's own `circle_create` resolves to a circle of one (itself),
  with the right name.
- A device's own `circle_join` for an existing circle resolves to that
  circle's name and every other member who has create/joined it.
- **Switching**: a device with an older `circle_join` for circle A and a
  newer one for circle B resolves to B, and does not appear in A's member
  list when A's own fold runs.
- An unrelated circle's `circle_create`/`circle_join` events contribute
  nothing to my fold.
- Fold order independence: the same events in every rotation resolve to
  the same circle for every member (mirrors the existing reducer tests'
  own rotation-invariance pattern).
- QR encode/decode round-trip for the new `{circleId, name}` shape;
  `KAALERTO/CIRCLE/1:`-prefixed input is rejected, not misparsed.

**Manual device verification** (airplane mode where noted, two phones):

1. Phone A creates a circle, names it, confirms it shows as a circle of
   one.
2. A shares the code (share-sheet text, and separately, the QR). Phone B
   joins by pasting the code; confirm both A and B now show each other
   with the right name.
3. Check in on either phone; confirm the status reaches the other,
   unchanged from the existing check-in test plan.
4. With connectivity, confirm the join resolves promptly via Supabase
   (not waiting on mesh proximity). Then repeat the join in airplane mode
   between two phones already near each other over Bluetooth, confirming
   the mesh path still works standalone.
5. Phone B joins a third circle (a fresh code from a third device, or a
   second circle A never joins); confirm B drops out of A's member list
   on A's next fold, and A is unaffected.
6. Cold-relaunch both phones; confirm both circles are still correct after
   replay from Room, not just from in-memory state.

## Open questions for implementation time

- Whether `pairedAtMs` on `CircleMember` (now "this member's own
  create/join timestamp") needs surfacing in the UI at all — carried
  forward unresolved from the 12 Sep spec, still true here.
- Whether a `circle_join` for a `circleId` with no matching `circle_create`
  anywhere in the local log yet (join code entered before the create event
  has arrived) needs its own "waiting to confirm" UI state, or whether
  resolving to "a circle of one, for now" and correcting silently on the
  next fold (as designed above) is honest enough. Leaning toward the
  latter, consistent with this app's general no-pending-sync-UI stance,
  but flagging since it's a real first-run UX moment.
