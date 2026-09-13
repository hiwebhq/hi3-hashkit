package hi3.hashkit.data.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsReportTest {

    private fun inputs(rows: List<OpsReport.MinerRow>) = OpsReport.Inputs(
        periodDays = 7,
        generatedAtEpochMs = 1_789_000_000_000L,
        miners = rows,
        alertsByType = listOf("OFFLINE" to 3, "CHIP_TEMP" to 1),
        electricityRatePerKwh = 0.12,
        currencyCode = "USD",
    )

    @Test
    fun `report renders per-miner rows and fleet tiles`() {
        val html = OpsReport.html(
            inputs(
                listOf(
                    OpsReport.MinerRow("GammaHex1", "Bitaxe Gamma", 99.5, 8600.0, 8500.0, 61.0, 4.2, 2),
                    OpsReport.MinerRow("Nano3", "Avalon Nano 3", 97.0, 3900.0, null, 72.0, 6.9, 2),
                )
            )
        )
        assertTrue("GammaHex1" in html)
        assertTrue("99.5%" in html)
        assertTrue("8.60 TH/s" in html) // per-miner average
        assertTrue("101%" in html) // attainment vs expected
        assertTrue("11.1 kWh" in html) // fleet energy tile (4.2 + 6.9)
        assertTrue("1.33 USD" in html) // 11.1 kWh * 0.12
        assertTrue("OFFLINE: 3" in html)
        assertTrue("Last 7 days" in html)
    }

    @Test
    fun `missing data renders as dashes not zeros`() {
        val html = OpsReport.html(
            inputs(listOf(OpsReport.MinerRow("NewMiner", null, null, null, null, null, null, 0)))
        )
        assertTrue("NewMiner" in html)
        assertFalse("0.00 TH/s" in html)
        assertTrue("—" in html)
    }

    @Test
    fun `html-sensitive names are escaped`() {
        val html = OpsReport.html(
            inputs(listOf(OpsReport.MinerRow("<script>x</script>", null, 1.0, null, null, null, null, 0)))
        )
        assertFalse("<script>x" in html)
        assertTrue("&lt;script&gt;" in html)
    }
}
