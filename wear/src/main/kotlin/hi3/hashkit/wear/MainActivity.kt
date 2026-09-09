package hi3.hashkit.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

@Composable
private fun WearApp() {
    val context = LocalContext.current
    var summary by remember { mutableStateOf(FleetStore.load(context)) }

    // On open, pull the latest DataItem directly in case a background update was missed.
    LaunchedEffect(Unit) {
        val latest = withContext(Dispatchers.IO) { fetchLatestSummary(context) }
        if (latest != null) {
            FleetStore.save(context, latest)
            summary = latest
        }
    }

    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Hi3 Hashkit",
                    color = Color(summary.accentArgb),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.title3,
                )
                if (summary.isEmpty) {
                    Text(
                        "Open Hi3 Hashkit on your phone to sync fleet status.",
                        color = Color(0xFF93A3B4),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2,
                    )
                } else {
                    Text(
                        WearFormat.hashrate(summary.totalHashrateGhs),
                        color = Color(0xFFE8EEF4),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.display3,
                    )
                    Text(
                        "${summary.online}/${summary.total} online",
                        color = Color(0xFF2BD97C),
                        style = MaterialTheme.typography.body1,
                    )
                    val extra = buildString {
                        if (summary.offline > 0) append("${summary.offline} offline")
                        summary.worstTempC?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append("${it.toInt()}°C")
                        }
                    }
                    if (extra.isNotEmpty()) {
                        Text(
                            extra,
                            color = Color(0xFF93A3B4),
                            style = MaterialTheme.typography.caption2,
                        )
                    }
                }
            }
        }
    }
}

/** Read the newest fleet-summary DataItem the phone has published, or null if none. */
private fun fetchLatestSummary(context: android.content.Context): FleetSummary? = runCatching {
    val buffer = Tasks.await(Wearable.getDataClient(context).dataItems)
    try {
        buffer
            .filter { it.uri.path == WearContract.PATH_FLEET_SUMMARY }
            .map { DataMapItem.fromDataItem(it).dataMap }
            .maxByOrNull { it.getLong(WearContract.KEY_UPDATED_AT_MS, 0L) }
            ?.let { map ->
                FleetSummary(
                    totalHashrateGhs = map.getDouble(WearContract.KEY_TOTAL_HASHRATE_GHS, 0.0),
                    online = map.getInt(WearContract.KEY_ONLINE, 0),
                    offline = map.getInt(WearContract.KEY_OFFLINE, 0),
                    total = map.getInt(WearContract.KEY_TOTAL, 0),
                    worstTempC = if (map.containsKey(WearContract.KEY_WORST_TEMP_C))
                        map.getDouble(WearContract.KEY_WORST_TEMP_C) else null,
                    updatedAtMs = map.getLong(WearContract.KEY_UPDATED_AT_MS, 0L),
                    accentArgb = if (map.containsKey(WearContract.KEY_ACCENT_ARGB))
                        map.getInt(WearContract.KEY_ACCENT_ARGB) else FleetSummary.DEFAULT_ACCENT,
                )
            }
    } finally {
        buffer.release()
    }
}.getOrNull()
