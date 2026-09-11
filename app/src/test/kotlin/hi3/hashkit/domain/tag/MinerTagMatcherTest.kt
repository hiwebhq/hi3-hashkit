package hi3.hashkit.domain.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MinerTagMatcherTest {

    private val fleet = listOf(
        MinerTagMatcher.Candidate(1, "Bitaxe-01", "10.0.0.42", "AA:BB:CC:DD:EE:FF", "SN-1"),
        MinerTagMatcher.Candidate(2, "Bitaxe-02", "10.0.0.43", "11:22:33:44:55:66", "SN-2"),
    )

    @Test fun macWinsAcrossSeparatorStyles() {
        val tag = MinerTag.parse("name=Wrong\nmac=aa-bb-cc-dd-ee-ff\nip=10.0.0.43")!!
        // MAC points at #1 even though the IP is #2's — MAC has priority.
        assertEquals(1L, MinerTagMatcher.match(tag, fleet)!!.id)
    }

    @Test fun ipMatchesWhenNoMac() {
        val tag = MinerTag.parse("ip=10.0.0.43")!!
        assertEquals(2L, MinerTagMatcher.match(tag, fleet)!!.id)
    }

    @Test fun nameMatchesWhenNoMacOrIp() {
        val tag = MinerTag.parse("name=Bitaxe-02")!!
        assertEquals(2L, MinerTagMatcher.match(tag, fleet)!!.id)
    }

    @Test fun bareValueMatchesLegacyQr() {
        assertEquals(1L, MinerTagMatcher.match(MinerTag.parse("10.0.0.42")!!, fleet)!!.id)
        assertEquals(1L, MinerTagMatcher.match(MinerTag.parse("SN-1")!!, fleet)!!.id)
        assertEquals(2L, MinerTagMatcher.match(MinerTag.parse("2")!!, fleet)!!.id)
    }

    @Test fun noMatchReturnsNull() {
        assertNull(MinerTagMatcher.match(MinerTag.parse("ip=10.0.0.99")!!, fleet))
    }
}
