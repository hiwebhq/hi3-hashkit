package hi3.hashkit.integrations.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FirmwareReleaseParseTest {

    // Shape of api.github.com/repos/bitaxeorg/ESP-Miner/releases/latest, trimmed.
    private val v2151 = """
        {"tag_name":"v2.15.1","html_url":"https://github.com/bitaxeorg/ESP-Miner/releases/tag/v2.15.1",
         "assets":[
           {"name":"esp-miner-factory-204-v2.15.1.bin","size":15802368,
            "browser_download_url":"https://github.com/bitaxeorg/ESP-Miner/releases/download/v2.15.1/esp-miner-factory-204-v2.15.1.bin"},
           {"name":"esp-miner.bin","size":2528048,
            "browser_download_url":"https://github.com/bitaxeorg/ESP-Miner/releases/download/v2.15.1/esp-miner.bin"}
         ]}
    """.trimIndent()

    @Test
    fun `picks the OTA image and ignores factory images`() {
        val r = FirmwareUpdateChecker.parseRelease(v2151)!!
        assertEquals("v2.15.1", r.tag)
        assertNotNull(r.firmware)
        assertEquals(2528048L, r.firmware!!.sizeBytes)
        assertEquals("https://github.com/bitaxeorg/ESP-Miner/releases/download/v2.15.1/esp-miner.bin", r.firmware!!.url)
        assertNull(r.www) // v2.15+ embeds the web UI in the firmware image
    }

    @Test
    fun `older releases also expose the separate web-UI image`() {
        val body = v2151.replace(
            "\"assets\":[",
            "\"assets\":[{\"name\":\"www.bin\",\"size\":1200000,\"browser_download_url\":\"https://github.com/x/www.bin\"},",
        ).replace("v2.15.1", "v2.14.2")
        val r = FirmwareUpdateChecker.parseRelease(body)!!
        assertEquals("v2.14.2", r.tag)
        assertEquals("www.bin", r.www?.name)
        assertEquals("esp-miner.bin", r.firmware?.name)
    }

    @Test
    fun `a body without a tag or with no assets degrades safely`() {
        assertNull(FirmwareUpdateChecker.parseRelease("{}"))
        assertNull(FirmwareUpdateChecker.parseRelease("not json"))
        val noAssets = FirmwareUpdateChecker.parseRelease("""{"tag_name":"v3.0.0"}""")!!
        assertNull(noAssets.firmware)
        assertEquals("https://github.com/bitaxeorg/ESP-Miner/releases", noAssets.url)
    }
}
