package hi3.hashkit.data.repo

import androidx.annotation.StringRes
import hi3.hashkit.R
import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.domain.adapter.ActionResult
import hi3.hashkit.domain.adapter.FanControl
import hi3.hashkit.domain.adapter.AdapterRegistry
import hi3.hashkit.domain.model.Capability
import javax.inject.Inject
import javax.inject.Singleton

/** One planned bulk action against a set of miners. */
sealed interface BulkAction {
    val capability: Capability

    /** Display label, resolved at render time via stringResource([labelRes], *[labelArgs]). */
    @get:StringRes val labelRes: Int
    val labelArgs: List<Any> get() = emptyList()

    data object Reboot : BulkAction {
        override val capability = Capability.REBOOT
        override val labelRes = R.string.bulk_restart
    }

    data class SetPool(val url: String, val port: Int, val worker: String) : BulkAction {
        override val capability = Capability.SET_POOLS
        override val labelRes = R.string.bulk_set_pool
        override val labelArgs: List<Any> get() = listOf(url, port)
    }

    data class SetFan(val config: FanControl) : BulkAction {
        override val capability = Capability.SET_FAN
        override val labelRes = when (config) {
            is FanControl.Automatic -> R.string.bulk_fan_auto
            is FanControl.Manual -> R.string.bulk_fan_manual
        }
        override val labelArgs: List<Any> get() = when (config) {
            is FanControl.Automatic -> emptyList()
            is FanControl.Manual -> listOf(config.percent)
        }
    }

    data class Power(val action: hi3.hashkit.domain.adapter.PowerAction) : BulkAction {
        override val capability = Capability.POWER_CONTROL
        override val labelRes = when (action) {
            hi3.hashkit.domain.adapter.PowerAction.PAUSE -> R.string.bulk_pause
            hi3.hashkit.domain.adapter.PowerAction.RESUME -> R.string.bulk_resume
        }
    }

    /** Apply a specific (firmware-approved) frequency/voltage — e.g. a low-power TOU preset. */
    data class SetTune(val frequencyMhz: Int, val coreVoltageMv: Int) : BulkAction {
        override val capability = Capability.APPLY_APPROVED_TUNE
        override val labelRes = R.string.bulk_tune
        override val labelArgs: List<Any> get() = listOf(frequencyMhz, coreVoltageMv)
    }

    /** Quiet / Normal / Boost, resolved per miner against its own approved option lists. */
    data class SetPowerMode(val mode: hi3.hashkit.domain.tune.PowerMode) : BulkAction {
        override val capability = Capability.APPLY_APPROVED_TUNE
        override val labelRes = when (mode) {
            hi3.hashkit.domain.tune.PowerMode.QUIET -> R.string.bulk_mode_quiet
            hi3.hashkit.domain.tune.PowerMode.NORMAL -> R.string.bulk_mode_normal
            hi3.hashkit.domain.tune.PowerMode.BOOST -> R.string.bulk_mode_boost
        }
    }
}

data class BulkPlan(
    val action: BulkAction,
    val supported: List<MinerEntity>,
    /** Skipped miners with the reason each cannot run the action. */
    val skipped: List<Pair<MinerEntity, String>>,
)

data class BulkOutcome(
    val minerId: Long,
    val minerName: String,
    val result: ActionResult,
)

/**
 * Bulk execution over compatible miners. The plan/execute split guarantees the UI can
 * show exactly which devices will run, which are skipped and why, before anything is
 * sent — and per-device outcomes after. A partial failure is never summarized as
 * success.
 */
@Singleton
class FleetControl @Inject constructor(
    private val registry: AdapterRegistry,
    private val controlRepository: ControlRepository,
    private val minerRepository: MinerRepository,
) {
    fun plan(action: BulkAction, targets: List<MinerEntity>): BulkPlan {
        val supported = mutableListOf<MinerEntity>()
        val skipped = mutableListOf<Pair<MinerEntity, String>>()
        for (entity in targets) {
            if (entity.isDemo) {
                skipped += entity to "Demo miner"
                continue
            }
            val adapter = registry.byType(entity.adapterType)
            if (adapter == null) {
                skipped += entity to "No adapter installed for ${entity.adapterType}"
                continue
            }
            val caps = adapter.getCapabilities(minerRepository.identityOf(entity))
            if (action.capability in caps) {
                supported += entity
            } else {
                skipped += entity to (caps.unsupportedReasons[action.capability]
                    ?: "Not supported by this device")
            }
        }
        return BulkPlan(action, supported, skipped)
    }

    /** Execute sequentially (deliberate: never hammer every miner at once). */
    suspend fun execute(plan: BulkPlan): List<BulkOutcome> =
        plan.supported.map { entity ->
            val result = runCatching {
                when (val action = plan.action) {
                    is BulkAction.Reboot -> controlRepository.reboot(entity)
                    is BulkAction.SetPool ->
                        controlRepository.setPrimaryPool(entity, action.url, action.port, action.worker)
                    is BulkAction.SetFan -> controlRepository.setFan(entity, action.config)
                    is BulkAction.Power -> controlRepository.powerControl(entity, action.action)
                    is BulkAction.SetTune ->
                        controlRepository.applyTune(entity, action.frequencyMhz, action.coreVoltageMv)
                    is BulkAction.SetPowerMode -> controlRepository.applyPowerMode(entity, action.mode)
                }
            }.getOrElse { ActionResult.Failure(it.message ?: "Unexpected error") }
            BulkOutcome(entity.id, entity.name, result)
        }
}
