package hi3.hashkit.domain.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinerTagTest {

    @Test fun parsesKeyValueBlock() {
        val tag = MinerTag.parse("name=Bitaxe-01\nmac=AA:BB:CC:DD:EE:FF\nip=10.0.0.42\nlocation=Rack 1 / Shelf 2")!!
        assertEquals("Bitaxe-01", tag.name)
        assertEquals("AA:BB:CC:DD:EE:FF", tag.mac)
        assertEquals("10.0.0.42", tag.ip)
        assertEquals("Rack 1 / Shelf 2", tag.location)
        assertNull(tag.rawValue)
    }

    @Test fun acceptsHostAndLocAliasesAndSemicolons() {
        val tag = MinerTag.parse("host=10.0.0.5;loc=Garage")!!
        assertEquals("10.0.0.5", tag.ip)
        assertEquals("Garage", tag.location)
    }

    @Test fun parsesUriScheme() {
        val tag = MinerTag.parse("hi3miner://miner?name=Bitaxe-02&mac=11-22-33-44-55-66&ip=10.0.0.9&loc=Rack%202")!!
        assertEquals("Bitaxe-02", tag.name)
        assertEquals("11-22-33-44-55-66", tag.mac)
        assertEquals("10.0.0.9", tag.ip)
        assertEquals("Rack 2", tag.location)
    }

    @Test fun bareValueBecomesRaw() {
        val tag = MinerTag.parse("10.0.0.42")!!
        assertEquals("10.0.0.42", tag.rawValue)
        assertTrue(!tag.hasFields)
    }

    @Test fun blankIsNull() {
        assertNull(MinerTag.parse("   "))
        assertNull(MinerTag.parse(null))
    }

    @Test fun normalizeMacStripsSeparators() {
        assertEquals("aabbccddeeff", MinerTag.normalizeMac("AA:BB:CC:DD:EE:FF"))
        assertEquals("aabbccddeeff", MinerTag.normalizeMac("aa-bb-cc-dd-ee-ff"))
        assertNull(MinerTag.normalizeMac(null))
    }
}
