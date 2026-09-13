package hi3.hashkit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import hi3.hashkit.R
import hi3.hashkit.core.Units
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Home-screen mini fleet table: one compact row per miner (status dot, name, rate,
 * temp), highest hashrate first, capped to fit a small widget. Offline miners sort
 * last so trouble is visible without opening the app. Tap opens the app.
 */
class FleetTableWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FleetTableEntryPoint {
        fun minerRepository(): MinerRepository
    }

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, FleetTableEntryPoint::class.java)
            .minerRepository()
        val now = Instant.now()
        val miners = repo.observeMinerEntities().first()
            .filter { !it.isDemo }
            .map { repo.toDomain(it, now) }
            .sortedWith(
                compareBy<Miner> { it.status == MinerStatus.OFFLINE }
                    .thenByDescending { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
            )
        val shown = miners.take(MAX_ROWS)
        val totalGhs = miners
            .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
            .sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }

        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(BG).padding(10.dp)
                    .clickable(actionStartActivity<hi3.hashkit.ui.MainActivity>()),
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(
                        LocalContext.current.getString(R.string.widget_fleet),
                        style = TextStyle(color = ColorProvider(DIM), fontSize = 10.sp()),
                    )
                    Text(
                        "  " + Units.formatHashrate(totalGhs),
                        style = TextStyle(
                            color = ColorProvider(ACCENT), fontSize = 10.sp(),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                if (shown.isEmpty()) {
                    Text(
                        LocalContext.current.getString(R.string.widget_no_miners_yet),
                        style = TextStyle(color = ColorProvider(DIM), fontSize = 12.sp()),
                    )
                }
                shown.forEach { MinerRow(it) }
                if (miners.size > shown.size) {
                    Text(
                        LocalContext.current.getString(R.string.widget_more_count, miners.size - shown.size),
                        style = TextStyle(color = ColorProvider(DIM), fontSize = 10.sp()),
                    )
                }
            }
        }
    }

    @Composable
    private fun MinerRow(miner: Miner) {
        val t = miner.lastTelemetry
        val dot = when (miner.status) {
            MinerStatus.ONLINE -> GREEN
            MinerStatus.DEGRADED -> WARN
            MinerStatus.OFFLINE -> RED
            MinerStatus.UNKNOWN -> DIM
        }
        Row(modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("●", style = TextStyle(color = ColorProvider(dot), fontSize = 10.sp()))
            Text(
                " " + miner.name.take(NAME_CHARS),
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp()),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                if (miner.status == MinerStatus.OFFLINE) LocalContext.current.getString(R.string.widget_offline)
                else Units.formatHashrate(t?.hashrateGhs?.value),
                style = TextStyle(color = ColorProvider(ACCENT), fontSize = 11.sp()),
            )
            Text(
                t?.chipTempC?.value?.let { "  %.0f°".format(it) } ?: "",
                style = TextStyle(color = ColorProvider(DIM), fontSize = 11.sp()),
            )
        }
    }

    private companion object {
        const val MAX_ROWS = 6
        const val NAME_CHARS = 14
        val BG = Color(0xFF0B0F14)
        val DIM = Color(0xFF93A3B4)
        val ACCENT = Color(0xFF3987E5)
        val GREEN = Color(0xFF2BD97C)
        val WARN = Color(0xFFE5A439)
        val RED = Color(0xFFE5544B)
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

class FleetTableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FleetTableWidget()
}
