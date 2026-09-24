package com.macci.kaalerto.route

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.hypot

/**
 * PRD 7.9: routes with no internet. The phone carries the car-usable roads of Pangasinan
 * (`assets/roadgraph.bin`, built by `tools/roadgraph/build.py` from OpenStreetMap) and
 * searches them itself, so nothing leaves the device. Online, the public router is still
 * used (Routing.kt); this is what answers when it cannot be reached.
 *
 * Two routes are offered, then ranked by the same rule as online ones ([rankRoutes]): one
 * that avoids every road within reach of a spot reported impassable (S2, S3) or
 * conflicting, and the plain shortest one if it differs. When nothing avoids the floods,
 * only the shortest is shown and its flood count says so — never a route claimed safe.
 */
class RoadGraph(
    val lat: DoubleArray,
    val lon: DoubleArray,
    /** CSR: the edges out of node i are targets[offsets[i] until offsets[i + 1]]. */
    val offsets: IntArray,
    val targets: IntArray,
) {
    val size get() = lat.size
}

fun parseRoadGraph(bytes: ByteArray): RoadGraph? = runCatching {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val magic = ByteArray(4).also { buf.get(it) }
    require(String(magic) == "KRG1")
    val n = buf.int
    val m = buf.int
    val lat = DoubleArray(n)
    val lon = DoubleArray(n)
    for (i in 0 until n) {
        lat[i] = buf.int / 1e6
        lon[i] = buf.int / 1e6
    }
    val offsets = IntArray(n + 1) { buf.int }
    val targets = IntArray(m) { buf.int }
    RoadGraph(lat, lon, offsets, targets)
}.getOrNull()

/** The bundled graph, read from assets once per process. */
object BundledRoadGraph {
    @Volatile private var cached: RoadGraph? = null

    suspend fun get(context: Context): RoadGraph? = cached ?: withContext(Dispatchers.IO) {
        runCatching { context.assets.open("roadgraph.bin").use { it.readBytes() } }.getOrNull()
            ?.let(::parseRoadGraph)?.also { cached = it }
    }
}

/** Farther than this from any road in the graph, a point is outside the offline map. */
private const val MAX_SNAP_M = 1_000.0

/** Floods within this of the route line block it — the same reach [rankRoutes] counts with. */
private const val BLOCK_M = 75.0

/** No live speeds offline; a steady 30 km/h keeps the times honest about being rough. */
private const val OFFLINE_SPEED_MPS = 30_000.0 / 3600

private fun metres(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double =
    hypot((aLat - bLat) * 110_540.0, (aLon - bLon) * 111_320.0 * cos(Math.toRadians((aLat + bLat) / 2)))

internal fun nearestNode(g: RoadGraph, lat: Double, lon: Double): Pair<Int, Double> {
    var best = -1
    var bestM = Double.MAX_VALUE
    for (i in 0 until g.size) {
        val m = metres(lat, lon, g.lat[i], g.lon[i])
        if (m < bestM) {
            bestM = m
            best = i
        }
    }
    return best to bestM
}

/** A* over the graph; [blocked] vetoes a directed edge. Null when [to] cannot be reached. */
internal fun shortestPath(g: RoadGraph, from: Int, to: Int, blocked: (Int, Int) -> Boolean): IntArray? {
    val dist = DoubleArray(g.size) { Double.MAX_VALUE }
    val prev = IntArray(g.size) { -1 }
    val done = BooleanArray(g.size)
    val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
    fun h(i: Int) = metres(g.lat[i], g.lon[i], g.lat[to], g.lon[to])
    dist[from] = 0.0
    queue.add(h(from) to from)
    while (queue.isNotEmpty()) {
        val u = queue.poll()!!.second
        if (done[u]) continue
        if (u == to) break
        done[u] = true
        for (e in g.offsets[u] until g.offsets[u + 1]) {
            val v = g.targets[e]
            if (done[v] || blocked(u, v)) continue
            val d = dist[u] + metres(g.lat[u], g.lon[u], g.lat[v], g.lon[v])
            if (d < dist[v]) {
                dist[v] = d
                prev[v] = u
                queue.add((d + h(v)) to v)
            }
        }
    }
    if (dist[to] == Double.MAX_VALUE) return null
    val path = ArrayList<Int>()
    var at = to
    while (at != -1) {
        path += at
        at = prev[at]
    }
    return path.reversed().toIntArray()
}

private fun toRaw(g: RoadGraph, path: IntArray): RawRoute {
    val coords = path.map { g.lon[it] to g.lat[it] }
    var length = 0.0
    for (i in 1 until path.size) length += metres(g.lat[path[i - 1]], g.lon[path[i - 1]], g.lat[path[i]], g.lon[path[i]])
    return RawRoute(coords, length, length / OFFLINE_SPEED_MPS)
}

/** Offline counterpart of [fetchRoutes]. [RoutesResult.OutsideOfflineMap] when either end is off the graph. */
fun offlineRoutes(g: RoadGraph, from: LatLon, to: LatLon, floods: List<FloodPoint>): RoutesResult {
    val (start, startM) = nearestNode(g, from.lat, from.lon)
    val (end, endM) = nearestNode(g, to.lat, to.lon)
    if (start < 0 || startM > MAX_SNAP_M || endM > MAX_SNAP_M) return RoutesResult.OutsideOfflineMap
    if (start == end) return RoutesResult.NoRoute

    // A flood at either end is where the trip starts or the reason for going — not an obstacle.
    val obstacles = floods.filter {
        metres(it.lat, it.lon, from.lat, from.lon) > BLOCK_M && metres(it.lat, it.lon, to.lat, to.lon) > BLOCK_M
    }
    val avoid: (Int, Int) -> Boolean = { u, v ->
        val seg = listOf(g.lon[u] to g.lat[u], g.lon[v] to g.lat[v])
        obstacles.any { distanceToLineM(LatLon(it.lat, it.lon), seg) <= BLOCK_M }
    }
    val shortest = shortestPath(g, start, end) { _, _ -> false } ?: return RoutesResult.NoRoute
    val clear = if (obstacles.isEmpty()) null else shortestPath(g, start, end, avoid)
    val raw = listOfNotNull(clear, shortest.takeIf { clear == null || !it.contentEquals(clear) }).map { toRaw(g, it) }
    return RoutesResult.Ok(rankRoutes(raw, floods, to), offline = true)
}
