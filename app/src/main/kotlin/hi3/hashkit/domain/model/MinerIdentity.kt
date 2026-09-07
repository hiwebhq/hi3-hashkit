package hi3.hashkit.domain.model

/**
 * Identity as reported by the device. Stable-key priority: MAC > serial > composite
 * fingerprint > IP fallback. An IP change must re-bind to the same miner, never duplicate it.
 */
data class MinerIdentity(
    val macAddress: String? = null,
    val serialNumber: String? = null,
    val hostname: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val boardVersion: String? = null,
    val asicModel: String? = null,
    val firmwareFamily: String? = null,
    val firmwareVersion: String? = null,
) {
    /** Strongest stable key available for identity tracking. */
    fun stableKey(fallbackHost: String): String = when {
        !macAddress.isNullOrBlank() -> "mac:${macAddress.lowercase()}"
        !serialNumber.isNullOrBlank() -> "sn:$serialNumber"
        !hostname.isNullOrBlank() && !model.isNullOrBlank() -> "fp:$model/$hostname"
        else -> "ip:$fallbackHost"
    }
}
