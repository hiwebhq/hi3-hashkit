package hi3.hashkit.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives the phone's fleet-summary DataItem, caches it locally, and asks the tile to
 * refresh so the watch face's tile shows the latest numbers.
 */
class FleetDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var changed = false
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path != WearContract.PATH_FLEET_SUMMARY) continue
            val map = DataMapItem.fromDataItem(item).dataMap
            FleetStore.save(
                this,
                FleetSummary(
                    totalHashrateGhs = map.getDouble(WearContract.KEY_TOTAL_HASHRATE_GHS, 0.0),
                    online = map.getInt(WearContract.KEY_ONLINE, 0),
                    offline = map.getInt(WearContract.KEY_OFFLINE, 0),
                    total = map.getInt(WearContract.KEY_TOTAL, 0),
                    worstTempC = if (map.containsKey(WearContract.KEY_WORST_TEMP_C))
                        map.getDouble(WearContract.KEY_WORST_TEMP_C) else null,
                    updatedAtMs = map.getLong(WearContract.KEY_UPDATED_AT_MS, 0L),
                ),
            )
            changed = true
        }
        if (changed) {
            TileService.getUpdater(this).requestUpdate(FleetTileService::class.java)
        }
    }
}
