package hi3.hashkit.domain.viz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Fleet3DTest {

    private fun unit(loc: String? = null, ip: String = "10.0.0.1", farm: Int = -1) =
        Fleet3D.Unit3D(loc, Fleet3D.ipKey(ip), farm)

    @Test
    fun `slotted miners land on rack coordinates, loose ones fill virtual racks behind`() {
        val scene = Fleet3D.layout(
            listOf(
                unit(loc = "B1-R1-T1-P1"),
                unit(loc = "B1-R1-T2-P1"),
                unit(ip = "10.0.0.107"),
                unit(ip = "10.0.0.9"),
            ),
            Fleet3D.Layout.RACKS,
            rackSize = 4,
        )
        assertEquals(4, scene.placed.size)
        val byIndex = scene.placed.associateBy { it.index }
        // Tier 2 sits above tier 1 in the same column.
        assertEquals(byIndex[0]!!.center.x, byIndex[1]!!.center.x, 1e-4f)
        assertTrue(byIndex[1]!!.center.y > byIndex[0]!!.center.y)
        // Loose miners sit on the back plane, IP-ordered: .9 before .107.
        assertTrue(byIndex[2]!!.center.z < 0f && byIndex[3]!!.center.z < 0f)
        assertTrue(byIndex[3]!!.center.x < byIndex[2]!!.center.x)
        // Frames: one for the real rack, one for the virtual rack.
        assertEquals(2, scene.frames.size)
    }

    @Test
    fun `rack size caps units per virtual rack`() {
        val scene = Fleet3D.layout(
            List(5) { unit(ip = "10.0.0.${it + 1}") },
            Fleet3D.Layout.RACKS,
            rackSize = 2, // 2x2 racks -> 5 miners need 2 racks
        )
        assertEquals(2, scene.frames.size)
    }

    @Test
    fun `farm mode clusters by farm ordinal`() {
        val scene = Fleet3D.layout(
            listOf(unit(farm = 1, ip = "10.0.0.2"), unit(farm = 0, ip = "10.0.0.3")),
            Fleet3D.Layout.FARMS,
            rackSize = 4,
        )
        val byIndex = scene.placed.associateBy { it.index }
        // Farm 0 cluster sits left of farm 1's.
        assertTrue(byIndex[1]!!.center.x < byIndex[0]!!.center.x)
    }

    @Test
    fun `projection is centered and depth-orders near vs far`() {
        val pivot = Fleet3D.P3(0f, 0f, 0f)
        val center = Fleet3D.project(pivot, pivot, 0.3f, 0.2f, 1f, 500f, 500f, 40f)
        assertEquals(500f, center.x, 1e-3f)
        assertEquals(500f, center.y, 1e-3f)
        val near = Fleet3D.project(Fleet3D.P3(0f, 0f, 5f), pivot, 0f, 0f, 1f, 500f, 500f, 40f)
        val far = Fleet3D.project(Fleet3D.P3(0f, 0f, -5f), pivot, 0f, 0f, 1f, 500f, 500f, 40f)
        assertTrue(near.depth > far.depth)
        assertTrue(near.scale > far.scale)
    }

    @Test
    fun `ironbow runs black to white and stays in range`() {
        val (r0, g0, b0) = Fleet3D.ironbow(0f)
        val (r1, g1, b1) = Fleet3D.ironbow(1f)
        assertEquals(0f, r0 + g0 + b0, 1e-4f)
        assertEquals(3f, r1 + g1 + b1, 1e-4f)
        for (i in 0..20) {
            val (r, g, b) = Fleet3D.ironbow(i / 20f)
            assertTrue(r in 0f..1f && g in 0f..1f && b in 0f..1f)
        }
    }

    @Test
    fun `ip keys sort octet-wise not lexicographically`() {
        assertTrue(Fleet3D.ipKey("10.0.0.9") < Fleet3D.ipKey("10.0.0.107"))
        assertTrue(Fleet3D.ipKey("not-an-ip") == Long.MAX_VALUE)
    }
}
