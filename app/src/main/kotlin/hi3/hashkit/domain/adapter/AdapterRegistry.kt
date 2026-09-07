package hi3.hashkit.domain.adapter

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of installed adapters. Probing tries each real adapter in order;
 * the demo adapter is never used to probe real hosts.
 */
@Singleton
class AdapterRegistry @Inject constructor(
    private val adapters: Set<@JvmSuppressWildcards MinerAdapter>,
) {
    fun byType(adapterType: String): MinerAdapter? =
        adapters.firstOrNull { it.adapterType == adapterType }

    /** Adapters eligible for probing real network hosts. */
    fun probeable(): List<MinerAdapter> =
        adapters.filter { it.adapterType != DEMO_ADAPTER_TYPE }

    companion object {
        const val DEMO_ADAPTER_TYPE = "demo"
    }
}
