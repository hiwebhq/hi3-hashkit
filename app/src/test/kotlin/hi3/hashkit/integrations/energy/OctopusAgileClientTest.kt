package hi3.hashkit.integrations.energy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OctopusAgileClientTest {

    private val sample = """
        {"count":3,"next":null,"previous":null,"results":[
          {"value_exc_vat":20.0,"value_inc_vat":21.0,"valid_from":"2026-09-10T23:00:00Z","valid_to":"2026-09-10T23:30:00Z","payment_method":null},
          {"value_exc_vat":10.0,"value_inc_vat":10.5,"valid_from":"2026-09-10T22:30:00Z","valid_to":"2026-09-10T23:00:00Z","payment_method":null},
          {"value_exc_vat":30.0,"value_inc_vat":31.5,"valid_from":"2026-09-10T22:00:00Z","valid_to":"2026-09-10T22:30:00Z","payment_method":null}
        ]}
    """.trimIndent()

    @Test fun parsesAllSlots() {
        val rates = OctopusAgileClient.parseRates(sample)
        assertEquals(3, rates.size)
        assertEquals(10.5, rates.first { it.pencePerKwh == 10.5 }.pencePerKwh, 0.0001)
    }

    @Test fun currentRatePicksTheCoveringSlot() {
        val rates = OctopusAgileClient.parseRates(sample)
        val now = Instant.parse("2026-09-10T22:45:00Z")
        val current = OctopusAgileClient.currentRate(rates, now)
        assertNotNull(current)
        assertEquals(10.5, current!!.pencePerKwh, 0.0001)
    }

    @Test fun currentRateNullWhenUncovered() {
        val rates = OctopusAgileClient.parseRates(sample)
        assertNull(OctopusAgileClient.currentRate(rates, Instant.parse("2026-09-11T05:00:00Z")))
    }

    @Test fun upcomingKeepsFutureSlotsSorted() {
        val rates = OctopusAgileClient.parseRates(sample)
        val now = Instant.parse("2026-09-10T22:45:00Z")
        val upcoming = OctopusAgileClient.upcoming(rates, now)
        // 22:00-22:30 has ended; two remain, sorted ascending.
        assertEquals(2, upcoming.size)
        assertTrue(upcoming[0].validFrom.isBefore(upcoming[1].validFrom))
    }

    @Test fun emptyOnGarbage() {
        assertTrue(OctopusAgileClient.parseRates("not json").isEmpty())
    }
}
