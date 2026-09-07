package hi3.hashkit.data.repo

import hi3.hashkit.data.db.FarmDao
import hi3.hashkit.data.db.FarmEntity
import hi3.hashkit.data.db.MinerDao
import hi3.hashkit.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages farms/sites: create, rename, choose the default, delete, and assign miners.
 * The "active" farm (what the dashboard shows) is persisted in settings; it falls back to
 * the default farm when unset. Deleting a farm never deletes its miners — they simply
 * become unassigned so no telemetry history is lost.
 */
@Singleton
class FarmRepository @Inject constructor(
    private val farmDao: FarmDao,
    private val minerDao: MinerDao,
    private val settingsRepository: SettingsRepository,
) {
    fun observeFarms(): Flow<List<FarmEntity>> = farmDao.observeAll()

    suspend fun farms(): List<FarmEntity> = farmDao.all()

    suspend fun byId(id: Long): FarmEntity? = farmDao.byId(id)

    suspend fun countInFarm(id: Long): Int = minerDao.countInFarm(id)

    /** Create a farm. The very first farm becomes the default and the active view. */
    suspend fun createFarm(name: String, subnetsCsv: String, notes: String? = null): Long {
        val isFirst = farmDao.count() == 0
        val id = farmDao.insert(
            FarmEntity(
                name = name.trim().ifBlank { "Farm" },
                isDefault = isFirst,
                subnetsCsv = subnetsCsv.trim(),
                notes = notes?.trim()?.ifBlank { null },
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )
        if (isFirst) settingsRepository.setActiveFarmId(id)
        return id
    }

    suspend fun updateFarm(farm: FarmEntity) = farmDao.update(farm)

    /** Make [id] the sole default farm. */
    suspend fun setDefault(id: Long) {
        farmDao.clearDefaults()
        farmDao.markDefault(id)
    }

    suspend fun setActive(id: Long) = settingsRepository.setActiveFarmId(id)

    suspend fun assignMiner(minerId: Long, farmId: Long?) = minerDao.assignFarm(minerId, farmId)

    /**
     * Delete a farm; its miners become unassigned. Promotes another farm to default when
     * the deleted one was default, and resets the active view if it pointed here.
     */
    suspend fun deleteFarm(id: Long) {
        val wasDefault = farmDao.byId(id)?.isDefault == true
        minerDao.clearFarm(id)
        farmDao.delete(id)
        val remaining = farmDao.all()
        if (wasDefault && remaining.isNotEmpty() && remaining.none { it.isDefault }) {
            setDefault(remaining.first().id)
        }
        if (settingsRepository.current().activeFarmId == id) {
            settingsRepository.setActiveFarmId(farmDao.defaultFarm()?.id ?: -1)
        }
    }

    /** The active farm, or the default one when no explicit active farm is set. */
    suspend fun activeOrDefaultFarm(): FarmEntity? {
        val activeId = settingsRepository.current().activeFarmId
        return (activeId.takeIf { it > 0 }?.let { farmDao.byId(it) }) ?: farmDao.defaultFarm()
    }
}
