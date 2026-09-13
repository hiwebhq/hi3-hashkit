package hi3.hashkit.domain.viz

import hi3.hashkit.ui.sitemap.Slot
import kotlin.math.cos
import kotlin.math.sin

/**
 * Layout and camera math for the 3D fleet view: miners become unit boxes in world space,
 * an orbit camera (yaw/pitch/zoom) projects them to screen points. Pure and unit-testable;
 * rendering (faces, palettes, gestures) lives in the UI layer.
 *
 * Layout rules (user-chosen): a miner with a parsed rack Location (B-R-T-P) sits at its
 * physical spot; everything else fills virtual racks of [rackSize]×[rackSize] units
 * (2..8, "like an actual rack of ASICs"), IP-ordered, placed behind the real racks.
 * FARMS mode instead clusters miners per farm into the same virtual racks.
 */
object Fleet3D {

    data class P3(val x: Float, val y: Float, val z: Float)

    data class Placed(val index: Int, val center: P3)

    /** A rack outline (min/max world corners), drawn as a frame so groups read as racks. */
    data class Frame(val min: P3, val max: P3)

    data class Scene(val placed: List<Placed>, val frames: List<Frame>)

    /** Input facts for one miner, already ordered however the caller likes. */
    data class Unit3D(
        val locationCode: String?,
        /** Numeric sort key for the IP (octet-wise); ties break by name upstream. */
        val ipKey: Long,
        /** 0-based farm ordinal for FARMS mode; -1 = no farm. */
        val farmOrdinal: Int,
    )

    enum class Layout { RACKS, FARMS }

    const val BOX = 1.0f // unit box edge; world units are box-sized
    const val RACK_SIZE_MIN = 2
    const val RACK_SIZE_MAX = 8
    private const val GAP = 0.35f
    private const val PITCH = BOX + GAP // center-to-center spacing
    private const val RACK_GAP = PITCH * 1.6f
    private const val BUILDING_GAP = PITCH * 4f
    private const val BACK_GRID_Z = -3.5f * PITCH
    private const val FRAME_PAD = GAP * 0.75f

    fun layout(units: List<Unit3D>, mode: Layout, rackSize: Int): Scene {
        val size = rackSize.coerceIn(RACK_SIZE_MIN, RACK_SIZE_MAX)
        return when (mode) {
            Layout.RACKS -> layoutRacks(units, size)
            Layout.FARMS -> layoutFarms(units, size)
        }
    }

    private fun layoutRacks(units: List<Unit3D>, rackSize: Int): Scene {
        val placed = mutableListOf<Placed>()
        val frames = mutableListOf<Frame>()
        val loose = mutableListOf<Pair<Int, Unit3D>>()
        val slotted = mutableMapOf<Pair<Int, Int>, MutableList<P3>>() // (building, rack) -> centers
        units.forEachIndexed { i, u ->
            val slot = Slot.parse(u.locationCode)
            if (slot != null) {
                val c = P3(
                    x = (slot.rack - 1) * (rackSize * PITCH + RACK_GAP) +
                        (slot.position - 1) * PITCH +
                        (slot.building - 1) * BUILDING_GAP,
                    y = (slot.tier - 1) * PITCH,
                    z = 0f,
                )
                placed += Placed(i, c)
                slotted.getOrPut(slot.building to slot.rack) { mutableListOf() } += c
            } else {
                loose += i to u
            }
        }
        slotted.values.forEach { frames += frameAround(it) }
        // Unplaced miners: IP-ordered virtual racks behind the real ones.
        placeVirtualRacks(
            loose.sortedBy { it.second.ipKey }.map { it.first },
            rackSize, originX = 0f, z = BACK_GRID_Z, placed, frames,
        )
        return Scene(placed, frames)
    }

    private fun layoutFarms(units: List<Unit3D>, rackSize: Int): Scene {
        val placed = mutableListOf<Placed>()
        val frames = mutableListOf<Frame>()
        var originX = 0f
        units.withIndex()
            .groupBy { it.value.farmOrdinal }
            .toSortedMap()
            .values.forEach { members ->
                val ordered = members.sortedBy { it.value.ipKey }.map { it.index }
                val racks = placeVirtualRacks(ordered, rackSize, originX, z = 0f, placed, frames)
                originX += racks * (rackSize * PITCH + RACK_GAP) + RACK_GAP
            }
        return Scene(placed, frames)
    }

    /** Fill rackSize×rackSize racks left-to-right from [originX]; returns racks used. */
    @Suppress("LongParameterList") // internal helper: sinks two accumulators + placement params
    private fun placeVirtualRacks(
        indices: List<Int>,
        rackSize: Int,
        originX: Float,
        z: Float,
        placed: MutableList<Placed>,
        frames: MutableList<Frame>,
    ): Int {
        val perRack = rackSize * rackSize
        indices.chunked(perRack).forEachIndexed { rackN, rackMembers ->
            val rackX = originX + rackN * (rackSize * PITCH + RACK_GAP)
            val centers = rackMembers.mapIndexed { n, i ->
                val c = P3(
                    x = rackX + (n % rackSize) * PITCH,
                    y = (n / rackSize) * PITCH,
                    z = z,
                )
                placed += Placed(i, c)
                c
            }
            check(centers.isNotEmpty()) // chunked() never yields an empty chunk
            // Frame the FULL rack footprint, not just occupied slots — empty bays read
            // as empty bays, like a real rack.
            frames += Frame(
                min = P3(rackX - HALF - FRAME_PAD, -HALF - FRAME_PAD, z - HALF - FRAME_PAD),
                max = P3(
                    rackX + (rackSize - 1) * PITCH + HALF + FRAME_PAD,
                    (rackSize - 1) * PITCH + HALF + FRAME_PAD,
                    z + HALF + FRAME_PAD,
                ),
            )
        }
        return (indices.size + perRack - 1) / perRack
    }

    private const val HALF = BOX / 2f

    private fun frameAround(centers: List<P3>): Frame = Frame(
        min = P3(
            centers.minOf { it.x } - HALF - FRAME_PAD,
            centers.minOf { it.y } - HALF - FRAME_PAD,
            centers.minOf { it.z } - HALF - FRAME_PAD,
        ),
        max = P3(
            centers.maxOf { it.x } + HALF + FRAME_PAD,
            centers.maxOf { it.y } + HALF + FRAME_PAD,
            centers.maxOf { it.z } + HALF + FRAME_PAD,
        ),
    )

    /** World-space center of a set of placements, for orbiting around the fleet middle. */
    fun centerOf(placed: List<Placed>): P3 {
        if (placed.isEmpty()) return P3(0f, 0f, 0f)
        return P3(
            placed.map { it.center.x }.average().toFloat(),
            placed.map { it.center.y }.average().toFloat(),
            placed.map { it.center.z }.average().toFloat(),
        )
    }

    data class Projected(val x: Float, val y: Float, val depth: Float, val scale: Float)

    /**
     * Orbit-camera perspective projection. Yaw/pitch in radians rotate the world around
     * [pivot]; [zoom] scales apparent size; result y grows downward (screen convention).
     * Depth sorts painter's-algorithm draws (larger = nearer); scale sizes the box.
     */
    @Suppress("LongParameterList") // a camera is inherently many scalars; grouping adds nothing
    fun project(
        p: P3,
        pivot: P3,
        yawRad: Float,
        pitchRad: Float,
        zoom: Float,
        screenCx: Float,
        screenCy: Float,
        pxPerUnit: Float,
    ): Projected {
        val x0 = p.x - pivot.x
        val y0 = p.y - pivot.y
        val z0 = p.z - pivot.z
        // Yaw around the vertical axis, then pitch around the horizontal.
        val x1 = x0 * cos(yawRad) + z0 * sin(yawRad)
        val z1 = -x0 * sin(yawRad) + z0 * cos(yawRad)
        val y2 = y0 * cos(pitchRad) - z1 * sin(pitchRad)
        val z2 = y0 * sin(pitchRad) + z1 * cos(pitchRad)
        // Perspective: camera sits CAMERA_DIST behind the pivot on +z.
        val denom = (CAMERA_DIST - z2).coerceAtLeast(NEAR_PLANE)
        val persp = CAMERA_DIST / denom * zoom
        return Projected(
            x = screenCx + x1 * persp * pxPerUnit,
            y = screenCy - y2 * persp * pxPerUnit,
            depth = z2,
            scale = persp,
        )
    }

    private const val CAMERA_DIST = 18f
    private const val NEAR_PLANE = 2f

    /** Octet-wise numeric key so 10.0.0.9 sorts before 10.0.0.107. */
    fun ipKey(host: String): Long {
        val parts = host.substringBefore(':').split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != IPV4_OCTETS) return Long.MAX_VALUE
        return parts.fold(0L) { acc, o -> acc * OCTET_RADIX + o }
    }

    private const val IPV4_OCTETS = 4
    private const val OCTET_RADIX = 256L

    /**
     * FLIR ironbow: normalized heat 0..1 → (r,g,b) floats through the classic
     * black→purple→red→orange→yellow→white ramp. Pure so the palette is testable.
     */
    fun ironbow(heat: Float): Triple<Float, Float, Float> {
        val t = heat.coerceIn(0f, 1f)
        val stops = IRONBOW_STOPS
        val seg = (t * (stops.size - 1)).toInt().coerceAtMost(stops.size - 2)
        val f = t * (stops.size - 1) - seg
        val (r1, g1, b1) = stops[seg]
        val (r2, g2, b2) = stops[seg + 1]
        return Triple(r1 + (r2 - r1) * f, g1 + (g2 - g1) * f, b1 + (b2 - b1) * f)
    }

    @Suppress("MagicNumber") // the palette IS the numbers
    private val IRONBOW_STOPS = listOf(
        Triple(0.00f, 0.00f, 0.00f), // black
        Triple(0.20f, 0.00f, 0.40f), // deep purple
        Triple(0.55f, 0.00f, 0.60f), // magenta
        Triple(0.85f, 0.20f, 0.10f), // red
        Triple(1.00f, 0.55f, 0.00f), // orange
        Triple(1.00f, 0.85f, 0.25f), // yellow
        Triple(1.00f, 1.00f, 1.00f), // white-hot
    )

    // ------------------------------------------------------------------ drone tour ----

    /** One frame of the automated drone tour: full camera state + which unit is focused. */
    data class TourFrame(
        val yaw: Float,
        val pitch: Float,
        val zoom: Float,
        val pivot: P3,
        /** Index into the placed list of the unit being inspected; -1 = fleet overview. */
        val focusPlacedIndex: Int,
    )

    private const val TOUR_OVERVIEW_MS = 6_000L
    private const val TOUR_TRAVEL_MS = 1_500L
    private const val TOUR_ORBIT_MS = 4_500L
    private const val TOUR_VISIT_MS = TOUR_TRAVEL_MS + TOUR_ORBIT_MS
    private const val TOUR_OVERVIEW_ZOOM = 1.0f
    private const val TOUR_CLOSE_ZOOM = 3.6f
    private const val TOUR_PITCH = 0.28f
    private const val TOUR_YAW_RATE = 0.45f // rad/s of continuous glide
    private const val MS_PER_SECOND = 1000f
    private const val TWO_PI = (Math.PI * 2).toFloat()

    /**
     * Drone flight plan: a slow orbit of the whole fleet, then a visit to each unit —
     * fly in, circle it a full turn at close zoom, fly on — looping forever. Pure
     * function of elapsed time so the flight is deterministic and testable.
     */
    fun tourFrame(elapsedMs: Long, targets: List<P3>, fleetPivot: P3): TourFrame {
        val cycle = TOUR_OVERVIEW_MS + targets.size * TOUR_VISIT_MS
        val t = if (cycle > 0) elapsedMs % cycle else 0L
        // Yaw advances continuously so the whole flight feels like one glide.
        val yaw = elapsedMs / MS_PER_SECOND * TOUR_YAW_RATE
        if (t < TOUR_OVERVIEW_MS || targets.isEmpty()) {
            return TourFrame(yaw, TOUR_PITCH, TOUR_OVERVIEW_ZOOM, fleetPivot, -1)
        }
        val visitT = t - TOUR_OVERVIEW_MS
        val idx = (visitT / TOUR_VISIT_MS).toInt().coerceAtMost(targets.size - 1)
        val inVisit = visitT - idx * TOUR_VISIT_MS
        val from = if (idx == 0) fleetPivot else targets[idx - 1]
        val target = targets[idx]
        return if (inVisit < TOUR_TRAVEL_MS) {
            val f = smooth(inVisit / TOUR_TRAVEL_MS.toFloat())
            TourFrame(
                yaw, TOUR_PITCH,
                TOUR_OVERVIEW_ZOOM + (TOUR_CLOSE_ZOOM - TOUR_OVERVIEW_ZOOM) * f,
                lerp(from, target, f),
                idx,
            )
        } else {
            // Full extra turn around the unit on top of the base glide.
            val orbitF = (inVisit - TOUR_TRAVEL_MS) / TOUR_ORBIT_MS.toFloat()
            TourFrame(yaw + orbitF * TWO_PI, TOUR_PITCH, TOUR_CLOSE_ZOOM, target, idx)
        }
    }

    @Suppress("MagicNumber") // the standard smoothstep polynomial
    private fun smooth(f: Float): Float {
        val t = f.coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun lerp(a: P3, b: P3, f: Float): P3 =
        P3(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, a.z + (b.z - a.z) * f)
}
