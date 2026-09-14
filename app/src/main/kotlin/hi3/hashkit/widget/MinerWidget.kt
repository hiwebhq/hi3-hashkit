package hi3.hashkit.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import hi3.hashkit.data.db.PersonalBestDao
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.MainActivity
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Home-screen widget for ONE miner — the single-Bitaxe view: name, live hashrate, chip
 * temp and best share. The miner is chosen once in [MinerWidgetConfigActivity] and kept
 * in the widget's own Glance state; tapping opens that miner's detail.
 */
class MinerWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MinerWidgetEntryPoint {
        fun minerRepository(): MinerRepository
        fun personalBestDao(): PersonalBestDao
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors
            .fromApplication(context.applicationContext, MinerWidgetEntryPoint::class.java)
        val repo = entry.minerRepository()
        val minerId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[MINER_ID_KEY]
        val entity = minerId?.let { repo.observeMinerEntity(it).first() }
        val miner = entity?.let { repo.toDomain(it, Instant.now()) }
        val best = minerId?.let { entry.personalBestDao().bestFor(it) } ?: miner?.lastTelemetry?.bestDifficulty
        val openIntent = Intent(context, MainActivity::class.java)
            .setAction("$OPEN_ACTION_PREFIX${minerId ?: 0}")
            .putExtra(MainActivity.EXTRA_OPEN_MINER_ID, minerId ?: -1L)

        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(BACKGROUND).padding(14.dp)
                    .clickable(actionStartActivity(openIntent)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (miner == null) {
                    Text(
                        context.getString(R.string.widget_pick_miner_empty),
                        style = TextStyle(color = ColorProvider(DIM), fontSize = 12.sp()),
                    )
                } else {
                    MinerBody(miner, best)
                }
            }
        }
    }

    @Composable
    private fun MinerBody(miner: hi3.hashkit.domain.model.Miner, best: Double?) {
        val t = miner.lastTelemetry
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(8.dp).background(statusColor(miner.status))) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                miner.name,
                style = TextStyle(color = ColorProvider(DIM), fontSize = 12.sp()),
                maxLines = 1,
            )
        }
        Text(
            Units.formatHashrate(t?.hashrateGhs?.value),
            style = TextStyle(color = ColorProvider(ACCENT), fontSize = 24.sp(), fontWeight = FontWeight.Bold),
        )
        Text(
            listOfNotNull(
                Units.formatTemp(t?.chipTempC?.value).takeIf { it != "—" },
                best?.let { "★ " + Units.formatDifficulty(it) },
            ).joinToString("  ·  "),
            style = TextStyle(color = ColorProvider(TEXT), fontSize = 12.sp()),
            maxLines = 1,
        )
    }

    private fun statusColor(status: MinerStatus): Color = when (status) {
        MinerStatus.ONLINE -> ONLINE
        MinerStatus.DEGRADED -> DEGRADED
        MinerStatus.OFFLINE -> OFFLINE
        MinerStatus.UNKNOWN -> DIM
    }

    companion object {
        val MINER_ID_KEY = longPreferencesKey("minerId")
        private const val OPEN_ACTION_PREFIX = "hi3.hashkit.OPEN_MINER_"
        private val BACKGROUND = Color(0xFF0B0F14)
        private val ACCENT = Color(0xFF3987E5)
        private val TEXT = Color(0xFFE8EEF4)
        private val DIM = Color(0xFF93A3B4)
        private val ONLINE = Color(0xFF2BD97C)
        private val DEGRADED = Color(0xFFF5B94A)
        private val OFFLINE = Color(0xFFE5484D)
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

class MinerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MinerWidget()
}
