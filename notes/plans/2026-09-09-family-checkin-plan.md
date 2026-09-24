# Family / Household Check-In Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A household "circle" of paired devices, joined by a mutual one-scan QR handshake, where any member can post a one-tap "Ligtas ako" (I'm safe) check-in that every other member's phone shows with an age, entirely over the existing offline mesh — no server, no new transport.

**Architecture:** Two new replicated event types (`family_checkin`, `circle_invite`) ride the existing append-only event log and mesh with zero transport changes. Circle membership is computed as a pure derived view (`effectiveCircle`) — a small local address book of people I scanned, unioned with a fold over `circle_invite` events targeting me — rather than a stateful sync protocol. A second pure fold (`circleStatuses`) turns that membership plus the event log into what the screen renders. QR pairing reuses the existing hand-drawn ZXing encode/Canvas-render path for showing your own code, and adds `com.journeyapps:zxing-android-embedded` for the live scan (the app's first use of the `CAMERA` permission).

**Tech Stack:** Kotlin, Jetpack Compose, Room (existing `events` table, no schema change), kotlinx.serialization, ZXing (`core` — already a dependency; `zxing-android-embedded` — new).

**Spec:** [specs/2026-09-09-family-checkin-design.md](../specs/2026-09-09-family-checkin-design.md)

## Global Constraints

- **Every event is content-hash-deduplicated on insert** (`EventDao.insert`, `OnConflictStrategy.IGNORE`) — re-delivery over the mesh is always harmless. Never write code that assumes an event arrives exactly once.
- **`Event.lat`/`Event.lon` are non-nullable `Double`.** Events with no real location use `0.0, 0.0` (the "null island" sentinel), exactly as `identity/RoleEvents.kt` already does — never add a nullable-location workaround.
- **`Event.featureRef` must be `null`** for both new event types. The flood reducer (`data/Reducer.kt`) groups on `featureRef`; a non-null one would put a family-circle event on the map as a flood marker. This is enforced by a unit test, mirroring `RoleReducerTest`'s `role events never carry a featureRef` test.
- **The author's name is embedded in the event at creation, never looked up later** — same rule as every other event type in this app.
- **Nothing on the render path awaits the network.** Every read in this feature is local (Room + SharedPreferences).
- **Filipino is the base language everywhere**; every user-facing string goes through `com.macci.kaalerto.i18n.tr(fil, en)` (composable) or `tr(language, fil, en)` (non-composable), matching every other screen in the app.
- **No crypto, no accounts** (ground rule 4) — a scanned QR is trusted outright; this is stated in code comments where relevant, not hidden.

---

### Task 1: Circle event types and payload

**Files:**
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleEvents.kt`
- Test: `android/app/src/test/kotlin/com/macci/kaalerto/family/CircleEventsTest.kt`

**Interfaces:**
- Consumes: `com.macci.kaalerto.data.Event` (existing), `com.macci.kaalerto.identity.LocalIdentity.Identity` (existing), `com.macci.kaalerto.identity.ROLE_TTL_MS` (existing, reused for the invite's lifetime).
- Produces: `const val TYPE_CHECKIN`, `const val TYPE_CIRCLE_INVITE`, `data class CircleInvitePayload(val targetAuthorId: String)`, `fun CircleInvitePayload.encode(): String`, `fun decodeCircleInvitePayload(raw: String?): CircleInvitePayload?`, `fun newCheckInEvent(identity: LocalIdentity.Identity, lat: Double?, lon: Double?, nowMs: Long): Event`, `fun newCircleInviteEvent(identity: LocalIdentity.Identity, targetAuthorId: String, nowMs: Long): Event`, `const val CHECKIN_TTL_MS`.

- [ ] **Step 1: Write the failing tests**

Create `android/app/src/test/kotlin/com/macci/kaalerto/family/CircleEventsTest.kt`:

```kotlin
package com.macci.kaalerto.family

import com.macci.kaalerto.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleEventsTest {

    private val now = 1_700_000_000_000L

    private fun identity(id: String, name: String) =
        LocalIdentity.Identity(authorId = id, authorName = name, authorRole = LocalIdentity.ROLE_RESIDENT)

    @Test
    fun `a check-in with no location uses the null-island sentinel, same as role events`() {
        val event = newCheckInEvent(identity("local-a1", "Residente A1B2"), lat = null, lon = null, nowMs = now)

        assertEquals(0.0, event.lat, 0.0)
        assertEquals(0.0, event.lon, 0.0)
    }

    @Test
    fun `a check-in with a real location stores it verbatim`() {
        val event = newCheckInEvent(identity("local-a1", "Residente A1B2"), lat = 18.1709, lon = 120.6058, nowMs = now)

        assertEquals(18.1709, event.lat, 0.000001)
        assertEquals(120.6058, event.lon, 0.000001)
    }

    @Test
    fun `a check-in carries no payload and the right type`() {
        val event = newCheckInEvent(identity("local-a1", "Residente A1B2"), lat = null, lon = null, nowMs = now)

        assertEquals(TYPE_CHECKIN, event.type)
        assertNull(event.payload)
        assertEquals("local-a1", event.authorId)
        assertEquals("Residente A1B2", event.authorName)
    }

    @Test
    fun `a circle invite carries the target authorId in its payload, not a flat column`() {
        val event = newCircleInviteEvent(identity("local-a1", "Residente A1B2"), targetAuthorId = "local-b2", nowMs = now)

        assertEquals(TYPE_CIRCLE_INVITE, event.type)
        assertEquals("local-a1", event.authorId) // the inviter
        val payload = decodeCircleInvitePayload(event.payload)
        assertEquals("local-b2", payload?.targetAuthorId) // who it's for
    }

    @Test
    fun `circle events never carry a featureRef or severity, so the flood reducer never sees them`() {
        val checkIn = newCheckInEvent(identity("local-a1", "A"), lat = 18.0, lon = 120.0, nowMs = now)
        val invite = newCircleInviteEvent(identity("local-a1", "A"), targetAuthorId = "local-b2", nowMs = now)

        assertTrue(checkIn.featureRef == null && checkIn.severity == null)
        assertTrue(invite.featureRef == null && invite.severity == null)
    }

    @Test
    fun `an invite outlives a check-in, matching how a role outlives an observation`() {
        val checkIn = newCheckInEvent(identity("local-a1", "A"), lat = null, lon = null, nowMs = now)
        val invite = newCircleInviteEvent(identity("local-a1", "A"), targetAuthorId = "local-b2", nowMs = now)

        assertTrue("invite must outlive check-in", invite.expiresAt - now > checkIn.expiresAt - now)
    }

    @Test
    fun `decodeCircleInvitePayload returns null for garbage rather than throwing`() {
        assertNull(decodeCircleInvitePayload(null))
        assertNull(decodeCircleInvitePayload("not json"))
        assertNull(decodeCircleInvitePayload("""{"wrong":"shape"}"""))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.family.CircleEventsTest"`
Expected: FAIL — `CircleEvents.kt` does not exist yet, compilation error ("unresolved reference: newCheckInEvent" etc.).

- [ ] **Step 3: Write the implementation**

Create `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleEvents.kt`:

```kotlin
package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.identity.ROLE_TTL_MS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** A "Ligtas ako" (I'm safe) presence ping. Carries no payload — the event's own
 * flat columns are enough (see [newCheckInEvent]). */
const val TYPE_CHECKIN = "family_checkin"

/** A device that scanned another's QR posting "add this authorId back to your circle
 * too" — the mechanism that makes pairing mutual from one scan. See [newCircleInviteEvent]. */
const val TYPE_CIRCLE_INVITE = "circle_invite"

val FAMILY_TYPES = setOf(TYPE_CHECKIN, TYPE_CIRCLE_INVITE)

/**
 * A check-in is an observation that ages, the same way a flood report is — "last known
 * status: 3 days ago" is still shown by the reducer as long as the event exists (nothing
 * here filters on expiry), but 24h matches the order of magnitude every other
 * observation-type event in this app uses for how long the mesh actively keeps relaying
 * it, rather than inventing a new time constant with no precedent.
 */
const val CHECKIN_TTL_MS = 24L * 60 * 60 * 1000

private val circleJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * What rides in [Event.payload] for [TYPE_CIRCLE_INVITE]. The event's own `authorId`/
 * `authorName` already identify the inviter (standard rule, same as every other event
 * type) — this payload only needs to say who the invite is *for*.
 */
@Serializable
data class CircleInvitePayload(val targetAuthorId: String)

fun CircleInvitePayload.encode(): String = circleJson.encodeToString(CircleInvitePayload.serializer(), this)

fun decodeCircleInvitePayload(raw: String?): CircleInvitePayload? =
    raw?.let { runCatching { circleJson.decodeFromString(CircleInvitePayload.serializer(), it) }.getOrNull() }

/**
 * The check-in itself. `featureRef = null`, same reasoning as the `sos*` family: a
 * presence ping is not an observation of a flooded segment, and `data/Reducer.kt` must
 * never see it. `lat`/`lon` are the null-island sentinel when the resident declines to
 * share position ("location optional" per `docs/03-architecture.md` §1.6.4) — see
 * `identity/RoleEvents.kt`'s identical convention for "no real coordinate here".
 */
fun newCheckInEvent(
    identity: LocalIdentity.Identity,
    lat: Double?,
    lon: Double?,
    nowMs: Long,
): Event = Event(
    id = "checkin-${UUID.randomUUID()}",
    type = TYPE_CHECKIN,
    lat = lat ?: 0.0,
    lon = lon ?: 0.0,
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = identity.authorId,
    authorName = identity.authorName,
    authorRole = identity.authorRole,
    timestampMs = nowMs,
    expiresAt = nowMs + CHECKIN_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = null,
)

/**
 * Written by the **scanning** device immediately after it decodes the other party's QR.
 * `lat`/`lon` are the null-island sentinel — who is in a circle is not a fact about where
 * either phone was standing, same reasoning as `identity/RoleEvents.kt`'s role events.
 *
 * The TTL is [ROLE_TTL_MS] (a year), not [CHECKIN_TTL_MS], and that difference is
 * load-bearing: `family/CircleStore.kt`'s `effectiveCircle` folds *every* invite event
 * targeting a device on every read, forever — if this event purged on the same short
 * clock as a check-in, a pairing would silently come undone once the invite aged out of
 * local storage, with no record anywhere of why. Membership is not an observation that
 * goes stale, exactly the same call `identity/RoleEvents.kt` already made for roles.
 */
fun newCircleInviteEvent(
    identity: LocalIdentity.Identity,
    targetAuthorId: String,
    nowMs: Long,
): Event = Event(
    id = "circle-invite-${UUID.randomUUID()}",
    type = TYPE_CIRCLE_INVITE,
    lat = 0.0,
    lon = 0.0,
    featureRef = null,
    severity = null,
    waterLevel = null,
    authorId = identity.authorId,
    authorName = identity.authorName,
    authorRole = identity.authorRole,
    timestampMs = nowMs,
    expiresAt = nowMs + ROLE_TTL_MS,
    origin = "local",
    hopCount = 0,
    note = null,
    payload = CircleInvitePayload(targetAuthorId = targetAuthorId).encode(),
)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.family.CircleEventsTest"`
Expected: PASS, all 7 tests green.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/family/CircleEvents.kt android/app/src/test/kotlin/com/macci/kaalerto/family/CircleEventsTest.kt
git commit -m "feat(android): family check-in and circle-invite event types"
```

---

### Task 2: Circle store and the effective-circle fold

**Files:**
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleStore.kt`
- Test: `android/app/src/test/kotlin/com/macci/kaalerto/family/EffectiveCircleTest.kt`

**Interfaces:**
- Consumes: `Task 1`'s `TYPE_CIRCLE_INVITE`, `decodeCircleInvitePayload`; `com.macci.kaalerto.data.Event`.
- Produces: `data class CircleMember(val authorId: String, val displayName: String, val pairedAtMs: Long)`, `object CircleStore { fun get(context: Context): List<CircleMember>; fun add(context: Context, member: CircleMember) }`, `fun effectiveCircle(locallyAdded: List<CircleMember>, allEvents: List<Event>, myAuthorId: String): List<CircleMember>`.

`effectiveCircle` is a pure function with no `Context` dependency — deliberately, so it can be unit tested directly. `CircleStore.get`/`add` touch `Context`/`SharedPreferences` and follow the exact same untested-by-unit-test precedent as `geofence/HomeLocationStore.kt` (no test file exists for that class either) — their correctness is proven by the manual device verification in Task 8, not a JVM unit test.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/macci/kaalerto/family/EffectiveCircleTest.kt`:

```kotlin
package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveCircleTest {

    private val now = 1_700_000_000_000L
    private val me = "local-me"

    private fun invite(id: String, from: String, fromName: String, targetAuthorId: String, minutesAgo: Long) = Event(
        id = id,
        type = TYPE_CIRCLE_INVITE,
        lat = 0.0,
        lon = 0.0,
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = from,
        authorName = fromName,
        authorRole = "resident",
        timestampMs = now - minutesAgo * 60_000,
        expiresAt = now + 1_000_000_000L,
        origin = "local",
        hopCount = 0,
        note = null,
        payload = CircleInvitePayload(targetAuthorId).encode(),
    )

    @Test
    fun `a circle_invite targeting me contributes the inviter`() {
        val events = listOf(invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = me, minutesAgo = 5))

        val result = effectiveCircle(locallyAdded = emptyList(), allEvents = events, myAuthorId = me)

        assertEquals(listOf("local-boy"), result.map { it.authorId })
        assertEquals("Boy", result.single().displayName)
    }

    @Test
    fun `a circle_invite targeting someone else is dropped`() {
        val events = listOf(invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = "local-someone-else", minutesAgo = 5))

        val result = effectiveCircle(locallyAdded = emptyList(), allEvents = events, myAuthorId = me)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `two invites from the same inviter still produce exactly one entry`() {
        val events = listOf(
            invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = me, minutesAgo = 5),
            invite("i2", from = "local-boy", fromName = "Boy", targetAuthorId = me, minutesAgo = 1), // mesh re-delivery, or scanned twice
        )

        val result = effectiveCircle(locallyAdded = emptyList(), allEvents = events, myAuthorId = me)

        assertEquals(1, result.size)
    }

    @Test
    fun `an invite I authored myself never contributes an entry back to my own list`() {
        // I am the one who scanned Boy's QR — I authored this invite, targeting Boy.
        val events = listOf(invite("i1", from = me, fromName = "Me", targetAuthorId = "local-boy", minutesAgo = 5))

        val result = effectiveCircle(locallyAdded = emptyList(), allEvents = events, myAuthorId = me)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `locally-added members are always included even with no invite events`() {
        val locallyAdded = listOf(CircleMember(authorId = "local-boy", displayName = "Boy", pairedAtMs = now))

        val result = effectiveCircle(locallyAdded = locallyAdded, allEvents = emptyList(), myAuthorId = me)

        assertEquals(listOf("local-boy"), result.map { it.authorId })
    }

    @Test
    fun `a member present both locally and via invite appears once`() {
        val locallyAdded = listOf(CircleMember(authorId = "local-boy", displayName = "Boy", pairedAtMs = now))
        val events = listOf(invite("i1", from = "local-boy", fromName = "Boy", targetAuthorId = me, minutesAgo = 5))

        val result = effectiveCircle(locallyAdded, events, me)

        assertEquals(1, result.size)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.family.EffectiveCircleTest"`
Expected: FAIL — `CircleStore.kt` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleStore.kt`:

```kotlin
package com.macci.kaalerto.family

import android.content.Context
import com.macci.kaalerto.data.Event
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One paired household-circle member, as scanned by this device. */
@Serializable
data class CircleMember(val authorId: String, val displayName: String, val pairedAtMs: Long)

/**
 * The local address book: people *this device* explicitly scanned. SharedPreferences,
 * one key, JSON-encoded list — the same small-local-state pattern as
 * `geofence/HomeLocationStore.kt` and `identity/LocalIdentity.kt`. No Room table: there
 * is no need for two devices to ever agree on who is in a circle (NFR-4 doesn't apply
 * here the way it does to roles — a circle is a personal watch-list, not a shared fact
 * the barangay must agree on), so a replicated membership protocol would be
 * over-engineering against a feature the build plan's own cut ladder puts third-from-last.
 */
object CircleStore {
    private const val PREFS = "kaalerto_circle"
    private const val KEY_MEMBERS = "members"
    private val json = Json { ignoreUnknownKeys = true }
    private val membersSerializer = ListSerializer(CircleMember.serializer())

    fun get(context: Context): List<CircleMember> {
        val raw = prefs(context).getString(KEY_MEMBERS, null) ?: return emptyList()
        return runCatching { json.decodeFromString(membersSerializer, raw) }.getOrDefault(emptyList())
    }

    /** Dedups on `authorId` — re-scanning an existing member is a no-op, not a duplicate entry. */
    fun add(context: Context, member: CircleMember) {
        val current = get(context)
        if (current.any { it.authorId == member.authorId }) return
        val updated = current + member
        prefs(context).edit().putString(KEY_MEMBERS, json.encodeToString(membersSerializer, updated)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * "Who's in my circle" as a pure derived view, not something written on receipt of an
 * invite — no background watcher, no write-on-arrival, no idempotency bookkeeping
 * needed. This reuses the same reducer pattern every other piece of derived state in
 * this app already relies on (`data/Reducer.kt`, `sos/SosReducer.kt`,
 * `identity/RoleReducer.kt`) instead of inventing a new stateful one.
 *
 * [locallyAdded] only ever holds people *I* scanned (the inviter side, written
 * immediately at scan time). People who scanned *me* show up purely through this fold
 * over [TYPE_CIRCLE_INVITE] events — recomputed the same way on every call, never cached.
 *
 * Known caveat, acceptable for now (see `specs/2026-09-09-family-checkin-design.md`):
 * because this folds *every* matching invite event on every call, a member removed
 * locally in some future release would immediately reappear, since the original invite
 * event never leaves the log. Not a problem today — there is no removal UI yet.
 */
fun effectiveCircle(
    locallyAdded: List<CircleMember>,
    allEvents: List<Event>,
    myAuthorId: String,
): List<CircleMember> {
    val invited = allEvents
        .asSequence()
        .filter { it.type == TYPE_CIRCLE_INVITE }
        .mapNotNull { event -> decodeCircleInvitePayload(event.payload)?.let { event to it } }
        .filter { (_, payload) -> payload.targetAuthorId == myAuthorId }
        .map { (event, _) -> CircleMember(authorId = event.authorId, displayName = event.authorName, pairedAtMs = event.timestampMs) }
    return (locallyAdded + invited).distinctBy { it.authorId }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.family.EffectiveCircleTest"`
Expected: PASS, all 6 tests green.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/family/CircleStore.kt android/app/src/test/kotlin/com/macci/kaalerto/family/EffectiveCircleTest.kt
git commit -m "feat(android): circle membership store and the effectiveCircle fold"
```

---

### Task 3: Circle status reducer

**Files:**
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleReducer.kt`
- Test: `android/app/src/test/kotlin/com/macci/kaalerto/family/CircleReducerTest.kt`

**Interfaces:**
- Consumes: Task 1's `TYPE_CHECKIN`; Task 2's `CircleMember`; `com.macci.kaalerto.data.Event`.
- Produces: `data class CircleMemberStatus(val authorId: String, val displayName: String, val lastCheckInMs: Long?)`, `fun circleStatuses(allEvents: List<Event>, circle: List<CircleMember>): List<CircleMemberStatus>`.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/macci/kaalerto/family/CircleReducerTest.kt`:

```kotlin
package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircleReducerTest {

    private val now = 1_700_000_000_000L

    private fun checkIn(id: String, authorId: String, authorName: String, minutesAgo: Long) = Event(
        id = id,
        type = TYPE_CHECKIN,
        lat = 0.0,
        lon = 0.0,
        featureRef = null,
        severity = null,
        waterLevel = null,
        authorId = authorId,
        authorName = authorName,
        authorRole = "resident",
        timestampMs = now - minutesAgo * 60_000,
        expiresAt = now + CHECKIN_TTL_MS,
        origin = "local",
        hopCount = 0,
        note = null,
    )

    private val boy = CircleMember(authorId = "local-boy", displayName = "Boy", pairedAtMs = now)
    private val maria = CircleMember(authorId = "local-maria", displayName = "Maria", pairedAtMs = now)

    @Test
    fun `a member with no check-in events reads as no check-in yet`() {
        val result = circleStatuses(allEvents = emptyList(), circle = listOf(boy))

        assertEquals(1, result.size)
        assertNull(result.single().lastCheckInMs)
    }

    @Test
    fun `the latest check-in per member wins`() {
        val events = listOf(
            checkIn("c1", "local-boy", "Boy", minutesAgo = 30),
            checkIn("c2", "local-boy", "Boy", minutesAgo = 5),
        )

        val result = circleStatuses(events, listOf(boy))

        assertEquals(now - 5 * 60_000, result.single().lastCheckInMs)
    }

    @Test
    fun `a check-in from someone not in the circle is ignored`() {
        val events = listOf(checkIn("c1", "local-stranger", "Stranger", minutesAgo = 5))

        val result = circleStatuses(events, listOf(boy))

        assertNull(result.single().lastCheckInMs)
    }

    @Test
    fun `every paired member appears even with zero matching events — absence is not the same claim as presence`() {
        val result = circleStatuses(allEvents = emptyList(), circle = listOf(boy, maria))

        assertEquals(setOf("local-boy", "local-maria"), result.map { it.authorId }.toSet())
    }

    @Test
    fun `the fold is independent of delivery order`() {
        val events = listOf(
            checkIn("c1", "local-boy", "Boy", minutesAgo = 40),
            checkIn("c2", "local-maria", "Maria", minutesAgo = 20),
            checkIn("c3", "local-boy", "Boy", minutesAgo = 10),
        )
        val expected = circleStatuses(events, listOf(boy, maria))

        for (i in events.indices) {
            val rotated = events.drop(i) + events.take(i)
            assertEquals("rotation $i disagreed", expected, circleStatuses(rotated, listOf(boy, maria)))
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.family.CircleReducerTest"`
Expected: FAIL — `CircleReducer.kt` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleReducer.kt`:

```kotlin
package com.macci.kaalerto.family

import com.macci.kaalerto.data.Event

/** One circle member as the Family screen renders them. */
data class CircleMemberStatus(
    val authorId: String,
    val displayName: String,
    /** Null means "no check-in yet" — never rendered the same as a bad-news state. */
    val lastCheckInMs: Long?,
)

/**
 * Folds the shared event stream independently of the flood reducer (`data/Reducer.kt`),
 * the same shape as `sos/SosReducer.kt`/`identity/RoleReducer.kt`. [circle] is
 * `effectiveCircle(...)`'s output, not raw `CircleStore` — every paired member appears
 * here regardless of whether any check-in has arrived, the same "absence isn't the same
 * claim as presence" reasoning `map/RoleActionStrip.kt` already established for the
 * empty rescue queue.
 */
fun circleStatuses(allEvents: List<Event>, circle: List<CircleMember>): List<CircleMemberStatus> {
    val circleIds = circle.map { it.authorId }.toSet()
    val latestCheckIn: Map<String, Event> = allEvents
        .asSequence()
        .filter { it.type == TYPE_CHECKIN && it.authorId in circleIds }
        .groupBy { it.authorId }
        .mapValues { (_, events) -> events.maxBy { it.timestampMs } }

    return circle.map { member ->
        val event = latestCheckIn[member.authorId]
        CircleMemberStatus(
            authorId = member.authorId,
            // The event's own authorName is the ground truth for display, same rule as
            // everywhere else in the app — falls back to the locally-known name only
            // when nobody has checked in yet to supply a fresher one.
            displayName = event?.authorName ?: member.displayName,
            lastCheckInMs = event?.timestampMs,
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.family.CircleReducerTest"`
Expected: PASS, all 5 tests green.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/family/CircleReducer.kt android/app/src/test/kotlin/com/macci/kaalerto/family/CircleReducerTest.kt
git commit -m "feat(android): circle check-in status reducer"
```

---

### Task 4: QR payload for circle pairing

**Files:**
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleQr.kt`
- Test: `android/app/src/test/kotlin/com/macci/kaalerto/family/CircleQrTest.kt`

**Interfaces:**
- Consumes: `com.macci.kaalerto.sos.encodeQr`, `com.macci.kaalerto.sos.QrMatrix` (existing, reused as-is — no duplication).
- Produces: `const val CIRCLE_QR_PREFIX`, `data class CircleCard(val authorId: String, val authorName: String)`, `fun CircleCard.encode(): String`, `fun decodeCircleCard(scanned: String): CircleCard?`.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/macci/kaalerto/family/CircleQrTest.kt`:

```kotlin
package com.macci.kaalerto.family

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.macci.kaalerto.sos.QrMatrix
import com.macci.kaalerto.sos.encodeQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors `sos/SosQrTest.kt`'s approach exactly: render the matrix to real pixels and
 * hand them to ZXing's *decoder*, so "scannable" is a checked fact, not a claim. */
class CircleQrTest {

    private fun render(matrix: QrMatrix, modulePx: Int = 4): Triple<IntArray, Int, Int> {
        val quietZone = 4
        val side = (matrix.size + quietZone * 2) * modulePx
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                val originX = (x + quietZone) * modulePx
                val originY = (y + quietZone) * modulePx
                for (dy in 0 until modulePx) {
                    for (dx in 0 until modulePx) {
                        pixels[(originY + dy) * side + originX + dx] = 0xFF000000.toInt()
                    }
                }
            }
        }
        return Triple(pixels, side, side)
    }

    private fun decode(content: String): String {
        val (pixels, width, height) = render(encodeQr(content))
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        val result = QRCodeReader().decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true))
        return result.text
    }

    private val card = CircleCard(authorId = "local-a1b2c3d4", authorName = "Residente A1B2")

    @Test
    fun `a circle card round-trips through an actual QR decode`() {
        val encoded = card.encode()

        val scanned = decode(encoded)

        assertEquals(encoded, scanned)
        val decoded = decodeCircleCard(scanned)
        assertNotNull(decoded)
        assertEquals(card.authorId, decoded!!.authorId)
        assertEquals(card.authorName, decoded.authorName)
    }

    @Test
    fun `the payload carries the identity itself, not a link to it`() {
        val encoded = card.encode()

        assertTrue(encoded.startsWith(CIRCLE_QR_PREFIX))
        assertTrue(!encoded.contains("http"))
        assertTrue(encoded.contains(card.authorId))
    }

    @Test
    fun `a QR that is not ours is rejected rather than half-parsed`() {
        assertNull(decodeCircleCard("https://example.com"))
        assertNull(decodeCircleCard("""{"authorId":"local-1"}"""))
        assertNull(decodeCircleCard(CIRCLE_QR_PREFIX + "not json"))
        // Also not confusable with the SOS card's own prefix:
        assertNull(decodeCircleCard("KAALERTO/SOS/1:{}"))
    }

    @Test
    fun `the code stays coarse enough to read off a phone screen`() {
        val matrix = encodeQr(card.encode())

        // Same worst-case-device math as SosQrTest — a 150dp box at 2.0x density.
        val worstCaseBoxPx = 300
        val modulePx = worstCaseBoxPx / (matrix.size + 8)
        assertTrue("QR is ${matrix.size} modules -> ${modulePx}px per module", modulePx >= 3)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.family.CircleQrTest"`
Expected: FAIL — `CircleQr.kt` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleQr.kt`:

```kotlin
package com.macci.kaalerto.family

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Marks the payload as a circle-pairing card, distinct from `sos/SosQr.kt`'s
 * `KAALERTO/SOS/1:` prefix — a scanner (or a person eyeballing a raw scan result) can
 * tell the two apart, and `decodeCircleCard` rejects anything that isn't this. */
const val CIRCLE_QR_PREFIX = "KAALERTO/CIRCLE/1:"

/** What a circle-pairing QR encodes — just enough to add the other device to a circle
 * and post the mutual [TYPE_CIRCLE_INVITE] back. Short keys for the same reason
 * `sos/SosQr.kt`'s `SosCard` uses them: QR capacity is the binding constraint. */
@Serializable
data class CircleCard(
    @SerialName("id") val authorId: String,
    @SerialName("n") val authorName: String,
)

private val cardJson = Json { ignoreUnknownKeys = true }

fun CircleCard.encode(): String = CIRCLE_QR_PREFIX + cardJson.encodeToString(CircleCard.serializer(), this)

/** Returns null for anything that is not one of our circle-pairing codes, or that will not parse. */
fun decodeCircleCard(scanned: String): CircleCard? {
    if (!scanned.startsWith(CIRCLE_QR_PREFIX)) return null
    return runCatching {
        cardJson.decodeFromString(CircleCard.serializer(), scanned.removePrefix(CIRCLE_QR_PREFIX))
    }.getOrNull()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.macci.kaalerto.family.CircleQrTest"`
Expected: PASS, all 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/family/CircleQr.kt android/app/src/test/kotlin/com/macci/kaalerto/family/CircleQrTest.kt
git commit -m "feat(android): circle-pairing QR payload"
```

---

### Task 5: Add the live-scanner dependency and CAMERA permission

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `com.journeyapps.barcodescanner.ScanContract`, `ScanOptions`, `ScanIntentResult` become available for Task 6 to use with `rememberLauncherForActivityResult`.

No unit test for this task — it's a build-configuration change, verified by a successful compile/assemble. `zxing-android-embedded:4.3.0`'s own minSdk is 19, well under this project's minSdk 26, so no multidex or desugaring config is needed.

- [ ] **Step 1: Add the version and library entry**

In `android/gradle/libs.versions.toml`, under `[versions]`, add a line right after the existing `zxing = "3.5.3"`:

```toml
zxingAndroidEmbedded = "4.3.0"
```

Under `[libraries]`, replace the existing `zxing-core` block's comment and add the new library beneath it:

```toml
# QR encoding for the rescue card (BUILD_TASKS.md day 8) and for the family-circle
# pairing card (build day 11a). "core" is the pure-Java half of ZXing with no Android
# dependency, so the encoder runs in unit tests and both cards are decoded and asserted
# rather than eyeballed — both are drawn on a Compose Canvas from the bit matrix, no
# bitmap. Kept deliberately separate from zxing-android-embedded below: this one never
# touches a camera.
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }

# The live QR *scanner*, for family-circle pairing only (build day 11a) — the app's
# first use of the CAMERA permission. Rejected on day 8 for the rescue card ("a camera
# scanner UI this build never needs") because nothing needed to *scan* a QR back then;
# day 11a does, and hand-rolling CameraX + frame-by-frame ZXing decoding for a solved
# problem is not a good use of hackathon time. Handles its own camera permission
# request and scanning UI — see family/FamilyCircleScreen.kt.
zxing-android-embedded = { group = "com.journeyapps", name = "zxing-android-embedded", version.ref = "zxingAndroidEmbedded" }
```

- [ ] **Step 2: Add the dependency**

In `android/app/build.gradle.kts`, right after the existing `implementation(libs.zxing.core)` line, add:

```kotlin
    implementation(libs.zxing.android.embedded)
```

- [ ] **Step 3: Add the CAMERA permission**

In `android/app/src/main/AndroidManifest.xml`, find the block of `<uses-permission>` tags (near the top, alongside `ACCESS_FINE_LOCATION` etc.) and add:

```xml
    <!-- Family-circle QR pairing only (build day 11a) — the app's first camera use. -->
    <uses-permission android:name="android.permission.CAMERA" />
```

- [ ] **Step 4: Verify the build resolves and compiles**

Run: `cd android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew compileDebugKotlin -q`
Expected: succeeds with no output (matches this repo's established `-q` convention — any output is a failure).

Run: `./gradlew assembleDebug -q`
Expected: succeeds; confirms the new AAR's manifest (which declares its own `CaptureActivity`) merges cleanly with no conflicts.

- [ ] **Step 5: Commit**

```bash
git add android/gradle/libs.versions.toml android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml
git commit -m "build(android): add zxing-android-embedded for family-circle QR scanning"
```

---

### Task 6: The Family Circle screen, wired into navigation

**Files:**
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleSubmit.kt`
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/family/FamilyCircleScreen.kt`
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/nav/Screen.kt`
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/nav/NavDrawer.kt`
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/ui/KaAlertoApp.kt`

**Interfaces:**
- Consumes: Task 1 (`newCheckInEvent`, `newCircleInviteEvent`), Task 2 (`CircleMember`, `CircleStore`, `effectiveCircle`), Task 3 (`CircleMemberStatus`, `circleStatuses`), Task 4 (`CircleCard`, `CIRCLE_QR_PREFIX`, `decodeCircleCard`), Task 5 (`ScanContract`, `ScanOptions`), plus existing `com.macci.kaalerto.sos.QrCode` (the Canvas-drawing composable, reused as-is), `com.macci.kaalerto.identity.LocalIdentity`, `com.macci.kaalerto.data.EventRepository`, `com.macci.kaalerto.data.KaAlertoDatabase`, `com.macci.kaalerto.i18n.tr`.
- Produces: `suspend fun submitCheckIn(context: Context, lat: Double?, lon: Double?)`, `suspend fun submitCircleInvite(context: Context, targetAuthorId: String)`, `@Composable fun FamilyCircleScreen(modifier: Modifier, myQrContent: String, statuses: List<CircleMemberStatus>, onCheckIn: () -> Unit, onMemberScanned: (authorId: String, authorName: String) -> Unit, onBack: () -> Unit, onOpenMenu: () -> Unit)`, `data object Screen.FamilyCircle`.

This task has no new unit tests of its own — it is UI wiring plus two thin Context-touching submit functions, matching every prior UI-wiring task in this codebase (e.g. the original NavDrawer addition: "121/121 tests and lint pass — pure UI wiring, no new pure logic to cover"). Verification is a clean build plus manual device checks in Task 8.

- [ ] **Step 1: The submit functions**

Create `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleSubmit.kt`:

```kotlin
package com.macci.kaalerto.family

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity

/** Posts this device's "Ligtas ako". [lat]/[lon] null means the resident declined to
 * share position — see `newCheckInEvent`'s null-island handling. */
suspend fun submitCheckIn(context: Context, lat: Double?, lon: Double?) {
    val identity = LocalIdentity.getOrCreate(context)
    val event = newCheckInEvent(identity, lat, lon, System.currentTimeMillis())
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}

/** Posts the mutual-pairing invite after this device scans [targetAuthorId]'s QR. */
suspend fun submitCircleInvite(context: Context, targetAuthorId: String) {
    val identity = LocalIdentity.getOrCreate(context)
    val event = newCircleInviteEvent(identity, targetAuthorId, System.currentTimeMillis())
    EventRepository(KaAlertoDatabase.getInstance(context).eventDao()).insert(event)
}
```

- [ ] **Step 2: Add the screen to the nav switch**

In `android/app/src/main/kotlin/com/macci/kaalerto/nav/Screen.kt`, add inside the `sealed interface Screen { ... }` body, after `data object EvacCentres : Screen`:

```kotlin

    /** Build day 11a — a household circle joined by QR, plus the one-tap "Ligtas ako". */
    data object FamilyCircle : Screen
```

- [ ] **Step 3: Add the drawer entry**

In `android/app/src/main/kotlin/com/macci/kaalerto/nav/NavDrawer.kt`, add a new parameter to the `NavDrawer` composable's signature, right after `onOpenProfile: () -> Unit,`:

```kotlin
    onOpenFamily: () -> Unit,
```

Then in the drawer row `Column`, add a new row right after the "Ang profile ko" row and before "Mga silungan":

```kotlin
            DrawerRow(tr("Mapa", "Map"), onClick = { onDismiss(); onOpenMap() })
            DrawerRow(tr("Papel mo sa barangay", "Your role in the barangay"), onClick = { onDismiss(); onOpenRoles() })
            DrawerRow(tr("Ang profile ko", "My profile"), onClick = { onDismiss(); onOpenProfile() })
            DrawerRow(tr("Aking Pamilya", "My Family"), onClick = { onDismiss(); onOpenFamily() })
            DrawerRow(tr("Mga silungan", "Evacuation centres"), onClick = { onDismiss(); onOpenEvac() })
```

- [ ] **Step 4: The screen itself**

Create `android/app/src/main/kotlin/com/macci/kaalerto/family/FamilyCircleScreen.kt`:

```kotlin
package com.macci.kaalerto.family

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.macci.kaalerto.i18n.tr
import com.macci.kaalerto.nav.HamburgerButton
import com.macci.kaalerto.sos.QrCode

/**
 * "Aking Pamilya" — a household circle joined by QR, plus a one-tap "Ligtas ako".
 * Deliberately no ViewModel: [statuses] and [myQrContent] are computed by the caller
 * (`ui/KaAlertoApp.kt`) from the shared event stream, the same pattern
 * `Screen.EvacCentres` already uses — this screen is presentation only.
 */
@Composable
fun FamilyCircleScreen(
    myQrContent: String,
    statuses: List<CircleMemberStatus>,
    onCheckIn: () -> Unit,
    onMemberScanned: (authorId: String, authorName: String) -> Unit,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents ?: return@rememberLauncherForActivityResult
        val card = decodeCircleCard(scanned) ?: return@rememberLauncherForActivityResult
        onMemberScanned(card.authorId, card.authorName)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HamburgerButton(onClick = onOpenMenu)
                Spacer(Modifier.size(8.dp))
                Text(tr("Aking Pamilya", "My Family"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            // "Isara" (Close), same text-button-not-icon convention `evac/EvacScreen.kt`
            // already uses for onBack alongside its own onOpenMenu hamburger.
            Box(Modifier.clickable(onClick = onBack).padding(8.dp)) {
                Text(tr("Isara", "Close"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onCheckIn)
                .padding(vertical = 20.dp),
        ) {
            Text(
                tr("Ligtas ako", "I'm safe"),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(tr("MGA KASAPI", "MEMBERS"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(statuses, key = { it.authorId }) { status -> CircleMemberRow(status) }
        }

        Spacer(Modifier.height(16.dp))
        Text(tr("I-SCAN PARA MAGDAGDAG", "SCAN TO ADD"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        QrCode(content = myQrContent, modifier = Modifier.size(150.dp))
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable {
                    scanLauncher.launch(
                        ScanOptions().setPrompt(tr("Itapat sa QR", "Point at the QR")).setBeepEnabled(false),
                    )
                }
                .padding(vertical = 14.dp),
        ) {
            Text(
                tr("Mag-scan ng QR", "Scan a QR"),
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CircleMemberRow(status: CircleMemberStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(status.displayName, fontWeight = FontWeight.Medium)
        Text(
            checkInAgeLabel(status.lastCheckInMs),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Ligtas ako · 18 min ago", or "no check-in yet" — never a bad-news state, since the
 * only status this feature can carry at all is "safe". Deliberately duplicated rather
 * than reusing `detail/DetailSheet.kt`'s private `ageLabel` (not exported) — the same
 * small duplication already exists between that function and `sos/SosShared.kt`'s
 * `elapsedLabel`, which uses a different mm:ss format unsuited to this screen. */
@Composable
private fun checkInAgeLabel(lastCheckInMs: Long?): String {
    if (lastCheckInMs == null) return tr("wala pang check-in", "no check-in yet")
    val minutes = (System.currentTimeMillis() - lastCheckInMs) / 60_000
    val age = when {
        minutes < 1 -> tr("ngayon lang", "just now")
        minutes < 60 -> tr("$minutes min ang nakalipas", "$minutes min ago")
        else -> tr("${minutes / 60}h ${minutes % 60}m ang nakalipas", "${minutes / 60}h ${minutes % 60}m ago")
    }
    return "${tr("Ligtas ako", "I'm safe")} · $age"
}
```

- [ ] **Step 5: Wire it into `KaAlertoApp.kt`**

In `android/app/src/main/kotlin/com/macci/kaalerto/ui/KaAlertoApp.kt`, add a new branch inside the `when (val current = screen) { ... }` block, right after the `Screen.EvacCentres -> { ... }` branch closes:

```kotlin
        Screen.FamilyCircle -> {
            val events by mapEvents.collectAsStateWithLifecycle()
            val familyIdentity = LocalIdentity.getOrCreate(context)
            var locallyAdded by remember { mutableStateOf(CircleStore.get(context)) }
            val effective = remember(locallyAdded, events, familyIdentity.authorId) {
                effectiveCircle(locallyAdded, events, familyIdentity.authorId)
            }
            val statuses = remember(effective, events) { circleStatuses(events, effective) }
            FamilyCircleScreen(
                modifier = modifier,
                myQrContent = remember(familyIdentity.authorId, familyIdentity.authorName) {
                    CircleCard(familyIdentity.authorId, familyIdentity.authorName).encode()
                },
                statuses = statuses,
                onCheckIn = { scope.launch { submitCheckIn(context, lat = null, lon = null) } },
                onMemberScanned = { authorId, authorName ->
                    CircleStore.add(context, CircleMember(authorId, authorName, System.currentTimeMillis()))
                    locallyAdded = CircleStore.get(context)
                    scope.launch { submitCircleInvite(context, authorId) }
                },
                onBack = { screen = Screen.Map },
                onOpenMenu = { drawerOpen = true },
            )
        }
```

Add the matching imports near the top of `KaAlertoApp.kt`, alongside the other `com.macci.kaalerto.*` imports:

```kotlin
import com.macci.kaalerto.family.CircleCard
import com.macci.kaalerto.family.CircleMember
import com.macci.kaalerto.family.CircleStore
import com.macci.kaalerto.family.FamilyCircleScreen
import com.macci.kaalerto.family.circleStatuses
import com.macci.kaalerto.family.effectiveCircle
import com.macci.kaalerto.family.submitCheckIn
import com.macci.kaalerto.family.submitCircleInvite
```

Finally, wire the drawer callback in the same file's `NavDrawer(...)` call (near the bottom, alongside `onOpenEvac`):

```kotlin
        onOpenFamily = { screen = Screen.FamilyCircle },
```

- [ ] **Step 6: Verify the build compiles and lint passes**

Run: `cd android && ./gradlew compileDebugKotlin lintDebug -q`
Expected: succeeds with no output.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/family/CircleSubmit.kt android/app/src/main/kotlin/com/macci/kaalerto/family/FamilyCircleScreen.kt android/app/src/main/kotlin/com/macci/kaalerto/nav/Screen.kt android/app/src/main/kotlin/com/macci/kaalerto/nav/NavDrawer.kt android/app/src/main/kotlin/com/macci/kaalerto/ui/KaAlertoApp.kt
git commit -m "feat(android): Family Circle screen — QR pairing, Ligtas ako, member list"
```

---

### Task 7: Check-in notifications

**Files:**
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/notification/NotificationChannels.kt`
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/notification/CircleCheckInNotification.kt`
- Create: `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleCheckInNotifier.kt`
- Modify: `android/app/src/main/kotlin/com/macci/kaalerto/KaAlertoApplication.kt`

**Interfaces:**
- Consumes: Task 1 (`TYPE_CHECKIN`), Task 2 (`CircleStore`, `effectiveCircle`); existing `EventRepository`, `KaAlertoDatabase`, `LocalIdentity`, `NotificationChannels`.
- Produces: `const val NotificationChannels.CHANNEL_FAMILY_CHECKIN`, `object CircleCheckInNotification { fun notify(context: Context, event: Event) }`, `class CircleCheckInNotifier(context: Context) { fun start(scope: CoroutineScope) }`.

No unit test — mirrors `geofence/GeofenceNotifier.kt`, which also has none (Context/Flow-driven side-effecting code, verified by manual device testing, same as every notification-firing class in this codebase).

- [ ] **Step 1: Add the notification channel**

In `android/app/src/main/kotlin/com/macci/kaalerto/notification/NotificationChannels.kt`, add a new constant alongside the existing ones:

```kotlin
    /** A circle member checked in. Its own channel for the same reason CHANNEL_SOS is
     * its own: a resident who mutes flood chatter must not thereby mute "your sister
     * checked in". */
    const val CHANNEL_FAMILY_CHECKIN = "family_checkin"
```

And a new channel registration inside `ensureCreated`, after the existing `CHANNEL_MESH` block:

```kotlin
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FAMILY_CHECKIN, tr(language, "Pamilya — check-in", "Family check-in"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = tr(language, "May kasapi ng iyong circle na nag-check in", "A member of your circle checked in")
            },
        )
```

- [ ] **Step 2: The notify object**

Create `android/app/src/main/kotlin/com/macci/kaalerto/notification/CircleCheckInNotification.kt`:

```kotlin
package com.macci.kaalerto.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.macci.kaalerto.MainActivity
import com.macci.kaalerto.R
import com.macci.kaalerto.data.Event
import com.macci.kaalerto.i18n.LanguagePrefs
import com.macci.kaalerto.i18n.tr

/** Fires a local notification for one circle member's check-in — mirrors
 * `FloodNotifier.notify`'s shape exactly. */
object CircleCheckInNotification {
    fun notify(context: Context, event: Event) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val language = LanguagePrefs.get(context)
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(context, 0, openApp, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_FAMILY_CHECKIN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tr(language, "Ligtas si ${event.authorName}", "${event.authorName} is safe"))
            .setContentText(tr(language, "Nag-check in gamit ang Aking Pamilya", "Checked in via My Family"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
    }
}
```

- [ ] **Step 3: The watcher**

Create `android/app/src/main/kotlin/com/macci/kaalerto/family/CircleCheckInNotifier.kt`:

```kotlin
package com.macci.kaalerto.family

import android.content.Context
import com.macci.kaalerto.data.EventRepository
import com.macci.kaalerto.data.KaAlertoDatabase
import com.macci.kaalerto.identity.LocalIdentity
import com.macci.kaalerto.notification.CircleCheckInNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Fires a local notification when a circle member checks in. Mirrors
 * `geofence/GeofenceNotifier.kt`'s shape exactly: the first Flow emission establishes a
 * baseline with no notifications (a fresh install, or a cold start with a backlog of
 * old check-ins, must not fire N notifications at once), and only a genuinely new
 * [TYPE_CHECKIN] event from someone in [effectiveCircle] counts. Pairing itself
 * (`circle_invite`) is deliberately silent — see the design spec's Notifications section.
 */
class CircleCheckInNotifier(private val context: Context) {
    fun start(scope: CoroutineScope) {
        val repository = EventRepository(KaAlertoDatabase.getInstance(context).eventDao())
        scope.launch {
            var knownIds: Set<String>? = null
            repository.observeAll().collect { events ->
                val currentIds = events.map { it.id }.toSet()
                val previous = knownIds
                if (previous != null) {
                    val myAuthorId = LocalIdentity.getOrCreate(context).authorId
                    val circleIds = effectiveCircle(CircleStore.get(context), events, myAuthorId)
                        .map { it.authorId }
                        .toSet()
                    events
                        .asSequence()
                        .filter { it.id !in previous && it.type == TYPE_CHECKIN && it.authorId in circleIds }
                        .forEach { event -> CircleCheckInNotification.notify(context, event) }
                }
                knownIds = currentIds
            }
        }
    }
}
```

- [ ] **Step 4: Start it at application launch**

In `android/app/src/main/kotlin/com/macci/kaalerto/KaAlertoApplication.kt`, add the import:

```kotlin
import com.macci.kaalerto.family.CircleCheckInNotifier
```

And add, right after the existing `GeofenceNotifier(this).start(applicationScope)` line:

```kotlin
        // Build day 11a. Fires when a household-circle member checks in — see
        // family/CircleCheckInNotifier.kt.
        CircleCheckInNotifier(this).start(applicationScope)
```

- [ ] **Step 5: Verify the build compiles and lint passes**

Run: `cd android && ./gradlew compileDebugKotlin lintDebug -q`
Expected: succeeds with no output.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/macci/kaalerto/notification/NotificationChannels.kt android/app/src/main/kotlin/com/macci/kaalerto/notification/CircleCheckInNotification.kt android/app/src/main/kotlin/com/macci/kaalerto/family/CircleCheckInNotifier.kt android/app/src/main/kotlin/com/macci/kaalerto/KaAlertoApplication.kt
git commit -m "feat(android): notify on a circle member's check-in"
```

---

### Task 8: Full verification pass and documentation

**Files:**
- Modify: `CLAUDE.md` (append the standard "what changed, what to verify" entry this repo writes after every build day)
- No code files — this task is verification and documentation only.

- [ ] **Step 1: Run the full unit test suite and lint**

Run: `cd android && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew testDebugUnitTest lintDebug -q`
Expected: succeeds with no output. Confirm the new test count by running:

```bash
grep -h '<testsuite ' app/build/test-results/testDebugUnitTest/*.xml | grep -o 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END{print s}'
grep -h '<testsuite ' app/build/test-results/testDebugUnitTest/*.xml | grep -o 'failures="[0-9]*"' | awk -F'"' '{s+=$2} END{print s}'
```

Expected: failures = 0, and the total is 22 more than whatever the count was before this plan started (7 + 6 + 5 + 4 = 22 new tests across Tasks 1–4).

- [ ] **Step 2: Build and install a fresh debug APK on a running emulator**

Run: `./gradlew assembleDebug -q`

Then, with an emulator running (`adb devices` shows a device):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: Manual device verification — two emulators, airplane mode**

Follow the spec's own manual test plan (`specs/2026-09-09-family-checkin-design.md`, "Testing" section) exactly, on two running emulators:

1. Airplane mode on both phones first, then pair by QR — **only phone A scans phone B**.
2. Confirm phone A's circle list shows B immediately.
3. Confirm phone B's circle list shows A too, within the mesh's normal exchange latency — this is the behavior the mutual-pairing revision exists to prove.
4. Check in ("Ligtas ako") on phone A.
5. Confirm the check-in appears on phone B's circle list, with a correct age label.
6. Confirm a notification fires on B for the check-in (not for the pairing itself).
7. Cold-relaunch both phones, confirm circle membership and last-known status both survive.

- [ ] **Step 4: Update CLAUDE.md**

Append a new entry to `CLAUDE.md`'s "Current state" section (after the most recent existing entry), following this repo's established format — state what was built, what was verified, and any caveats found during manual testing. Write the actual content based on what Step 3 showed (if a step failed, document the fix that was needed, matching how every prior build day's entry in `CLAUDE.md` records real findings rather than a scripted summary).

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: record family check-in build day 11a — verified two-phone QR pairing over mesh"
```

---

## What this plan deliberately does not cover

- **Route check** (the second half of build day 11) — a separate plan, once this one ships, per the spec's own scope note.
- **Circle member removal** — no UI, no tombstone. See the spec's Non-goals and the `effectiveCircle` caveat in Task 2.
- **The location-optional toggle's exact UX** — Task 6's `onCheckIn` call passes `lat = null, lon = null` unconditionally for this pass (always-omit, the simpler of the spec's two open options) rather than adding a location-consent UI element. Revisit if the demo script calls for showing a check-in's location.
