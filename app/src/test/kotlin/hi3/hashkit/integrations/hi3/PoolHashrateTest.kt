package hi3.hashkit.integrations.hi3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ckpool encodes worker hashrates as suffix strings (K/M/G/T/P/E of H/s). These map to GH/s.
 * Field shape verified against the ckpool user JSON (raw.stats.ckpool.org/users/<address>).
 */
class PoolHashrateTest {

    @Test
    fun `ckpool suffix hashrates convert to GH per second`() {
        // 1.5 TH/s = 1500 GH/s
        assertEquals(1500.0, Hi3PoolClient.parseCkHashToGhs("1.5T")!!, 0.001)
        // 500 GH/s stays 500
        assertEquals(500.0, Hi3PoolClient.parseCkHashToGhs("500G")!!, 0.001)
        // 2 PH/s = 2,000,000 GH/s
        assertEquals(2_000_000.0, Hi3PoolClient.parseCkHashToGhs("2P")!!, 0.001)
        // 750 MH/s = 0.75 GH/s
        assertEquals(0.75, Hi3PoolClient.parseCkHashToGhs("750M")!!, 0.0001)
        // Idle worker
        assertEquals(0.0, Hi3PoolClient.parseCkHashToGhs("0")!!, 0.0)
    }

    @Test
    fun `blank or malformed hashrates yield null`() {
        assertNull(Hi3PoolClient.parseCkHashToGhs(null))
        assertNull(Hi3PoolClient.parseCkHashToGhs(""))
        assertNull(Hi3PoolClient.parseCkHashToGhs("null"))
        assertNull(Hi3PoolClient.parseCkHashToGhs("abc"))
    }
}
