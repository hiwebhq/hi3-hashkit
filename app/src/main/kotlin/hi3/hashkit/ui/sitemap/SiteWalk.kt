package hi3.hashkit.ui.sitemap

/**
 * Site geometry for the Site Map walk: buildings → racks → tiers (shelves), where each
 * tier is one left-to-right row of miner positions.
 */
data class SiteMapConfig(
    val buildings: Int,
    val racksPerBuilding: Int,
    val tiersPerRack: Int,
    val positionsPerTier: Int,
) {
    val slotsPerRack: Int get() = tiersPerRack * positionsPerTier
    val slotsPerBuilding: Int get() = racksPerBuilding * slotsPerRack
    val totalSlots: Int get() = buildings * slotsPerBuilding
    val isValid: Boolean
        get() = buildings > 0 && racksPerBuilding > 0 && tiersPerRack > 0 && positionsPerTier > 0
}

/** One physical shelf slot; all coordinates are 1-based. */
data class Slot(val building: Int, val rack: Int, val tier: Int, val position: Int) {
    /** Compact location code written into a miner's Location field, e.g. "B1-R2-T3-P4". */
    val code: String get() = "B$building-R$rack-T$tier-P$position"
    val label: String get() = "Building $building · Rack $rack · Tier $tier · Position $position"
}

/**
 * Walk order: start at Building 1, Rack 1, Tier 1, Position 1 (left end). Fill each tier
 * left→right, then the next tier up of the SAME rack, then the next rack, then the next
 * building. Slot index 0 is always B1-R1-T1-P1.
 */
object SiteWalk {

    fun slotAt(config: SiteMapConfig, index: Int): Slot? {
        if (index < 0 || index >= config.totalSlots) return null
        val building = index / config.slotsPerBuilding
        val inBuilding = index % config.slotsPerBuilding
        val rack = inBuilding / config.slotsPerRack
        val inRack = inBuilding % config.slotsPerRack
        val tier = inRack / config.positionsPerTier
        val position = inRack % config.positionsPerTier
        return Slot(building + 1, rack + 1, tier + 1, position + 1)
    }

    fun indexOf(config: SiteMapConfig, slot: Slot): Int =
        (slot.building - 1) * config.slotsPerBuilding +
            (slot.rack - 1) * config.slotsPerRack +
            (slot.tier - 1) * config.positionsPerTier +
            (slot.position - 1)
}

/** A filled slot: which miner answered (or was typed in) at that physical location. */
data class CapturedSlot(
    val slot: Slot,
    val ip: String,
    val mac: String?,
    val manual: Boolean,
    val atEpochMs: Long,
) {
    val lastOctet: String get() = ip.substringAfterLast('.')
}

/** Details fetched from the miner's own API after capture ("Scan miners"). */
data class EnrichedMiner(
    val adapterType: String? = null,
    val model: String? = null,
    val mac: String? = null,
    val serial: String? = null,
    val pool: String? = null,
    val worker: String? = null,
    val hashrateGhs: Double? = null,
    val error: String? = null,
)

/**
 * Results CSV: header plus one row per captured slot, in walk order. Enrichment columns
 * are filled from [enrichedByCode] (keyed by slot code) when an API scan has run.
 */
fun siteMapCsv(captured: List<CapturedSlot>, enrichedByCode: Map<String, EnrichedMiner> = emptyMap()): String =
    buildString {
        appendLine(
            "building,rack,tier,position,location,ip,last_octet,mac,source,captured_at_epoch_ms," +
                "model,serial,pool,worker,hashrate_ghs",
        )
        captured.forEach { c ->
            val e = enrichedByCode[c.slot.code]
            appendLine(
                listOf(
                    c.slot.building, c.slot.rack, c.slot.tier, c.slot.position, c.slot.code,
                    c.ip, c.lastOctet, (c.mac ?: e?.mac).orEmpty(),
                    if (c.manual) "manual" else "ip-report", c.atEpochMs,
                    csvField(e?.model), csvField(e?.serial), csvField(e?.pool), csvField(e?.worker),
                    e?.hashrateGhs?.let { String.format(java.util.Locale.US, "%.1f", it) }.orEmpty(),
                ).joinToString(","),
            )
        }
    }

private fun csvField(value: String?): String {
    val v = value.orEmpty()
    return if (v.contains(',') || v.contains('"')) "\"${v.replace("\"", "\"\"")}\"" else v
}
