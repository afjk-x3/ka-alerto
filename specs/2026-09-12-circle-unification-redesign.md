# Circle unification — redesign

Status: proposed, not yet implemented. Narrow amendment to
`specs/2026-09-09-family-checkin-design.md` — everything in that spec about
the check-in event, the notification watcher, and the screen layout stands
unchanged. Only "how a scan turns into circle membership" changes.

## Why this exists

A field bug report asked for one specific behavior: *if User 3 scans
**either** User 1's or User 2's QR code, User 3 should join the exact same
circle as both of them — not create a second, disconnected group.* An
attempted fix (external, unreviewed, uncommitted — see git history around
9-12 Sep) added a persisted `circleId` concept (`CircleStore.Circle`,
`CircleLogic.processScan`, a `circleId` field on both `CircleCard` and
`CircleInvitePayload`) to solve this. It was reviewed against the actual
requirement and does not meet it:

- A QR only ever encodes one person plus (in the attempted fix) one
  `circleId` string — never a member roster. So when a device scans someone
  whose circle it doesn't already have locally, it can only reconstruct a
  circle of **the two people involved in that one scan**. Anyone else
  already in the scanned person's circle is silently dropped from the
  result.
- Worse, the scanned party's own device never learns the `circleId` at all.
  `CircleStore.addToCircle` is only ever called on the *scanner's* device
  (confirmed by grep — `mergeCircles`/`createCircle` are dead code, never
  called from the app). The scanned party only sees the pairing through the
  existing ephemeral `effectiveCircle` fold, which never had a `circleId` to
  begin with. Their own QR keeps encoding `circleId = null` forever, so the
  *next* person who scans *them* (rather than the original scanner) always
  falls into the "no circleId" branch and mints a brand-new, disconnected
  circle — reproducing the exact bug the fix was meant to close.
- No test exercises this end to end (`CircleLogicTest.kt` doesn't exist).

The root problem isn't the specific bug — it's the shape of the fix. A
`circleId` is a piece of shared state that every member's device would need
to agree on, kept in a structure (`CircleStore`'s persisted `Circle`) that
nothing ever reconciles across devices. This app has exactly one mechanism
for two devices to agree on a fact with no server and no negotiation: fold
the same replicated event log the same way, and let transitivity fall out
of the fold instead of being tracked by hand. Roles, SOS state, and the
flood reducer already work this way. Circle membership should too — and
once it does, "scan A, or scan B, end up in the same circle" stops being a
special case and becomes a restatement of graph connectivity.

## Design

**Circle membership is the connected component of the `circle_invite`
event graph containing me — computed fresh on every fold, never persisted,
never assigned an ID of its own.**

Treat every `circle_invite` event as one *undirected* edge between two
people (who scanned whom is provenance, not a constraint on the result).
"Who's in my circle" is everyone reachable from `myAuthorId` by following
those edges through every `circle_invite` event this device has ever
received — not just events that directly name me. Two devices holding the
same set of `circle_invite` events fold to the same circle, by the same
NFR-4 reasoning `RoleReducer`/`SosReducer` already rely on — this is not a
new invariant, it's the existing one applied to a graph instead of a flat
filter.

```kotlin
fun effectiveCircle(
    allEvents: List<Event>,
    myAuthorId: String,
): List<CircleMember> {
    data class Edge(val authorId: String, val authorName: String,
                     val targetId: String, val targetName: String, val atMs: Long)

    val edges = allEvents
        .asSequence()
        .filter { it.type == TYPE_CIRCLE_INVITE }
        .mapNotNull { e -> decodeCircleInvitePayload(e.payload)?.let {
            Edge(e.authorId, e.authorName, it.targetAuthorId, it.targetAuthorName, e.timestampMs)
        } }
        .toList()

    val neighbors = mutableMapOf<String, MutableSet<String>>()
    val nameOf = mutableMapOf<String, String>()
    val firstSeenAt = mutableMapOf<String, Long>()
    for (edge in edges) {
        neighbors.getOrPut(edge.authorId) { mutableSetOf() }.add(edge.targetId)
        neighbors.getOrPut(edge.targetId) { mutableSetOf() }.add(edge.authorId)
        nameOf[edge.authorId] = edge.authorName
        nameOf[edge.targetId] = edge.targetName
        firstSeenAt.merge(edge.authorId, edge.atMs, ::minOf)
        firstSeenAt.merge(edge.targetId, edge.atMs, ::minOf)
    }

    // BFS from myAuthorId over the undirected invite graph.
    val visited = mutableSetOf(myAuthorId)
    val queue = ArrayDeque(listOf(myAuthorId))
    while (queue.isNotEmpty()) {
        for (neighbor in neighbors[queue.removeFirst()].orEmpty()) {
            if (visited.add(neighbor)) queue.add(neighbor)
        }
    }

    return visited.filterNot { it == myAuthorId }.map { id ->
        CircleMember(authorId = id, displayName = nameOf[id] ?: id, pairedAtMs = firstSeenAt[id] ?: 0L)
    }
}
```

This one function replaces `CircleStore.get/add`, `CircleLogic.processScan`,
`CircleStore.Circle`/`addToCircle`/`mergeCircles`/`createCircle`/
`generateCircleId`, and every `circleId` field the attempted fix added. It
is a pure function of the shared event log, same shape as
`data/Reducer.kt`, `sos/SosReducer.kt`, `identity/RoleReducer.kt` — no new
category of state, no new file beyond the one this already lives in
(`family/CircleStore.kt`, kept as the home for this function; the
`CircleStore` *object* — SharedPreferences persistence — is deleted
entirely, not just emptied).

**Why this needs no fan-out messaging, unlike the attempted fix.** The
attempted fix's `CircleLogic.processScan` sent a `circle_invite` to every
*other* member of a circle at scan time, because its `circleId`-based model
had no other way to make a third person's device aware of the group.
Transitivity here is free: as soon as `edge(me, B)` exists in my own event
log (written the instant I scan B — see below) and `edge(A, B)` eventually
arrives over the mesh (it already does, unconditionally — every event rides
`MeshProtocol`'s anti-entropy exchange regardless of type), my own next BFS
walks `me → B → A` without anyone needing to have sent me anything
addressed to A. One event per scan, exactly as the original 9 Sep spec
already had it.

**One real gap in the original design, fixed here rather than carried
forward.** The committed `CircleInvitePayload(targetAuthorId: String)`
never carries the *target's* display name — only the inviter's, via the
event's own `authorId`/`authorName` columns. In the original one-hop
design this didn't matter: the scanning device already had the target's
name from decoding their QR card directly, and stashed it in the local
`CircleStore`. In the graph design there is no longer a local store to
stash it in, and a transitively-connected member (someone I never scanned
and who never scanned me) has no other event authored *by them* guaranteed
to exist — so their name must ride on the one edge event that does
exist. Fix: add `targetAuthorName` to the payload, filled in by the
scanning device from the QR it just decoded (data it already has in hand,
zero new lookups):

```kotlin
@Serializable
data class CircleInvitePayload(val targetAuthorId: String, val targetAuthorName: String)
```

This is a payload-shape change only — `Event.payload` is a schema-less JSON
column (the same "no schema change needed" property `CLAUDE.md` already
credits this mechanism with for every other event type), so it needs no
Room migration. Old event rows with no `targetAuthorName` decode fine under
`ignoreUnknownKeys`/a default value and simply can't have their target
member named until that person authors their own edge — a pre-existing
limitation stated plainly rather than solved retroactively, not a
regression this redesign introduces.

**`CircleCard` (the QR payload) does not need a `circleId` field at all.**
Revert it to the originally-shipped two-field shape
(`authorId`, `authorName`) — there is no group identity for a QR to carry
when group membership is never assigned an ID in the first place.

## Non-goals (unchanged from, or reinforced by, the 9 Sep spec)

- **No canonical circle name or ID, ever.** Nothing needs one: the UI shows
  "who's in my circle" as a flat member list, exactly as today. If a future
  screen wants a label like "3-person circle," derive it fresh each render
  from the current member set (e.g. a sorted-and-hashed digest) — never
  persist a decision that two devices would need to keep in sync, which is
  the exact mistake this redesign is undoing.
- **No guaranteed same-instant convergence across devices.** If User 3
  scans User 2 before `edge(1,2)` has reached User 3's phone over the mesh,
  User 3's own circle list shows only {2} until that edge arrives, then
  grows to {1, 2} on the next fold with no user action needed. This is the
  same eventual-consistency tolerance every other event-sourced feature in
  this app already accepts (mesh delivery is not instant) — not a defect
  specific to this feature, and not something to build a "pending sync"
  UI state around.
- **No removal/leave-circle mechanism** — unchanged from the 9 Sep spec's
  own non-goal; the tombstone caveat noted there applies identically here
  (a removed member's edge event never leaves the log, so they'd
  reappear on the next fold — still nobody's problem until removal UI
  exists).

## Privacy residual — restated, not newly introduced

`family/CircleEvents.kt`'s existing file-level disclosure already covers
this fully: `circle_invite` rides the mesh in the clear, and any relaying
device can read the pairing graph straight out of its own local database.
This redesign changes *what* that graph reveals in one respect worth
naming explicitly: previously a relaying phone could only ever reconstruct
**pairs** (who invited whom, one edge at a time). Once any device computes
full transitive closure, a phone holding enough edges can reconstruct an
entire household's membership as a set, not just isolated pairs. This is
an inherent consequence of solving "unified circle" with no central
authority and no crypto (ground rule 4) — there is no protocol change that
avoids it without one of those two — so it is disclosed here rather than
treated as a new bug. Update `CircleEvents.kt`'s existing residual comment
to say so plainly when this lands.

## Pairing flow (revised)

Unchanged from the 9 Sep spec except that step 3 loses all of its
branching:

1. Screen shows your own QR: `CircleCard(authorId, authorName)` — same as
   originally shipped, `circleId` field removed.
2. "Mag-scan ng QR" opens the camera scanner (unchanged;
   `zxing-android-embedded`'s own scan activity — the "camera takes over
   the whole screen" complaint is a separate, already-identified UI issue
   with the wrapper screen, not something this spec touches).
3. On a successful decode: write exactly one `circle_invite` event —
   `authorId`/`authorName` = mine (as always), `targetAuthorId` = the
   scanned card's `authorId`, `targetAuthorName` = the scanned card's
   `authorName`. That's the entire handler. No local store write, no
   circle lookup, no fan-out to other members — `effectiveCircle`'s next
   fold (triggered the normal way, by the same `Flow` every other screen
   in this app already observes off `EventRepository`) picks up the new
   edge and, transitively, anyone already connected through it.
4. Unchanged: a non-`KAALERTO/CIRCLE/1:` scan shows an error, no crash.

## What gets deleted

Everything the attempted fix added for group tracking, since the graph
fold makes it unnecessary:

- `family/CircleLogic.kt` — the whole file (`ScanResult`, `processScan`).
- `CircleStore` object in `family/CircleStore.kt` — `Circle`,
  `getCircles`/`getCircle`/`getCircleForMember`/`getAllMembers`/
  `addToCircle`/`createCircle`/`mergeCircles`/`removeMember`/
  `generateCircleId`, and the SharedPreferences persistence underneath all
  of it. `effectiveCircle` is the only thing that survives in this file,
  in the revised form above.
- `circleId` field on `CircleMember`, `CircleCard`, and
  `CircleInvitePayload` (the last one replaced by `targetAuthorName`, not
  simply dropped).
- The `Screen.QrScanner` wiring in `ui/KaAlertoApp.kt` stays (that part of
  the attempted fix was a reasonable, independent response to the
  full-screen-camera complaint) but its `onResult` body shrinks to the
  single-event write in step 3 above, and `onMemberScanned` — dead in the
  attempted fix already, never called from `FamilyCircleScreen` — should
  be removed from `FamilyCircleScreen`'s parameter list rather than kept
  unused.

Not part of this spec, but flagged so they aren't lost: the attempted fix
also changed `SeedLoader`'s seed-report epoch to a hardcoded, incorrectly
computed timestamp (a year off, would empty the demo map) and changed
`SosMeshPolicy.REDACTED_AUTHOR` from Filipino to English (a user-visible
string, not wire-only despite its new comment). Both are unrelated to
circle unification and should be reverted independently of whether this
redesign is accepted.

## Testing

**Unit tests** (new file `family/EffectiveCircleTest.kt` additions, or
extend the existing one):

- Direct pairing still works: A scans B → B appears in A's circle
  immediately (edge exists in A's own local event log the instant the
  event is written, no mesh round trip needed for your own scan).
- **The actual bug-report scenario**: given edges `(1↔2)` and `(1↔3)` (User
  1 scanned both, or was scanned by both, in either direction), User 2's
  fold and User 3's fold both include each other — proving transitivity
  works without either of them ever scanning the other.
- **Scan-order independence**: the same three edges folded in every
  rotation/order produce the same circle for every member (mirrors the
  existing role-fold test's "rotate the whole event list, assert identical
  result" pattern).
- A `circle_invite` naming people entirely unrelated to me contributes
  nothing to my own fold.
- A member reachable only transitively (never directly invited by or
  inviting me) still gets a real display name, not their raw `authorId` —
  this is what `targetAuthorName` exists to guarantee; a regression test
  should construct exactly this case (edge `A↔B` with no edge touching
  `me`, plus edge `me↔A`) and assert `B`'s name renders correctly in `me`'s
  fold.
- Re-scanning an existing member (duplicate edge, or the same edge
  delivered twice by mesh redelivery) doesn't duplicate the member or
  break the BFS (adjacency sets already dedupe; assert it explicitly
  anyway).
- QR encode/decode round-trip for the two-field `CircleCard` (already
  covered by the existing `CircleQrTest.kt`; just drop its `circleId`
  assertions if the attempted fix's version of that test lands first).

**Manual device verification** (airplane mode, three phones — the first
time this feature has actually needed a third device):

1. Phones A and B pair by QR (either direction, one scan only — per the 9
   Sep spec's existing test plan).
2. Confirm both A and B show each other.
3. Phone C scans **B**, not A. Confirm C's circle shows B immediately
   (local edge) and, once the mesh carries `edge(A,B)` to C, **A also
   appears on C without C ever scanning A**. This is the specific scenario
   the original bug report named and the one the attempted fix failed.
4. Reverse the last check: instead of C scanning B, have C scan A. Confirm
   B appears on C the same way, proving the result doesn't depend on which
   of the two existing members gets scanned.
5. Check in on any one phone; confirm the status reaches all three,
   unchanged from the existing check-in test plan (this path is untouched
   by this spec).
6. Cold-relaunch all three; confirm all three circles are still correct
   after replay from Room, not just from in-memory state.

## Files touched

- `family/CircleStore.kt` — delete the `CircleStore` object and
  `CircleMember.circleId`; replace `effectiveCircle`'s body as above.
  Keep the file (it's still the natural home for the fold function and any
  future circle-membership helper).
- `family/CircleLogic.kt` — delete.
- `family/CircleEvents.kt` — `CircleInvitePayload` gains
  `targetAuthorName`; `newCircleInviteEvent` takes and forwards it; update
  the file-level residual comment per the Privacy section above.
- `family/CircleQr.kt` — `CircleCard` loses `circleId`.
- `family/CircleCheckInNotifier.kt` — call site updates to
  `effectiveCircle(events, myAuthorId)` (drops the `CircleStore` argument
  entirely — there's nothing left to pass).
- `ui/KaAlertoApp.kt` — `Screen.FamilyCircle` and `Screen.QrScanner`
  branches simplify to the single-event-write handler in step 3 above;
  drop the now-nonexistent `circles`/`myCircleId` state and the
  `onMemberScanned` dead parameter.
- `family/FamilyCircleScreen.kt` — drop the `onMemberScanned` parameter
  (unused even in the attempted fix).
- Test files: `family/EffectiveCircleTest.kt` (extended per Testing
  above), `family/CircleEventsTest.kt` (payload assertions updated for
  `targetAuthorName`), `family/CircleQrTest.kt` (drop `circleId`
  assertions if present). Delete any `CircleLogicTest.kt` if one exists
  from the attempted fix (it doesn't, per the review — confirmed by grep).

## Open questions for implementation time

- Whether `pairedAtMs` (renamed in spirit to "earliest edge this device
  has seen connecting them," per the code above) needs to be exposed in
  the UI at all — the current screen doesn't render it; if it stays
  display-only-if-ever-used, the exact semantics matter less than the
  field existing for type-shape continuity with `CircleReducer`.
- Whether to cap BFS traversal depth or member count for pathological
  cases (someone scanning dozens of unrelated people, merging unrelated
  households into one giant component). Out of scope per the 9 Sep spec's
  own "no group size enforcement in code" non-goal — flagging only because
  the graph model makes an accidental merge easier to create by mistake
  (one bad scan can now bridge two previously-separate households) than
  the original one-hop model did. Product judgment call, not a technical
  blocker.
