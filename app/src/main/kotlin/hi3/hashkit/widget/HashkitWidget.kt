package hi3.hashkit.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import hi3.hashkit.core.Units
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.MinerStatus
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Home-screen widget: fleet total hashrate + online/total, tapping opens the app.
 * Reads the current fleet from the repository via a Hilt entry point (widgets run
 * outside the activity graph). Refreshed at the end of each foreground poll cycle.
 */
class HashkitWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun minerRepository(): MinerRepository
    }

    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .minerRepository()
        val miners = repo.observeMinerEntities().first()
            .filter { !it.isDemo }
            .map { repo.toDomain(it, Instant.now()) }
        val live = miners.filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        val totalGhs = live.sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        val online = miners.count { it.status == MinerStatus.ONLINE }

        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(Color(0xFF0B0F14)).padding(14.dp)
                    .clickable(actionStartActivity<hi3.hashkit.ui.MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Hi3 Hashkit",
                    style = TextStyle(color = ColorProvider(Color(0xFF93A3B4)), fontSize = 12.sp()),
                )
                Text(
                    Units.formatHashrate(totalGhs),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF3987E5)),
                        fontSize = 24.sp(), fontWeight = FontWeight.Bold,
                    ),
                )
                Row {
                    Text(
                        "$online/${miners.size} online",
                        style = TextStyle(color = ColorProvider(Color(0xFF2BD97C)), fontSize = 12.sp()),
                    )
                }
            }
        }
    }

}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

class HashkitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HashkitWidget()
}
