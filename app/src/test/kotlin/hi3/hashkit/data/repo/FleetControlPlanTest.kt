package hi3.hashkit.data.repo

import hi3.hashkit.adapters.demo.DemoMinerAdapter
import hi3.hashkit.adapters.espminer.EspMinerAdapter
import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.domain.adapter.AdapterRegistry
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertNotNull

/**
 * Bulk planning must partition supported vs skipped purely from stored identity and
 * capabilities — no network. Uses the real adapters with a registry, and a null
 * control repository is not needed because plan() never executes anything.
 */
class FleetControlPlanTest {

    private val registry = AdapterRegistry(
        setOf(EspMinerAdapter(OkHttpClient()), DemoMinerAdapter()),
    )

    private fun entity(
        id: Long,
        adapterType: String = "espminer",
        firmware: String? = "v2.15.1",
        model: String? = "Bitaxe Gamma",
        isDemo: Boolean = false,
    ) = MinerEntity(
        id = id, stableKey = "k$id", adapterType = adapterType, name = "m$id",
        host = "10.0.0.$id", port = 80, macAddress = null, serialNumber = null,
        hostname = null, manufacturer = null, model = model, boardVersion = null,
        asicModel = null, firmwareFamily = null, firmwareVersion = firmware,
        groupName = null, location = null, notes = null, tagsCsv = "",
        expectedHashrateGhs = null, isDemo = isDemo, createdAtEpochMs = 0,
        lastSeenAtEpochMs = null,
    )

    private fun fleetControl(): FleetControl {
        // MinerRepository is only used for identityOf(), which is pure — build a
        // lightweight instance via the same identity mapping logic.
        val repo = MinerRepositoryIdentityShim(registry)
        return FleetControl(registry, repo.controlRepository, repo.minerRepository)
    }

    @Test
    fun `plan partitions official firmware from forks demos and unknown adapters`() {
        val fleet = fleetControl()
        val targets = listOf(
            entity(1, firmware = "v2.15.1"),                           // supported
            entity(2, firmware = "v2.14.2"),                           // supported
            entity(3, firmware = "2.0.0 20260418", model = "Hammer"),  // fork -> skipped
            entity(4, firmware = "v1.1.0", model = "NerdQAxe++"),      // nerdqaxe -> skipped
            entity(5, isDemo = true),                                  // demo -> skipped
            entity(6, adapterType = "braiins"),                        // no adapter -> skipped
        )
        val plan = fleet.plan(BulkAction.Reboot, targets)
        assertEquals(listOf(1L, 2L), plan.supported.map { it.id })
        assertEquals(listOf(3L, 4L, 5L, 6L), plan.skipped.map { it.first.id })
        // Every skip carries a human-readable reason.
        plan.skipped.forEach { (_, reason) -> assertTrue(reason.isNotBlank()) }
        assertNotNull(plan.skipped.first { it.first.id == 5L }.second.contains("Demo"))
    }
}

/** Minimal shim: FleetControl.plan only calls identityOf (pure), never the network. */
private class MinerRepositoryIdentityShim(registry: AdapterRegistry) {
    val minerRepository = MinerRepository(
        minerDao = FailingMinerDao,
        telemetryDao = FailingTelemetryDao,
        registry = registry,
    )
    val controlRepository = ControlRepository(
        registry = registry,
        auditDao = FailingAuditDao,
        minerRepository = minerRepository,
    )
}

private object FailingMinerDao : hi3.hashkit.data.db.MinerDao {
    override fun observeAll() = throw UnsupportedOperationException()
    override fun observeById(id: Long) = throw UnsupportedOperationException()
    override suspend fun byId(id: Long) = throw UnsupportedOperationException()
    override suspend fun byStableKey(stableKey: String) = throw UnsupportedOperationException()
    override suspend fun insert(miner: MinerEntity) = throw UnsupportedOperationException()
    override suspend fun update(miner: MinerEntity) = throw UnsupportedOperationException()
    override suspend fun delete(id: Long) = throw UnsupportedOperationException()
    override suspend fun updateHostAndSeen(id: Long, host: String, seenAt: Long) = throw UnsupportedOperationException()
    override suspend fun insertAddress(address: hi3.hashkit.data.db.MinerAddressEntity) = throw UnsupportedOperationException()
    override suspend fun touchAddress(minerId: Long, host: String, seenAt: Long) = throw UnsupportedOperationException()
}

private object FailingTelemetryDao : hi3.hashkit.data.db.TelemetryDao {
    override suspend fun samplesBetween(minerId: Long, fromEpochMs: Long, toEpochMs: Long) = throw UnsupportedOperationException()
    override suspend fun oldestSampleTimestamp(minerId: Long) = throw UnsupportedOperationException()
    override suspend fun insert(sample: hi3.hashkit.data.db.TelemetrySampleEntity) = throw UnsupportedOperationException()
    override fun observeSince(minerId: Long, sinceEpochMs: Long) = throw UnsupportedOperationException()
    override suspend fun latest(minerId: Long) = throw UnsupportedOperationException()
    override suspend fun pruneBefore(beforeEpochMs: Long) = throw UnsupportedOperationException()
    override suspend fun insertRaw(raw: hi3.hashkit.data.db.RawResponseEntity) = throw UnsupportedOperationException()
    override suspend fun latestRaw(minerId: Long) = throw UnsupportedOperationException()
    override suspend fun pruneRawBefore(beforeEpochMs: Long) = throw UnsupportedOperationException()
}

private object FailingAuditDao : hi3.hashkit.data.db.AuditDao {
    override suspend fun insert(event: hi3.hashkit.data.db.AuditEventEntity) = throw UnsupportedOperationException()
    override fun observeForMiner(minerId: Long, limit: Int) = throw UnsupportedOperationException()
    override suspend fun latestOf(minerId: Long, action: String) = throw UnsupportedOperationException()
}
