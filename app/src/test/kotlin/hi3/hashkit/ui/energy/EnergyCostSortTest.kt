package hi3.hashkit.ui.energy

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyCostSortTest {

    private fun row(
        id: Long,
        cost: Double? = null,
        jth: Double? = null,
        net: Double? = null,
        powerW: Double? = null,
    ) = EnergyRow(
        id = id, name = "m$id", host = "10.0.0.$id", model = null,
        powerW = powerW, estimated = false, kwhPerDay = null,
        costPerDay = cost, costPerMonth = null,
        efficiencyJTh = jth, revenuePerDay = null, netPerDay = net,
        online = true,
    )

    @Test
    fun `cost sort is descending with power as fallback`() {
        val rows = listOf(row(1, cost = 1.0), row(2, cost = 3.0), row(3, powerW = 5000.0))
        val sorted = EnergyCostViewModel.sortRows(rows, EnergySort.COST)
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun `efficiency sort puts lowest J per TH first and unknowns last`() {
        val rows = listOf(row(1, jth = 30.0), row(2, jth = 18.5), row(3))
        val sorted = EnergyCostViewModel.sortRows(rows, EnergySort.EFFICIENCY)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun `net sort puts most profitable first and unknowns last`() {
        val rows = listOf(row(1, net = -0.5), row(2, net = 1.2), row(3))
        val sorted = EnergyCostViewModel.sortRows(rows, EnergySort.NET)
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }
}
