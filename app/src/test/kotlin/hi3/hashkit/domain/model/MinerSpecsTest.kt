package hi3.hashkit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinerSpecsTest {

    @Test
    fun `most-specific model wins over the general prefix`() {
        // "S21 XP" and "S21 Pro" must not be swallowed by the generic "S21" entry.
        assertEquals(270_000.0, MinerSpecs.nominalHashrateGhs("Antminer S21 XP")!!, 0.1)
        assertEquals(234_000.0, MinerSpecs.nominalHashrateGhs("Antminer S21 Pro")!!, 0.1)
        assertEquals(200_000.0, MinerSpecs.nominalHashrateGhs("Antminer S21")!!, 0.1)
        // Canaan Nano 3S before Nano 3.
        assertEquals(6_000.0, MinerSpecs.nominalHashrateGhs("Avalon Nano 3S")!!, 0.1)
        assertEquals(4_000.0, MinerSpecs.nominalHashrateGhs("Avalon Nano 3")!!, 0.1)
    }

    @Test
    fun `FutureBit and generic lookups`() {
        assertEquals(3_800.0, MinerSpecs.nominalHashrateGhs("FutureBit Apollo BTC")!!, 0.1)
        assertEquals(200.0, MinerSpecs.nominalPowerW("Apollo")!!, 0.1)
        assertNull(MinerSpecs.nominalHashrateGhs("Some Unknown Rig"))
        assertNull(MinerSpecs.nominalHashrateGhs(null))
    }
}
