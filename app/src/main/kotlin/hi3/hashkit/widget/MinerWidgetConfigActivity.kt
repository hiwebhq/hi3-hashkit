package hi3.hashkit.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import hi3.hashkit.R
import hi3.hashkit.data.prefs.AppSettings
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.ui.theme.Hi3MinerWatchTheme
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Widget-configuration screen: pick which miner a [MinerWidget] instance shows. */
@AndroidEntryPoint
class MinerWidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var minerRepository: MinerRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Cancelled until a miner is picked, so a back-out removes the placeholder widget.
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val miners by minerRepository.observeMinerEntities().collectAsState(initial = emptyList())
            Hi3MinerWatchTheme(themeMode = settings.themeMode, themeColor = settings.themeColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = HiBrand.background) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.widget_pick_miner_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        val real = miners.filter { !it.isDemo }
                        if (real.isEmpty()) {
                            Text(stringResource(R.string.widget_pick_miner_empty), color = HiBrand.textSecondary)
                        }
                        LazyColumn {
                            items(real, key = { it.id }) { miner ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { pick(miner.id) }
                                        .padding(vertical = 12.dp),
                                ) {
                                    Text(miner.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        listOfNotNull(miner.model, miner.host).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HiBrand.textSecondary,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pick(minerId: Long) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@MinerWidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@MinerWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[MinerWidget.MINER_ID_KEY] = minerId }
            }
            MinerWidget().update(this@MinerWidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
