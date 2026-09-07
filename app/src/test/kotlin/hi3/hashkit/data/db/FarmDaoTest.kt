package hi3.hashkit.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the farms table and miners.farmId against the real Room schema (built from
 * the v7 definition), covering the default-farm invariant and the delete-detaches-miners
 * behaviour that [hi3.hashkit.data.repo.FarmRepository] relies on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FarmDaoTest {

    private lateinit var db: HashkitDatabase
    private lateinit var farmDao: FarmDao
    private lateinit var minerDao: MinerDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HashkitDatabase::class.java,
        ).allowMainThreadQueries().build()
        farmDao = db.farmDao()
        minerDao = db.minerDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `default farm is unique and reassignable`() = runTest {
        val a = farmDao.insert(farm("A", isDefault = true))
        val b = farmDao.insert(farm("B", isDefault = false))
        assertEquals(a, farmDao.defaultFarm()?.id)

        // Switch the default to B: only one row may carry the flag.
        farmDao.clearDefaults()
        farmDao.markDefault(b)
        assertEquals(b, farmDao.defaultFarm()?.id)
        assertEquals(2, farmDao.count())
    }

    @Test
    fun `assigning and clearing a farm updates miners without deleting them`() = runTest {
        val farmId = farmDao.insert(farm("Site", isDefault = true))
        val minerId = minerDao.insert(miner("m1"))
        minerDao.assignFarm(minerId, farmId)
        assertEquals(1, minerDao.countInFarm(farmId))

        // Deleting the farm detaches its miners (history preserved), not removes them.
        minerDao.clearFarm(farmId)
        farmDao.delete(farmId)
        assertEquals(0, minerDao.countInFarm(farmId))
        assertNull(farmDao.byId(farmId))
        // The miner still exists, now unassigned.
        val survivor = minerDao.byId(minerId)
        assertTrue(survivor != null)
        assertNull(survivor!!.farmId)
        assertFalse(farmDao.all().any { it.id == farmId })
    }

    private fun farm(name: String, isDefault: Boolean) = FarmEntity(
        name = name, isDefault = isDefault, subnetsCsv = "192.168.1.0/24",
        notes = null, createdAtEpochMs = 0L,
    )

    private fun miner(key: String) = MinerEntity(
        stableKey = key, adapterType = "demo", name = key, host = "127.0.0.1", port = 80,
        macAddress = null, serialNumber = null, hostname = null, manufacturer = null,
        model = null, boardVersion = null, asicModel = null, firmwareFamily = null,
        firmwareVersion = null, groupName = null, location = null, notes = null,
        tagsCsv = "", expectedHashrateGhs = null, isDemo = false, createdAtEpochMs = 0L,
        lastSeenAtEpochMs = null,
    )
}
