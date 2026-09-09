package hi3.hashkit.integrations.metrics

import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HtmlDashboardTest {

    private fun miner(name: String, hr: Double, demo: Boolean = false) = Miner(
        id = 1, stableKey = "k", adapterType = "espminer", name = name,
        host = "10.0.0.5", port = 80, identity = MinerIdentity(model = "Gamma"),
        isDemo = demo, status = MinerStatus.ONLINE,
        lastTelemetry = MinerTelemetry(
            timestamp = Instant.now(), status = MinerStatus.ONLINE,
            hashrateGhs = Sourced.reported(hr), powerW = Sourced.reported(15.0),
        ),
    )

    @Test fun rendersSelfContainedHtmlWithFleetAndRows() {
        val html = HtmlDashboard.render(listOf(miner("Bitaxe A", 1200.0)))
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(html.contains("Bitaxe A"))
        assertTrue(html.contains("Gamma"))
        assertTrue(html.contains("http-equiv=\"refresh\""))
        // No external resource references.
        assertFalse(html.contains("http://") && html.contains("src="))
    }

    @Test fun escapesMinerNames() {
        val html = HtmlDashboard.render(listOf(miner("<script>x</script>", 100.0)))
        assertFalse(html.contains("<script>x"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test fun demoMinersExcluded() {
        val html = HtmlDashboard.render(listOf(miner("DemoUnit", 999.0, demo = true)))
        assertFalse(html.contains("DemoUnit"))
    }
}
