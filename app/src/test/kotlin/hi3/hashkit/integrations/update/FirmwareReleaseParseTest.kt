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
    fun `reads the published sha256 digest when GitHub provides one`() {
        val hex = "a".repeat(64)
        val body = v2151.replace("\"name\":\"esp-miner.bin\",", "\"name\":\"esp-miner.bin\",\"digest\":\"sha256:$hex\",")
        assertEquals(hex, FirmwareUpdateChecker.parseRelease(body)!!.firmware!!.sha256)
        assertNull(FirmwareUpdateChecker.parseRelease(v2151)!!.firmware!!.sha256) // absent → no check, as before
        assertEquals(hex, FirmwareUpdateChecker.parseSha256Digest("SHA256:${hex.uppercase()}"))
        assertNull(FirmwareUpdateChecker.parseSha256Digest("sha512:$hex"))
        assertNull(FirmwareUpdateChecker.parseSha256Digest("sha256:tooshort"))
        assertNull(FirmwareUpdateChecker.parseSha256Digest(null))
    }

    @Test
    fun `sha256Hex matches a known vector`() {
        val f = java.io.File.createTempFile("hashkit", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FirmwareUpdateChecker.sha256Hex(f))
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
