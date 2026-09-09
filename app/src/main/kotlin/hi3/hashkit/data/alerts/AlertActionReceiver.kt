package hi3.hashkit.data.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import hi3.hashkit.data.db.AlertDao
import hi3.hashkit.data.repo.ControlRepository
import hi3.hashkit.data.repo.MinerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** Handles the Reboot / Acknowledge buttons on an alert notification. */
class AlertActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun controlRepository(): ControlRepository
        fun minerRepository(): MinerRepository
        fun alertDao(): AlertDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val minerId = intent.getLongExtra(EXTRA_MINER_ID, -1L).takeIf { it > 0 } ?: return
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_ACK -> deps.alertDao().acknowledgeOpen(minerId, type)
                    ACTION_REBOOT -> {
                        val entity = deps.minerRepository().observeMinerEntity(minerId).firstOrNull()
                        if (entity != null) deps.controlRepository().reboot(entity)
                        deps.alertDao().acknowledgeOpen(minerId, type)
                    }
                }
                if (notificationId >= 0) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REBOOT = "hi3.hashkit.ACTION_ALERT_REBOOT"
        const val ACTION_ACK = "hi3.hashkit.ACTION_ALERT_ACK"
        const val EXTRA_MINER_ID = "minerId"
        const val EXTRA_TYPE = "type"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
