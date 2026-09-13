package hi3.hashkit.ui.dashboard

/**
 * The dashboard's reorderable blocks, in default order. The user arranges them in
 * Settings → Display; the farm selector and firmware banner stay pinned on top
 * (contextual system rows). A card still only renders when its feature is enabled.
 */
enum class DashboardCard(val label: String) {
    FLEET("Fleet summary"),
    PROFIT("Profitability"),
    SOLO("Solo odds"),
    HALVING("Halving countdown"),
    POOL("Hi3 Pool stats"),
    MMP("MMP fleet"),
    MINERS("Miner list");

    companion object {
        /**
         * Parse a saved CSV of names into a full order: unknown names are dropped and
         * cards missing from the CSV (e.g. added in a later version) append in default
         * order, so old settings keep working as the card set grows.
         */
        fun orderFrom(csv: String): List<DashboardCard> {
            val saved = csv.split(',').mapNotNull { name ->
                entries.firstOrNull { it.name == name.trim() }
            }.distinct()
            return saved + entries.filter { it !in saved }
        }

        fun toCsv(order: List<DashboardCard>): String = order.joinToString(",") { it.name }
    }
}
