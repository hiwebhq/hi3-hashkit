package hi3.hashkit.ui.rack

import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class RackViewModelTest {

    private fun miner(id: Long, name: String, location: String?): Miner =
        Miner(
            id = id,
            stableKey = "k$id",
            adapterType = "espminer",
            name = name,
            host = "10.0.0.$id",
            port = 80,
            identity = MinerIdentity(),
            location = location,
        )

    @Test fun blankOrNullLocationBecomesUnassignedAndSortsLast() {
        val groups = RackViewModel.groupByLocation(
            listOf(
                miner(1, "a", "Garage"),
                miner(2, "b", null),
                miner(3, "c", "  "),
                miner(4, "d", "Attic"),
            ),
        )
        assertEquals(listOf("Attic", "Garage", "Unassigned"), groups.map { it.location })
        // Both the null and the blank-location miners fall into Unassigned.
        assertEquals(2, groups.first { it.location == "Unassigned" }.total)
    }

    @Test fun minersWithinAGroupAreSortedByNameCaseInsensitively() {
        val groups = RackViewModel.groupByLocation(
            listOf(
                miner(1, "Zeta", "Rack 1"),
                miner(2, "alpha", "Rack 1"),
                miner(3, "Beta", "Rack 1"),
            ),
        )
        assertEquals(listOf("alpha", "Beta", "Zeta"), groups.single().miners.map { it.name })
    }
}
