package hi3.hashkit.adapters.espminer

import hi3.hashkit.domain.model.MinerIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EspMinerFirmwareTest {

    private fun identity(version: String?, model: String? = "Bitaxe Gamma") =
        MinerIdentity(firmwareVersion = version, model = model)

    @Test
    fun `official v2_14 uses flat pool fields`() {
        assertEquals(
            EspMinerFlavor.OFFICIAL_V2_FLAT_POOLS,
            EspMinerFirmware.flavorOf(identity("v2.14.2")),
        )
        assertEquals(
            EspMinerFlavor.OFFICIAL_V2_FLAT_POOLS,
            EspMinerFirmware.flavorOf(identity("v2.14.0-50-gcf062dd-dirty")),
        )
    }

    @Test
    fun `official v2_15 plus uses pools array`() {
        assertEquals(
            EspMinerFlavor.OFFICIAL_V2_POOLS_ARRAY,
            EspMinerFirmware.flavorOf(identity("v2.15.1")),
        )
        assertEquals(
            EspMinerFlavor.OFFICIAL_V2_POOLS_ARRAY,
            EspMinerFirmware.flavorOf(identity("v2.15.2rc0")),
        )
    }

    @Test
    fun `nerdqaxe and forks are monitoring only`() {
        val nerd = EspMinerFirmware.flavorOf(identity("v1.1.0", model = "NerdQAxe++ TPS546"))
        assertEquals(EspMinerFlavor.NERDQAXE, nerd)
        assertFalse(EspMinerFirmware.controlsSupported(nerd))

        val fork = EspMinerFirmware.flavorOf(identity("2.0.0 20260418", model = "Hammer"))
        assertEquals(EspMinerFlavor.UNKNOWN_FORK, fork)
        assertFalse(EspMinerFirmware.controlsSupported(fork))

        assertFalse(EspMinerFirmware.controlsSupported(EspMinerFirmware.flavorOf(identity(null))))
    }

    @Test
    fun `official v2 firmware supports controls`() {
        assertTrue(EspMinerFirmware.controlsSupported(EspMinerFirmware.flavorOf(identity("v2.14.2"))))
        assertTrue(EspMinerFirmware.controlsSupported(EspMinerFirmware.flavorOf(identity("v2.15.1"))))
    }
}
