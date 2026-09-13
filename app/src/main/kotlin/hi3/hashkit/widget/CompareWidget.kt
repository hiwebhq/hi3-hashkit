package hi3.hashkit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import hi3.hashkit.core.Units
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.integrations.hi3.Hi3PoolRepository
import hi3.hashkit.integrations.hi3.MmpRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/**
 * Home-screen widget comparing the fleet's hashrate as three sources see it:
 * miner-side (local polls), pool-side (Hi3 Pool / PPLNS stats), and MMP. Reads the
 * repositories' cached state and opportunistically refreshes when stale; refreshed
 * like the main widget at the end of each poll cycle.
 */
class CompareWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CompareEntryPoint {
        fun minerRepository(): MinerRepository
        fun poolRepository(): Hi3PoolRepository
        fun mmpRepository(): MmpRepository
    }

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val entry = EntryPointAccessors
            .fromApplication(context.applicationContext, CompareEntryPoint::class.java)
        val repo = entry.minerRepository()
        val poolRepo = entry.poolRepository()
        val mmpRepo = entry.mmpRepository()

        val now = Instant.now()
        val miners = repo.observeMinerEntities().first()
            .filter { !it.isDemo }
            .map { repo.toDomain(it, now) }
        val localGhs = miners
            .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
            .sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }

        // Cached integration state; refresh when enabled and older than the poll cadence.
        fun stale(at: Instant?) = at == null || Duration.between(at, now).toMinutes() >= STALE_AFTER_MIN
        if (poolRepo.state.value.enabled && stale(poolRepo.state.value.lastUpdated)) {
            runCatching { poolRepo.refresh(miners) }
        }
        if (mmpRepo.state.value.enabled && stale(mmpRepo.state.value.lastUpdated)) {
            runCatching { mmpRepo.refresh() }
        }
        val pool = poolRepo.state.value
        val mmp = mmpRepo.state.value
        val mmpGhs = mmp.summary?.hashrateThs?.times(GHS_PER_THS)

        provideContent { Content(localGhs, pool, mmp, mmpGhs) }
    }

    @Composable
    private fun Content(
        localGhs: Double,
        pool: hi3.hashkit.integrations.hi3.Hi3PoolState,
        mmp: hi3.hashkit.integrations.hi3.MmpState,
        mmpGhs: Double?,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(BG).padding(12.dp)
                .clickable(actionStartActivity<hi3.hashkit.ui.MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HASHRATE SOURCES", style = TextStyle(color = ColorProvider(DIM), fontSize = 10.sp()))
            CompareRow("Local", Units.formatHashrate(localGhs), ACCENT)
            if (pool.enabled) {
                CompareRow(
                    "Pool",
                    Units.formatHashrate(pool.totalPoolHashrateGhs) +
                        deltaPct(pool.totalPoolHashrateGhs, localGhs),
                    if (pool.error != null) WARN else GREEN,
                )
            } else {
                CompareRow("Pool", "off", DIM)
            }
            if (mmp.enabled) {
                CompareRow(
                    "MMP",
                    mmpGhs?.let { Units.formatHashrate(it) + deltaPct(it, localGhs) } ?: "—",
                    if (mmp.error != null) WARN else GREEN,
                )
            } else {
                CompareRow("MMP", "off", DIM)
            }
        }
    }

    @Composable
    private fun CompareRow(label: String, value: String, valueColor: Color) {
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                label,
                style = TextStyle(color = ColorProvider(DIM), fontSize = 12.sp()),
                modifier = GlanceModifier.padding(end = 8.dp),
            )
            Text(
                value,
                style = TextStyle(
                    color = ColorProvider(valueColor),
                    fontSize = 13.sp(), fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    private fun deltaPct(other: Double, local: Double): String {
        if (local <= 0 || other <= 0) return ""
        val pct = (other - local) / local * PERCENT
        return "  (" + (if (pct >= 0) "+" else "") + "%.1f%%)".format(pct)
    }

    private companion object {
        val DIM = Color(0xFF93A3B4)
        val ACCENT = Color(0xFF3987E5)
        val GREEN = Color(0xFF2BD97C)
        val WARN = Color(0xFFE5A439)
        const val PERCENT = 100.0
        const val GHS_PER_THS = 1000.0
        const val STALE_AFTER_MIN = 3
        val BG = Color(0xFF0B0F14)
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

class CompareWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompareWidget()
}
