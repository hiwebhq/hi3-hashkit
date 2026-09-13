package hi3.hashkit.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Refresh every Hashkit home-screen widget; called at the end of each poll cycle. */
suspend fun updateAllWidgets(context: Context) {
    runCatching { HashkitWidget().updateAll(context) }
    runCatching { CompareWidget().updateAll(context) }
    runCatching { FleetTableWidget().updateAll(context) }
}
