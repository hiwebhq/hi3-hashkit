package hi3.hashkit.widget

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import androidx.glance.appwidget.updateAll

/** Refresh every Hashkit home-screen widget and the Quick Settings tile; called at the end of each poll cycle. */
suspend fun updateAllWidgets(context: Context) {
    runCatching { HashkitWidget().updateAll(context) }
    runCatching { CompareWidget().updateAll(context) }
    runCatching { FleetTableWidget().updateAll(context) }
    runCatching { MinerWidget().updateAll(context) }
    runCatching {
        TileService.requestListeningState(context, ComponentName(context, HashrateTileService::class.java))
    }
}
