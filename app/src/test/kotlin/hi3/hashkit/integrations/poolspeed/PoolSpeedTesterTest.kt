package hi3.hashkit.integrations.poolspeed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoolSpeedTesterTest {

    @Test fun parsesStratumSchemes() {
        assertEquals("pool.hi3.cc" to 3333, PoolSpeedTester.parseStratum("stratum+tcp://pool.hi3.cc:3333"))
        assertEquals("solo.ckpool.org" to 3333, PoolSpeedTester.parseStratum("stratum+ssl://solo.ckpool.org:3333"))
        assertEquals("host.example" to 4444, PoolSpeedTester.parseStratum("host.example:4444"))
        assertEquals("host.example" to 3333, PoolSpeedTester.parseStratum("host.example")) // default port
        assertEquals("192.0.2.10" to 3333, PoolSpeedTester.parseStratum("//192.0.2.10:3333"))
    }

    @Test fun usesProvidedDefaultPort() {
        assertEquals("h" to 4028, PoolSpeedTester.parseStratum("h", defaultPort = 4028))
    }

    @Test fun rejectsGarbage() {
        assertNull(PoolSpeedTester.parseStratum(""))
        assertNull(PoolSpeedTester.parseStratum("host:99999")) // port out of range
    }

    @Test fun stripsTrailingPath() {
        assertEquals("pool.example" to 3333, PoolSpeedTester.parseStratum("stratum+tcp://pool.example:3333/worker"))
    }
}
