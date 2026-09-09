package hi3.hashkit.ui.table

import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TableViewModelTest {

    private fun miner(id: Long, name: String, host: String, hr: Double?) =
        Miner(
            id = id, stableKey = "k$id", adapterType = "espminer", name = name,
            host = host, port = 80, identity = MinerIdentity(),
            lastTelemetry = MinerTelemetry(
                timestamp = Instant.now(),
                status = hi3.hashkit.domain.model.MinerStatus.ONLINE,
                hashrateGhs = if (hr == null) Sourced.unavailable() else Sourced.reported(hr),
            ),
        )

    @Test fun sortByHashrateDescending() {
        val list = listOf(miner(1, "a", "10.0.0.1", 500.0), miner(2, "b", "10.0.0.2", 1500.0))
        val sorted = TableViewModel.sortMiners(list, SortColumn.HASHRATE, ascending = false)
        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test fun sortByNameAscending() {
        val list = listOf(miner(1, "Zeta", "10.0.0.9", 1.0), miner(2, "alpha", "10.0.0.1", 1.0))
        val sorted = TableViewModel.sortMiners(list, SortColumn.NAME, ascending = true)
        assertEquals(listOf("alpha", "Zeta"), sorted.map { it.name })
    }

    @Test fun ipsSortNumericallyNotLexically() {
        val list = listOf(
            miner(1, "a", "10.0.0.100", 1.0),
            miner(2, "b", "10.0.0.9", 1.0),
            miner(3, "c", "10.0.0.20", 1.0),
        )
        val sorted = TableViewModel.sortMiners(list, SortColumn.IP, ascending = true)
        assertEquals(listOf("10.0.0.9", "10.0.0.20", "10.0.0.100"), sorted.map { it.host })
    }
}
